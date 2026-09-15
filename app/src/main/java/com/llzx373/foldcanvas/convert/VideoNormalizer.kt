package com.llzx373.foldcanvas.convert

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * 用户视频规格化：截取 [startMs, endMs] 区间、居中裁剪缩放到目标分辨率
 * （不超出源分辨率，避免放大模糊）、按源规格自适应码率，输出适合壁纸播放/抽帧的 mp4。
 */
@androidx.annotation.OptIn(UnstableApi::class)
class VideoNormalizer(private val context: Context) {

    private data class SourceInfo(
        val width: Int,
        val height: Int,
        val fps: Int?,
        val bitrate: Int?,
    )

    suspend fun normalize(
        input: Uri,
        output: File,
        targetWidth: Int,
        targetHeight: Int,
        startMs: Long,
        endMs: Long,
        onProgress: (Float) -> Unit = {},
    ): Result<File> = withContext(Dispatchers.Main) {
        val source = withContext(Dispatchers.IO) { readSourceInfo(input) }

        // 源小于目标时按比例收缩输出尺寸，不做放大
        var outWidth = targetWidth
        var outHeight = targetHeight
        if (source != null) {
            val upscale = maxOf(
                targetWidth / source.width.toFloat(),
                targetHeight / source.height.toFloat(),
            )
            if (upscale > 1f) {
                outWidth = (targetWidth / upscale).toInt() and -2
                outHeight = (targetHeight / upscale).toInt() and -2
            }
        }

        val bitrate = pickBitrate(outWidth, outHeight, source)
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setEncoderFactory(
                DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(
                        VideoEncoderSettings.Builder()
                            .setBitrate(bitrate)
                            .setBitrateMode(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
                            .setEncodingProfileLevel(
                                MediaCodecInfo.CodecProfileLevel.AVCProfileHigh,
                                MediaCodecInfo.CodecProfileLevel.AVCLevel51,
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        val clipping = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(startMs)
            .setEndPositionMs(endMs)
            .build()
        val mediaItem = MediaItem.Builder()
            .setUri(input)
            .setClippingConfiguration(clipping)
            .build()
        val presentation = Presentation.createForWidthAndHeight(
            outWidth, outHeight, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP,
        )
        val edited = EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(emptyList(), listOf(presentation)))
            .build()
        val composition = Composition.Builder(
            EditedMediaItemSequence.Builder(listOf(edited)).build(),
        ).build()

        val result = kotlinx.coroutines.coroutineScope {
            val poller = launch {
                val holder = ProgressHolder()
                while (isActive) {
                    if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(holder.progress / 100f)
                    }
                    delay(200)
                }
            }
            val outcome = suspendCancellableCoroutine<Result<File>> { cont ->
                transformer.addListener(object : Transformer.Listener {
                    override fun onCompleted(comp: Composition, exportResult: ExportResult) {
                        onProgress(1f)
                        cont.resume(Result.success(output))
                    }

                    override fun onError(
                        comp: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        cont.resume(Result.failure(exportException))
                    }
                })
                cont.invokeOnCancellation { transformer.cancel() }
                output.parentFile?.mkdirs()
                transformer.start(composition, output.absolutePath)
            }
            poller.cancel()
            outcome
        }

        result
    }

    private fun readSourceInfo(uri: Uri): SourceInfo? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            fun meta(key: Int) = retriever.extractMetadata(key)?.toIntOrNull()
            var width = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) ?: return null
            var height = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT) ?: return null
            val rotation = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) ?: 0
            if (rotation == 90 || rotation == 270) {
                val tmp = width; width = height; height = tmp
            }
            SourceInfo(
                width = width,
                height = height,
                fps = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                    ?.toFloatOrNull()?.toInt(),
                bitrate = meta(MediaMetadataRetriever.METADATA_KEY_BITRATE),
            )
        } finally {
            retriever.release()
        }
    }.getOrNull()

    private fun pickBitrate(width: Int, height: Int, source: SourceInfo?): Int {
        val pixels = width.toLong() * height
        val fps = source?.fps?.coerceIn(1, 120) ?: 30
        val heuristic = (pixels * fps * BITS_PER_PIXEL).toInt()
        val fromSource = source?.bitrate?.let {
            (it.toLong() * pixels / (source.width.toLong() * source.height)).toInt()
        } ?: 0
        return maxOf(heuristic, fromSource).coerceIn(MIN_VIDEO_BITRATE, MAX_VIDEO_BITRATE)
    }

    companion object {
        /** H.264 High profile 高质量档位的每像素每帧比特数。 */
        private const val BITS_PER_PIXEL = 0.2
        private const val MIN_VIDEO_BITRATE = 8_000_000
        private const val MAX_VIDEO_BITRATE = 45_000_000
    }
}
