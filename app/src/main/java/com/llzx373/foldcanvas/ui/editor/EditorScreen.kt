package com.llzx373.foldcanvas.ui.editor

import android.graphics.Bitmap
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.llzx373.foldcanvas.convert.ClipRange
import com.llzx373.foldcanvas.convert.VideoNormalizer
import com.llzx373.foldcanvas.data.DeviceBrand
import com.llzx373.foldcanvas.data.DeviceProfile
import com.llzx373.foldcanvas.data.MediaPermissions
import com.llzx373.foldcanvas.theme.ThemeRepository
import com.llzx373.foldcanvas.theme.model.OuterAutoMode
import com.llzx373.foldcanvas.theme.model.ThemeCategory
import com.llzx373.foldcanvas.ui.ImageUtils
import com.llzx373.foldcanvas.ui.components.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(editThemeId: String? = null, onBack: () -> Unit, onSaved: (String) -> Unit) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val repository = remember { ThemeRepository(appContext) }
    val normalizer = remember { VideoNormalizer(appContext) }
    val scope = rememberCoroutineScope()

    // 编辑模式：回填已有元数据与素材（类别与机型锁定，避免派生尺寸错位）
    val editMeta = remember(editThemeId) {
        editThemeId?.let { repository.customThemeMeta(it) }
    }
    val editing = editMeta != null

    var name by remember { mutableStateOf(editMeta?.name ?: "") }
    var category by remember {
        mutableStateOf(ThemeCategory.fromKey(editMeta?.category))
    }
    var outerAutoMode by remember {
        mutableStateOf(
            editMeta?.outerMode?.let { m ->
                OuterAutoMode.entries.firstOrNull { it.name == m }
            } ?: OuterAutoMode.RIGHT,
        )
    }
    var wideFold by remember { mutableStateOf(editMeta?.formFactor == "wide") }
    val profile = if (wideFold) DeviceProfile.WIDE_FOLD else DeviceProfile.NORMAL_FOLD

    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var videoDurationSec by remember { mutableFloatStateOf(0f) }
    var clipStartSec by remember { mutableFloatStateOf(0f) }
    var clipEndSec by remember { mutableFloatStateOf(0f) }
    var normalizedVideo by remember { mutableStateOf<File?>(null) }
    var converting by remember { mutableStateOf(false) }
    var convertProgress by remember { mutableFloatStateOf(0f) }
    var convertError by remember { mutableStateOf(false) }
    var convertJob by remember { mutableStateOf<Job?>(null) }
    // 编辑展屏动画主题且已有视频：可不重选，保存时沿用
    var hasExistingVideo by remember { mutableStateOf(editMeta?.animationFile != null) }

    var outerBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var innerBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var saving by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(editThemeId) {
        if (editMeta == null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val dir = java.io.File(appContext.filesDir, "themes/${editMeta.id}")
            fun decode(name: String) = runCatching {
                android.graphics.BitmapFactory.decodeFile(java.io.File(dir, name).absolutePath)
            }.getOrNull()
            val outer = decode(editMeta.outerFile)
            val inner = decode(editMeta.innerFile)
            withContext(Dispatchers.Main) {
                outerBitmap = outer
                innerBitmap = inner
            }
        }
    }

    fun startConvert() {
        val uri = videoUri ?: return
        convertJob?.cancel()
        converting = true
        convertError = false
        normalizedVideo = null
        convertProgress = 0f
        convertJob = scope.launch {
            val range = ClipRange.clamp(clipStartSec, clipEndSec, videoDurationSec)
            if (range == null) {
                converting = false
                convertError = true
                return@launch
            }
            val output = File(appContext.cacheDir, "normalized_${System.currentTimeMillis()}.mp4")
            val result = normalizer.normalize(
                uri, output,
                targetWidth = profile.innerWidth, targetHeight = profile.innerHeight,
                startMs = (range.first * 1000).toLong(),
                endMs = (range.second * 1000).toLong(),
            ) { convertProgress = it }
            result
                .onSuccess { normalizedVideo = it; converting = false }
                .onFailure { if (it !is kotlinx.coroutines.CancellationException) convertError = true; converting = false }
        }
    }

    fun onVideoSelected(uri: Uri) {
        videoUri = uri
        scope.launch {
            val durationMs = withContext(Dispatchers.IO) { readDurationMs(appContext, uri) }
            videoDurationSec = durationMs / 1000f
            clipStartSec = 0f
            clipEndSec = videoDurationSec
            startConvert()
        }
    }

    fun onOuterSelected(uri: Uri) {
        scope.launch {
            val bmp = withContext(Dispatchers.IO) {
                ImageUtils.decodeCenterCrop(
                    appContext, uri,
                    profile.outerWidth, profile.outerHeight,
                )
            }
            if (bmp != null) {
                outerBitmap = bmp
            } else {
                Toast.makeText(
                    appContext, "图片读取失败，请换一张或尝试「从文件选择」", Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    fun onInnerSelected(uri: Uri) {
        scope.launch {
            val bmp = withContext(Dispatchers.IO) {
                ImageUtils.decodeCenterCrop(
                    appContext, uri,
                    profile.innerWidth, profile.innerHeight,
                )
            }
            if (bmp != null) {
                innerBitmap = bmp
            } else {
                Toast.makeText(
                    appContext, "图片读取失败，请换一张或尝试「从文件选择」", Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) onVideoSelected(uri) }
    val outerPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) onOuterSelected(uri) }
    val innerPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) onInnerSelected(uri) }

    // SAF 文件选择：可浏览下载等相册外目录（免权限）；持久化读取授权防止后续访问失效
    fun persistUri(uri: Uri) {
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }
    val videoFilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) { persistUri(uri); onVideoSelected(uri) } }
    val outerFilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) { persistUri(uri); onOuterSelected(uri) } }
    val innerFilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) { persistUri(uri); onInnerSelected(uri) } }

    // 小米设备：进入编辑器时检查媒体权限，未授权则弹框引导（也可走免授权的 SAF）
    var showMediaPermissionDialog by remember { mutableStateOf(false) }
    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (DeviceBrand.isXiaomi && !mediaPermissionsGranted(appContext)) {
            showMediaPermissionDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editing) "编辑主题" else "创建自定义主题") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 基本信息：名称 + 类别 + 机型
            SectionCard(title = "基本信息") {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("主题名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                )

                FieldLabel(
                    if (editing) "主题类别（编辑时不可更改）" else "主题类别（切换后需重新选择素材）",
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ThemeCategory.entries.forEachIndexed { index, c ->
                        SegmentedButton(
                            selected = category == c,
                            onClick = {
                                if (category != c) {
                                    category = c
                                    convertJob?.cancel()
                                    converting = false
                                    videoUri = null; normalizedVideo = null
                                    outerBitmap = null; innerBitmap = null
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = ThemeCategory.entries.size,
                            ),
                            enabled = !editing,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(c.label)
                        }
                    }
                }
                Text(
                    when (category) {
                        ThemeCategory.ANIMATION ->
                            "视频驱动展开动画；外屏/内屏壁纸可单独指定或自动派生"
                        ThemeCategory.IMAGES ->
                            "静态外屏 + 内屏壁纸，展开时交叉淡化，无需视频"
                        ThemeCategory.DUO_BLUR ->
                            "只需内屏壁纸；外屏自动取右半幅，展开时呈现 Duo 透视模糊动画"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                FieldLabel(
                    if (editing) "目标机型（编辑时不可更改）" else "目标机型（切换后需重新选择素材）",
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !wideFold,
                        onClick = {
                            if (wideFold) {
                                wideFold = false
                                convertJob?.cancel()
                                converting = false
                                videoUri = null; normalizedVideo = null
                                outerBitmap = null; innerBitmap = null
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        enabled = !editing,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("正常折叠屏（内屏竖屏）")
                    }
                    SegmentedButton(
                        selected = wideFold,
                        onClick = {
                            if (!wideFold) {
                                wideFold = true
                                convertJob?.cancel()
                                converting = false
                                videoUri = null; normalizedVideo = null
                                outerBitmap = null; innerBitmap = null
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        enabled = !editing,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("宽屏折叠屏（内屏横屏）")
                    }
                }
                Text(
                    if (wideFold) "内屏 2364×1672（横）/ 外屏 1168×1712（竖）"
                    else "内屏 1812×2176（竖）/ 外屏 1080×2400（竖）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 视频素材：仅展屏动画类别需要
            if (category == ThemeCategory.ANIMATION) {
                SectionCard(title = "视频素材") {
                    Text(
                        "展开动画（必选视频，规格化后均匀抽取 45 帧）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = {
                            videoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                            )
                        }) {
                            Text(if (videoUri == null) "选择视频" else "重新选择视频")
                        }
                        OutlinedButton(onClick = {
                            videoFilePicker.launch(arrayOf("video/*"))
                        }) {
                            Text("从文件选择")
                        }
                    }
                    when {
                        converting -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Text(
                                "转换中 ${(convertProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        normalizedVideo != null -> VideoStatusRow(
                            icon = Icons.Default.CheckCircle,
                            tint = MaterialTheme.colorScheme.primary,
                            text = "✓ 已规格化",
                        )
                        hasExistingVideo -> VideoStatusRow(
                            icon = Icons.Default.CheckCircle,
                            tint = MaterialTheme.colorScheme.secondary,
                            text = "✓ 沿用已有视频（可重新选择替换）",
                        )
                        convertError -> VideoStatusRow(
                            icon = Icons.Default.Error,
                            tint = MaterialTheme.colorScheme.error,
                            text = "转换失败，请重试",
                        )
                    }
                    if (converting) {
                        LinearProgressIndicator(
                            progress = { convertProgress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (videoUri != null && videoDurationSec > 0f) {
                        Column {
                            Text(
                                "截取区间：${"%.1f".format(clipStartSec)}s ~ " +
                                    "${"%.1f".format(clipEndSec)}s" +
                                    "（共 ${"%.1f".format(clipEndSec - clipStartSec)}s / " +
                                    "全长 ${"%.1f".format(videoDurationSec)}s）",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            RangeSlider(
                                value = clipStartSec..clipEndSec,
                                onValueChange = { range ->
                                    clipStartSec = range.start
                                    clipEndSec = range.endInclusive
                                },
                                onValueChangeFinished = { startConvert() },
                                valueRange = 0f..videoDurationSec,
                            )
                        }
                    }
                }
            }

            // 外屏壁纸
            if (category != ThemeCategory.DUO_BLUR) {
                SectionCard(title = "外屏壁纸") {
                    Text(
                        when (category) {
                            ThemeCategory.ANIMATION -> "可选；不选则取内屏右半部分"
                            else -> "可选；不选则按下方方式从内屏派生"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ImagePickerRow(
                        bitmap = outerBitmap,
                        aspect = profile.outerWidth.toFloat() / profile.outerHeight,
                        placeholder = "自动派生",
                        description = "外屏壁纸预览",
                        onPick = {
                            outerPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        onPickFile = { outerFilePicker.launch(arrayOf("image/*")) },
                    )
                    if (category == ThemeCategory.IMAGES && outerBitmap == null) {
                        FieldLabel("派生方式")
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            OuterAutoMode.entries.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    selected = outerAutoMode == mode,
                                    onClick = { outerAutoMode = mode },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = OuterAutoMode.entries.size,
                                    ),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("内屏${mode.label}")
                                }
                            }
                        }
                    }
                }
            } else {
                SectionCard(title = "外屏壁纸") {
                    Text(
                        "自动使用内屏右半部分（Duo 效果的铰链侧窗口），无需选择",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 内屏壁纸
            SectionCard(title = "内屏壁纸") {
                Text(
                    when (category) {
                        ThemeCategory.ANIMATION -> "可选；不选则取视频末帧"
                        ThemeCategory.IMAGES -> "必选"
                        ThemeCategory.DUO_BLUR -> "必选；展开动画由其自动生成"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ImagePickerRow(
                    bitmap = innerBitmap,
                    aspect = profile.innerWidth.toFloat() / profile.innerHeight,
                    placeholder = if (category == ThemeCategory.ANIMATION) "自动派生" else "未选择",
                    description = "内屏壁纸预览",
                    onPick = {
                        innerPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onPickFile = { innerFilePicker.launch(arrayOf("image/*")) },
                )
            }

            Button(
                onClick = {
                    saving = true
                    scope.launch {
                        val theme = withContext(Dispatchers.IO) {
                            repository.saveCustomTheme(
                                name = name.trim(),
                                category = category,
                                normalizedVideo = normalizedVideo,
                                outerBitmap = outerBitmap,
                                innerBitmap = innerBitmap,
                                outerAutoMode = outerAutoMode,
                                profile = profile,
                                existingId = editThemeId,
                            )
                        }
                        saving = false
                        onSaved(theme.id)
                    }
                },
                enabled = !saving && !converting && name.isNotBlank() &&
                    when (category) {
                        ThemeCategory.ANIMATION -> normalizedVideo != null || hasExistingVideo
                        else -> innerBitmap != null
                    },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                Text(if (saving) "保存中…" else "保存主题")
            }
        }
    }

    if (showMediaPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showMediaPermissionDialog = false },
            title = { Text("需要媒体访问权限") },
            text = {
                Text(
                    "检测到小米设备。为读取相册与下载等目录中的图片和视频，" +
                        "请授予媒体访问权限；也可以在素材区点「从文件选择」免授权浏览全部目录。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showMediaPermissionDialog = false
                    mediaPermissionLauncher.launch(
                        MediaPermissions.required(android.os.Build.VERSION.SDK_INT),
                    )
                }) { Text("去授权") }
            },
            dismissButton = {
                TextButton(onClick = { showMediaPermissionDialog = false }) { Text("暂不") }
            },
        )
    }
}

private fun mediaPermissionsGranted(context: android.content.Context): Boolean =
    MediaPermissions.required(android.os.Build.VERSION.SDK_INT).all {
        context.checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

private fun readDurationMs(context: android.content.Context, uri: Uri): Long {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: 0L
    } catch (e: Exception) {
        0L
    } finally {
        runCatching { retriever.release() }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun VideoStatusRow(
    icon: ImageVector,
    tint: Color,
    text: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
        Text(text, color = tint, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ImagePickerRow(
    bitmap: Bitmap?,
    aspect: Float,
    placeholder: String,
    description: String,
    onPick: () -> Unit,
    onPickFile: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPick) {
                Text(if (bitmap == null) "选择图片" else "重新选择")
            }
            if (onPickFile != null) {
                OutlinedButton(onClick = onPickFile) {
                    Text("从文件选择")
                }
            }
        }
        if (bitmap != null) {
            AsyncImage(
                model = bitmap,
                contentDescription = description,
                modifier = Modifier
                    .height(140.dp)
                    .aspectRatio(aspect)
                    .clip(MaterialTheme.shapes.medium),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .height(140.dp)
                    .aspectRatio(aspect)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Default.AddPhotoAlternate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp),
                    )
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
