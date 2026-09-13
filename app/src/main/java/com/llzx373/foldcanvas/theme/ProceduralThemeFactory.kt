package com.llzx373.foldcanvas.theme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.llzx373.foldcanvas.theme.model.ThemeCategory
import kotlin.math.sin

/**
 * 内置占位主题的程序化素材生成：渐变外屏/内屏壁纸 + 展开动画帧序列。
 * 正式美术资源到位后替换 assets 即可，渲染管线不变。
 */
object ProceduralThemeFactory {

    data class BuiltinSpec(
        val id: String,
        val name: String,
        val outerColors: IntArray = intArrayOf(),
        val innerColors: IntArray = intArrayOf(),
        val glowColor: Int = 0,
        /** 非空表示素材来自 assets（outerFile/innerFile/animationFile 为包内文件名） */
        val assetDir: String? = null,
        val outerFile: String = "outer.png",
        val innerFile: String = "inner.png",
        val animationFile: String = "animation.mp4",
        /** 主题类别；asset 主题固定为展屏动画。 */
        val category: ThemeCategory = ThemeCategory.ANIMATION,
    )

    val specs = listOf(
        BuiltinSpec(
            id = "dawn",
            name = "晨曦金",
            outerColors = intArrayOf(0xFF1A1A2E.toInt(), 0xFF4A3B52.toInt(), 0xFFE8A87C.toInt()),
            innerColors = intArrayOf(0xFF0F0F23.toInt(), 0xFF6B4E71.toInt(), 0xFFF5C396.toInt()),
            glowColor = 0xFFFFD9A0.toInt(),
        ),
        BuiltinSpec(
            id = "abyss",
            name = "深海蓝",
            outerColors = intArrayOf(0xFF021B2D.toInt(), 0xFF0A4D68.toInt(), 0xFF088388.toInt()),
            innerColors = intArrayOf(0xFF010F1A.toInt(), 0xFF0D5C7F.toInt(), 0xFF3FA796.toInt()),
            glowColor = 0xFF7FDBFF.toInt(),
        ),
        BuiltinSpec(
            id = "nebula",
            name = "星云紫",
            outerColors = intArrayOf(0xFF160F29.toInt(), 0xFF3B2E5A.toInt(), 0xFF8E44AD.toInt()),
            innerColors = intArrayOf(0xFF0D0A1A.toInt(), 0xFF4A3A6B.toInt(), 0xFFB056C4.toInt()),
            glowColor = 0xFFD9A7FF.toInt(),
        ),
        BuiltinSpec(
            id = "wings",
            name = "展翼",
            assetDir = "themes/wings",
        ),
        BuiltinSpec(
            id = "wings_wide",
            name = "展翼·宽幅",
            assetDir = "themes/wings_wide",
            outerFile = "outer_wide.png",
            innerFile = "inner_wide.png",
            animationFile = "animation_wide.mp4",
        ),
        BuiltinSpec(
            id = "lakes",
            name = "湖光双色",
            category = ThemeCategory.IMAGES,
            outerColors = intArrayOf(0xFF0B2545.toInt(), 0xFF136F63.toInt(), 0xFF8AB17D.toInt()),
            innerColors = intArrayOf(0xFF2B2D42.toInt(), 0xFF8D6A9F.toInt(), 0xFFEFB0A1.toInt()),
        ),
        BuiltinSpec(
            id = "mist",
            name = "雾境朦胧",
            category = ThemeCategory.DUO_BLUR,
            innerColors = intArrayOf(0xFF141E30.toInt(), 0xFF3A5A78.toInt(), 0xFFA8C0D6.toInt()),
        ),
    )

    fun gradientBitmap(width: Int, height: Int, colors: IntArray): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                colors, null, Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        return bitmap
    }

    /**
     * 生成一帧展开动画：底色渐变随进度在内屏配色间过渡，中心光晕随进度扩散，
     * 模拟"外屏画面延展为内屏画面"的效果。
     */
    fun animationFrame(
        width: Int,
        height: Int,
        progress: Float,
        spec: BuiltinSpec,
    ): Bitmap {
        val t = progress.coerceIn(0f, 1f)
        val colors = IntArray(spec.outerColors.size) { i ->
            blend(spec.outerColors[i], spec.innerColors[i % spec.innerColors.size], t)
        }
        val bitmap = gradientBitmap(width, height, colors)
        val canvas = Canvas(bitmap)
        val maxRadius = width * 0.95f
        val radius = maxRadius * (0.15f + 0.85f * t)
        val cx = width / 2f + sin(t * Math.PI.toFloat()) * width * 0.05f
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, height / 2f, radius,
                withAlpha(spec.glowColor, (0.55f * (1f - t * 0.4f) * 255).toInt()),
                withAlpha(spec.glowColor, 0),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), glow)
        return bitmap
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        val inv = 1f - t
        val alpha = ((a ushr 24) * inv + (b ushr 24) * t).toInt() shl 24
        val r = ((a shr 16 and 0xFF) * inv + (b shr 16 and 0xFF) * t).toInt() shl 16
        val g = ((a shr 8 and 0xFF) * inv + (b shr 8 and 0xFF) * t).toInt() shl 8
        val bl = ((a and 0xFF) * inv + (b and 0xFF) * t).toInt()
        return alpha or r or g or bl
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
}
