package com.llzx373.foldcanvas.theme.duo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import com.llzx373.foldcanvas.theme.ThemeRepository
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/**
 * 展屏模糊（Duo 效果）动画帧离线生成器。
 *
 * 实现思想参照 DuoFoldWallpaper：以铰链（内屏中线）为锚点，展开过程中
 * 左面板按余弦投影从侧视压缩态逐渐展开并伴随渐进模糊与压暗，
 * 右面板始终清晰静止；左面板让出的区域由整幅内屏的模糊氛围层填充。
 * 末帧（progress=1）与内屏壁纸完全一致，首帧（progress=0）只剩清晰右半，
 * 与"外屏 = 内屏右半幅"的静态外屏壁纸衔接。
 *
 * 与视频抽帧（FrameExtractor）同产出：JPEG 帧序列写入帧缓存，渲染管线不变。
 */
class DuoFrameGenerator {

    fun generate(
        innerFile: File,
        outDir: File,
        frameCount: Int,
        targetWidth: Int,
        onProgress: (Float) -> Unit = {},
    ): Boolean {
        return runCatching {
            val raw = BitmapFactory.decodeFile(innerFile.absolutePath) ?: return false
            val width = minOf(targetWidth, raw.width)
            val height = raw.height * width / raw.width
            val inner = if (width != raw.width) {
                Bitmap.createScaledBitmap(raw, width, height, true).also { raw.recycle() }
            } else raw

            val halfW = width / 2
            val fullRect = Rect(0, 0, width, height)
            val rightSrc = Rect(halfW, 0, width, height)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            val ambient = BoxBlur.blur(inner, DuoFoldMath.AMBIENT_BLUR_RADIUS)
            val leftHalf = Bitmap.createBitmap(inner, 0, 0, halfW, height)
            // 8 级模糊金字塔：逐帧按半径选最近级合成，避免每帧整幅重模糊（耗时约降为 1/4）
            val panelLevels = 8
            val panelPyramid = Array(panelLevels + 1) { k ->
                if (k == 0) leftHalf else BoxBlur.blur(
                    leftHalf,
                    (DuoFoldMath.MAX_BLUR_RADIUS * k / panelLevels).roundToInt(),
                )
            }

            outDir.mkdirs()
            for (i in 0 until frameCount) {
                val progress = i / (frameCount - 1).toFloat()
                val frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(frame)
                // 氛围层：整幅内屏模糊 + 压暗，填充左面板压缩后让出的区域
                canvas.drawBitmap(ambient, null, fullRect, paint)
                canvas.drawRect(
                    fullRect,
                    Paint().apply { color = DuoFoldMath.AMBIENT_DIM_ALPHA shl 24 },
                )
                // 右面板：铰链锚定侧，始终清晰静止
                canvas.drawBitmap(inner, rightSrc, rightSrc, paint)
                // 左面板：铰链锚定的余弦压缩 + 渐进模糊 + 压暗
                val panelW = (DuoFoldMath.panelScale(progress) * halfW).roundToInt()
                if (panelW > 0) {
                    val panelRect = Rect(halfW - panelW, 0, halfW, height)
                    val radius = DuoFoldMath.blurRadius(progress)
                    val level = (radius / DuoFoldMath.MAX_BLUR_RADIUS * panelLevels)
                        .roundToInt().coerceIn(0, panelLevels)
                    canvas.drawBitmap(panelPyramid[level], null, panelRect, paint)
                    val dim = DuoFoldMath.panelDimAlpha(progress)
                    if (dim > 0) {
                        canvas.drawRect(panelRect, Paint().apply { color = dim shl 24 })
                    }
                }
                FileOutputStream(File(outDir, ThemeRepository.frameName(i))).use {
                    frame.compress(Bitmap.CompressFormat.JPEG, 85, it)
                }
                frame.recycle()
                onProgress((i + 1) / frameCount.toFloat())
            }
            panelPyramid.forEach { if (it != leftHalf) it.recycle() }
            ambient.recycle()
            leftHalf.recycle()
            inner.recycle()
            true
        }.getOrElse { false }
    }

