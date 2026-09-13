# -*- coding: utf-8 -*-
"""
FoldCanvas 品牌图标生成器（Pillow，4x 超采样抗锯齿）。

设计：折叠双屏 + 金色铰链，配色取内置主题「雾境朦胧」的深蓝灰渐变。
- 背景：深蓝海蓝对角渐变（adaptive background）
- 前景：中央金色铰链条；右页为完整明亮圆角面板（外屏窗口），
  左页为压缩变暗的梯形（折叠态左面板），呼应 Duo 翻页效果
- 安全区：关键元素限制在中央 72dp/108dp 内，兼容圆形/圆角方形遮罩

产出（直接写入 app/src/main/res/）：
- mipmap-{density}/ic_launcher_foreground.png   自适应前景（108/162/216/324/432）
- mipmap-{density}/ic_launcher_background.png   自适应背景
- mipmap-{density}/ic_launcher_monochrome.png   单色剪影（前景 alpha，主题化图标用）
- mipmap-{density}/ic_launcher.png              传统图标（48/72/96/144/192）
- mipmap-{density}/ic_launcher_round.png        传统圆形图标
"""
import math
import os

from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.abspath(os.path.join(HERE, "..", "..", "app", "src", "main", "res"))

BASE = 432          # xxxhdpi 自适应图标基准（108dp × 4）
SS = 4              # 超采样倍数
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}

BG_STOPS = [(0x14, 0x1E, 0x30), (0x24, 0x3F, 0x63), (0x3A, 0x5A, 0x78)]
GOLD = (0xFF, 0xD9, 0xA0)
RIGHT_TOP = (0xE8, 0xF0, 0xF8)
RIGHT_BOT = (0x9D, 0xB8, 0xD0)
LEFT_TOP = (0x54, 0x6E, 0x8C)
LEFT_BOT = (0x33, 0x47, 0x61)


def lerp(c1, c2, t):
    return tuple(int(a + (b - a) * t) for a, b in zip(c1, c2))


def gradient(size, stops, diagonal=True):
    w, h = size
    img = Image.new("RGB", size)
    px = img.load()
    for y in range(h):
        for x in range(w):
            t = ((x + y) / (w + h)) if diagonal else (y / h)
            seg = min(int(t * (len(stops) - 1)), len(stops) - 2)
            f = t * (len(stops) - 1) - seg
            px[x, y] = lerp(stops[seg], stops[seg + 1], f)
    return img


def draw_foreground():
    """在 BASE*SS 透明画布上绘制前景，返回缩到 BASE 的 RGBA 图。"""
    s = BASE * SS
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    def sc(v):
        return int(round(v * s))

    # 以 108dp 视口（0..1）布局，安全区为中央 2/3
    cx = 0.5
    hinge_w = 0.018
    top, bot = 0.27, 0.73
    right_x0, right_x1 = cx + 0.015, cx + 0.21
    left_x0, left_x1 = cx - 0.015, cx - 0.155   # 左页铰链侧 → 外缘（压缩）
    inset = 0.028                                # 左页上下内缩（侧视透视）

    # 右页：完整圆角面板（竖向亮渐变）
    rp = gradient((sc(right_x1 - right_x0), sc(bot - top)),
                  [RIGHT_TOP, RIGHT_BOT], diagonal=False).convert("RGBA")
    mask = Image.new("L", rp.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        [0, 0, rp.size[0] - 1, rp.size[1] - 1], radius=sc(0.035), fill=255)
    img.paste(rp, (sc(right_x0), sc(top)), mask)

    # 左页：压缩变暗梯形（折叠态，近铰链侧与右页同高，外缘侧内缩）
    lw, lh = sc(left_x0 - left_x1), sc(bot - top)
    lp = gradient((lw, lh), [LEFT_TOP, LEFT_BOT], diagonal=False).convert("RGBA")
    lmask = Image.new("L", (lw, lh), 0)
    ImageDraw.Draw(lmask).polygon(
        [(lw - 1, 0), (0, sc(inset)), (0, lh - 1 - sc(inset)), (lw - 1, lh - 1)],
        fill=255)
    img.paste(lp, (sc(left_x1), sc(top)), lmask)

    # 左页外缘描一道暗边，强化侧视翻页感
    d.line([(sc(left_x1), sc(top + inset)), (sc(left_x1), sc(bot - inset))],
           fill=(0x1A, 0x26, 0x38, 255), width=sc(0.006))

    # 金色铰链条
    d.rounded_rectangle(
        [sc(cx - hinge_w / 2), sc(top - 0.02), sc(cx + hinge_w / 2), sc(bot + 0.02)],
        radius=sc(hinge_w / 2), fill=GOLD + (255,))

    # 右页上的两道翼弧（呼应「展翼」）
    arc = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    ad = ImageDraw.Draw(arc)
    for i, alpha in enumerate((200, 120)):
        r0 = 0.10 + i * 0.055
        ad.arc([sc(cx + 0.03 - r0), sc(0.5 - r0 * 1.6),
                sc(cx + 0.03 + r0), sc(0.5 + r0 * 1.6)],
               start=-55, end=55, fill=(0xFF, 0xFF, 0xFF, alpha), width=sc(0.012))
    img.alpha_composite(arc.filter(ImageFilter.GaussianBlur(SS * 0.6)))

    return img.resize((BASE, BASE), Image.LANCZOS)


