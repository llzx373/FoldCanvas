# -*- coding: utf-8 -*-
"""
展屏模糊（Duo 效果）离线预览生成器。

用 Pillow 复刻 Android 端 DuoFrameGenerator + DuoFoldMath 的同款数学：
  - 铰链（内屏中线）锚定，左面板 sin(p·π/2) 余弦投影压缩
  - 左面板渐进模糊（半径 (1-scale)·MAX）与压暗
  - 右面板始终清晰静止
  - 左面板让出的区域由整幅内屏的模糊压暗氛围层填充

产出（output/）：
  - inner.png                示例内屏壁纸（带结构纹理，便于模糊可测）
  - frames/frame_XXX.png     45 帧展开动画（PNG 无损，便于像素级断言）
  - contact_sheet.png        9 宫格关键时刻总览
  - duo_preview.gif          折叠→展开循环预览
"""
import math
import os

from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "output")
FRAMES = os.path.join(OUT, "frames")

# 与 app/src/main/java/com/llzx373/foldcanvas/theme/duo/DuoFoldMath.kt 保持一致
FRAME_COUNT = 45
FRAME_WIDTH = 1440
FRAME_HEIGHT = 1728  # 1812×2176 内屏比例
OUTER_WIDTH, OUTER_HEIGHT = 1080, 2400  # 常规折叠外屏（DeviceProfile.NORMAL_FOLD）
COVER_MAX_BLUR_RADIUS = 40.0  # 与 DuoFoldMath.COVER_MAX_BLUR_RADIUS 一致
COVER_FRAMES = os.path.join(OUT, "frames_cover")


def smoothstep(x):
    t = min(1.0, max(0.0, x))
    return t * t * (3 - 2 * t)


def cover_progress(angle_deg):
    return min(1.0, max(0.0, angle_deg / 90.0))


def cover_blur_radius(edge, motion, max_radius=COVER_MAX_BLUR_RADIUS):
    return max_radius * min(1.0, max(0.0, motion)) * min(1.0, max(0.0, edge)) ** 1.35


def cover_darken(edge, motion):
    dark_g = min(1.0, max(0.0, (min(1.0, max(0.0, edge)) - 0.2) / 0.8))
    return min(1.0, max(0.0, min(1.0, max(0.0, motion)) * dark_g ** 1.35 * 2))


def render_cover_frame(window, pyramid, motion, bands=32):
    """外屏帧：竖带按 edge 取最近模糊级 + 铰链→外缘压暗渐变（对齐 Android 端实现）。"""
    w, h = window.size
    band_w = w // bands
    frame = Image.new("RGB", (w, h))
    for b in range(bands):
        edge = (b + 0.5) / bands
        r = cover_blur_radius(edge, motion)
        k = int(round(r / COVER_MAX_BLUR_RADIUS * (len(pyramid) - 1)))
        k = max(0, min(len(pyramid) - 1, k))
        x0 = b * band_w
        x1 = w if b == bands - 1 else (b + 1) * band_w
        frame.paste(pyramid[k].crop((x0, 0, x1, h)), (x0, 0))
    # 压暗：逐列逼近 edge^1.35 曲线
    dark = Image.new("L", (w, 1))
    dark.putdata([int(round(cover_darken(x / (w - 1), motion) * 255)) for x in range(w)])
    frame.paste(Image.new("RGB", (w, h), (0, 0, 0)), (0, 0),
                dark.resize((w, h)))
    return frame


def make_cover_frames(outer):
    os.makedirs(COVER_FRAMES, exist_ok=True)
    levels = 8
    pyramid = [outer] + [
        outer.filter(ImageFilter.GaussianBlur(COVER_MAX_BLUR_RADIUS * k / levels))
        for k in range(1, levels + 1)
    ]
    frames = []
    for i in range(FRAME_COUNT):
        p = i / (FRAME_COUNT - 1)
        frame = render_cover_frame(outer, pyramid, smoothstep(p))
        frame.save(os.path.join(COVER_FRAMES, "frame_%03d.png" % i))
        frames.append(frame)
    return frames
MAX_BLUR_RADIUS = 48.0
AMBIENT_BLUR_RADIUS = 24
AMBIENT_DIM_ALPHA = 50
PANEL_MAX_DIM_ALPHA = 90


def panel_scale(p):
    p = min(1.0, max(0.0, p))
    return math.sin(p * math.pi / 2)


def blur_radius(p, max_radius=MAX_BLUR_RADIUS):
    return (1 - panel_scale(p)) * max_radius


def panel_dim_alpha(p):
    return int(round((1 - panel_scale(p)) * PANEL_MAX_DIM_ALPHA))


