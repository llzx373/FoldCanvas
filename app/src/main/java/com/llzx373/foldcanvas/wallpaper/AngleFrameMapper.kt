package com.llzx373.foldcanvas.wallpaper

import kotlin.math.roundToInt

object AngleFrameMapper {

    /** 铰链角度 → 展开进度 [0,1]。angle<=start 为 0（外屏），>=end 为 1（内屏）。 */
    fun progress(angle: Float, start: Float, end: Float): Float {
        if (end <= start) return if (angle >= end) 1f else 0f
        return ((angle - start) / (end - start)).coerceIn(0f, 1f)
    }

    /** 展开进度 → 帧序号。 */
    fun frameIndex(progress: Float, frameCount: Int): Int {
        if (frameCount <= 0) return 0
        return (progress.coerceIn(0f, 1f) * (frameCount - 1)).roundToInt()
            .coerceIn(0, frameCount - 1)
    }

    /** EMA 低通滤波单步：current 向 target 靠近 alpha 比例（alpha ∈ (0,1]）。 */
    fun lowPass(current: Float, target: Float, alpha: Float): Float {
        val a = alpha.coerceIn(0f, 1f)
        return current + a * (target - current)
    }
}
