# -*- coding: utf-8 -*-
"""
折叠画卷 - 「展翼」主题预览生成器

概念：以中线铰链为轴的抽象矢量翅膀。
- 折叠态(t=0)：右翅收折在左翅之下，外屏只看到左翅（左盖右）。
- 展开过程：右翅绕铰链"翻页式"展开（x 方向按 cos(θ) 投影压缩模拟 3D）。
- 展开态(t=1)：双翅完全展开。

变体（VARIANTS）：
  默认：外屏 1080x2400 / 内屏 1812x2176
  _wide：外屏 1168x1712（竖屏）/ 内屏 2364x1672（横屏）
  外屏壁纸策略（outer_mode）：
    默认：取 t=0 折叠态左半幅场景 cover 铺满（铰链贴右边缘，左盖右）。
    _wide（inner_right_half）：取 t=1 展开态内屏图的右半边（铰链在左边缘、
      含完整右翅），cover 铺满放大到外屏分辨率。详见 README.md。

输出（output/，{s} 为后缀）：
  outer{s}.png / inner{s}.png   外屏/内屏壁纸
  frames{s}/                    45 帧展开动画
  wings{s}.mp4                  帧序列合成的预览视频（亦作为 App 内置动画素材）
  contact_sheet{s}.png          全部帧速览
"""
import math
import os
import subprocess
import sys

from PIL import Image, ImageDraw, ImageFilter

FRAME_COUNT = 45
FPS = 30
SS = 2  # 超采样抗锯齿

VARIANTS = [
    {"suffix": "", "outer": (1080, 2400), "inner": (1812, 2176)},
    {"suffix": "_wide", "outer": (1168, 1712), "inner": (2364, 1672),
     "outer_mode": "inner_right_half"},
]

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "output")

# 配色：深夜蓝紫背景 + 金→琥珀→珊瑚→品红 翅膀
BG_TOP = (16, 14, 38)
BG_BOTTOM = (42, 26, 74)
GLOW = (255, 214, 140)

# 翅膀造型：上扬的不对称羽扇（角度相对 +x 方向，度；长度 ×min(W,H)*FEATHER_BASE；宽度 ×长度）
# (angle_deg, len_ratio, width_ratio, color, pivot_dy)  —— 绘制顺序即叠放次序
FEATHERS = [
    # 主羽：越往上越长越亮（后画者压先画者）
    (-2, 0.55, 0.15, (168, 72, 138), 34),
    (-14, 0.68, 0.16, (184, 84, 128), 24),
    (-28, 0.84, 0.16, (208, 100, 110), 13),
    (-42, 0.96, 0.155, (240, 148, 88), 2),
    (-56, 1.00, 0.15, (250, 176, 96), -9),
    (-70, 0.90, 0.145, (255, 202, 118), -20),
    # 覆羽：较短，压在主羽根部
    (-22, 0.44, 0.21, (226, 122, 96), 11),
    (-40, 0.50, 0.21, (250, 166, 92), -2),
    (-58, 0.46, 0.20, (255, 214, 128), -15),
    # 翼根盖片：竖直粗羽，遮盖羽毛结合部
    (-88, 0.30, 0.30, (255, 225, 150), -6),
]
FEATHER_BASE = 0.56  # 长度基准 ×min(W,H)
DESIGN_REF = 1812    # pivot_dy 等绝对像素参数的参考尺寸


def lerp(a, b, t):
    return a + (b - a) * t


def lerp_color(c1, c2, t):
    return tuple(int(round(lerp(a, b, t))) for a, b in zip(c1, c2))


def ease_in_out_cubic(t):
    return 4 * t * t * t if t < 0.5 else 1 - (-2 * t + 2) ** 3 / 2


def bezier(p0, p1, p2, p3, n=24):
    pts = []
    for i in range(n + 1):
        t = i / n
        mt = 1 - t
        x = mt**3 * p0[0] + 3 * mt**2 * t * p1[0] + 3 * mt * t**2 * p2[0] + t**3 * p3[0]
        y = mt**3 * p0[1] + 3 * mt**2 * t * p1[1] + 3 * mt * t**2 * p2[1] + t**3 * p3[1]
        pts.append((x, y))
    return pts


