package com.llzx373.foldcanvas.theme.model

/**
 * 主题类别：
 * ANIMATION 展屏动画（视频抽帧驱动展开动画）；
 * IMAGES 内外图片（静态外屏/内屏壁纸，展开时交叉淡化）；
 * DUO_BLUR 展屏模糊（内屏右半作外屏，展开时呈现 Duo 透视压缩 + 渐进模糊动画）。
 */
enum class ThemeCategory(val key: String, val label: String) {
    ANIMATION("animation", "展屏动画"),
    IMAGES("images", "内外图片"),
    DUO_BLUR("duo_blur", "展屏模糊");

    companion object {
        /** 旧主题无 category 键时按展屏动画处理。 */
        fun fromKey(key: String?): ThemeCategory =
            entries.firstOrNull { it.key == key } ?: ANIMATION
    }
}
