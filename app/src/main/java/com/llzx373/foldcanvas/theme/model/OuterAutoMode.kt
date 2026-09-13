package com.llzx373.foldcanvas.theme.model

/** 内外图片类别下未指定外屏壁纸时，从内屏派生外屏的取图方式。 */
enum class OuterAutoMode(val label: String) {
    LEFT("左半"),
    CENTER("中间"),
    RIGHT("右半");
}
