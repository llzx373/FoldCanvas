package com.llzx373.foldcanvas.ui.detail

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.llzx373.foldcanvas.convert.FrameExtractor
import com.llzx373.foldcanvas.data.SettingsStore
import com.llzx373.foldcanvas.theme.FrameCache
import com.llzx373.foldcanvas.theme.ThemeRepository
import com.llzx373.foldcanvas.theme.model.FoldTheme
import com.llzx373.foldcanvas.wallpaper.AngleFrameMapper
import com.llzx373.foldcanvas.wallpaper.FoldWallpaperService
import dev.axiom.sdk.source.hinge.rememberHingeAngle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(themeId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val repository = remember { ThemeRepository(appContext) }
    val frameCache = remember { FrameCache(appContext) }
    val frameExtractor = remember { FrameExtractor(appContext) }
    val settings = remember { SettingsStore(appContext) }
    val scope = rememberCoroutineScope()

    val theme by produceState<FoldTheme?>(initialValue = null, themeId) {
        value = withContext(Dispatchers.IO) { repository.findTheme(themeId) }
    }
    val frames by produceState(initialValue = emptyList<File>(), themeId) {
        value = withContext(Dispatchers.IO) { frameCache.frames(themeId) }
    }

    var followHinge by remember { mutableStateOf(true) }
    var manualAngle by remember { mutableFloatStateOf(0f) }
    val hingeAngle by rememberHingeAngle()
    val angle = if (followHinge) hingeAngle else manualAngle

    var applying by remember { mutableStateOf(false) }
    var applyProgress by remember { mutableFloatStateOf(0f) }
    var framesFailed by remember { mutableStateOf(false) }

    val current = theme
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (current != null && !current.isBuiltin) {
                        IconButton(onClick = {
                            repository.deleteCustomTheme(current.id)
                            frameCache.clear(current.id)
                            onBack()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除主题")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AngleDrivenPreview(
                theme = current,
                frames = frames,
                angle = angle,
                angleStart = settings.angleStart,
                angleEnd = settings.angleEnd,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("跟随铰链角度", modifier = Modifier.weight(1f))
                Switch(checked = followHinge, onCheckedChange = { followHinge = it })
            }
            if (!followHinge) {
                Column {
                    Text("模拟角度：${angle.toInt()}°", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = manualAngle,
                        onValueChange = { manualAngle = it },
                        valueRange = 0f..180f,
                    )
                }
            } else {
                Text(
                    "当前铰链角度：${"%.1f".format(hingeAngle)}°（非折叠设备固定为 180°）",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                when {
                    angle < settings.angleStart -> "外屏壁纸"
                    angle >= settings.angleEnd -> "内屏壁纸"
                    else -> "展开动画"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            if (applying) {
                Column {
                    Text("正在准备动画帧…", style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(
                        progress = { applyProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Button(
                onClick = {
                    scope.launch {
                        applying = true
                        framesFailed = false
                        applyProgress = 0f
                        val ok = prepareFrames(
                            current, repository, frameCache, frameExtractor,
                        ) { applyProgress = it }
                        framesFailed = !ok
                        settings.activeThemeId = current.id
                        applying = false
                        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
                            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                            ComponentName(context, FoldWallpaperService::class.java),
                        )
                        context.startActivity(intent)
                    }
                },
                enabled = !applying,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("应用为壁纸（桌面/锁屏可选）")
            }
            if (framesFailed) {
                Text(
                    "动画帧准备失败：已退化为外屏/内屏淡化切换，请检查主题视频后重试",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** 与壁纸引擎同一套角度→画面映射逻辑的三段式预览。 */
@Composable
private fun AngleDrivenPreview(
    theme: FoldTheme,
    frames: List<File>,
    angle: Float,
    angleStart: Float,
    angleEnd: Float,
) {
    val progress = AngleFrameMapper.progress(angle, angleStart, angleEnd)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.83f)
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        when {
            progress <= 0f -> PreviewImage(theme.outerWallpaper, theme.name)
            progress >= 1f -> PreviewImage(theme.innerWallpaper, theme.name)
            frames.isNotEmpty() -> {
                val index = AngleFrameMapper.frameIndex(progress, frames.size)
                PreviewImage(UriFile(frames[index]), theme.name)
            }
            else -> {
                // 帧尚未生成：交叉淡化预览
                PreviewImage(theme.outerWallpaper, theme.name)
                Box(Modifier.fillMaxSize().alpha(progress)) {
                    PreviewImage(theme.innerWallpaper, theme.name)
                }
            }
        }
    }
}

private fun UriFile(file: File): android.net.Uri = android.net.Uri.fromFile(file)

@Composable
private fun PreviewImage(model: Any, description: String) {
    AsyncImage(
        model = model,
        contentDescription = description,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
    )
}

private suspend fun prepareFrames(
    theme: FoldTheme,
    repository: ThemeRepository,
    frameCache: FrameCache,
    frameExtractor: FrameExtractor,
    onProgress: (Float) -> Unit,
): Boolean = withContext(Dispatchers.IO) {
    // 锁与抽帧都在同一 IO 线程内完成，避免 monitor 跨线程释放
    synchronized(FrameCache.extractLock(theme.id)) {
        if (frameCache.isReady(theme.id)) return@synchronized true
        val video = theme.unfoldAnimation
        val ok = if (video != null) {
            frameExtractor.extract(
                video = video,
                outDir = frameCache.prepareDir(theme.id),
                frameCount = FrameCache.FRAME_COUNT,
                targetWidth = FrameCache.FRAME_WIDTH,
                onProgress = onProgress,
            )
        } else if (theme.isBuiltin) {
            kotlinx.coroutines.runBlocking {
                repository.ensureBuiltinFrames(theme.id, frameCache, onProgress)
            }
        } else {
            false
        }
        if (ok) frameCache.markReady(theme.id)
        ok
    }
}
