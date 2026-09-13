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

    @Test
    fun `formFactor round trip`() {
        val meta = CustomThemeProps.CustomThemeMeta(
            id = "custom_w",
            name = "宽幅主题",
            outerFile = "outer.png",
            innerFile = "inner.png",
            animationFile = "animation.mp4",
            formFactor = "wide",
        )
        val decoded = CustomThemeProps.decode(CustomThemeProps.encode(meta))
        assertEquals(meta, decoded)
        assertEquals("wide", decoded?.formFactor)
    }

    @Test
    fun `legacy props without formFactor decode to null`() {
        // 旧版本主题没有 formFactor 键，必须向后兼容
        val legacy = "id=custom_old\nname=旧主题\nouter=outer.png\ninner=inner.png\n"
        val decoded = CustomThemeProps.decode(legacy)
        assertEquals("custom_old", decoded?.id)
        assertNull(decoded?.formFactor)
    }

    @Test
    fun `category round trip`() {
        val meta = CustomThemeProps.CustomThemeMeta(
            id = "custom_duo",
            name = "展屏模糊主题",
            outerFile = "outer.png",
            innerFile = "inner.png",
            animationFile = null,
            formFactor = "normal",
            category = "duo_blur",
        )
        val decoded = CustomThemeProps.decode(CustomThemeProps.encode(meta))
        assertEquals(meta, decoded)
        assertEquals("duo_blur", decoded?.category)
    }

    @Test
    fun `legacy props without category decode to null`() {
        // 旧版本主题没有 category 键，缺省按展屏动画处理（由 ThemeCategory.fromKey 兜底）
        val legacy = "id=custom_old\nname=旧主题\nouter=outer.png\ninner=inner.png\n" +
            "animation=animation.mp4\nformFactor=normal\n"
        val decoded = CustomThemeProps.decode(legacy)
        assertEquals("custom_old", decoded?.id)
        assertNull(decoded?.category)
        assertEquals(
            com.llzx373.foldcanvas.theme.model.ThemeCategory.ANIMATION,
            com.llzx373.foldcanvas.theme.model.ThemeCategory.fromKey(decoded?.category),
        )
        assertEquals(
            com.llzx373.foldcanvas.theme.model.ThemeCategory.DUO_BLUR,
            com.llzx373.foldcanvas.theme.model.ThemeCategory.fromKey("duo_blur"),
        )
        assertEquals(
            com.llzx373.foldcanvas.theme.model.ThemeCategory.IMAGES,
            com.llzx373.foldcanvas.theme.model.ThemeCategory.fromKey("images"),
        )
    }
}
