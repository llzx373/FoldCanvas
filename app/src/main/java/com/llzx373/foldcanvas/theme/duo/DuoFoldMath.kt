package com.llzx373.foldcanvas.theme.duo

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * 展屏模糊（Duo 效果）的进度映射数学，与 Android 框架解耦，便于 JVM 单元测试。
 *
 * 模仿 DuoFoldWallpaper / iPhone Duo 的折叠过渡：以铰链（内屏中线）为锚点，
 * 左面板按 cos 透视投影压缩并伴随渐进模糊，右面板始终保持清晰静止；
 * 进度 0 = 折叠态（左面板完全压缩、模糊最大），进度 1 = 展开态（整幅内屏清晰）。
 */
object DuoFoldMath {

    /** 左面板最大模糊半径（帧宽 1440 下的像素值）。 */
    const val MAX_BLUR_RADIUS = 48f

    /** 外屏外缘最大模糊半径（帧宽 1080 下的像素值）。 */
    const val COVER_MAX_BLUR_RADIUS = 40f

    /** 背景氛围层固定模糊半径（填充左面板压缩后让出的区域）。 */
    const val AMBIENT_BLUR_RADIUS = 24

    /** 背景氛围层压暗透明度（0-255）。 */
    const val AMBIENT_DIM_ALPHA = 50

    /** 左面板折叠时最大压暗透明度（0-255）。 */
    const val PANEL_MAX_DIM_ALPHA = 90

    /**
     * 左面板水平宽度占比：0 = 完全压缩（侧视），1 = 完全展开。
     * sin(p·π/2) = cos((1-p)·π/2)，即余弦边缘投影的等价形式，展开末段自然减速。
     */
    fun panelScale(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return sin(p * PI.toFloat() / 2f)
    }

    /** 左面板模糊半径：面板越接近侧视越模糊，展开到位时为 0。 */
    fun blurRadius(progress: Float, maxRadius: Float = MAX_BLUR_RADIUS): Float =
        (1f - panelScale(progress)) * maxRadius

    /** 左面板压暗透明度：面板越接近侧视越暗，模拟侧视时的光通量损失。 */
    fun panelDimAlpha(progress: Float): Int =
        ((1f - panelScale(progress)) * PANEL_MAX_DIM_ALPHA).toInt().coerceIn(0, 255)

    /** 标准 smoothstep，输入钳制到 [0,1]。 */
    fun smoothstep(x: Float): Float {
        val t = x.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** 外屏动画进度：铰链角度 0°→90° 线性映射并钳制（90° 以上物理上外屏已熄灭）。 */
    fun coverProgress(angleDegrees: Float): Float =
        (angleDegrees / 90f).coerceIn(0f, 1f)

    /** 外屏动画强度：motion = smoothstep(coverProgress)。 */
    fun coverMotion(angleDegrees: Float): Float =
        smoothstep(coverProgress(angleDegrees))

    /**
     * 外屏某竖带（edge：0 = 铰链侧，1 = 外缘）在给定 motion 下的模糊半径。
     * 与参考 shader 一致：radius = maxRadius · motion · edge^1.35。
     */
    fun coverBlurRadius(
        edge: Float,
        motion: Float,
        maxRadius: Float = COVER_MAX_BLUR_RADIUS,
    ): Float = maxRadius * motion.coerceIn(0f, 1f) *
        edge.coerceIn(0f, 1f).pow(1.35f)

    /**
     * 外屏某竖带的压暗比例（0-1）。
     * 与参考 shader 一致：darkG = (edge-0.2)/0.8，effect = motion · darkG^1.35 · 2。
     */
    fun coverDarken(edge: Float, motion: Float): Float {
        val darkG = ((edge.coerceIn(0f, 1f) - 0.2f) / 0.8f).coerceIn(0f, 1f)
        return (motion.coerceIn(0f, 1f) * darkG.pow(1.35f) * 2f).coerceIn(0f, 1f)
    }
}
