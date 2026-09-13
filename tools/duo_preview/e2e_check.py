# -*- coding: utf-8 -*-
"""
展屏模糊（Duo 效果）端到端校验（不启动 Android，纯逻辑 + 像素验证）

模拟 App 完整链路：
  铰链角度 → AngleFrameMapper 同款映射 → 帧号 → 加载帧文件 → 像素分析

断言：
  1. Python 预览常量与 Kotlin DuoFoldMath.kt 源码一致（数学未漂移）
  2. 45 帧齐全且尺寸正确
  3. 角度→帧号单调不减（与 AngleFrameMapperTest 互证）
  4. 所有帧的右半幅与内屏右半像素一致（铰链侧始终清晰）
  5. 末帧（p=1）与内屏壁纸完全一致（展开到位无残留模糊/压暗）
  6. 首帧（p=0）左半幅为模糊氛围层（高频细节显著少于内屏左半）
  7. 左半幅清晰度随帧号单调不减（渐进模糊方向正确，无"回摆"）
  8. 铰链锚定：首帧左半恰为氛围层（面板宽 0），中间帧左半为压缩模糊中间态
  9. 外屏派生策略：内屏右半幅 centerCrop 后与内屏右半一致（外屏=右半窗口）
"""
import os
import re
import sys

from PIL import Image, ImageChops, ImageFilter, ImageStat

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "output")
FRAMES = os.path.join(OUT, "frames")
KOTLIN_MATH = os.path.abspath(os.path.join(
    HERE, "..", "..", "app", "src", "main", "java",
    "com", "llzx373", "foldcanvas", "theme", "duo", "DuoFoldMath.kt"))

FRAME_COUNT = 45
ANGLE_START, ANGLE_END = 30.0, 150.0

FAILURES = []


def check(name, cond, detail=""):
    status = "PASS" if cond else "FAIL"
    print(f"[{status}] {name}" + (f"  {detail}" if detail else ""))
    if not cond:
        FAILURES.append(name)


def progress(angle, start=ANGLE_START, end=ANGLE_END):
    return min(1.0, max(0.0, (angle - start) / (end - start)))


def frame_index(p, count=FRAME_COUNT):
    return min(count - 1, max(0, round(p * (count - 1))))


def region_std(img, box):
    return ImageStat.Stat(img.convert("L").crop(box)).stddev[0]


def edge_energy(img, box):
    """FIND_EDGES 后的平均亮度，作为清晰度（高频细节）度量。"""
    edges = img.convert("L").crop(box).filter(ImageFilter.FIND_EDGES)
    return ImageStat.Stat(edges).mean[0]


def mse(a, b):
    diff = ImageChops.difference(a.convert("RGB"), b.convert("RGB"))
    h = diff.histogram()
    sq = sum((i % 256) ** 2 * v for i, v in enumerate(h))
    return sq / (a.size[0] * a.size[1] * 3)


def kotlin_constants():
    src = open(KOTLIN_MATH, encoding="utf-8").read()
    def num(name):
        m = re.search(name + r"\s*=\s*([0-9.]+)f?", src)
        return float(m.group(1)) if m else None
    return {
        "MAX_BLUR_RADIUS": num("MAX_BLUR_RADIUS"),
        "AMBIENT_BLUR_RADIUS": num("AMBIENT_BLUR_RADIUS"),
        "AMBIENT_DIM_ALPHA": num("AMBIENT_DIM_ALPHA"),
        "PANEL_MAX_DIM_ALPHA": num("PANEL_MAX_DIM_ALPHA"),
    }


