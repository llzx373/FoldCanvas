package com.llzx373.foldcanvas

import com.llzx373.foldcanvas.wallpaper.AngleFrameMapper
import org.junit.Assert.assertEquals
import org.junit.Test

class AngleFrameMapperTest {

    @Test
    fun `progress clamps below start`() {
        assertEquals(0f, AngleFrameMapper.progress(0f, 30f, 150f))
        assertEquals(0f, AngleFrameMapper.progress(29.9f, 30f, 150f))
    }

    @Test
    fun `progress clamps above end`() {
        assertEquals(1f, AngleFrameMapper.progress(150f, 30f, 150f))
        assertEquals(1f, AngleFrameMapper.progress(180f, 30f, 150f))
    }

    @Test
    fun `progress linear in range`() {
        assertEquals(0.5f, AngleFrameMapper.progress(90f, 30f, 150f), 0.001f)
        assertEquals(0.25f, AngleFrameMapper.progress(60f, 30f, 150f), 0.001f)
    }

    @Test
    fun `progress handles degenerate range`() {
        assertEquals(1f, AngleFrameMapper.progress(160f, 150f, 150f))
        assertEquals(0f, AngleFrameMapper.progress(90f, 150f, 150f))
        assertEquals(0f, AngleFrameMapper.progress(20f, 150f, 150f))
    }

    @Test
    fun `frameIndex maps progress to frames`() {
        assertEquals(0, AngleFrameMapper.frameIndex(0f, 45))
        assertEquals(44, AngleFrameMapper.frameIndex(1f, 45))
        assertEquals(22, AngleFrameMapper.frameIndex(0.5f, 45))
    }

    @Test
    fun `frameIndex guards empty`() {
        assertEquals(0, AngleFrameMapper.frameIndex(0.5f, 0))
    }

    @Test
    fun `angle sweep produces monotonic frame indices`() {
        // 端到端映射：角度 0~180 全量扫描，帧号必须单调不减（无回摆）
        var prev = 0
        var angle = 0f
        while (angle <= 180f) {
            val idx = AngleFrameMapper.frameIndex(
                AngleFrameMapper.progress(angle, 30f, 150f), 45,
            )
            assert(idx >= prev) { "frame index decreased at angle=$angle: $prev -> $idx" }
            prev = idx
            angle += 0.5f
        }
        assertEquals(44, prev)
    }

    @Test
    fun `angle sweep hits every frame exactly in order`() {
        // 0.5° 步进扫描应覆盖全部 45 帧且无跳帧
        val seen = sortedSetOf<Int>()
        var angle = 0f
        while (angle <= 180f) {
            seen += AngleFrameMapper.frameIndex(
                AngleFrameMapper.progress(angle, 30f, 150f), 45,
            )
            angle += 0.5f
        }
        assertEquals((0..44).toSet(), seen)
    }

    @Test
    fun `lowPass single step moves toward target by alpha`() {
        assertEquals(15f, AngleFrameMapper.lowPass(0f, 100f, 0.15f), 0.001f)
        assertEquals(85f, AngleFrameMapper.lowPass(100f, 0f, 0.15f), 0.001f)
    }

    @Test
    fun `lowPass clamps alpha`() {
        assertEquals(100f, AngleFrameMapper.lowPass(0f, 100f, 1.5f), 0.001f)
        assertEquals(0f, AngleFrameMapper.lowPass(0f, 100f, -0.5f), 0.001f)
    }

    @Test
    fun `lowPass converges monotonically to target`() {
        // 模拟铰链静止后自驱收敛：差值必须每步缩小且方向不反转
        var current = 0f
        var prevDiff = Float.MAX_VALUE
        repeat(500) {
            current = AngleFrameMapper.lowPass(current, 173f, 0.15f)
            val diff = kotlin.math.abs(173f - current)
            assert(diff <= prevDiff) { "diff grew: $prevDiff -> $diff" }
            prevDiff = diff
        }
        assertEquals(173f, current, 0.001f)
    }

    @Test
    fun `lowPass filtered sweep keeps frame indices monotonic`() {
        // 端到端：角度单调上升经过 EMA 滤波后，帧号仍须单调不减
        var filtered = 0f
        var prev = 0
        var angle = 0f
        while (angle <= 180f) {
            filtered = AngleFrameMapper.lowPass(filtered, angle, 0.15f)
            val idx = AngleFrameMapper.frameIndex(
                AngleFrameMapper.progress(filtered, 30f, 150f), 45,
            )
            assert(idx >= prev) { "frame index decreased at angle=$angle: $prev -> $idx" }
            prev = idx
            angle += 0.5f
        }
    }
}
