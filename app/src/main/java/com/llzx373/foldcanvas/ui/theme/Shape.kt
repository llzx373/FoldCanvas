package com.llzx373.foldcanvas.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Expressive 形状体系：整体圆角上移一档，卡片与预览容器更柔和
val FoldShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

// 预览容器专用圆角（Detail 页两段式预览）
val PreviewShape = RoundedCornerShape(28.dp)
