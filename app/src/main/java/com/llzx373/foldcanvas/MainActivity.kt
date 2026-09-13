package com.llzx373.foldcanvas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.llzx373.foldcanvas.ui.detail.DetailScreen
import com.llzx373.foldcanvas.ui.editor.EditorScreen
import com.llzx373.foldcanvas.ui.gallery.GalleryScreen
import com.llzx373.foldcanvas.ui.settings.SettingsScreen
import com.llzx373.foldcanvas.ui.theme.FoldCanvasTheme

class MainActivity : ComponentActivity() {

    private sealed interface Screen {
        data object Gallery : Screen
        data class Detail(val themeId: String) : Screen
        data object Editor : Screen
        data object Settings : Screen
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FoldCanvasTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var screen by remember { mutableStateOf<Screen>(Screen.Gallery) }
                    if (screen !is Screen.Gallery) {
                        BackHandler { screen = Screen.Gallery }
                    }
                    when (val current = screen) {
                        Screen.Gallery -> GalleryScreen(
                            onOpenTheme = { screen = Screen.Detail(it) },
                            onCreateTheme = { screen = Screen.Editor },
                            onOpenSettings = { screen = Screen.Settings },
                        )
                        is Screen.Detail -> DetailScreen(
                            themeId = current.themeId,
                            onBack = { screen = Screen.Gallery },
                        )
                        Screen.Editor -> EditorScreen(
                            onBack = { screen = Screen.Gallery },
                            onSaved = { screen = Screen.Detail(it) },
                        )
                        Screen.Settings -> SettingsScreen(
                            onBack = { screen = Screen.Gallery },
                        )
                    }
                }
            }
        }
    }
}
