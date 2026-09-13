package com.llzx373.foldcanvas

import com.llzx373.foldcanvas.theme.duo.DuoFoldMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuoFoldMathTest {

    @Test
    fun `panelScale endpoints`() {
        assertEquals(0f, DuoFoldMath.panelScale(0f), 1e-6f)
        assertEquals(1f, DuoFoldMath.panelScale(1f), 1e-6f)
    }

    @Test
    fun `panelScale clamps out of range progress`() {
        assertEquals(0f, DuoFoldMath.panelScale(-0.5f), 1e-6f)
        assertEquals(1f, DuoFoldMath.panelScale(1.5f), 1e-6f)
    }

    @Test
    fun `panelScale monotonically increases`() {
        var prev = -1f
        for (i in 0..100) {
            val s = DuoFoldMath.panelScale(i / 100f)
            assertTrue("scale must be non-decreasing at $i", s >= prev)
            assertTrue(s in 0f..1f)
            prev = s
        }
    }

    @Test
    fun `blurRadius fades from max to zero`() {
        assertEquals(DuoFoldMath.MAX_BLUR_RADIUS, DuoFoldMath.blurRadius(0f), 1e-6f)
        assertEquals(0f, DuoFoldMath.blurRadius(1f), 1e-6f)
        var prev = Float.MAX_VALUE
        for (i in 0..100) {
            val r = DuoFoldMath.blurRadius(i / 100f)
            assertTrue("blur must be non-increasing at $i", r <= prev)
            prev = r
        }
    }

    @Test
    fun `panelDimAlpha range and endpoints`() {
        assertEquals(DuoFoldMath.PANEL_MAX_DIM_ALPHA, DuoFoldMath.panelDimAlpha(0f))
        assertEquals(0, DuoFoldMath.panelDimAlpha(1f))
        for (i in 0..100) {
            assertTrue(DuoFoldMath.panelDimAlpha(i / 100f) in 0..DuoFoldMath.PANEL_MAX_DIM_ALPHA)
        }
    }

    @Test
    fun `final frame is fully unfolded and sharp`() {
        // 末帧必须与内屏壁纸一致：面板完整、无模糊、无压暗
        assertEquals(1f, DuoFoldMath.panelScale(1f), 1e-6f)
        assertEquals(0f, DuoFoldMath.blurRadius(1f), 1e-6f)
        assertEquals(0, DuoFoldMath.panelDimAlpha(1f))
    }

    @Test
    fun `coverProgress maps 0 to 90 degrees and clamps`() {
        assertEquals(0f, DuoFoldMath.coverProgress(0f), 1e-6f)
        assertEquals(0.5f, DuoFoldMath.coverProgress(45f), 1e-6f)
        assertEquals(1f, DuoFoldMath.coverProgress(90f), 1e-6f)
        assertEquals(1f, DuoFoldMath.coverProgress(180f), 1e-6f)
        assertEquals(0f, DuoFoldMath.coverProgress(-10f), 1e-6f)
    }

    @Test
    fun `coverBlurRadius grows from hinge to outer edge`() {
        val motion = 1f
        assertEquals(0f, DuoFoldMath.coverBlurRadius(0f, motion), 1e-6f)
        assertEquals(
            DuoFoldMath.COVER_MAX_BLUR_RADIUS,
            DuoFoldMath.coverBlurRadius(1f, motion),
            1e-6f,
        )
        var prev = -1f
        for (i in 0..100) {
            val r = DuoFoldMath.coverBlurRadius(i / 100f, motion)
            assertTrue(r >= prev)
            prev = r
        }
        // motion=0（完全合盖）时全屏清晰
        assertEquals(0f, DuoFoldMath.coverBlurRadius(1f, 0f), 1e-6f)
    }

    @Test
    fun `coverDarken zero near hinge and bounded`() {
        assertEquals(0f, DuoFoldMath.coverDarken(0f, 1f), 1e-6f)
        assertEquals(0f, DuoFoldMath.coverDarken(0.2f, 1f), 1e-6f)
        assertEquals(1f, DuoFoldMath.coverDarken(1f, 1f), 1e-6f)
        for (i in 0..100) {
            val d = DuoFoldMath.coverDarken(i / 100f, 0.5f)
            assertTrue(d in 0f..1f)
        }
    }

    @Test
    fun `smoothstep endpoints and monotonic`() {
        assertEquals(0f, DuoFoldMath.smoothstep(0f), 1e-6f)
        assertEquals(1f, DuoFoldMath.smoothstep(1f), 1e-6f)
        assertEquals(0.5f, DuoFoldMath.smoothstep(0.5f), 1e-6f)
        var prev = -1f
        for (i in 0..100) {
            val s = DuoFoldMath.smoothstep(i / 100f)
            assertTrue(s >= prev)
            prev = s
        }
    }
}
