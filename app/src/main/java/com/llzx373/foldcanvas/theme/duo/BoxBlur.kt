package com.llzx373.foldcanvas.theme.duo

import android.graphics.Bitmap

/**
 * 滑动窗口盒式模糊（三次往返近似高斯），纯软件实现以兼容 minSdk 26，
 * 用于离线生成展屏模糊动画帧（不做运行时渲染）。
 * 时间复杂度与半径无关：水平 + 垂直各一遍 O(w·h)。
 */
object BoxBlur {

    /** 返回源位图的模糊副本；radius <= 0 时返回原图的拷贝。 */
    fun blur(src: Bitmap, radius: Int): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        if (radius <= 0) return out
        val w = out.width
        val h = out.height
        val pixels = IntArray(w * h)
        out.getPixels(pixels, 0, w, 0, 0, w, h)
        repeat(3) {
            boxPass(pixels, w, h, radius, horizontal = true)
            boxPass(pixels, w, h, radius, horizontal = false)
        }
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    private fun boxPass(pixels: IntArray, w: Int, h: Int, radius: Int, horizontal: Boolean) {
        val outer = if (horizontal) h else w
        val inner = if (horizontal) w else h
        val div = 2 * radius + 1
        val line = IntArray(inner)
        for (o in 0 until outer) {
            // 取出一行（或一列）
            for (i in 0 until inner) {
                line[i] = pixels[if (horizontal) o * w + i else i * w + o]
            }
            var sumA = 0
            var sumR = 0
            var sumG = 0
            var sumB = 0
            // 以右缘像素填充窗口左侧，避免边缘发黑
            for (i in -radius..radius) {
                val c = line[i.coerceIn(0, inner - 1)]
                sumA += c ushr 24
                sumR += c shr 16 and 0xFF
                sumG += c shr 8 and 0xFF
                sumB += c and 0xFF
            }
            for (i in 0 until inner) {
                val c = ((sumA / div) shl 24) or ((sumR / div) shl 16) or
                    ((sumG / div) shl 8) or (sumB / div)
                pixels[if (horizontal) o * w + i else i * w + o] = c
                val addC = line[(i + radius + 1).coerceAtMost(inner - 1)]
                val subC = line[(i - radius).coerceIn(0, inner - 1)]
                sumA += (addC ushr 24) - (subC ushr 24)
                sumR += (addC shr 16 and 0xFF) - (subC shr 16 and 0xFF)
                sumG += (addC shr 8 and 0xFF) - (subC shr 8 and 0xFF)
                sumB += (addC and 0xFF) - (subC and 0xFF)
            }
        }
    }
}
