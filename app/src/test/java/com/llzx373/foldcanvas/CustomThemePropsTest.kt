package com.llzx373.foldcanvas

import com.llzx373.foldcanvas.theme.CustomThemeProps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomThemePropsTest {

    @Test
    fun `encode decode round trip`() {
        val meta = CustomThemeProps.CustomThemeMeta(
            id = "custom_ab12cd34",
            name = "我的主题",
            outerFile = "outer.png",
            innerFile = "inner.png",
            animationFile = "animation.mp4",
        )
        val decoded = CustomThemeProps.decode(CustomThemeProps.encode(meta))
        assertEquals(meta, decoded)
    }

    @Test
    fun `decode without animation`() {
        val meta = CustomThemeProps.CustomThemeMeta(
            id = "custom_x",
            name = "静态主题",
            outerFile = "outer.png",
            innerFile = "inner.png",
            animationFile = null,
        )
        val decoded = CustomThemeProps.decode(CustomThemeProps.encode(meta))
        assertEquals(meta, decoded)
        assertNull(decoded?.animationFile)
    }

    @Test
    fun `decode invalid returns null`() {
        assertNull(CustomThemeProps.decode("not a properties file \u0000\u0001"))
        assertNull(CustomThemeProps.decode("id=only_id"))
        assertNull(CustomThemeProps.decode(""))
    }
}
