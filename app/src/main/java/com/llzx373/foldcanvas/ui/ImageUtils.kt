package com.llzx373.foldcanvas.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri

object ImageUtils {

    /** 解码并居中裁剪缩放到目标分辨率（用于自定义外屏/内屏壁纸）。 */
    fun decodeCenterCrop(context: Context, uri: Uri, targetWidth: Int, targetHeight: Int): Bitmap? {
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
        val raw = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null
        return centerCrop(raw, targetWidth, targetHeight)
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
}