def main():
    import generate_duo as gen

    # 1. Kotlin 源码常量与 Python 预览一致
    kc = kotlin_constants()
    check("Kotlin DuoFoldMath 常量与 Python 预览一致",
          kc["MAX_BLUR_RADIUS"] == gen.MAX_BLUR_RADIUS and
          kc["AMBIENT_BLUR_RADIUS"] == gen.AMBIENT_BLUR_RADIUS and
          kc["AMBIENT_DIM_ALPHA"] == gen.AMBIENT_DIM_ALPHA and
          kc["PANEL_MAX_DIM_ALPHA"] == gen.PANEL_MAX_DIM_ALPHA,
          str(kc))

    # 2. 帧齐全
    files = sorted(f for f in os.listdir(FRAMES) if f.endswith(".png"))
    check("45 帧齐全", len(files) == FRAME_COUNT, f"got {len(files)}")
    if len(files) != FRAME_COUNT:
        summary()
    inner = Image.open(os.path.join(OUT, "inner.png"))
    w, h = inner.size
    half = w // 2
    frames = [Image.open(os.path.join(FRAMES, f)) for f in files]
    check("帧尺寸与内屏一致",
          all(f.size == (w, h) for f in frames), f"{w}x{h}")

    # 3. 角度→帧号单调
    indices = [frame_index(progress(a)) for a in range(0, 181, 2)]
    check("角度→帧号单调不减",
          all(b >= a for a, b in zip(indices, indices[1:])), str(indices[:10]) + "...")

    # 4. 右半幅始终清晰（与内屏右半一致）
    inner_right = inner.crop((half, 0, w, h))
    right_errs = [mse(f.crop((half, 0, w, h)), inner_right) for f in frames]
    check("所有帧右半幅与内屏右半一致",
          max(right_errs) < 1.0, f"max MSE={max(right_errs):.3f}")

    # 5. 末帧 == 内屏
    check("末帧与内屏壁纸完全一致", mse(frames[-1], inner) < 1.0,
          f"MSE={mse(frames[-1], inner):.3f}")

    # 6. 首帧左半显著模糊于内屏左半
    left_box = (0, 0, half, h)
    e_first = edge_energy(frames[0], left_box)
    e_inner = edge_energy(inner, left_box)
    check("首帧左半为模糊氛围层", e_first < e_inner * 0.45,
          f"first={e_first:.2f} inner={e_inner:.2f}")

    # 7. 左半清晰度单调不减（允许相邻帧 ±2% 抖动）
    energies = [edge_energy(f, left_box) for f in frames]
    mono = all(b >= a * 0.98 for a, b in zip(energies, energies[1:]))
    check("左半清晰度随帧号单调不减", mono,
          f"e[0]={energies[0]:.2f} e[22]={energies[22]:.2f} e[44]={energies[44]:.2f}")

    # 8. 铰链锚定：p=0 时左半恰为氛围层（面板宽 0）；
    #    中间帧左半既非纯氛围层也非清晰内屏（面板处于压缩模糊中间态）。
    #    面板宽度单调性由 DuoFoldMathTest 从数学上证明，像素层只验端点与中间态。
    ambient = inner.filter(ImageFilter.GaussianBlur(gen.AMBIENT_BLUR_RADIUS))
    ambient = Image.blend(ambient, Image.new("RGB", inner.size, (0, 0, 0)),
                          gen.AMBIENT_DIM_ALPHA / 255.0)
    ambient_left = ambient.convert("RGB").crop(left_box)
    first_left = frames[0].convert("RGB").crop(left_box)
    check("首帧左半与氛围层一致（面板宽 0）",
          mse(first_left, ambient_left) < 1.0,
          f"MSE={mse(first_left, ambient_left):.3f}")
    mid_left = frames[FRAME_COUNT // 2].convert("RGB").crop(left_box)
    inner_left = inner.convert("RGB").crop(left_box)
    check("中间帧左半为压缩模糊中间态",
          mse(mid_left, ambient_left) > 5.0 and mse(mid_left, inner_left) > 5.0,
          f"vs ambient={mse(mid_left, ambient_left):.2f} vs inner={mse(mid_left, inner_left):.2f}")

    # 9. 外屏 = 内屏右半幅完整拉伸铺满（Duo 外屏 UV 窗口）：
    #    竖向不裁剪（y 全幅保留）、不放大，缩回右半尺寸后与内屏右半逐像素一致
    outer = Image.open(os.path.join(OUT, "outer.png"))
    check("外屏尺寸为外屏档案分辨率", outer.size == (gen.OUTER_WIDTH, gen.OUTER_HEIGHT),
          f"{outer.size}")
    right_half = inner.crop((half, 0, w, h))
    roundtrip = outer.resize((w - half, h))
    # 双向重采样会在高频网格上产生噪声，先轻度模糊再比对结构（裁剪/错位不会被模糊掩盖）
    rt_blur = roundtrip.filter(ImageFilter.GaussianBlur(3))
    rh_blur = right_half.filter(ImageFilter.GaussianBlur(3))
    check("外屏为内屏右半幅完整保留（无 centerCrop）",
          mse(rt_blur, rh_blur) < 2.0,
          f"MSE(blur)={mse(rt_blur, rh_blur):.3f}")

    # 10-14. 外屏动画帧（Duo 外屏翻页：铰链→外缘渐进模糊压暗）
    cdir = os.path.join(OUT, "frames_cover")
    cfiles = sorted(f for f in os.listdir(cdir) if f.endswith(".png"))
    check("外屏 45 帧齐全且尺寸正确",
          len(cfiles) == FRAME_COUNT and
          all(Image.open(os.path.join(cdir, f)).size == (gen.OUTER_WIDTH, gen.OUTER_HEIGHT)
              for f in cfiles),
          f"got {len(cfiles)}")
    cframes = [Image.open(os.path.join(cdir, f)) for f in cfiles]
    ow, oh = gen.OUTER_WIDTH, gen.OUTER_HEIGHT
    hinge_band = (0, 0, ow // 8, oh)        # 铰链侧竖带
    edge_band = (ow * 7 // 8, 0, ow, oh)    # 外缘竖带
    check("外屏首帧与外屏图一致（motion=0 全屏清晰）",
          mse(cframes[0], outer) < 2.0, f"MSE={mse(cframes[0], outer):.3f}")
    edge_energy_seq = [edge_energy(f, edge_band) for f in cframes]
    check("外屏外缘清晰度随展开单调不增（渐进模糊）",
          all(b <= a * 1.05 + 0.05 for a, b in zip(edge_energy_seq, edge_energy_seq[1:])),
          f"e[0]={edge_energy_seq[0]:.2f} e[44]={edge_energy_seq[-1]:.2f}")
    check("外屏末帧铰链侧比外缘清晰（模糊自铰链向外缘递增）",
          edge_energy(cframes[-1], hinge_band) > edge_energy(cframes[-1], edge_band) * 1.5,
          f"hinge={edge_energy(cframes[-1], hinge_band):.2f} "
          f"edge={edge_energy(cframes[-1], edge_band):.2f}")
    brightness = [ImageStat.Stat(f.convert("L").crop(edge_band)).mean[0] for f in cframes]
    check("外屏外缘亮度随展开单调不增（渐进压暗）",
          all(b <= a + 0.5 for a, b in zip(brightness, brightness[1:])) and
          brightness[-1] < brightness[0] * 0.6,
          f"b[0]={brightness[0]:.1f} b[44]={brightness[-1]:.1f}")

    summary()


def summary():
    if FAILURES:
        print(f"\n{len(FAILURES)} 项失败: {FAILURES}")
        sys.exit(1)
    print("\n全部通过")


if __name__ == "__main__":
    main()
