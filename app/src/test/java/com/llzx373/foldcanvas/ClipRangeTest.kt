package com.llzx373.foldcanvas

import com.llzx373.foldcanvas.convert.ClipRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClipRangeTest {

    @Test
    fun `full range passes through`() {
        assertEquals(0f to 12.4f, ClipRange.clamp(0f, 12.4f, 12.4f))
    }

    @Test
    fun `out of bound values are clamped to duration`() {
        assertEquals(0f to 10f, ClipRange.clamp(-3f, 99f, 10f))
        assertEquals(2f to 8f, ClipRange.clamp(2f, 8f, 10f))
    }

    @Test
    fun `segment shorter than minimum is rejected`() {
        assertNull(ClipRange.clamp(3f, 3.2f, 10f))
        assertNull(ClipRange.clamp(5f, 5f, 10f))
        // 恰好达到最小长度则接受
        assertEquals(3f to 3.5f, ClipRange.clamp(3f, 3.5f, 10f))
    }

    @Test
    fun `reversed range is rejected`() {
        assertNull(ClipRange.clamp(8f, 2f, 10f))
    }

    @Test
    fun `invalid duration is rejected`() {
        assertNull(ClipRange.clamp(0f, 5f, 0f))
        assertNull(ClipRange.clamp(0f, 5f, -1f))
    }
}
