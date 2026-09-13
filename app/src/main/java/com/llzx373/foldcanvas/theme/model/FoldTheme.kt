package com.llzx373.foldcanvas.theme.model

import android.net.Uri

data class FoldTheme(
    val id: String,
    val name: String,
    val isBuiltin: Boolean,
    val outerWallpaper: Uri,
    val innerWallpaper: Uri,
    val unfoldAnimation: Uri?,
    val category: ThemeCategory = ThemeCategory.ANIMATION,
)
