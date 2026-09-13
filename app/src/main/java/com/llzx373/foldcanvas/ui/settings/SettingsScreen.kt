package com.llzx373.foldcanvas.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.llzx373.foldcanvas.data.SettingsStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsStore(context.applicationContext) }

    var animationEnabled by remember { mutableStateOf(settings.animationEnabled) }
    var angleStart by remember { mutableFloatStateOf(settings.angleStart) }
    var angleEnd by remember { mutableFloatStateOf(settings.angleEnd) }
    var smoothingEnabled by remember { mutableStateOf(settings.smoothingEnabled) }
    var smoothingAlpha by remember { mutableFloatStateOf(settings.smoothingAlpha) }
    var demoMode by remember { mutableStateOf(settings.demoMode) }

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
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("展开动画", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "关闭后外屏/内屏壁纸随角度交叉淡化切换",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = animationEnabled,
                    onCheckedChange = {
                        animationEnabled = it
                        settings.animationEnabled = it
                    },
                )
            }

            Column {
                Text("动画起始角度：${angleStart.toInt()}°", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = angleStart,
                    onValueChange = {
                        angleStart = it.coerceAtMost(angleEnd - 5f)
                        settings.angleStart = angleStart
                    },
                    valueRange = 0f..175f,
                )
            }

            Column {
                Text("动画完成角度：${angleEnd.toInt()}°", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = angleEnd,
                    onValueChange = {
                        angleEnd = it.coerceAtLeast(angleStart + 5f)
                        settings.angleEnd = angleEnd
                    },
                    valueRange = 5f..180f,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("铰链去抖平滑", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "传感器抖动明显时开启；会带来少量延迟，默认关闭",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = smoothingEnabled,
                    onCheckedChange = {
                        smoothingEnabled = it
                        settings.smoothingEnabled = it
                    },
                )
            }

            if (smoothingEnabled) {
                Column {
                    Text(
                        "平滑强度：${(smoothingAlpha * 100).toInt()}%",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "越小越平滑、延迟越大",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("演示模式", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "壁纸自动循环开合动画，用于录屏演示或无铰链传感器设备",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = demoMode,
                    onCheckedChange = {
                        demoMode = it
                        settings.demoMode = it
                    },
                )
            }
        }
    }
}