def feather_polygon(pivot, angle_deg, length, width):
    """修长泪滴形羽毛多边形：根窄、四成处最宽、端部收成尖。"""
    a = math.radians(angle_deg)
    d = (math.cos(a), math.sin(a))       # 方向
    n = (-d[1], d[0])                    # 法向
    px, py = pivot
    tip = (px + d[0] * length, py + d[1] * length)

    def off(t_along, t_side):
        return (
            px + d[0] * length * t_along + n[0] * width * t_side,
            py + d[1] * length * t_along + n[1] * width * t_side,
        )

    side1 = bezier(off(0.02, 0.0), off(0.22, 0.62), off(0.55, 1.0), tip)
    side2 = bezier(tip, off(0.55, -1.0), off(0.22, -0.62), off(0.02, 0.0))
    return side1 + side2


def draw_wing(layer, hinge, mirror, scale_x, base, k, palette_shift=0.0):
    """在 layer 上画一只翅膀。mirror=True 为左翅（水平镜像）。
    scale_x 为绕铰链的投影系数（可为负：负值表示翻到对侧，会被上层翅膀遮住）。
    base 为羽毛长度基准像素，k 为绝对像素参数的缩放系数。"""
    hx, hy = hinge
    draw = ImageDraw.Draw(layer)
    for ang, len_r, wid_r, color, pivot_dy in FEATHERS:
        length = len_r * base
        width = wid_r * length
        pivot = (hx, hy + pivot_dy * k)
        poly = feather_polygon(pivot, ang, length, width)
        # 绕铰链做投影变换（保留符号：收折态翻到左翅背后）
        t = []
        for x, y in poly:
            dx = (x - hx) * scale_x
            if mirror:
                dx = -dx
            t.append((hx + dx, y))
        if palette_shift > 0:
            color = lerp_color(color, (255, 255, 255), palette_shift)
        draw.polygon(t, fill=color + (235,),
                     outline=lerp_color(color, (255, 255, 255), 0.35) + (220,))
    # 铰链结合部圆盘，遮盖羽毛根部的接缝
    r = 20 * k
    draw.ellipse([hx - r, hy - r, hx + r, hy + r],
                 fill=(255, 225, 150, 245))


def render_frame(t, out_w, out_h, hinge_ratio=0.5, ref_min=None):
    """以 out_w×out_h 为设计空间直接渲染进度 t ∈ [0,1] 的一帧。
    hinge_ratio：铰链在画面中的水平位置（0.5 居中；1.0 贴右边缘，用于外屏半幅）。
    ref_min：翅膀尺寸参考边长（默认 min(out_w,out_h)；外屏半幅时传全幅短边，保证与内屏同一比例尺）。"""
    W, H = out_w * SS, out_h * SS
    min_dim = (ref_min * SS) if ref_min else min(W, H)
    k = min_dim / (DESIGN_REF * SS)  # 绝对像素参数缩放
    base = min_dim * FEATHER_BASE

    # 背景竖向渐变
    bg = Image.new("RGB", (1, H))
    for y in range(H):
        bg.putpixel((0, y), lerp_color(BG_TOP, BG_BOTTOM, y / H))
    img = bg.resize((W, H)).convert("RGBA")

    hinge = (W * hinge_ratio, H * 0.56)
    te = t  # 线性：帧进度与铰链角度 1:1 映射，不加缓动

    # 铰链光晕（随展开增强）
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    r = (0.12 + 0.55 * te) * min_dim * 0.5
    gd.ellipse([hinge[0] - r, hinge[1] - r, hinge[0] + r, hinge[1] + r],
               fill=GLOW + (int(40 + 90 * te),))
    glow = glow.filter(ImageFilter.GaussianBlur(radius=int(min_dim * 0.05)))
    img = Image.alpha_composite(img, glow)

    # 右翅：θ 从 165°(收折在左翅背后) → 0°(完全展开)
    # θ>90° 时翅膀物理上在左翅背后，直接不绘制；
    # 若按 cos 负值镜像投影，会从较窄的左翅边缘露出，造成"先回摆再展开"的假动作
    theta = math.radians(165 * (1 - te))
    scale_x = math.cos(theta)
    wing = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    if scale_x > 0.02:
        draw_wing(wing, hinge, mirror=False, scale_x=scale_x, base=base, k=k)
    img = Image.alpha_composite(img, wing)

    # 左翅：始终在上层（盖住收折的右翅），展开过程中从微垂轻抬到完全展开
    left = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw_wing(left, hinge, mirror=True, scale_x=0.90 + 0.10 * te,
              base=base, k=k, palette_shift=0.10 * te)
    img = Image.alpha_composite(img, left)

    # 铰链亮线
    ld = ImageDraw.Draw(img)
    lw = max(2, int(3 * k * SS / 2))
    ld.line([(hinge[0], H * 0.06), (hinge[0], H * 0.97)],
            fill=lerp_color(GLOW, (255, 255, 255), 0.4) + (int(90 + 100 * te),), width=lw)

    return img.convert("RGB").resize((out_w, out_h), Image.LANCZOS)


