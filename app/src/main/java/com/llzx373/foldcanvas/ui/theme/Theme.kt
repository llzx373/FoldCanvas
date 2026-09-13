package com.llzx373.foldcanvas.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = Indigo40,
    onPrimary = Color.White,
    primaryContainer = Indigo90,
    onPrimaryContainer = Indigo10,
    secondary = Teal40,
    onSecondary = Color.White,
    secondaryContainer = Teal90,
    onSecondaryContainer = Teal10,
    tertiary = Amber40,
    onTertiary = Color.White,
    tertiaryContainer = Amber90,
    onTertiaryContainer = Amber10,
    background = MistWhite,
    onBackground = Neutral10,
    surface = MistWhite,
    onSurface = Neutral10,
    surfaceVariant = NeutralVariant90,
    onSurfaceVariant = NeutralVariant30,
    surfaceTint = Indigo40,
    inverseSurface = Ink30,
    inverseOnSurface = InkLight,
    inversePrimary = Indigo80,
    outline = NeutralVariant50,
    outlineVariant = NeutralVariant80,
    surfaceBright = MistWhite,
    surfaceDim = SurfaceDimLight,
    surfaceContainerLowest = PureWhite,
    surfaceContainerLow = ContainerLowLight,
    surfaceContainer = ContainerLight,
    surfaceContainerHigh = ContainerHighLight,
    surfaceContainerHighest = ContainerHighestLight
)

private val DarkColorScheme = darkColorScheme(
    primary = Indigo80,
    onPrimary = Indigo30,
    primaryContainer = Indigo70,
    onPrimaryContainer = Indigo90,
    secondary = Teal80,
    onSecondary = Teal30,
    secondaryContainer = Teal70,
    onSecondaryContainer = Teal90,
    tertiary = Amber80,
    onTertiary = Amber30,
    tertiaryContainer = Amber70,
    onTertiaryContainer = Amber90,
    background = InkDark,
    onBackground = Neutral90,
    surface = InkDark,
    onSurface = Neutral90,
    surfaceVariant = NeutralVariant30,
    onSurfaceVariant = NeutralVariant80,
    surfaceTint = Indigo80,
    inverseSurface = Neutral90,
    inverseOnSurface = Ink30,
    inversePrimary = Indigo40,
    outline = Color(0xFF918F9A),
    outlineVariant = NeutralVariant30,
    surfaceBright = SurfaceBrightDark,
    surfaceDim = InkDark,
    surfaceContainerLowest = InkDeep,
    surfaceContainerLow = ContainerLowDark,
    surfaceContainer = ContainerDark,
    surfaceContainerHigh = ContainerHighDark,
    surfaceContainerHighest = ContainerHighestDark
)

@Composable
fun FoldCanvasTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // 品牌色为主；动态取色保留入口，默认关闭
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = FoldShapes,
        typography = Typography,
        content = content
    )
}
