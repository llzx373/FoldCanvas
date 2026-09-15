package com.llzx373.foldcanvas

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.llzx373.foldcanvas.theme.ThemePackageImporter
import com.llzx373.foldcanvas.ui.detail.DetailScreen
import com.llzx373.foldcanvas.ui.editor.EditorScreen
import com.llzx373.foldcanvas.ui.gallery.GalleryScreen
import com.llzx373.foldcanvas.ui.settings.SettingsScreen
import com.llzx373.foldcanvas.ui.theme.FoldCanvasTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private sealed interface Screen {
        data object Gallery : Screen
        data class Detail(val themeId: String) : Screen
        data class Editor(val themeId: String? = null) : Screen
        data object Settings : Screen
    }

    private var navigateToTheme: ((String) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FoldCanvasTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var screen by remember { mutableStateOf<Screen>(Screen.Gallery) }
                    navigateToTheme = { screen = Screen.Detail(it) }
                    if (screen !is Screen.Gallery) {
                        BackHandler { screen = Screen.Gallery }
                    }
                    AnimatedContent(
                        targetState = screen,
                        transitionSpec = {
                            (fadeIn() + slideInHorizontally { it / 12 })
                                .togetherWith(fadeOut() + slideOutHorizontally { -it / 12 })
                        },
                        label = "screen"
                    ) { current ->
                        when (current) {
                            Screen.Gallery -> GalleryScreen(
                                onOpenTheme = { screen = Screen.Detail(it) },
                                onCreateTheme = { screen = Screen.Editor() },
                                onOpenSettings = { screen = Screen.Settings },
                            )
                            is Screen.Detail -> DetailScreen(
                                themeId = current.themeId,
                                onBack = { screen = Screen.Gallery },
                                onEdit = { screen = Screen.Editor(it) },
                            )
                            is Screen.Editor -> EditorScreen(
                                editThemeId = current.themeId,
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
        handleViewIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleViewIntent(intent)
    }

    /** 文件管理器点开 .foldtheme：导入主题包并进入详情页。 */
    private fun handleViewIntent(intent: Intent?) {
        val uri = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data ?: return
        // 消费掉该 intent，避免旋转/重建后重复导入
        setIntent(Intent(Intent.ACTION_MAIN))
        importPackage(uri)
    }

    private fun importPackage(uri: Uri) {
        val importer = ThemePackageImporter(applicationContext)
        lifecycleScope.launch {
            Toast.makeText(this@MainActivity, "正在导入主题包…", Toast.LENGTH_SHORT).show()
            importer.import(uri)
                .onSuccess {
                    Toast.makeText(this@MainActivity, "主题包已导入", Toast.LENGTH_SHORT).show()
                    navigateToTheme?.invoke(it.id)
                }
                .onFailure {
                    Toast.makeText(
                        this@MainActivity,
                        it.message ?: "主题包导入失败",
                        Toast.LENGTH_LONG,
                    ).show()
                }
        }
    }
}