def draw_background():
    s = BASE * SS
    return gradient((s, s), BG_STOPS).resize((BASE, BASE), Image.LANCZOS)


def monochrome(fg):
    """前景 alpha 剪影：单色层只取形状，颜色由系统主题化着色。"""
    alpha = fg.getchannel("A")
    img = Image.new("RGBA", fg.size, (0, 0, 0, 255))
    img.putalpha(alpha)
    return img


def compose(fg, bg, size, round_mask=False):
    """背景 + 居中前景合成传统图标。"""
    scale = size / BASE
    img = bg.resize((size, size), Image.LANCZOS).convert("RGBA")
    img.alpha_composite(fg.resize((size, size), Image.LANCZOS))
    if round_mask:
        m = Image.new("L", (size, size), 0)
        ImageDraw.Draw(m).ellipse([0, 0, size - 1, size - 1], fill=255)
        out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        out.paste(img, (0, 0), m)
        return out
    return img


def main():
    fg = draw_foreground()
    bg = draw_background()
    mono = monochrome(fg)
    fg.save(os.path.join(HERE, "output_preview_fg.png"))
    bg.save(os.path.join(HERE, "output_preview_bg.png"))

    for density, mult in DENSITIES.items():
        d = os.path.join(RES, "mipmap-" + density)
        os.makedirs(d, exist_ok=True)
        adap = int(round(108 * mult))
        fg.resize((adap, adap), Image.LANCZOS).save(
            os.path.join(d, "ic_launcher_foreground.png"))
        bg.resize((adap, adap), Image.LANCZOS).save(
            os.path.join(d, "ic_launcher_background.png"))
        mono.resize((adap, adap), Image.LANCZOS).save(
            os.path.join(d, "ic_launcher_monochrome.png"))
        legacy = int(round(48 * mult))
        compose(fg, bg, legacy).save(os.path.join(d, "ic_launcher.png"))
        compose(fg, bg, legacy, round_mask=True).save(
            os.path.join(d, "ic_launcher_round.png"))
        # 移除模板遗留 webp，避免同资源名冲突
        for f in ("ic_launcher.webp", "ic_launcher_round.webp"):
            p = os.path.join(d, f)
            if os.path.exists(p):
                os.remove(p)

    # 模板矢量前景/背景移除（由 PNG mipmap 取代）
    for f in ("ic_launcher_foreground.xml", "ic_launcher_background.xml"):
        p = os.path.join(RES, "drawable", f)
        if os.path.exists(p):
            os.remove(p)
    print("icons written to", RES)


if __name__ == "__main__":
    main()
