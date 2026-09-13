package com.llzx373.foldcanvas.convert

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.llzx373.foldcanvas.theme.ThemeRepository
import java.io.File
import java.io.FileOutputStream

/**
 * 把展开动画视频离线抽帧为 JPEG 帧序列（角度联动渲染直接消费帧，不做运行时 seek）。
 */
class FrameExtractor(private val context: Context) {

    fun extract(
        video: Uri,
        outDir: File,
        frameCount: Int,
        targetWidth: Int,
        onProgress: (Float) -> Unit = {},
    ): Boolean {
        val retriever = MediaMetadataRetriever()
        return try {
            if (video.scheme == "file" || video.scheme == null) {
                retriever.setDataSource(video.path)
            } else {
                retriever.setDataSource(context, video)
            }
            val durationUs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.times(1000L) ?: return false
            if (durationUs <= 0) return false
            outDir.mkdirs()
            // 末帧时间向内收敛，越界取帧在部分设备上会返回 null
            val lastUs = (durationUs - 1000L).coerceAtLeast(0L)
            var written = 0
            for (i in 0 until frameCount) {
                val timeUs = minOf(lastUs, durationUs * i / (frameCount - 1))
                val raw = retriever.getFrameAtTime(
                    timeUs, MediaMetadataRetriever.OPTION_CLOSEST,
                ) ?: continue
                val scaled = scaleToWidth(raw, targetWidth)
                if (scaled != raw) raw.recycle()
                FileOutputStream(File(outDir, ThemeRepository.frameName(i))).use {
                    scaled.compress(Bitmap.CompressFormat.JPEG, 85, it)
                }
                scaled.recycle()
                written++
                onProgress((i + 1) / frameCount.toFloat())
            }
            return written >= frameCount - 1
        } catch (e: Exception) {
            false
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun scaleToWidth(src: Bitmap, width: Int): Bitmap {
        if (src.width <= width) return src
        val height = src.height * width / src.width
        return Bitmap.createScaledBitmap(src, width, height, true)
    }
}
