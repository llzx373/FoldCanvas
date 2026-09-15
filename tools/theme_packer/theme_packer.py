# -*- coding: utf-8 -*-
"""theme_packer — 折叠画卷主题包（.foldtheme）电脑端预览与打包工具。

在电脑上选好素材、预览外屏/内屏效果，一键打包成 .foldtheme（zip），
传到手机后用文件管理器点开或在 App 内「导入主题包」即可，手机端不再转码。

依赖：Pillow（必需）；ffmpeg（可选，用于视频抽帧预览与从内屏派生末帧）。
"""

import json
import shutil
import subprocess
import tempfile
import uuid
import zipfile
from pathlib import Path

import tkinter as tk
from tkinter import filedialog, messagebox, ttk

from PIL import Image, ImageTk

# ---------------------------------------------------------------------------
# 与 App 端对应的常量（app/src/main/java/com/llzx373/foldcanvas/...）
# ---------------------------------------------------------------------------

# DeviceProfile.WIDE_FOLD / NORMAL_FOLD
DEVICES = {
    "常规折叠（内屏 1812×2176）": {
        "formFactor": "normal", "inner": (1812, 2176), "outer": (1080, 2400),
    },
    "宽屏折叠（内屏 2364×1672）": {
        "formFactor": "wide", "inner": (2364, 1672), "outer": (1168, 1712),
    },
}

# ThemeCategory
CATEGORIES = {
    "展屏动画": "animation",
    "内外图片": "images",
    "展屏模糊": "duo_blur",
}

# OuterAutoMode（animation/images 类别缺外屏图时的半幅来源）
OUTER_MODES = {"右半": "RIGHT", "中间": "CENTER", "左半": "LEFT"}

THEME_PROPS = "theme.properties"
OUTER_FILE = "outer.png"
INNER_FILE = "inner.png"
ANIMATION_FILE = "animation.mp4"


# ---------------------------------------------------------------------------
# 图像处理：复刻 App 端 ImageUtils 逻辑
# ---------------------------------------------------------------------------

def center_crop(img: Image.Image, tw: int, th: int) -> Image.Image:
    """等比缩放铺满目标尺寸后居中裁剪（对应 ImageUtils.centerCrop）。"""
    scale = max(tw / img.width, th / img.height)
    w, h = round(img.width * scale), round(img.height * scale)
    scaled = img.resize((w, h), Image.LANCZOS)
    left, top = (w - tw) // 2, (h - th) // 2
    return scaled.crop((left, top, left + tw, top + th))


def scale_fill(img: Image.Image, tw: int, th: int) -> Image.Image:
    """非等比拉伸铺满（对应 ImageUtils.scaleFill，展屏模糊外屏派生用）。"""
    return img.resize((tw, th), Image.LANCZOS)


