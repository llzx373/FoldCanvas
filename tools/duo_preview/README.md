# duo_preview — 展屏模糊（Duo 效果）离线预览与端到端校验

不启动 Android，用 Pillow 复刻 App 端 `theme/duo/`（DuoFoldMath + BoxBlur + DuoFrameGenerator）
的同款数学，离线渲染 45 帧展开动画并做像素级断言，用于效果评审与回归校验。

## 效果原理

实现思想参照 [DuoFoldWallpaper](https://github.com/Vyom-2007/DuoFoldWallpaper)（iPhone Duo
折叠过渡），常量和公式与 `app/.../theme/duo/DuoFoldMath.kt` 一一对应（e2e 会直接解析
Kotlin 源码比对，防止两侧漂移）：

- 铰链（内屏中线）锚定：左面板水平宽度 = `sin(p·π/2)`（等价余弦边缘投影），从铰链向左生长
- 左面板渐进模糊：半径 `(1 - scale) · 48px`，并随压缩程度压暗（≤ 90/255）
- 右面板（铰链侧）始终清晰静止——即"外屏 = 内屏右半幅"的窗口来源
- 左面板让出的区域由整幅内屏的模糊（24px）+ 压暗（50/255）氛围层填充
- p=0：左半全为氛围层；p=1：与内屏壁纸逐像素一致

与 Android 端差异：Pillow 用高斯模糊、Android 端用三次盒式模糊（minSdk 26 兼容），
两者视觉等价，断言均为相对度量不受影响。

## 用法

```bash
python generate_duo.py   # 渲染 45 帧 + contact_sheet.png + duo_preview.gif 到 output/
python e2e_check.py      # 11 项端到端断言，任一失败退出码非 0
```

## e2e 断言清单

1. Kotlin DuoFoldMath 常量与 Python 预览一致
2. 45 帧齐全、尺寸与内屏一致
3. 角度→帧号单调不减（与 AngleFrameMapperTest 互证）
4. 所有帧右半幅与内屏右半像素一致
5. 末帧与内屏壁纸完全一致
6. 首帧左半为模糊氛围层（高频细节显著少于内屏左半）
7. 左半清晰度随帧号单调不减
8. 铰链锚定：首帧左半恰为氛围层、中间帧左半为压缩模糊中间态
9. 外屏派生源为内屏右半幅
