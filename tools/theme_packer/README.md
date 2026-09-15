# theme_packer — 折叠画卷主题包（.foldtheme）电脑端制作工具

在电脑上选好素材、预览外屏/内屏效果，一键打包成 `.foldtheme` 文件；
传到手机后**直接点开即导入**，手机端不再做任何转码/裁剪。

## 依赖

```bash
pip install pillow
```

- ffmpeg / ffprobe（可选）：放在 PATH 中即可自动探测，用于
  - 视频首/中/末帧预览条
  - 未提供内屏图时，自动取视频末帧作为内屏壁纸
- 没有 ffmpeg 也能用：内屏图改为手动选择。

## 用法

从 GitHub Release 下载 `FoldThemePacker.exe`（无需安装 Python），或本地打包/从源码运行：

```bash
python theme_packer.py
```

本地重新打包 exe（产出 `dist/主题包制作工具.exe`；CI 发布时会改名为 FoldThemePacker.exe）：

```bash
pip install pyinstaller
pyinstaller 主题包制作工具.spec
```

1. 填名称，选类别（展屏动画 / 内外图片 / 展屏模糊）与机型（常规折叠 / 宽屏折叠）
2. 选素材：展开视频（展屏动画类别必选）、内屏图、外屏图（后两者可缺省，按 App 同规则自动派生）
3. 预览区确认外屏 / 内屏效果
4. 点「打包 .foldtheme」

## 派生规则（与 App 端 ImageUtils / saveCustomTheme 一一对应）

- 内屏图：用户所选图片 centerCrop 到内屏分辨率；缺省时取视频末帧（需 ffmpeg）
- 外屏图：用户所选图片 centerCrop 到外屏分辨率；缺省时——
  - 展屏模糊：内屏**右半幅非等比拉伸铺满**外屏（ImageUtils.scaleFill）
  - 其他类别：按「外屏派生」（右半/中间/左半）取半幅后 centerCrop

## 包格式（.foldtheme = zip）

```
theme.properties   # Java Properties 格式，非 ASCII 转 \uXXXX
outer.png          # 已按目标机型外屏分辨率裁好
inner.png          # 已按目标机型内屏分辨率裁好
animation.mp4      # 可选，请事先自行裁到目标分辨率（工具不做转码）
```

properties 键与 App 端 `CustomThemeProps` 一致：
`id / name / outer / inner / animation / formFactor(normal|wide) / category(animation|images|duo_blur) / outerMode(LEFT|CENTER|RIGHT)`

## 手机端导入（两种方式）

1. 用任意方式（微信文件、数据线、网盘）把 `.foldtheme` 传到手机，在文件管理器里**直接点开** → 选择「折叠画卷」打开
2. 或打开 App → 主题列表右上角「导入主题包」图标 → 选择该文件

导入后即出现在主题列表，进入详情页「应用为壁纸」即可。

## 冒烟测试

```bash
python smoke_test.py   # 生成测试图 → 打包 → 读回校验 zip 结构与 properties 转义
```
