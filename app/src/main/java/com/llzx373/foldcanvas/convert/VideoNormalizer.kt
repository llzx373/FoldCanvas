package com.llzx373.foldcanvas.convert

import android.content.Context
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
 * 用户视频规格化：截取 [startMs, endMs] 区间、居中裁剪缩放到目标分辨率、
 * H.264 码率上限，输出适合壁纸播放/抽帧的 mp4。
 */
@androidx.annotation.OptIn(UnstableApi::class)
class VideoNormalizer(private val context: Context) {

    suspend fun normalize(
        input: Uri,
        output: File,
        targetWidth: Int,
        targetHeight: Int,
        startMs: Long,
        endMs: Long,
        onProgress: (Float) -> Unit = {},
    ): Result<File> = withContext(Dispatchers.Main) {
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setEncoderFactory(
                DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(
                        VideoEncoderSettings.Builder()
                            .setBitrate(MAX_VIDEO_BITRATE)
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
            targetWidth, targetHeight, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP,
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

    companion object {
        const val MAX_VIDEO_BITRATE = 8_000_000
    }
}