def make_contact_sheet(frame_paths, path):
    cols, rows = 9, 5
    tw, th = 240, 200
    sheet = Image.new("RGB", (cols * tw, rows * th), (10, 10, 20))
    d = ImageDraw.Draw(sheet)
    for i, fp in enumerate(frame_paths):
        im = Image.open(fp)
        im.thumbnail((tw, th), Image.LANCZOS)
        x, y = (i % cols) * tw, (i // cols) * th
        sheet.paste(im, (x + (tw - im.width) // 2, y + (th - im.height) // 2))
        d.text((x + 6, y + 4), f"t={i / (len(frame_paths) - 1):.2f}", fill=(255, 255, 255))
    sheet.save(path)


def cover_fit(img, out_w, out_h):
    """等比缩放至铺满后居中裁剪。"""
    scale = max(out_w / img.width, out_h / img.height)
    nw, nh = round(img.width * scale), round(img.height * scale)
    img = img.resize((nw, nh), Image.LANCZOS)
    x, y = (nw - out_w) // 2, (nh - out_h) // 2
    return img.crop((x, y, x + out_w, y + out_h))


def render_outer(outer_size, inner_size, outer_mode=None):
    """外屏壁纸。
    outer_mode="inner_right_half"：取 t=1 展开态内屏场景的右半边
      （铰链在左边缘、含完整右翅），cover 铺满放大到外屏分辨率。
    竖屏外屏（默认）：按内屏全幅画布的左半幅渲染 t=0 折叠态（与内屏同一比例尺，
      铰链贴右边缘，左盖右），再铺满裁剪。
    若半幅宽高比与外屏差异过大（居中裁剪会切掉翅尖），改为在外屏设计空间直接渲染，
    翅膀比例尺仍与内屏一致（ref_min=内屏短边），保证完整半翅可见。
    横屏外屏：半幅竖图无法铺满横屏，直接在横屏设计空间渲染（铰链贴右边缘）。"""
    if outer_mode == "inner_right_half":
        scene = render_frame(1.0, *inner_size)
        right = scene.crop((scene.width // 2, 0, scene.width, scene.height))
        return cover_fit(right, *outer_size)
    if outer_size[0] > outer_size[1]:
        return render_frame(0.0, *outer_size, hinge_ratio=1.0)
    half_w = inner_size[0] // 2
    half_aspect = half_w / inner_size[1]
    outer_aspect = outer_size[0] / outer_size[1]
    if outer_aspect / half_aspect > 1.15:
        return render_frame(0.0, *outer_size, hinge_ratio=1.0,
                            ref_min=min(inner_size))
    scene = render_frame(0.0, half_w, inner_size[1],
                         hinge_ratio=1.0, ref_min=min(inner_size))
    return cover_fit(scene, *outer_size)


def render_variant(suffix, outer_size, inner_size, outer_mode=None):
    frames_dir = os.path.join(OUT, f"frames{suffix}")
    os.makedirs(frames_dir, exist_ok=True)
    frame_paths = []
    for i in range(FRAME_COUNT):
        t = i / (FRAME_COUNT - 1)
        frame = render_frame(t, *inner_size)
        fp = os.path.join(frames_dir, f"frame_{i:03d}.jpg")
        frame.save(fp, quality=88)
        frame_paths.append(fp)
        print(f"\r{suffix or 'default'} frames {i + 1}/{FRAME_COUNT}", end="", flush=True)
    print()

    render_outer(outer_size, inner_size, outer_mode).save(
        os.path.join(OUT, f"outer{suffix}.png"))
    render_frame(1.0, *inner_size).save(os.path.join(OUT, f"inner{suffix}.png"))
    make_contact_sheet(frame_paths, os.path.join(OUT, f"contact_sheet{suffix}.png"))

    mp4 = os.path.join(OUT, f"wings{suffix}.mp4")
    subprocess.run([
        "ffmpeg", "-y", "-framerate", str(FPS),
        "-i", os.path.join(frames_dir, "frame_%03d.jpg"),
        "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "20",
        "-movflags", "+faststart", mp4,
    ], check=True, capture_output=True)


def main():
    os.makedirs(OUT, exist_ok=True)
    for v in VARIANTS:
        render_variant(v["suffix"], v["outer"], v["inner"],
                       v.get("outer_mode"))
    print("done ->", OUT)


if __name__ == "__main__":
    sys.exit(main())
