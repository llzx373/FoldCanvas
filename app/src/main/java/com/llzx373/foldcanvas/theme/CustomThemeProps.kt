package com.llzx373.foldcanvas.theme

import java.util.Properties

/**
 * 自定义主题元数据的 properties 序列化（与 Android 框架解耦，便于 JVM 单元测试）。
 */
object CustomThemeProps {

    private const val KEY_ID = "id"
    private const val KEY_NAME = "name"
    private const val KEY_OUTER = "outer"
    private const val KEY_INNER = "inner"
    private const val KEY_ANIMATION = "animation"

    data class CustomThemeMeta(
        val id: String,
        val name: String,
        val outerFile: String,
        val innerFile: String,
        val animationFile: String?,
    )

    fun encode(meta: CustomThemeMeta): String {
        val props = Properties()
        props.setProperty(KEY_ID, meta.id)
        props.setProperty(KEY_NAME, meta.name)
        props.setProperty(KEY_OUTER, meta.outerFile)
        props.setProperty(KEY_INNER, meta.innerFile)
        meta.animationFile?.let { props.setProperty(KEY_ANIMATION, it) }
        val out = java.io.StringWriter()
        props.store(out, null)
        return out.toString()
    }

    fun decode(text: String): CustomThemeMeta? {
        val props = Properties()
        runCatching { props.load(text.reader()) }.getOrElse { return null }
        val id = props.getProperty(KEY_ID) ?: return null
        val name = props.getProperty(KEY_NAME) ?: return null
        val outer = props.getProperty(KEY_OUTER) ?: return null
        val inner = props.getProperty(KEY_INNER) ?: return null
        return CustomThemeMeta(
            id = id,
            name = name,
            outerFile = outer,
            innerFile = inner,
            animationFile = props.getProperty(KEY_ANIMATION),
        )
    }
}
