# -*- coding: utf-8 -*-
"""theme_packer 冒烟测试：生成测试图 → 打包 → 读回校验。退出码非 0 表示失败。"""

import tempfile
import zipfile
from pathlib import Path

from PIL import Image

from theme_packer import (
    ANIMATION_FILE, CATEGORIES, DEVICES, INNER_FILE, OUTER_FILE, THEME_PROPS,
    build_assets, java_escape, java_unescape, write_package,
)


def gradient(w: int, h: int, c0, c1) -> Image.Image:
    img = Image.new("RGB", (w, h))
    for y in range(h):
        t = y / max(h - 1, 1)
        color = tuple(round(a + (b - a) * t) for a, b in zip(c0, c1))
        for x in range(0, w, 64):  # 粗粒度填充即可，省时间
            for dx in range(64):
                if x + dx < w:
                    img.putpixel((x + dx, y), color)
    return img


def check(cond: bool, msg: str):
    print(("PASS " if cond else "FAIL ") + msg)
    if not cond:
        raise SystemExit(1)


def main():
    with tempfile.TemporaryDirectory() as td:
        td = Path(td)
        device = DEVICES["常规折叠（内屏 1812×2176）"]
        ow, oh = device["outer"]
        iw, ih = device["inner"]

        # 1. build_assets：外屏缺省时按右半派生
        inner_src = td / "inner_src.png"
        gradient(1200, 2000, (255, 0, 0), (0, 0, 255)).save(inner_src)
        outer, inner = build_assets(
            CATEGORIES["展屏动画"], device, str(inner_src), None, None, "RIGHT")
        check(outer.size == (ow, oh), f"outer 尺寸 {outer.size} == {(ow, oh)}")
        check(inner.size == (iw, ih), f"inner 尺寸 {inner.size} == {(iw, ih)}")

        # 2. duo_blur 外屏为右半幅拉伸：最左列像素应接近内屏中线像素
        outer2, _ = build_assets(
            CATEGORIES["展屏模糊"], device, str(inner_src), None, None, "RIGHT")
        check(outer2.size == (ow, oh), "duo_blur outer 尺寸正确")

        # 3. properties 转义 round-trip（含中文与分隔符）
        name = "星空: 折叠 = 主题 1"
        check(java_unescape(java_escape(name)) == name, "java_escape 中文/符号 round-trip")
        check(all(ord(c) < 128 for c in java_escape(name)), "转义后为纯 ASCII")

        # 4. 完整打包 + 读回校验
        out = td / "测试主题.foldtheme"
        write_package(str(out), name, CATEGORIES["内外图片"], device["formFactor"],
                      "RIGHT", outer, inner, None)
        with zipfile.ZipFile(out) as zf:
            names = set(zf.namelist())
            check(names == {THEME_PROPS, OUTER_FILE, INNER_FILE},
                  f"zip 条目 {sorted(names)}")
            props = zf.read(THEME_PROPS).decode("ascii")
            check("name=" in props and "category=images" in props
                  and "formFactor=normal" in props, "properties 关键字段齐全")
            check(ANIMATION_FILE not in names, "无视频时不含 animation.mp4")
            with zf.open(INNER_FILE) as f:
                check(Image.open(f).size == (iw, ih), "包内 inner.png 分辨率正确")
        print("\n全部通过")


if __name__ == "__main__":
    main()
