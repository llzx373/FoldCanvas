package com.llzx373.foldcanvas.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build

object ImageUtils {

    /** 解码并居中裁剪缩放到目标分辨率（用于自定义外屏/内屏壁纸）。 */
    fun decodeCenterCrop(context: Context, uri: Uri, targetWidth: Int, targetHeight: Int): Bitmap? {
        val raw = decodeSampled(context, uri, targetWidth, targetHeight)
            ?: decodeWithImageDecoder(context, uri, targetWidth, targetHeight)
        return raw?.let { centerCrop(it, targetWidth, targetHeight) }
    }

    /** BitmapFactory 采样解码；异常或格式不支持（部分 HEIC/云端图）返回 null。 */
    private fun decodeSampled(
        context: Context, uri: Uri, targetWidth: Int, targetHeight: Int,
    ): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetWidth &&
            bounds.outHeight / (sample * 2) >= targetHeight
        ) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    }.getOrNull()

    /** ImageDecoder 兜底解码（HEIC/AVIF 等新格式支持更好），API 28+。 */
    private fun decodeWithImageDecoder(
        context: Context, uri: Uri, targetWidth: Int, targetHeight: Int,
    ): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val size = info.size
                var sample = 1
                while (size.width / (sample * 2) >= targetWidth &&
                    size.height / (sample * 2) >= targetHeight
                ) sample *= 2
                decoder.setTargetSampleSize(sample)
            }
        }.getOrNull()
    }

    /** 等比缩放铺满目标尺寸后居中裁剪。 */
    fun centerCrop(src: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val result = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val scale = maxOf(
            targetWidth / src.width.toFloat(),
            targetHeight / src.height.toFloat(),
        )
        val left = (targetWidth - src.width * scale) / 2f
        val top = (targetHeight - src.height * scale) / 2f
        canvas.translate(left, top)
        canvas.scale(scale, scale)
        canvas.drawBitmap(src, 0f, 0f, null)
        if (result != src) src.recycle()
        return result
    }

    /** 取位图右半部分（宽屏折叠外屏壁纸的派生来源）。 */
    fun rightHalf(src: Bitmap): Bitmap =
        Bitmap.createBitmap(src, src.width / 2, 0, src.width - src.width / 2, src.height)

    /** 取位图左半部分（内外图片类别外屏壁纸的派生来源之一）。 */
    fun leftHalf(src: Bitmap): Bitmap =
        Bitmap.createBitmap(src, 0, 0, src.width / 2, src.height)

    /** 取位图中间半幅（内外图片类别外屏壁纸的派生来源之一）。 */
    fun centerHalf(src: Bitmap): Bitmap {
        val w = src.width / 2
        return Bitmap.createBitmap(src, (src.width - w) / 2, 0, w, src.height)
    }

    /**
     * 非等比拉伸铺满目标尺寸（x/y 独立缩放，不裁剪）。
     * 展屏模糊类别的外屏派生：完整保留内屏右半幅（含铰链侧边缘），
     * 与 DuoFoldWallpaper 外屏 UV 窗口（x∈[0.5,1], y∈[0,1] 铺满外屏）一致。
     */
    fun scaleFill(src: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val result = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(
            src, null,
            android.graphics.Rect(0, 0, targetWidth, targetHeight),
            android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG),
        )
        if (result != src) src.recycle()
        return result
    }
}
