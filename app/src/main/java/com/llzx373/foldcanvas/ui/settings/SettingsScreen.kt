package com.llzx373.foldcanvas.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.llzx373.foldcanvas.data.SettingsStore
import com.llzx373.foldcanvas.theme.FrameCache
import com.llzx373.foldcanvas.ui.components.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsStore(context.applicationContext) }
    val frameCache = remember { FrameCache(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var animationEnabled by remember { mutableStateOf(settings.animationEnabled) }
    var angleStart by remember { mutableFloatStateOf(settings.angleStart) }
    var angleEnd by remember { mutableFloatStateOf(settings.angleEnd) }
    var smoothingEnabled by remember { mutableStateOf(settings.smoothingEnabled) }
    var smoothingAlpha by remember { mutableFloatStateOf(settings.smoothingAlpha) }
    var demoMode by remember { mutableStateOf(settings.demoMode) }
    var cacheSize by remember { mutableLongStateOf(-1L) }
    LaunchedEffect(Unit) {
        cacheSize = withContext(Dispatchers.IO) { frameCache.totalSize() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
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
            SectionCard(title = "动画") {
                SwitchRow(
                    title = "展开动画",
                    subtitle = "关闭后外屏/内屏壁纸随角度交叉淡化切换",
                    checked = animationEnabled,
                    onCheckedChange = {
                        animationEnabled = it
                        settings.animationEnabled = it
                    },
                )

                SliderRow(
                    title = "动画起始角度",
                    valueLabel = "${angleStart.toInt()}°",
                ) {
                    Slider(
                        value = angleStart,
                        onValueChange = {
                            angleStart = it.coerceAtMost(angleEnd - 5f)
                            settings.angleStart = angleStart
                        },
                        valueRange = 0f..175f,
                    )
                }

                SliderRow(
                    title = "动画完成角度",
                    valueLabel = "${angleEnd.toInt()}°",
                ) {
                    Slider(
                        value = angleEnd,
                        onValueChange = {
                            angleEnd = it.coerceAtLeast(angleStart + 5f)
                            settings.angleEnd = angleEnd
                        },
                        valueRange = 5f..180f,
                    )
                }
            }

            SectionCard(title = "铰链") {
                SwitchRow(
                    title = "铰链去抖平滑",
                    subtitle = "传感器抖动明显时开启；会带来少量延迟，默认关闭",
                    checked = smoothingEnabled,
                    onCheckedChange = {
                        smoothingEnabled = it
                        settings.smoothingEnabled = it
                    },
                )

                if (smoothingEnabled) {
                    SliderRow(
                        title = "平滑强度",
                        subtitle = "越小越平滑、延迟越大",
                        valueLabel = "${(smoothingAlpha * 100).toInt()}%",
                    ) {
                        Slider(
                            value = smoothingAlpha,
                            onValueChange = {
                                smoothingAlpha = it
                                settings.smoothingAlpha = it
                            },
                            valueRange = 0.05f..0.5f,
                        )
                    }
                }
            }

            SectionCard(title = "其他") {
                SwitchRow(
                    title = "演示模式",
                    subtitle = "壁纸自动循环开合动画，用于录屏演示或无铰链传感器设备",
                    checked = demoMode,
                    onCheckedChange = {
                        demoMode = it
                        settings.demoMode = it
                    },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("动画帧缓存", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (cacheSize < 0L) "统计中…"
                            else "已占用 ${formatSize(cacheSize)}；清除后应用主题时会重新生成",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) { frameCache.clearAll() }
                                cacheSize = frameCache.totalSize()
                            }
                        },
                        enabled = cacheSize > 0L,
                    ) {
                        Text("清除")
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SliderRow(
    title: String,
    valueLabel: String,
    subtitle: String? = null,
    slider: @Composable () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                valueLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        slider()
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", bytes / 1048576.0)
    bytes >= 1L shl 10 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
