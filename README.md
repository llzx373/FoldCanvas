# FoldCanvas 折叠画卷

[![CI](https://github.com/llzx373/FoldCanvas/actions/workflows/ci.yml/badge.svg)](https://github.com/llzx373/FoldCanvas/actions/workflows/ci.yml)
[![Release](https://github.com/llzx373/FoldCanvas/actions/workflows/release.yml/badge.svg)](https://github.com/llzx373/FoldCanvas/actions/workflows/release.yml)

面向小米书式折叠屏的**铰链联动动态壁纸**：展开/合拢手机时，壁纸画面与铰链角度严格 1:1 逐帧映射——翅膀随手势逐渐展开，无定时播放、无追赶重放，跟手即所见。

## 特性

- **角度驱动帧序列**：`Sensor.TYPE_HINGE_ANGLE` → 线性映射 → 45 帧 JPEG 逐帧 blit；合并绘制只画最新角度 + 相邻帧预取，`SENSOR_DELAY_FASTEST` 采样
- **内置主题「展翼 / 展翼·宽幅」**：抽象矢量翅膀（贝塞尔羽毛），折叠态左盖右，展开至双翅全开；宽幅变体适配外屏竖屏 1168×1712 / 内屏横屏 2364×1672 机型
- **自定义主题**：上传视频可自选起止区间（RangeSlider），规格化（H.264 ≤8Mbps）后整段均匀抽取 45 帧；可选正常/宽屏折叠屏两种目标机型；外屏/内屏图可不选——自动取视频末帧作内屏、内屏右半部分作外屏
- **设置**：动画开关、动画角度区间、铰链去抖平滑（EMA，静止后自驱收敛，默认关闭）、演示模式（自动开合循环，录屏/无铰链设备用）
- **即时生效**：壁纸引擎监听 SharedPreferences 变化，设置改动无需重设壁纸
- **渲染**：`lockHardwareCanvas` 优先（失败回退软件画布），LruCache 帧缓存 + 运动方向预取

## 架构

```
app/src/main/java/com/llzx373/foldcanvas/
├── wallpaper/    FoldWallpaperService（引擎）/ FrameRenderer（逐帧渲染）/ AngleFrameMapper（角度→帧映射）
├── theme/        ThemeRepository / FrameCache / ProceduralThemeFactory（占位渐变主题）
├── convert/      VideoNormalizer（视频规格化）/ FrameExtractor（抽帧）
├── data/         SettingsStore / DeviceProfile（设备屏幕档案探测）
└── ui/           gallery（主题列表）/ detail / editor（自定义主题）/ settings
tools/wings_preview/   「展翼」素材生成器 + 纯逻辑 e2e 校验（22 项，详见其 README）
```

核心链路：铰链角度事件 → 记录最新角度 → HandlerThread 合并绘制 →
`AngleFrameMapper.progress/frameIndex` → 帧缓存取图 → SurfaceHolder blit。

## 构建

需要 JDK 25（Gradle daemon JVM 已按 toolchain 25 配置）：

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

产物：`app/build/outputs/apk/debug/FoldCanvas-v1.1-debug.apk`

素材改动流程：`tools/wings_preview/` 下重新生成 → 拷贝进 `app/src/main/assets/themes/` →
`python e2e_check.py` 全过 → 重新打包。

## CI / CD

- **CI**（`.github/workflows/ci.yml`）：push / PR 触发，跑单元测试 + 构建 debug APK，APK 作为 artifact 上传。
- **Release**（`.github/workflows/release.yml`）：推送 `v*` tag 触发，跑测试 + 构建 release/debug APK 并创建 GitHub Release 附件。

Release 签名（可选）：在仓库 Settings → Secrets and variables → Actions 配置以下 secrets 后，
release 流水线会用其签名 release APK；未配置时 release APK 为未签名包（请使用附件中的 debug APK 安装）。

| Secret | 说明 |
| --- | --- |
| `KEYSTORE_BASE64` | `base64 -w0 your.keystore` 的输出 |
| `KEYSTORE_PASSWORD` | keystore 密码 |
| `KEY_ALIAS` | 密钥别名 |
| `KEY_PASSWORD` | 密钥密码 |

## 真机调试

```bash
adb logcat -s FoldWallpaper
```

引擎会输出壁纸 surface 尺寸与系统 display 枚举（外屏是否独立 display 的诊断依据）。
