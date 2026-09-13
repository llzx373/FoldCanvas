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

        val result = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val scale = maxOf(
            targetWidth / raw.width.toFloat(),
            targetHeight / raw.height.toFloat(),
        )
        val left = (targetWidth - raw.width * scale) / 2f
        val top = (targetHeight - raw.height * scale) / 2f
        canvas.translate(left, top)
        canvas.scale(scale, scale)
        canvas.drawBitmap(raw, 0f, 0f, null)
        raw.recycle()
        return result
    }
}
