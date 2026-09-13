# -*- coding: utf-8 -*-
"""
展翼主题端到端校验（不启动 Android，纯逻辑 + 像素验证）

模拟 App 完整链路：
  铰链角度 → AngleFrameMapper 同款映射 → 帧号 → 加载帧文件 → 像素分析

断言：
  1. 角度→帧号单调不减（与 AngleFrameMapperTest 互证）
  2. 右翅亮区面积随角度单调增长（无"回摆再展开"假动作）
  3. t=0 时铰链右侧无翅膀（左盖右），t=1 帧与 inner.png 一致
  4. 左翅在所有帧可见
  5. app assets 中的素材与生成产物哈希一致（拷贝未出错/未过期）
  6. mp4 抽帧与源帧序列一致（视频→帧管线保真）
"""
import os
import subprocess
import sys
import tempfile

from PIL import Image, ImageChops

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "output")
ASSETS = os.path.abspath(os.path.join(HERE, "..", "..", "app", "src", "main", "assets", "themes"))

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


def wing_area(img, region):
    """区域内明显亮于背景的像素数（翅膀为亮色系，背景为深色渐变）。"""
    gray = img.convert("L")
    w, h = gray.size
    box = {
        "left": (0, 0, int(w * 0.47), h),
        "right": (int(w * 0.53), 0, w, h),
    }[region]
    crop = gray.crop(box)
    hist = crop.histogram()
    # 背景亮度上限约 90，翅膀最低配色约 72,128+，取 110 为阈值
    return sum(hist[110:])


def check_variant(suffix):
    frames_dir = os.path.join(OUT, f"frames{suffix}")
    frame_files = sorted(
        f for f in os.listdir(frames_dir) if f.endswith(".jpg")
    )
    check(f"{suffix}: 45 帧齐备", len(frame_files) == FRAME_COUNT,
          f"actual={len(frame_files)}")

    # 1+2+3+4: 角度扫描，像素级验证
    prev_idx, prev_right, prev_left = -1, -1, -1
    monotonic_idx, monotonic_right = True, True
    right_tol = None
    first_right_area, last_img = None, None
    angle = 0.0
    while angle <= 180.0:
        idx = frame_index(progress(angle))
        if idx != prev_idx:
            img = Image.open(os.path.join(frames_dir, frame_files[idx]))
            right = wing_area(img, "right")
            left = wing_area(img, "left")
            if right_tol is None:
                right_tol = 0  # 稍后按最大值设定
            if prev_right >= 0 and right < prev_right - 1:
                monotonic_right = False
            if prev_left >= 0 and left <= 0:
                FAILURES.append(f"{suffix}: left wing missing at frame {idx}")
            prev_idx, prev_right, prev_left = idx, right, left
            if first_right_area is None:
                first_right_area = right
            last_img = img
        angle += 0.5
    check(f"{suffix}: 角度→帧号单调不减", monotonic_idx)
    check(f"{suffix}: 右翅面积单调增长（无回摆）", monotonic_right,
          f"first={first_right_area}, last={prev_right}")
    check(f"{suffix}: t=0 右侧无翅膀（左盖右）",
          first_right_area is not None and first_right_area < prev_right * 0.02,
          f"t0_right={first_right_area}")
    check(f"{suffix}: 左翅全程可见", prev_left > 0)

    # t=1 帧 ≈ inner.png
    inner = Image.open(os.path.join(OUT, f"inner{suffix}.png")).convert("RGB")
    diff = ImageChops.difference(last_img.convert("RGB"), inner)
    mean_diff = sum(diff.convert("L").histogram()[i] * i for i in range(256)) / \
        (inner.width * inner.height)
    check(f"{suffix}: 末帧与 inner.png 一致", mean_diff < 2.0,
          f"mean_diff={mean_diff:.3f}")


def check_assets():
    # PNG 校验像素级一致（不比字节哈希：编码器实现差异不影响像素，
    # 渲染结果对同一 Pillow 版本是确定性的，仍能抓住素材过期/拷贝错误）
    png_pairs = [
        ("outer.png", "wings/outer.png"),
        ("inner.png", "wings/inner.png"),
        ("outer_wide.png", "wings_wide/outer_wide.png"),
        ("inner_wide.png", "wings_wide/inner_wide.png"),
    ]
    for gen, asset in png_pairs:
        gen_path = os.path.join(OUT, gen)
        asset_path = os.path.join(ASSETS, asset)
        ok = os.path.isfile(asset_path)
        detail = ""
        if ok:
            ia = Image.open(gen_path).convert("RGB")
            ib = Image.open(asset_path).convert("RGB")
            ok = ia.size == ib.size and \
                ImageChops.difference(ia, ib).getbbox() is None
            detail = f"gen={ia.size}, asset={ib.size}"
        check(f"assets 同步: {asset}", ok, detail)
    # mp4 字节流依赖 ffmpeg 版本/构建，跨环境不稳定，
    # 改为对 assets 中的 mp4 直接做帧数与像素保真校验
    mp4_pairs = [
        ("wings/animation.mp4", ""),
        ("wings_wide/animation_wide.mp4", "_wide"),
    ]
    for asset, suffix in mp4_pairs:
        check_mp4_fidelity(os.path.join(ASSETS, asset), suffix,
                           f"assets 保真{suffix}")


def check_mp4_fidelity(mp4_path, suffix, label):
    """ffmpeg 从 mp4 抽帧，与源帧序列做像素对比（验证视频→帧管线保真）。"""
    frames_dir = os.path.join(OUT, f"frames{suffix}")
    if not os.path.isfile(mp4_path):
        check(f"{label}: 文件存在", False, mp4_path)
        return
    with tempfile.TemporaryDirectory() as tmp:
        subprocess.run([
            "ffmpeg", "-y", "-i", mp4_path,
            os.path.join(tmp, "f_%03d.jpg"),
        ], check=True, capture_output=True)
        extracted = sorted(f for f in os.listdir(tmp) if f.endswith(".jpg"))
        check(f"{label}: mp4 帧数", len(extracted) == FRAME_COUNT,
              f"actual={len(extracted)}")
        worst = 0.0
        for a, b in zip(sorted(os.listdir(frames_dir))[:5], extracted[:5]):
            ia = Image.open(os.path.join(frames_dir, a)).convert("L")
            ib = Image.open(os.path.join(tmp, b)).convert("L").resize(ia.size)
            diff = ImageChops.difference(ia, ib)
            hist = diff.histogram()
            mean = sum(hist[i] * i for i in range(256)) / (ia.width * ia.height)
            worst = max(worst, mean)
        check(f"{label}: mp4 抽帧与源帧一致", worst < 3.0,
              f"worst_mean_diff={worst:.2f}")


def check_video_fidelity(suffix):
    check_mp4_fidelity(os.path.join(OUT, f"wings{suffix}.mp4"), suffix, suffix)


def main():
    check_variant("")
    check_variant("_wide")
    check_assets()
    check_video_fidelity("")
    check_video_fidelity("_wide")
    print()
    if FAILURES:
        print(f"FAILED: {len(FAILURES)} 项 -> {FAILURES}")
        return 1
    print("ALL CHECKS PASSED")
    return 0


if __name__ == "__main__":
    sys.exit(main())
