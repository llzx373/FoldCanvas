package com.llzx373.foldcanvas.ui.detail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.llzx373.foldcanvas.convert.FrameExtractor
import com.llzx373.foldcanvas.data.DeviceBrand
import com.llzx373.foldcanvas.data.SettingsStore
import com.llzx373.foldcanvas.theme.FrameCache
import com.llzx373.foldcanvas.theme.ThemeRepository
import com.llzx373.foldcanvas.theme.duo.DuoFrameGenerator
import com.llzx373.foldcanvas.theme.model.FoldTheme
import com.llzx373.foldcanvas.theme.model.ThemeCategory
import com.llzx373.foldcanvas.ui.components.SectionCard
import com.llzx373.foldcanvas.ui.theme.PreviewShape
import com.llzx373.foldcanvas.wallpaper.AngleFrameMapper
import com.llzx373.foldcanvas.wallpaper.WallpaperActivation
import dev.axiom.sdk.source.hinge.rememberHingeAngle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(themeId: String, onBack: () -> Unit, onEdit: (String) -> Unit = {}) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val repository = remember { ThemeRepository(appContext) }
    val frameCache = remember { FrameCache(appContext) }
    val frameExtractor = remember { FrameExtractor(appContext) }
    val settings = remember { SettingsStore(appContext) }
    val scope = rememberCoroutineScope()

    var framesVersion by remember { mutableIntStateOf(0) }
    val theme by produceState<FoldTheme?>(initialValue = null, themeId) {
        value = withContext(Dispatchers.IO) { repository.findTheme(themeId) }
    }
    val frames by produceState(initialValue = emptyList<File>(), themeId, framesVersion) {
        value = withContext(Dispatchers.IO) { frameCache.frames(themeId) }
    }
    // 展屏模糊主题的外屏动画帧（应用后才生成）
    val coverFrames by produceState(initialValue = emptyList<File>(), themeId, framesVersion) {
        value = withContext(Dispatchers.IO) {
            frameCache.frames(themeId + FrameCache.COVER_SUFFIX)
        }
    }

    var followHinge by remember { mutableStateOf(true) }
    var manualAngle by remember { mutableFloatStateOf(0f) }
    val hingeAngle by rememberHingeAngle()
    val angle = if (followHinge) hingeAngle else manualAngle

    var applying by remember { mutableStateOf(false) }
    var applyProgress by remember { mutableFloatStateOf(0f) }
    var framesFailed by remember { mutableStateOf(false) }
    var showWallpaperGuide by remember { mutableStateOf(false) }
    var guideNeedsPermission by remember { mutableStateOf(false) }

    // 从系统壁纸选择页返回后复检：未真正生效则按原因弹引导
    // （MIUI「壁纸」权限被禁会静默失败，需先去权限页开启）
    val wallpaperLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (!WallpaperActivation.isActive(appContext)) {
            guideNeedsPermission = !WallpaperActivation.wallpaperPermissionGranted(appContext)
            showWallpaperGuide = true
        }
    }

    val current = theme
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = { Text(current?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (current != null && !current.isBuiltin) {
                        IconButton(onClick = { onEdit(current.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "编辑主题")
                        }
                        IconButton(onClick = {
                            repository.deleteCustomTheme(current.id)
                            frameCache.clear(current.id)
                            frameCache.clear(current.id + FrameCache.COVER_SUFFIX)
                            onBack()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除主题")
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
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

            if (current.category == ThemeCategory.DUO_BLUR) {
                Text("外屏预览（0°→90°）", style = MaterialTheme.typography.titleSmall)
                CoverPreview(
                    theme = current,
                    coverFrames = coverFrames,
                    angle = angle,
                )
            }

            SectionCard(title = "预览控制") {
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    val (statusText, statusIcon) = when {
                        angle < settings.angleStart -> "外屏壁纸" to Icons.Default.Smartphone
                        angle >= settings.angleEnd -> "内屏壁纸" to Icons.Default.Tablet
                        current.category == ThemeCategory.IMAGES -> "交叉淡化" to Icons.Default.Compare
                        else -> "展开动画" to Icons.Default.PlayArrow
                    }
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(statusText) },
                        leadingIcon = {
                            Icon(
                                statusIcon,
                                contentDescription = null,
                                modifier = Modifier.size(AssistChipDefaults.IconSize),
                            )
                        },
                    )
                }
            }

            SectionCard(title = "应用") {
                if (applying) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            framesVersion++
                            applying = false
                            runCatching {
                                wallpaperLauncher.launch(WallpaperActivation.changeIntent(context))
                            }.onFailure {
                                guideNeedsPermission =
                                    !WallpaperActivation.wallpaperPermissionGranted(appContext)
                                showWallpaperGuide = true
                            }
                        }
                    },
                    enabled = !applying,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    Text("应用为壁纸（桌面/锁屏可选）")
                }
                if (framesFailed) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            "动画帧准备失败：已退化为外屏/内屏淡化切换，请检查主题素材后重试",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showWallpaperGuide) {
        AlertDialog(
            onDismissRequest = { showWallpaperGuide = false },
            title = { Text(if (guideNeedsPermission) "需要壁纸权限" else "动态壁纸未生效") },
            text = {
                Text(
                    when {
                        guideNeedsPermission -> {
                            "小米/MIUI 的「壁纸」权限被禁止，设置动态壁纸会被系统静默拦截。" +
                                "请在打开的权限页面中找到「壁纸」并选择允许，然后返回重试。"
                        }
                        DeviceBrand.isXiaomi -> {
                            "小米/MIUI 可能不会自动应用动态壁纸：请在壁纸预览页点击「应用/设定壁纸」。" +
                                "若预览页未出现或设置后仍无效，请前往 设置 → 壁纸与个性化 → 壁纸 → " +
                                "动态壁纸，手动选择「折叠画卷」。"
                        }
                        else -> {
                            "系统尚未将「折叠画卷」应用为动态壁纸，" +
                                "请在弹出的预览页中确认「应用」。"
                        }
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showWallpaperGuide = false
                    if (guideNeedsPermission) {
                        WallpaperActivation.launchMiuiPermissionEditor(context)
                    } else {
                        WallpaperActivation.launchPicker(context)
                    }
                }) { Text(if (guideNeedsPermission) "去开启权限" else "去开启") }
            },
            dismissButton = {
                TextButton(onClick = { showWallpaperGuide = false }) { Text("知道了") }
            },
        )
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
            .clip(PreviewShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, PreviewShape),
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

/** 外屏 surface 的动画预览：与引擎 coverMode 同一套 0°→90° 帧映射。 */
@Composable
private fun CoverPreview(
    theme: FoldTheme,
    coverFrames: List<File>,
    angle: Float,
) {
    val progress = (angle / 90f).coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.45f)
            .clip(PreviewShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, PreviewShape),
        contentAlignment = Alignment.Center,
    ) {
        if (coverFrames.isNotEmpty()) {
            val index = AngleFrameMapper.frameIndex(progress, coverFrames.size)
            PreviewImage(UriFile(coverFrames[index]), theme.name)
        } else {
            PreviewImage(theme.outerWallpaper, theme.name)
        }
    }
    if (coverFrames.isEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    "外屏动画帧尚未生成，点击「应用为壁纸」后可预览动态效果",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

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
        // 内外图片类别无动画帧，渲染时退化为外/内屏交叉淡化
        if (theme.category == ThemeCategory.IMAGES) {
            return@synchronized true
        }
        if (frameCache.isReady(theme.id)) return@synchronized true
        val video = theme.unfoldAnimation
        val ok = if (theme.category == ThemeCategory.DUO_BLUR) {
            val inner = theme.innerWallpaper.path?.let { File(it) }
            val innerOk = inner != null && inner.isFile && DuoFrameGenerator()
                .generate(
                    innerFile = inner,
                    outDir = frameCache.prepareDir(theme.id),
                    frameCount = FrameCache.FRAME_COUNT,
                    targetWidth = FrameCache.FRAME_WIDTH,
                    onProgress = { onProgress(it * 0.7f) },
                )
            // 外屏动画帧（外屏 surface 播放）：与内屏帧一起预生成
            val coverOk = repository.ensureDuoCoverFrames(theme, frameCache) { coverProgress ->
                onProgress(0.7f + coverProgress * 0.3f)
            }
            innerOk && coverOk
        } else if (video != null) {
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
