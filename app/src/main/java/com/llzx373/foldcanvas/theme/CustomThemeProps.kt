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
    private const val KEY_FORM_FACTOR = "formFactor"
    private const val KEY_CATEGORY = "category"
    private const val KEY_OUTER_MODE = "outerMode"

    data class CustomThemeMeta(
        val id: String,
        val name: String,
        val outerFile: String,
        val innerFile: String,
        val animationFile: String?,
        /** 目标机型：normal / wide，旧主题无此键时为 null。 */
        val formFactor: String? = null,
        /** 主题类别：animation / images / duo_blur，旧主题无此键时为 null（按 animation 处理）。 */
        val category: String? = null,
        /** 外屏自动派生方式：LEFT / CENTER / RIGHT，未指定外屏图时有意义。 */
        val outerMode: String? = null,
    )

    fun encode(meta: CustomThemeMeta): String {
        val props = Properties()
        props.setProperty(KEY_ID, meta.id)
        props.setProperty(KEY_NAME, meta.name)
        props.setProperty(KEY_OUTER, meta.outerFile)
        props.setProperty(KEY_INNER, meta.innerFile)
        meta.animationFile?.let { props.setProperty(KEY_ANIMATION, it) }
        meta.formFactor?.let { props.setProperty(KEY_FORM_FACTOR, it) }
        meta.category?.let { props.setProperty(KEY_CATEGORY, it) }
        meta.outerMode?.let { props.setProperty(KEY_OUTER_MODE, it) }
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
            formFactor = props.getProperty(KEY_FORM_FACTOR),
            category = props.getProperty(KEY_CATEGORY),
            outerMode = props.getProperty(KEY_OUTER_MODE),
        )
    }
}
