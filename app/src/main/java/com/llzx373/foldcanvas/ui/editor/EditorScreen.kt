package com.llzx373.foldcanvas.ui.editor

import android.graphics.Bitmap
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.llzx373.foldcanvas.convert.VideoNormalizer
import com.llzx373.foldcanvas.data.DeviceProfile
import com.llzx373.foldcanvas.theme.ThemeRepository
import com.llzx373.foldcanvas.ui.ImageUtils
import kotlinx.coroutines.Dispatchers
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
    val profile = remember { DeviceProfile.detect(appContext) }
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var normalizedVideo by remember { mutableStateOf<File?>(null) }
    var converting by remember { mutableStateOf(false) }
    var convertProgress by remember { mutableFloatStateOf(0f) }
    var convertError by remember { mutableStateOf(false) }
    var outerBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var innerBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var saving by remember { mutableStateOf(false) }

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            videoUri = uri
            normalizedVideo = null
            convertError = false
            converting = true
            convertProgress = 0f
            scope.launch {
                val output = File(appContext.cacheDir, "normalized_${System.currentTimeMillis()}.mp4")
                val result = normalizer.normalize(
                    uri, output,
                    targetWidth = profile.innerWidth, targetHeight = profile.innerHeight,
                ) { convertProgress = it }
                converting = false
                result
                    .onSuccess { normalizedVideo = it }
                    .onFailure { convertError = true }
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

            SectionTitle("展开动画（必选视频，自动转为 ≤5s 内屏规格）")
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

            SectionTitle("外屏壁纸（折叠时显示，${profile.outerWidth}×${profile.outerHeight}）")
            ImagePickerRow(
                bitmap = outerBitmap,
                aspect = profile.outerWidth.toFloat() / profile.outerHeight,
                onPick = {
                    outerPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            )

            SectionTitle("内屏壁纸（展开时显示，${profile.innerWidth}×${profile.innerHeight}）")
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
                    val outer = outerBitmap ?: return@Button
                    val inner = innerBitmap ?: return@Button
                    saving = true
                    scope.launch {
                        val theme = withContext(Dispatchers.IO) {
                            repository.saveCustomTheme(
                                name = name.trim(),
                                outerBitmap = outer,
                                innerBitmap = inner,
                                normalizedVideo = normalizedVideo,
                            )
                        }
                        saving = false
                        onSaved(theme.id)
                    }
                },
                enabled = !saving && !converting &&
                    name.isNotBlank() && outerBitmap != null && innerBitmap != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (saving) "保存中…" else "保存主题")
            }
        }
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
                Text("未选择", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