    /**
     * 外屏（cover）侧动画帧：右半幅窗口（outer.png）全屏呈现，
     * 随展开进度（motion = smoothstep(angle/90°)）从铰链侧（左缘）向外缘
     * 渐进模糊 + 压暗，对应参考实现的 uGrad 铰链→外缘扫掠。
     *
     * 软件实现：4 级【半分辨率】模糊金字塔（模糊为低频信息，半分辨率肉眼无差，
     * 内存峰值从 ~93MB 降到 ~13MB）+ 32 竖带按 edge 选取最近模糊级合成，
     * 再以 6 段线性渐变逼近 edge^1.35 压暗曲线；首帧与 outer.png 一致。
     */
    fun generateCover(
        outerFile: File,
        outDir: File,
        frameCount: Int,
        targetWidth: Int,
        targetHeight: Int,
        onProgress: (Float) -> Unit = {},
    ): Boolean {
        return runCatching {
            val raw = BitmapFactory.decodeFile(outerFile.absolutePath) ?: return false
            val window = if (raw.width != targetWidth || raw.height != targetHeight) {
                Bitmap.createScaledBitmap(raw, targetWidth, targetHeight, true)
                    .also { raw.recycle() }
            } else raw

            val smallW = targetWidth / 2
            val smallH = targetHeight / 2
            val small = Bitmap.createScaledBitmap(window, smallW, smallH, true)
            val levels = 4
            val pyramid = Array(levels + 1) { k ->
                if (k == 0) small else BoxBlur.blur(
                    small,
                    (DuoFoldMath.COVER_MAX_BLUR_RADIUS / 2f * k / levels).roundToInt(),
                )
            }
            val bands = 32
            val bandW = targetWidth / bands
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)

            outDir.mkdirs()
            for (i in 0 until frameCount) {
                val progress = i / (frameCount - 1).toFloat()
                val motion = DuoFoldMath.smoothstep(progress)
                val frame = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(frame)
                for (b in 0 until bands) {
                    val edge = (b + 0.5f) / bands
                    val radius = DuoFoldMath.coverBlurRadius(edge, motion)
                    val k = (radius / DuoFoldMath.COVER_MAX_BLUR_RADIUS * levels)
                        .roundToInt().coerceIn(0, levels)
                    val x0 = b * bandW
                    val x1 = if (b == bands - 1) targetWidth else (b + 1) * bandW
                    val dst = Rect(x0, 0, x1, targetHeight)
                    val src = Rect(
                        x0 / 2, 0,
                        if (b == bands - 1) smallW else x1 / 2, smallH,
                    )
                    canvas.drawBitmap(pyramid[k], src, dst, paint)
                }
                // 压暗渐变：铰链侧 0 → 外缘 coverDarken(1, motion)
                val stops = 6
                val colors = IntArray(stops + 1) { s ->
                    val edge = s / stops.toFloat()
                    (DuoFoldMath.coverDarken(edge, motion) * 255).roundToInt()
                        .coerceIn(0, 255) shl 24
                }
                canvas.drawRect(
                    0f, 0f, targetWidth.toFloat(), targetHeight.toFloat(),
                    Paint().apply {
                        shader = LinearGradient(
                            0f, 0f, targetWidth.toFloat(), 0f,
                            colors, null, Shader.TileMode.CLAMP,
                        )
                    },
                )
                FileOutputStream(File(outDir, ThemeRepository.frameName(i))).use {
                    frame.compress(Bitmap.CompressFormat.JPEG, 85, it)
                }
                frame.recycle()
                onProgress((i + 1) / frameCount.toFloat())
            }
            pyramid.forEach { if (it != small) it.recycle() }
            small.recycle()
            window.recycle()
            true
        }.getOrElse { false }
    }
}
