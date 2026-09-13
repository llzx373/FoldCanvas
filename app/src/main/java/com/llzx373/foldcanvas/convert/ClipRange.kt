package com.llzx373.foldcanvas.convert

/** 用户视频截取区间的校验与收敛（纯逻辑，便于 JVM 单元测试）。 */
object ClipRange {

    const val MIN_SEGMENT_SEC = 0.5f

    /**
     * 将用户选择的起止秒收敛到 [0, duration]。
     * 返回 null：时长非法，或收敛后区间不足 [MIN_SEGMENT_SEC]。
     */
    fun clamp(startSec: Float, endSec: Float, durationSec: Float): Pair<Float, Float>? {
        if (durationSec <= 0f) return null
        val s = startSec.coerceIn(0f, durationSec)
        val e = endSec.coerceIn(0f, durationSec)
        if (e - s < MIN_SEGMENT_SEC) return null
        return s to e
    }
}