def make_sample_inner(width=FRAME_WIDTH, height=FRAME_HEIGHT):
    """示例内屏壁纸：斜向渐变底 + 网格线 + 文字条（高频细节便于检测模糊）。"""
    img = Image.new("RGB", (width, height))
    px = img.load()
    stops = [(0x14, 0x1E, 0x30), (0x3A, 0x5A, 0x78), (0xA8, 0xC0, 0xD6)]
    for y in range(height):
        for x in range(0, width, 4):
            t = (x + y) / (width + height)
            seg = min(int(t * (len(stops) - 1)), len(stops) - 2)
            f = t * (len(stops) - 1) - seg
            c = tuple(int(stops[seg][i] * (1 - f) + stops[seg + 1][i] * f) for i in range(3))
            for dx in range(4):
                if x + dx < width:
                    px[x + dx, y] = c
    draw = ImageDraw.Draw(img)
    for gx in range(0, width, 60):
        draw.line([(gx, 0), (gx, height)], fill=(255, 255, 255, 40), width=2)
    for gy in range(0, height, 60):
        draw.line([(0, gy), (width, gy)], fill=(255, 255, 255, 40), width=2)
    for i in range(6):
        draw.rectangle(
            [80, 120 + i * 260, width - 80, 200 + i * 260],
            outline=(255, 255, 255), width=4,
        )
    return img


def render_frame(inner, ambient, p):
    width, height = inner.size
    half = width // 2
    frame = ambient.copy()
    # 右面板：铰链侧，始终清晰
    frame.paste(inner.crop((half, 0, width, height)), (half, 0))
    # 左面板：余弦压缩 + 渐进模糊 + 压暗
    panel_w = int(round(panel_scale(p) * half))
    if panel_w > 0:
        panel = inner.crop((0, 0, half, height))
        r = blur_radius(p)
        if r > 0.5:
            panel = panel.filter(ImageFilter.GaussianBlur(r))
        panel = panel.resize((panel_w, height))
        dim = panel_dim_alpha(p)
        if dim > 0:
            black = Image.new("RGB", panel.size, (0, 0, 0))
            panel = Image.blend(panel, black, dim / 255.0)
        frame.paste(panel, (half - panel_w, 0))
    return frame


def main():
    os.makedirs(FRAMES, exist_ok=True)
    inner = make_sample_inner()
    inner.save(os.path.join(OUT, "inner.png"))

    # 外屏 = 内屏右半幅完整拉伸铺满（Duo 外屏 UV 窗口，不 centerCrop）
    half = inner.size[0] // 2
    right_half = inner.crop((half, 0, inner.size[0], inner.size[1]))
    outer = right_half.resize((OUTER_WIDTH, OUTER_HEIGHT))
    outer.save(os.path.join(OUT, "outer.png"))
    cover_frames = make_cover_frames(outer)

    ambient = inner.filter(ImageFilter.GaussianBlur(AMBIENT_BLUR_RADIUS))
    ambient = Image.blend(ambient, Image.new("RGB", inner.size, (0, 0, 0)),
                          AMBIENT_DIM_ALPHA / 255.0)

    frames = []
    for i in range(FRAME_COUNT):
        p = i / (FRAME_COUNT - 1)
        frame = render_frame(inner, ambient, p)
        frame.save(os.path.join(FRAMES, "frame_%03d.png" % i))
        frames.append(frame)
        print("rendered frame %02d  p=%.3f  scale=%.3f  blur=%.1f  dim=%d"
              % (i, p, panel_scale(p), blur_radius(p), panel_dim_alpha(p)))

    # 9 宫格总览：0, 1/8, ..., 1
    cols, rows = 3, 3
    thumb_w = 360
    thumb_h = int(FRAME_HEIGHT * thumb_w / FRAME_WIDTH)
    sheet = Image.new("RGB", (cols * thumb_w, rows * thumb_h), (10, 10, 14))
    for k in range(cols * rows):
        idx = round(k * (FRAME_COUNT - 1) / (cols * rows - 1))
        sheet.paste(frames[idx].resize((thumb_w, thumb_h)),
                    ((k % cols) * thumb_w, (k // cols) * thumb_h))
    sheet.save(os.path.join(OUT, "contact_sheet.png"))

    # 外屏帧 9 宫格总览
    cthumb_w = 180
    cthumb_h = int(OUTER_HEIGHT * cthumb_w / OUTER_WIDTH)
    csheet = Image.new("RGB", (cols * cthumb_w, rows * cthumb_h), (10, 10, 14))
    for k in range(cols * rows):
        idx = round(k * (FRAME_COUNT - 1) / (cols * rows - 1))
        csheet.paste(cover_frames[idx].resize((cthumb_w, cthumb_h)),
                     ((k % cols) * cthumb_w, (k // cols) * cthumb_h))
    csheet.save(os.path.join(OUT, "contact_sheet_cover.png"))

    # GIF：折叠→展开→折叠循环
    gif_frames = [f.resize((360, 432)) for f in frames + frames[::-1]]
    gif_frames[0].save(
        os.path.join(OUT, "duo_preview.gif"), save_all=True,
        append_images=gif_frames[1:], duration=50, loop=0,
    )
    print("done ->", OUT)


if __name__ == "__main__":
    main()
