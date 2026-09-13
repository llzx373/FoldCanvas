package com.llzx373.foldcanvas.ui.editor

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.llzx373.foldcanvas.convert.ClipRange
import com.llzx373.foldcanvas.convert.VideoNormalizer
import com.llzx373.foldcanvas.data.DeviceProfile
import com.llzx373.foldcanvas.theme.ThemeRepository
import com.llzx373.foldcanvas.ui.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(onBack: () -> Unit, onSaved: (String) -> Unit) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val repository = remember { ThemeRepository(appContext) }
    val normalizer = remember { VideoNormalizer(appContext) }
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var wideFold by remember { mutableStateOf(false) }
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

    var outerBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var innerBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var saving by remember { mutableStateOf(false) }

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

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            videoUri = uri
            scope.launch {
                val durationMs = withContext(Dispatchers.IO) { readDurationMs(appContext, uri) }
                videoDurationSec = durationMs / 1000f
                clipStartSec = 0f
                clipEndSec = videoDurationSec
                startConvert()
            }
        }
    }
    val outerPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) scope.launch {
            outerBitmap = withContext(Dispatchers.IO) {
                ImageUtils.decodeCenterCrop(
                    appContext, uri,
                    profile.outerWidth, profile.outerHeight,
                )
            }
        }
    }
    val innerPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) scope.launch {
            innerBitmap = withContext(Dispatchers.IO) {
                ImageUtils.decodeCenterCrop(
                    appContext, uri,
                    profile.innerWidth, profile.innerHeight,
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("创建自定义主题") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
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
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("主题名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            SectionTitle("目标机型（切换后需重新选择素材）")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(
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
                    label = { Text("正常折叠屏（内屏竖屏）") },
                )
                FilterChip(
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
                    label = { Text("宽屏折叠屏（内屏横屏）") },
                )
            }
            Text(
                if (wideFold) "内屏 2364×1672（横）/ 外屏 1168×1712（竖）"
                else "内屏 1812×2176（竖）/ 外屏 1080×2400（竖）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionTitle("展开动画（必选视频，规格化后均匀抽取 45 帧）")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = {
                    videoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                    )
                }) {
                    Text(if (videoUri == null) "选择视频" else "重新选择视频")
                }
                when {
                    converting -> Text(
                        "转换中 ${(convertProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    normalizedVideo != null -> Text(
                        "✓ 已规格化",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    convertError -> Text(
                        "转换失败，请重试",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
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

            SectionTitle("外屏壁纸（可选；不选则取内屏右半部分）")
            ImagePickerRow(
                bitmap = outerBitmap,
                aspect = profile.outerWidth.toFloat() / profile.outerHeight,
                onPick = {
                    outerPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            )

            SectionTitle("内屏壁纸（可选；不选则取视频末帧）")
            ImagePickerRow(
                bitmap = innerBitmap,
                aspect = profile.innerWidth.toFloat() / profile.innerHeight,
                onPick = {
                    innerPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            )

            Button(
                onClick = {
                    val video = normalizedVideo ?: return@Button
                    saving = true
                    scope.launch {
                        val theme = withContext(Dispatchers.IO) {
                            repository.saveCustomTheme(
                                name = name.trim(),
                                normalizedVideo = video,
                                outerBitmap = outerBitmap,
                                innerBitmap = innerBitmap,
                                profile = profile,
                            )
                        }
                        saving = false
                        onSaved(theme.id)
                    }
                },
                enabled = !saving && !converting &&
                    name.isNotBlank() && normalizedVideo != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (saving) "保存中…" else "保存主题")
            }
        }
    }
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
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun ImagePickerRow(bitmap: Bitmap?, aspect: Float, onPick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(onClick = onPick) {
            Text(if (bitmap == null) "选择图片" else "重新选择")
        }
        if (bitmap != null) {
            AsyncImage(
                model = bitmap,
                contentDescription = null,
                modifier = Modifier.height(96.dp).aspectRatio(aspect),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier.height(96.dp).aspectRatio(aspect),
                contentAlignment = Alignment.Center,
            ) {
                Text("自动派生", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