def half(img: Image.Image, mode: str) -> Image.Image:
    """取半幅（对应 ImageUtils.leftHalf / centerHalf / rightHalf）。"""
    w = img.width // 2
    if mode == "LEFT":
        return img.crop((0, 0, w, img.height))
    if mode == "CENTER":
        return img.crop(((img.width - w) // 2, 0, (img.width - w) // 2 + w, img.height))
    return img.crop((img.width - w, 0, img.width, img.height))


# ---------------------------------------------------------------------------
# ffmpeg 辅助（可选）
# ---------------------------------------------------------------------------

def ffmpeg_path() -> str | None:
    return shutil.which("ffmpeg")


def video_frame(video: str, position: str) -> Image.Image:
    """用 ffmpeg 抽视频帧：position ∈ first / middle / last。"""
    ffmpeg = ffmpeg_path()
    if not ffmpeg:
        raise RuntimeError("未检测到 ffmpeg")
    if position == "first":
        seek = ["-ss", "0"]
    elif position == "middle":
        dur = video_duration(video)
        seek = ["-ss", f"{dur / 2:.3f}"] if dur else ["-ss", "0"]
    else:
        # 末帧：从尾部倒放截取（-sseof 需要较新版本 ffmpeg）
        seek = ["-sseof", "-0.1"]
    with tempfile.NamedTemporaryFile(suffix=".png", delete=False) as tmp:
        tmp_path = tmp.name
    try:
        subprocess.run(
            [ffmpeg, "-y", *seek, "-i", video, "-frames:v", "1", tmp_path],
            check=True, capture_output=True,
        )
        return Image.open(tmp_path).convert("RGB")
    finally:
        Path(tmp_path).unlink(missing_ok=True)


def video_duration(video: str) -> float | None:
    """ffprobe 读取时长（秒），失败返回 None。"""
    ffprobe = shutil.which("ffprobe")
    if not ffprobe:
        return None
    try:
        out = subprocess.run(
            [ffprobe, "-v", "error", "-show_entries", "format=duration",
             "-of", "json", video],
            check=True, capture_output=True, text=True,
        )
        return float(json.loads(out.stdout)["format"]["duration"])
    except Exception:
        return None


# ---------------------------------------------------------------------------
# 打包逻辑（可被 GUI 与冒烟测试复用）
# ---------------------------------------------------------------------------

def java_escape(text: str) -> str:
    """java.util.Properties 兼容转义：非 ASCII 转 \\uXXXX，并转义分隔符。"""
    out = []
    for ch in text:
        code = ord(ch)
        if ch in "\\":
            out.append("\\\\")
        elif ch in "=: \t":
            out.append("\\" + ch)
        elif code < 0x20 or code > 0x7E:
            out.append(f"\\u{code:04X}")
        else:
            out.append(ch)
    return "".join(out)


def java_unescape(text: str) -> str:
    """java_escape 的逆运算，用于校验 round-trip。"""
    out, i = [], 0
    while i < len(text):
        if text[i] == "\\" and i + 1 < len(text):
            nxt = text[i + 1]
            if nxt == "u":
                out.append(chr(int(text[i + 2:i + 6], 16)))
                i += 6
                continue
            out.append(nxt)
            i += 2
            continue
        out.append(text[i])
        i += 1
    return "".join(out)


def build_assets(
    category: str,
    device: dict,
    inner_image: str | None,
    outer_image: str | None,
    video: str | None,
    outer_mode: str,
) -> tuple[Image.Image, Image.Image]:
    """按 App 端 saveCustomTheme 的规则产出 (outer, inner) 两张目标分辨率图。"""
    iw, ih = device["inner"]
    ow, oh = device["outer"]

    inner = None
    if inner_image:
        inner = Image.open(inner_image).convert("RGB")
    elif video:
        inner = video_frame(video, "last")
    if inner is None:
        raise ValueError("缺少内屏壁纸：请选择内屏图片，或提供视频并安装 ffmpeg")

    if outer_image:
        outer = Image.open(outer_image).convert("RGB")
        outer = center_crop(outer, ow, oh)
    elif category == "duo_blur":
        outer = scale_fill(half(inner, "RIGHT"), ow, oh)
    else:
        outer = center_crop(half(inner, outer_mode), ow, oh)

    return outer, center_crop(inner, iw, ih)


def write_package(
    output: str,
    name: str,
    category: str,
    form_factor: str,
    outer_mode: str,
    outer: Image.Image,
    inner: Image.Image,
    video: str | None,
) -> None:
    """写 .foldtheme（zip）：theme.properties + outer.png + inner.png (+ animation.mp4)。"""
    props = {
        "id": "custom_" + uuid.uuid4().hex[:8],
        "name": name,
        "outer": OUTER_FILE,
        "inner": INNER_FILE,
        "formFactor": form_factor,
        "category": category,
        "outerMode": outer_mode,
    }
    if video:
        props["animation"] = ANIMATION_FILE
    lines = [f"{java_escape(k)}={java_escape(v)}" for k, v in props.items()]
    props_text = "#FoldTheme package\n" + "\n".join(lines) + "\n"

    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as zf:
        zf.writestr(THEME_PROPS, props_text.encode("ascii"))
        for arcname, img in ((OUTER_FILE, outer), (INNER_FILE, inner)):
            with tempfile.NamedTemporaryFile(suffix=".png", delete=False) as tmp:
                img.save(tmp.name, "PNG")
                zf.write(tmp.name, arcname)
            Path(tmp.name).unlink(missing_ok=True)
        if video:
            zf.write(video, ANIMATION_FILE)


# ---------------------------------------------------------------------------
# GUI
# ---------------------------------------------------------------------------

class ThemePackerApp(tk.Tk):

    PREVIEW_W = 200

    def __init__(self):
        super().__init__()
        self.title("折叠画卷 · 主题包制作")
        self.resizable(False, False)

        self.name_var = tk.StringVar(value="我的主题")
        self.category_var = tk.StringVar(value="展屏动画")
        self.device_var = tk.StringVar(value=next(iter(DEVICES)))
        self.outer_mode_var = tk.StringVar(value="右半")
        self.video_var = tk.StringVar()
        self.inner_var = tk.StringVar()
        self.outer_var = tk.StringVar()
        self._preview_images: list[ImageTk.PhotoImage] = []

        self._build_form()
        self._build_preview()
        self._build_actions()

        for var in (self.category_var, self.device_var, self.outer_mode_var,
                    self.video_var, self.inner_var, self.outer_var):
            var.trace_add("write", lambda *_: self.after(50, self.refresh_preview))
        if not ffmpeg_path():
            self.status_var.set("未检测到 ffmpeg：视频帧预览与「视频末帧作内屏」不可用")

    # -- 布局 --

    def _build_form(self):
        form = ttk.LabelFrame(self, text="主题信息")
        form.grid(row=0, column=0, padx=10, pady=(10, 4), sticky="ew")

        row = 0
        ttk.Label(form, text="名称").grid(row=row, column=0, sticky="e", padx=4, pady=2)
        ttk.Entry(form, textvariable=self.name_var, width=28).grid(
            row=row, column=1, columnspan=2, sticky="w", padx=4, pady=2)

        row += 1
        ttk.Label(form, text="类别").grid(row=row, column=0, sticky="e", padx=4, pady=2)
        ttk.Combobox(form, textvariable=self.category_var, state="readonly",
                     values=list(CATEGORIES), width=12).grid(
            row=row, column=1, sticky="w", padx=4, pady=2)
        ttk.Label(form, text="外屏派生").grid(row=row, column=2, sticky="e", padx=4)
        ttk.Combobox(form, textvariable=self.outer_mode_var, state="readonly",
                     values=list(OUTER_MODES), width=8).grid(
            row=row, column=3, sticky="w", padx=4, pady=2)

        row += 1
        ttk.Label(form, text="机型").grid(row=row, column=0, sticky="e", padx=4, pady=2)
        ttk.Combobox(form, textvariable=self.device_var, state="readonly",
                     values=list(DEVICES), width=28).grid(
            row=row, column=1, columnspan=3, sticky="w", padx=4, pady=2)

        row += 1
        for label, var, is_video in (
            ("展开视频", self.video_var, True),
            ("内屏图片", self.inner_var, False),
            ("外屏图片", self.outer_var, False),
        ):
            ttk.Label(form, text=label).grid(row=row, column=0, sticky="e", padx=4, pady=2)
            entry = ttk.Entry(form, textvariable=var, width=36)
            entry.grid(row=row, column=1, columnspan=2, sticky="w", padx=4, pady=2)
            filetypes = [("视频", "*.mp4 *.mov *.mkv *.webm")] if is_video \
                else [("图片", "*.png *.jpg *.jpeg *.webp *.bmp")]
            ttk.Button(form, text="浏览…", width=8,
                       command=lambda v=var, ft=filetypes: self._browse(v, ft)).grid(
                row=row, column=3, sticky="w", padx=4, pady=2)
            row += 1

    def _build_preview(self):
        box = ttk.LabelFrame(self, text="预览")
        box.grid(row=1, column=0, padx=10, pady=4, sticky="ew")
        self.preview_outer = ttk.Label(box, text="外屏", anchor="center")
        self.preview_inner = ttk.Label(box, text="内屏", anchor="center")
        self.preview_outer.grid(row=0, column=0, padx=8, pady=8)
        self.preview_inner.grid(row=0, column=1, padx=8, pady=8)
        self.video_strip = ttk.Label(box, anchor="center")
        self.video_strip.grid(row=1, column=0, columnspan=2, padx=8, pady=(0, 8))

    def _build_actions(self):
        bar = ttk.Frame(self)
        bar.grid(row=2, column=0, padx=10, pady=(4, 10), sticky="ew")
        self.status_var = tk.StringVar(value="就绪")
        ttk.Label(bar, textvariable=self.status_var, foreground="#666").pack(
            side="left", fill="x", expand=True)
        ttk.Button(bar, text="打包 .foldtheme", command=self.do_package).pack(side="right")

    # -- 交互 --

    def _browse(self, var: tk.StringVar, filetypes):
        path = filedialog.askopenfilename(filetypes=[*filetypes, ("所有文件", "*.*")])
        if path:
            var.set(path)

    def refresh_preview(self):
        self._preview_images.clear()
        try:
            device = DEVICES[self.device_var.get()]
            category = CATEGORIES[self.category_var.get()]
            outer_mode = OUTER_MODES[self.outer_mode_var.get()]
            outer, inner = build_assets(
                category, device,
                self.inner_var.get() or None,
                self.outer_var.get() or None,
                self.video_var.get() or None,
                outer_mode,
            )
            for widget, img in ((self.preview_outer, outer), (self.preview_inner, inner)):
                thumb = img.copy()
                thumb.thumbnail((self.PREVIEW_W, self.PREVIEW_W * 2))
                photo = ImageTk.PhotoImage(thumb)
                self._preview_images.append(photo)
                widget.configure(image=photo, text="")
            self._refresh_video_strip()
            self.status_var.set("预览已更新")
        except Exception as e:
            for widget in (self.preview_outer, self.preview_inner):
                widget.configure(image="", text="待选择素材")
            self.video_strip.configure(image="", text="")
            self.status_var.set(str(e))

    def _refresh_video_strip(self):
        video = self.video_var.get()
        if not video or not ffmpeg_path():
            self.video_strip.configure(image="", text="")
            return
        try:
            frames = [video_frame(video, pos) for pos in ("first", "middle", "last")]
            w, h = frames[0].size
            strip_h = 120
            strip_w = w * strip_h // h
            strip = Image.new("RGB", (strip_w * 3 + 16, strip_h), "#222222")
            for i, f in enumerate(frames):
                strip.paste(f.resize((strip_w, strip_h)), (i * (strip_w + 8), 0))
            photo = ImageTk.PhotoImage(strip)
            self._preview_images.append(photo)
            self.video_strip.configure(image=photo, text="")
        except Exception:
            self.video_strip.configure(image="", text="视频帧预览失败")

    def do_package(self):
        name = self.name_var.get().strip() or "未命名主题"
        category = CATEGORIES[self.category_var.get()]
        device = DEVICES[self.device_var.get()]
        video = self.video_var.get() or None

        if category == "animation" and not video:
            messagebox.showerror("无法打包", "展屏动画类别必须选择展开视频")
            return
        if category != "animation" and video:
            if not messagebox.askyesno("确认", "当前类别不需要视频，是否仍打包进主题包？"):
                return
        try:
            outer, inner = build_assets(
                category, device,
                self.inner_var.get() or None,
                self.outer_var.get() or None,
                video,
                OUTER_MODES[self.outer_mode_var.get()],
            )
        except Exception as e:
            messagebox.showerror("无法打包", str(e))
            return

        output = filedialog.asksaveasfilename(
            defaultextension=".foldtheme",
            initialfile=f"{name}.foldtheme",
            filetypes=[("折叠画卷主题包", "*.foldtheme")],
        )
        if not output:
            return
        try:
            write_package(
                output, name, category, device["formFactor"],
                OUTER_MODES[self.outer_mode_var.get()], outer, inner, video,
            )
        except Exception as e:
            messagebox.showerror("打包失败", str(e))
            return
        self.status_var.set(f"已打包：{output}")
        messagebox.showinfo("完成", f"主题包已生成：\n{output}\n\n传到手机后直接点开即可导入。")


if __name__ == "__main__":
    ThemePackerApp().mainloop()
