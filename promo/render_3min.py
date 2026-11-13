# -*- coding: utf-8 -*-
"""
HAM 工具合集 —— 横屏宣传片（3 分钟完整版）

1920×1080 / 30fps / 约 181s，覆盖 ToolRegistry 里全部 19 个工具：
10 个已实现的给出真实算例，9 个开发中的给出功能规划与状态标注，末尾附 19 工具快切回顾。

界面色值与图标取自 App 真实资源（品牌色 #006C5B、波瓣半径 32/23/14、±50°、笔宽 5.5），
数值全部按 App 内部公式实算。

配乐为 120 BPM 科技电子风格，鼓组 / 低音 / 琶音分层随分镜增减。

用法：
    python render_3min.py preview   # 关键帧 PNG
    python render_3min.py full      # 整片
"""

import math
import os
import subprocess
import sys
from functools import lru_cache

import numpy as np
from PIL import Image, ImageDraw, ImageFont

W, H = 1920, 1080
FPS = 30
DUR = 0.0          # 由时间轴构建后赋值
HERE = os.path.dirname(os.path.abspath(__file__))

BG_DARK = (10, 15, 14)
BRAND = (0, 108, 91)
BRAND_HI = (42, 217, 176)
BRAND_SOFT = (124, 245, 218)
CARD = (18, 26, 24)
LINE = (44, 58, 54)
TXT = (233, 242, 239)
TXT_DIM = (140, 159, 153)
TXT_FAINT = (92, 108, 104)

AMBER = (232, 176, 88)      # 开发中工具的标识色
AMBER_DIM = (150, 112, 56)

FONT_BOLD = "C:/Windows/Fonts/msyhbd.ttc"
FONT_REG = "C:/Windows/Fonts/msyh.ttc"
FONT_LIGHT = "C:/Windows/Fonts/msyhl.ttc"


@lru_cache(maxsize=128)
def font(size, weight="bold"):
    return ImageFont.truetype({"bold": FONT_BOLD, "reg": FONT_REG, "light": FONT_LIGHT}[weight],
                              size, index=0)


# ---------------------------------------------------------------- 缓动与工具

def clamp(x, lo=0.0, hi=1.0):
    return max(lo, min(hi, x))


def seg(t, start, dur):
    return clamp((t - start) / dur) if dur > 0 else (1.0 if t >= start else 0.0)


def ease_out(t, p=3.0):
    return 1.0 - (1.0 - t) ** p


def ease_in_out(t):
    return 3 * t * t - 2 * t * t * t


def ease_out_back(t, s=1.5):
    t -= 1.0
    return 1.0 + (s + 1.0) * t ** 3 + s * t * t


def lerp(a, b, t):
    return a + (b - a) * t


def mix(c1, c2, t):
    t = clamp(t)
    return tuple(int(round(a + (b - a) * t)) for a, b in zip(c1, c2))


def rgba(c, a=255):
    return c + (int(clamp(a, 0, 255)),)


def pre(c, a, bg=BG_DARK):
    """预混合：把 (c, alpha) 按 alpha 混到 bg 上，得到不透明色。

    PIL 在 RGBA 画布上用 ImageDraw 画半透明色是「替换像素」而非混合，
    所以在 RGBA 上直接画半透明只会得到一块实色。凡是需要淡入的元素，
    要么走 alpha_composite，要么用这个函数算出等效不透明色。
    """
    return mix(bg, c, clamp(a / 255.0)) + (255,)


def tracked_text(d, xy, s, fnt, fill, tracking=0.0, anchor="l", valign="a"):
    """带字距的文本。anchor: l / c / r（按给定 x 定位整段宽度）。

    valign 为 PIL 纵向锚点：a 升级线顶（旧行为）/ m 字体中线 / s 基线。
    整行文字用 m 按视觉中心定位，避免与面板、分隔线、徽章错位。
    """
    widths = [d.textlength(ch, font=fnt) for ch in s]
    total = sum(widths) + tracking * max(0, len(s) - 1)
    x, y = xy
    if anchor == "c":
        x -= total / 2
    elif anchor == "r":
        x -= total
    va = "l" + valign
    for ch, w in zip(s, widths):
        d.text((x, y), ch, font=fnt, fill=fill, anchor=va)
        x += w + tracking
    return total


def wrap_text(d, s, fnt, max_w):
    lines, cur = [], ""
    for ch in s:
        if ch == "\n":
            lines.append(cur)
            cur = ""
            continue
        if d.textlength(cur + ch, font=fnt) > max_w and cur:
            lines.append(cur)
            cur = ch
        else:
            cur += ch
    if cur:
        lines.append(cur)
    return lines


@lru_cache(maxsize=8)
def panel(size, radius, fill, outline=None, width=1):
    w, h = int(size[0]), int(size[1])
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, w - 1, h - 1], radius=radius, fill=fill,
                        outline=outline, width=width if outline else 0)
    return img


@lru_cache(maxsize=32)
def _glow_mask(r, softness):
    """径向衰减的柔和圆，边缘天然收敛到 0（高斯模糊在画布边界收不住会露方框）。"""
    size = max(8, int(r * 2))
    yy, xx = np.mgrid[0:size, 0:size]
    c = (size - 1) / 2.0
    dist = np.sqrt((xx - c) ** 2 + (yy - c) ** 2) / max(1e-6, c)
    falloff = np.clip(1.0 - dist, 0, 1) ** (2.0 + softness * 4.0)
    return Image.fromarray((falloff * 255).astype(np.uint8), "L")


def glow(img, cx, cy, r, color, alpha=150, softness=0.35):
    if r <= 2 or alpha <= 0:
        return img
    r = int(r)
    mask = _glow_mask(r, round(softness, 2))
    tinted = Image.new("RGB", mask.size, color)
    img.paste(tinted, (int(cx - r), int(cy - r)), mask.point(lambda v: int(v * clamp(alpha / 255.0))))
    return img


def build_bg():
    img = Image.new("RGB", (W, H), BG_DARK)
    yy = np.mgrid[0:H, 0:W][0].astype(np.float32)
    xx = np.mgrid[0:H, 0:W][1].astype(np.float32)
    d1 = np.sqrt((xx - W * 0.22) ** 2 + (yy - H * 0.05) ** 2) / (W * 0.85)
    d2 = np.sqrt((xx - W * 0.92) ** 2 + (yy - H * 1.02) ** 2) / (W * 0.75)
    t1 = np.clip(1 - d1, 0, 1) ** 2.4
    t2 = np.clip(1 - d2, 0, 1) ** 2.2
    arr = np.zeros((H, W, 3), np.float32)
    for i in range(3):
        arr[..., i] = BG_DARK[i] + t1 * (BRAND[i] * 0.30 - BG_DARK[i]) + t2 * (BRAND[i] * 0.16 - BG_DARK[i])
    # 细网格
    grid = np.zeros((H, W), np.float32)
    grid[::60, :] = 1.0
    grid[:, ::60] = 1.0
    arr += (grid * 3.5)[..., None]
    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGB")


BG = None


def wave_logo(img, cx, cy, dia, color=(255, 255, 255), bg=None):
    """App 图标：中心馈源 + 三道对称波瓣（取 ic_launcher_foreground 的几何参数）。"""
    r_out = dia / 2.0
    pad = int(dia) + 10
    lay = Image.new("RGBA", (pad, pad), (0, 0, 0, 0))
    d = ImageDraw.Draw(lay)
    s = dia / 108.0
    c = pad / 2.0
    if bg is not None:
        d.ellipse([c - r_out, c - r_out, c + r_out, c + r_out], fill=bg)
    col = color if len(color) == 4 else color + (255,)
    wid = max(2, int(round(5.5 * s)))
    for rad in (32, 23, 14):
        rr = rad * s
        box = [c - rr, c - rr, c + rr, c + rr]
        d.arc(box, start=-50, end=50, fill=col, width=wid)
        d.arc(box, start=130, end=230, fill=col, width=wid)
    core = 5 * s
    d.ellipse([c - core, c - core, c + core, c + core], fill=col)
    for rad in (32, 23, 14):
        rr = rad * s
        for ang in (-50, 50, 130, 230):
            a = math.radians(ang)
            px, py = c + rr * math.cos(a), c + rr * math.sin(a)
            d.ellipse([px - wid / 2, py - wid / 2, px + wid / 2, py + wid / 2], fill=col)
    img.alpha_composite(lay, (int(cx - pad / 2), int(cy - pad / 2)))
    return img


def glyph(d, cx, cy, size, kind, color):
    r = size / 2.0
    w = max(2, int(size * 0.09))
    if kind == "calc":
        d.rounded_rectangle([cx - r * 0.8, cy - r, cx + r * 0.8, cy + r], radius=r * 0.22,
                            outline=color, width=w)
        d.line([(cx - r * 0.45, cy - r * 0.5), (cx + r * 0.45, cy - r * 0.5)], fill=color, width=w)
        for i, yy in enumerate((0.02, 0.42)):
            d.ellipse([cx - r * 0.5, cy + r * yy - r * 0.11, cx - r * 0.28, cy + r * yy + r * 0.11],
                      fill=color)
            d.line([(cx - r * 0.05, cy + r * yy), (cx + r * 0.5, cy + r * yy)], fill=color, width=w)
    elif kind == "book":
        d.line([(cx - r * 0.75, cy - r * 0.65), (cx, cy - r * 0.45)], fill=color, width=w)
        d.line([(cx + r * 0.75, cy - r * 0.65), (cx, cy - r * 0.45)], fill=color, width=w)
        d.line([(cx - r * 0.75, cy - r * 0.65), (cx - r * 0.75, cy + r * 0.7)], fill=color, width=w)
        d.line([(cx + r * 0.75, cy - r * 0.65), (cx + r * 0.75, cy + r * 0.7)], fill=color, width=w)
        d.line([(cx, cy - r * 0.45), (cx, cy + r * 0.7)], fill=color, width=w)
        d.line([(cx - r * 0.75, cy + r * 0.7), (cx, cy + r * 0.5)], fill=color, width=w)
        d.line([(cx + r * 0.75, cy + r * 0.7), (cx, cy + r * 0.5)], fill=color, width=w)
    elif kind == "audio":
        for i, hh in enumerate((0.35, 0.75, 1.0, 0.62, 0.3)):
            xx = cx + (i - 2) * r * 0.34
            d.line([(xx, cy - r * hh * 0.8), (xx, cy + r * hh * 0.8)], fill=color, width=w)
    elif kind == "net":
        d.ellipse([cx - r * 0.85, cy - r * 0.85, cx + r * 0.85, cy + r * 0.85], outline=color, width=w)
        d.ellipse([cx - r * 0.85, cy - r * 0.42, cx + r * 0.85, cy + r * 0.42], outline=color, width=w)
        d.line([(cx, cy - r * 0.85), (cx, cy + r * 0.85)], fill=color, width=w)
    elif kind == "wave":
        for i in range(3):
            rr = r * (0.45 + i * 0.28)
            d.arc([cx - rr, cy - rr, cx + rr, cy + rr], -55, 55, fill=color, width=w)
            d.arc([cx - rr, cy - rr, cx + rr, cy + rr], 125, 235, fill=color, width=w)
        d.ellipse([cx - r * 0.13, cy - r * 0.13, cx + r * 0.13, cy + r * 0.13], fill=color)
    elif kind == "antenna":
        d.line([(cx, cy - r * 0.9), (cx, cy + r * 0.1)], fill=color, width=w)
        d.line([(cx - r * 0.7, cy + r * 0.15), (cx + r * 0.7, cy + r * 0.15)], fill=color, width=w)
        for i in range(3):
            yy = cy - r * 0.8 + i * r * 0.3
            hw = r * (0.3 + i * 0.18)
            d.line([(cx - hw, yy), (cx + hw, yy)], fill=color, width=w)
        d.line([(cx, cy + r * 0.15), (cx, cy + r * 0.85)], fill=color, width=w)
    elif kind == "vswr":
        d.line([(cx - r * 0.8, cy + r * 0.6), (cx + r * 0.8, cy + r * 0.6)], fill=color, width=w)
        for i, hh in enumerate((0.3, 0.85, 0.5, 0.7)):
            xx = cx - r * 0.6 + i * r * 0.4
            d.line([(xx, cy + r * 0.6), (xx, cy + r * 0.6 - r * hh * 1.3)], fill=color, width=w)
    elif kind == "cable":
        d.arc([cx - r * 0.9, cy - r * 0.5, cx + r * 0.1, cy + r * 0.9], 200, 340, fill=color, width=w)
        d.arc([cx - r * 0.1, cy - r * 0.9, cx + r * 0.9, cy + r * 0.5], 20, 160, fill=color, width=w)
        d.ellipse([cx + r * 0.55, cy - r * 0.95, cx + r * 0.95, cy - r * 0.55],
                  outline=color, width=w)
    elif kind == "link":
        d.line([(cx - r * 0.9, cy), (cx - r * 0.25, cy)], fill=color, width=w)
        d.line([(cx + r * 0.25, cy), (cx + r * 0.9, cy)], fill=color, width=w)
        d.ellipse([cx - r * 0.3, cy - r * 0.3, cx + r * 0.3, cy + r * 0.3], outline=color, width=w)
        for i in range(2):
            rr = r * (0.5 + i * 0.35)
            d.arc([cx - rr, cy - rr, cx + rr, cy + rr], -40, 40, fill=color, width=w)
    elif kind == "band":
        for i in range(3):
            yy = cy - r * 0.55 + i * r * 0.55
            d.line([(cx - r * 0.85, yy), (cx + r * 0.85, yy)], fill=color, width=w)
            d.ellipse([cx + r * 0.5, yy - r * 0.12, cx + r * 0.74, yy + r * 0.12], fill=color)
    elif kind == "globe":
        d.ellipse([cx - r * 0.85, cy - r * 0.85, cx + r * 0.85, cy + r * 0.85], outline=color, width=w)
        d.line([(cx - r * 0.85, cy), (cx + r * 0.85, cy)], fill=color, width=w)
        d.ellipse([cx - r * 0.4, cy - r * 0.85, cx + r * 0.4, cy + r * 0.85], outline=color, width=w)
    elif kind == "q":
        d.arc([cx - r * 0.8, cy - r * 0.9, cx + r * 0.5, cy + r * 0.4], 30, 330, fill=color, width=w)
        d.line([(cx + r * 0.1, cy + r * 0.1), (cx + r * 0.75, cy + r * 0.85)], fill=color, width=w)
    elif kind == "morse":
        d.ellipse([cx - r * 0.85, cy - r * 0.5, cx - r * 0.5, cy - r * 0.15], fill=color)
        d.rounded_rectangle([cx - r * 0.3, cy - r * 0.45, cx + r * 0.4, cy - r * 0.2],
                            radius=r * 0.1, fill=color)
        d.rounded_rectangle([cx - r * 0.85, cy + r * 0.3, cx - r * 0.15, cy + r * 0.55],
                            radius=r * 0.1, fill=color)
        d.ellipse([cx + r * 0.05, cy + r * 0.3, cx + r * 0.4, cy + r * 0.55], fill=color)
    elif kind == "sat":
        d.rounded_rectangle([cx - r * 0.28, cy - r * 0.28, cx + r * 0.28, cy + r * 0.28],
                            radius=r * 0.1, outline=color, width=w)
        d.line([(cx - r * 0.85, cy), (cx - r * 0.33, cy)], fill=color, width=w)
        d.line([(cx + r * 0.33, cy), (cx + r * 0.85, cy)], fill=color, width=w)
        d.line([(cx - r * 0.85, cy - r * 0.22), (cx - r * 0.85, cy + r * 0.22)], fill=color, width=w)
        d.line([(cx + r * 0.85, cy - r * 0.22), (cx + r * 0.85, cy + r * 0.22)], fill=color, width=w)
    elif kind == "key":
        d.rounded_rectangle([cx - r * 0.7, cy - r * 0.85, cx + r * 0.2, cy + r * 0.35],
                            radius=r * 0.2, outline=color, width=w)
        d.line([(cx + r * 0.2, cy - r * 0.2), (cx + r * 0.8, cy - r * 0.2)], fill=color, width=w)
        d.line([(cx - r * 0.6, cy + r * 0.55), (cx + r * 0.6, cy + r * 0.55)], fill=color, width=w)
    elif kind == "headphone":
        d.arc([cx - r * 0.85, cy - r * 0.8, cx + r * 0.85, cy + r * 0.6], 180, 360, fill=color, width=w)
        d.rounded_rectangle([cx - r * 0.9, cy - r * 0.05, cx - r * 0.5, cy + r * 0.6],
                            radius=r * 0.15, fill=color)
        d.rounded_rectangle([cx + r * 0.5, cy - r * 0.05, cx + r * 0.9, cy + r * 0.6],
                            radius=r * 0.15, fill=color)
    elif kind == "dial":
        for i in range(3):
            for j in range(3):
                xx = cx + (j - 1) * r * 0.55
                yy = cy + (i - 1) * r * 0.55
                d.ellipse([xx - r * 0.16, yy - r * 0.16, xx + r * 0.16, yy + r * 0.16], fill=color)
    elif kind == "sine":
        pts = []
        for i in range(40):
            xx = cx - r * 0.9 + i * (r * 1.8 / 39)
            pts.append((xx, cy - math.sin(i / 39 * math.pi * 2) * r * 0.5))
        d.line(pts, fill=color, width=w)
    elif kind == "sun":
        d.ellipse([cx - r * 0.42, cy - r * 0.42, cx + r * 0.42, cy + r * 0.42], outline=color, width=w)
        for i in range(8):
            a = i * math.pi / 4
            d.line([(cx + math.cos(a) * r * 0.62, cy + math.sin(a) * r * 0.62),
                    (cx + math.cos(a) * r * 0.92, cy + math.sin(a) * r * 0.92)], fill=color, width=w)
    elif kind == "cluster":
        for i, (dx, dy) in enumerate(((-0.45, -0.35), (0.4, -0.5), (0.0, 0.35), (-0.55, 0.5), (0.55, 0.4))):
            d.ellipse([cx + dx * r - r * 0.15, cy + dy * r - r * 0.15,
                       cx + dx * r + r * 0.15, cy + dy * r + r * 0.15], fill=color)
        d.line([(cx - r * 0.45, cy - r * 0.35), (cx, cy + r * 0.35)], fill=color, width=w)
        d.line([(cx + r * 0.4, cy - r * 0.5), (cx, cy + r * 0.35)], fill=color, width=w)
    elif kind == "grid":
        for i in range(3):
            xx = cx - r * 0.8 + i * r * 0.8
            d.line([(xx, cy - r * 0.8), (xx, cy + r * 0.8)], fill=color, width=w)
            yy = cy - r * 0.8 + i * r * 0.8
            d.line([(cx - r * 0.8, yy), (cx + r * 0.8, yy)], fill=color, width=w)
        d.ellipse([cx - r * 0.2, cy - r * 0.2, cx + r * 0.2, cy + r * 0.2], fill=color)
    elif kind == "map":
        d.line([(cx - r * 0.85, cy + r * 0.55), (cx - r * 0.3, cy - r * 0.6),
                (cx + r * 0.25, cy + r * 0.5), (cx + r * 0.85, cy - r * 0.45)], fill=color, width=w)
        d.ellipse([cx - r * 0.12, cy - r * 0.72, cx + r * 0.12, cy - r * 0.48], outline=color, width=w)
    elif kind == "search_id":
        d.ellipse([cx - r * 0.8, cy - r * 0.8, cx + r * 0.35, cy + r * 0.35], outline=color, width=w)
        d.line([(cx + r * 0.25, cy + r * 0.25), (cx + r * 0.85, cy + r * 0.85)], fill=color, width=w)
    else:
        d.ellipse([cx - r * 0.7, cy - r * 0.7, cx + r * 0.7, cy + r * 0.7], outline=color, width=w)


# ---------------------------------------------------------------- HUD

SECTION_MARKS = []
TOOL_SPANS = {}


def section_name(t):
    name = SECTION_MARKS[0][1] if SECTION_MARKS else ""
    for start, nm in SECTION_MARKS:
        if t >= start:
            name = nm
    return name


def draw_hud(img, t, alpha=1.0):
    if alpha <= 0.01:
        return img
    d = ImageDraw.Draw(img)
    wave_logo(img, 88, 62, 40, color=pre((255, 255, 255), 230 * alpha),
              bg=pre(BRAND, 240 * alpha))
    d = ImageDraw.Draw(img)
    tracked_text(d, (126, 62), "HAM 工具合集", font(24, "bold"), pre(TXT, 200 * alpha),
                 tracking=1, valign="m")

    nm = section_name(t)
    d.text((W - 90, 62), nm, font=font(20, "reg"), fill=pre(TXT_FAINT, 210 * alpha), anchor="rm")
    nx = W - 90 - d.textlength(nm, font=font(20, "reg")) - 26
    d.line([(nx, 54), (nx, 70)], fill=pre(BRAND_HI, 90 * alpha), width=2)

    prog = clamp(t / DUR)
    d.rectangle([0, H - 3, W, H], fill=pre((255, 255, 255), 14 * alpha))
    d.rectangle([0, H - 3, int(W * prog), H], fill=pre(BRAND_HI, 140 * alpha))
    return img


# ---------------------------------------------------------------- 场景 1：开场

def scene_intro(t):
    img = BG.copy().convert("RGBA")
    cx, cy = W / 2, H * 0.385

    rise = ease_out(seg(t, 0.0, 1.4))
    img = glow(img, cx, cy, 320 + 100 * rise, BRAND, alpha=int(45 + 55 * rise), softness=0.40)
    pulse = 0.5 + 0.5 * math.sin(t * 2.2)
    img = glow(img, cx, cy, 200, BRAND_HI, alpha=int(24 + 22 * pulse), softness=0.55)

    for i, b in enumerate((1.0, 1.45, 1.9)):
        p = seg(t, b, 2.0)
        if 0 < p < 1:
            r = 130 + 560 * ease_out(p, 2.2)
            ring = Image.new("RGBA", (int(r * 2) + 8, int(r * 2) + 8), (0, 0, 0, 0))
            ImageDraw.Draw(ring).ellipse([4, 4, ring.size[0] - 5, ring.size[1] - 5],
                                         outline=BRAND_HI + (int(110 * (1 - p) ** 1.6),),
                                         width=max(1, int(3 - 2 * p)))
            img.alpha_composite(ring, (int(cx - ring.size[0] / 2), int(cy - ring.size[1] / 2)))

    p = seg(t, 0.35, 1.05)
    if p > 0:
        sc = 0.6 + 0.4 * ease_out_back(p, 1.2)
        wave_logo(img, cx, cy, 220 * sc, color=(255, 255, 255, int(255 * p)),
                  bg=BRAND + (int(250 * p),))

    p = seg(t, 1.35, 0.9)
    if p > 0:
        tracked_text(ImageDraw.Draw(img), (cx, 600 + 30 * (1 - ease_out(p))), "HAM 工具合集",
                     font(96, "bold"), TXT + (int(255 * p),),
                     tracking=lerp(24, 8, ease_out(p)), anchor="c", valign="m")

    p = seg(t, 2.0, 0.85)
    if p > 0:
        tracked_text(ImageDraw.Draw(img), (cx, 690 + 20 * (1 - ease_out(p))),
                     "业余无线电 · 口袋工具箱", font(38, "reg"),
                     TXT_DIM + (int(220 * p),), tracking=8, anchor="c", valign="m")

    stats = [("19", "个工具"), ("4", "大板块"), ("100%", "核心离线可用")]
    for i, (num, label) in enumerate(stats):
        p = seg(t, 2.75 + i * 0.13, 0.7)
        if p <= 0:
            continue
        a = int(255 * p)
        x = cx + (i - 1) * 330
        d = ImageDraw.Draw(img)
        d.text((x, 780), num, font=font(64, "bold"), fill=BRAND_SOFT + (a,), anchor="mm")
        d.text((x, 848), label, font=font(24, "reg"), fill=TXT_DIM + (a,), anchor="mm")
        if i:
            d.line([(x - 165, 758), (x - 165, 812)],
                   fill=(58, 74, 70, int(120 * p)), width=1)

    p = seg(t, 3.55, 0.8)
    if p > 0:
        tracked_text(ImageDraw.Draw(img), (cx, 930), "10 个已可用 · 9 个开发中 · 作者 BA4IGG",
                     font(23, "light"), TXT_FAINT + (int(200 * p),),
                     tracking=3, anchor="c", valign="m")

    fade = 1 - 0.5 * seg(t, INTRO_DUR - 0.7, 0.65)
    if fade < 1:
        img = Image.blend(Image.new("RGBA", img.size, (0, 0, 0, 255)), img, fade)
    return img.convert("RGB")


# ---------------------------------------------------------------- 场景 2：痛点

def scene_pain(t):
    img = BG.copy().convert("RGBA")
    d = ImageDraw.Draw(img)
    cx = W / 2
    img = glow(img, cx, H * 0.45, 420, BRAND, alpha=36, softness=0.5)
    d = ImageDraw.Draw(img)

    p = seg(t, 0.15, 0.8)
    if p > 0:
        tracked_text(d, (cx, H * 0.245), "有没有过这样的时刻", font(28, "light"),
                     TXT_FAINT + (int(210 * p),), tracking=8, anchor="c")

    p = seg(t, 0.4, 0.9)
    if p > 0:
        tracked_text(d, (cx, H * 0.345 + 26 * (1 - ease_out(p))), "山顶架好天线，却没带计算器",
                     font(66, "bold"), TXT + (int(255 * p),), tracking=2, anchor="c")

    lines = [
        "想算半波长，手机却没信号，在线工具打不开",
        "好不容易连上网，先看三十秒广告",
        "查到一组中继频率，却不知道是哪一年登记的",
        "想练 CW，翻遍应用商店也没找到干净的软件",
    ]
    for i, text in enumerate(lines):
        p = seg(t, 1.5 + i * 0.48, 0.85)
        if p <= 0:
            continue
        a = int(255 * p)
        y = H * 0.465 + i * 68 + 24 * (1 - ease_out(p))
        d = ImageDraw.Draw(img)
        d.ellipse([cx - 430, y - 16, cx - 422, y - 8], fill=(96, 116, 110, a))
        d.text((cx - 398, y), text, font=font(31, "reg"), fill=TXT_DIM + (a,), anchor="ls")

    p = seg(t, 3.9, 0.9)
    if p > 0:
        a = int(255 * p)
        yy = H * 0.775
        d = ImageDraw.Draw(img)
        tracked_text(d, (cx, yy), "所以做了这个 ——", font(28, "light"),
                     TXT_FAINT + (a,), tracking=4, anchor="c")
        tracked_text(d, (cx, yy + 74), "一个离线优先、按需联网的业余无线电工具箱",
                     font(50, "bold"), BRAND_SOFT + (a,), tracking=2, anchor="c")

    return img.convert("RGB")


# ---------------------------------------------------------------- 场景 3：总览

BLOCKS = [
    dict(name="计算工具", gk="calc", done=5, total=5,
         tools="频率波长 · 天线长度 · 驻波比回损\n馈线损耗 · 链路预算"),
    dict(name="查询手册", gk="book", done=5, total=5,
         tools="频段划分 · 呼号前缀 · Q 简语\n摩尔斯对照 · 卫星与中继"),
    dict(name="音频工具", gk="audio", done=0, total=4,
         tools="CW 电键 · 听抄训练\nDTMF · 音调发生器"),
    dict(name="联网工具", gk="net", done=0, total=5,
         tools="传播指数 · DX 集群 · 网格方位\nAPRS 地图 · 呼号查询"),
]


def scene_overview(t):
    img = BG.copy().convert("RGBA")
    d = ImageDraw.Draw(img)

    p = seg(t, 0.1, 0.9)
    if p > 0:
        d.text((140, H * 0.275 + 22 * (1 - ease_out(p))), "一个入口",
               font=font(34, "light"), fill=BRAND_HI + (int(230 * p),), anchor="ls")
        tracked_text(d, (140, H * 0.385 + 26 * (1 - ease_out(p))), "19 个工具，按场景分组",
                     font(58, "bold"), TXT + (int(255 * p),), tracking=1, valign="m")

    p = seg(t, 0.45, 0.9)
    if p > 0:
        body = "计算、查询、音频、联网四大板块。前两个板块 10 个工具已全部可用，数据内置、无需联网；后两个板块 9 个工具正在开发。"
        for i, ln in enumerate(wrap_text(d, body, font(26, "reg"), 610)):
            d.text((140, H * 0.465 + i * 42), ln, font=font(26, "reg"),
                   fill=TXT_DIM + (int(225 * p),), anchor="ls")

    p = seg(t, 1.1, 0.8)
    if p > 0:
        wbar = 380 * ease_out(p)
        d.rounded_rectangle([140, H * 0.665, 140 + wbar, H * 0.665 + 54], radius=27,
                            fill=pre(BRAND_HI, 26 * p), outline=BRAND_HI + (int(90 * p),), width=1)
        d.text((168, H * 0.665 + 35), "10 已实现 / 9 开发中",
               font=font(23, "reg"), fill=BRAND_SOFT + (int(240 * p),), anchor="ls")

    bx, by, bw, bh, gap = 830, 195, 440, 315, 26
    for i, b in enumerate(BLOCKS):
        p = seg(t, 0.55 + i * 0.16, 0.95)
        if p <= 0:
            continue
        a = int(255 * p)
        x = bx + (i % 2) * (bw + gap)
        y = by + (i // 2) * (bh + gap) + 34 * (1 - ease_out(p))
        img.alpha_composite(panel((bw, bh), 30, CARD + (a,), outline=LINE + (int(a * .85),)),
                            (int(x), int(y)))
        dd = ImageDraw.Draw(img)
        acc = BRAND_HI if b["done"] else AMBER
        dd.rounded_rectangle([x + 34, y + 32, x + 96, y + 94], radius=18, fill=pre(acc, 26 * p, CARD))
        glyph(dd, x + 65, y + 63, 36, b["gk"], acc + (a,))
        dd.text((x + 34, y + 150), b["name"], font=font(31, "bold"), fill=TXT + (a,), anchor="ls")
        ctext = f'{b["done"]} / {b["total"]} 已实现' if b["done"] else f'{b["total"]} 个 · 开发中'
        dd.text((x + 34, y + 188), ctext, font=font(21, "reg"), fill=acc + (a,), anchor="ls")
        # 进度点
        for k in range(b["total"]):
            cxx = x + 38 + k * 26
            if k < b["done"]:
                dd.ellipse([cxx, y + 210, cxx + 14, y + 224], fill=BRAND_HI + (a,))
            else:
                dd.ellipse([cxx, y + 210, cxx + 14, y + 224], outline=LINE + (a,), width=2)
        for j, ln in enumerate(wrap_text(dd, b["tools"], font(19, "reg"), bw - 68)):
            dd.text((x + 34, y + 262 + j * 30), ln, font=font(19, "reg"),
                    fill=TXT_FAINT + (a,), anchor="ls")

    return img.convert("RGB")


# ---------------------------------------------------------------- 章节卡

def scene_chapter(t, cfg):
    img = BG.copy().convert("RGBA")
    cx = W / 2
    img = glow(img, W * 0.5, H * 0.48, 460, BRAND, alpha=int(40 * clamp(seg(t, 0, 0.5))), softness=0.5)
    d = ImageDraw.Draw(img)

    # 大字编号背景
    p = seg(t, 0.0, 0.7)
    if p > 0:
        d.text((W - 150, H * 0.80), cfg["idx"], font=font(300, "bold"),
               fill=pre(BRAND_HI, 12 * p), anchor="rs")

    p = seg(t, 0.05, 0.6)
    if p > 0:
        a = int(255 * p)
        d.text((160, H * 0.40), cfg["idx"], font=font(40, "bold"), fill=BRAND_HI + (a,), anchor="ls")
        d.line([(160, H * 0.435), (160 + 46 * ease_out(p), H * 0.435)],
               fill=BRAND_HI + (int(200 * p),), width=3)

    p = seg(t, 0.18, 0.7)
    if p > 0:
        tracked_text(d, (160, H * 0.535 + 30 * (1 - ease_out(p))), cfg["name"],
                     font(92, "bold"), TXT + (int(255 * p),), tracking=4)

    p = seg(t, 0.42, 0.7)
    if p > 0:
        acc = BRAND_SOFT if cfg["done"] else AMBER
        d.text((164, H * 0.720 + 16 * (1 - ease_out(p))), cfg["sub"],
               font=font(30, "reg"), fill=acc + (int(240 * p),), anchor="ls")

    # 右侧工具清单
    items = cfg["items"]
    for i, nm in enumerate(items):
        p = seg(t, 0.75 + i * 0.10, 0.55)
        if p <= 0:
            continue
        a = int(255 * p)
        yy = H * 0.30 + i * 74
        xx = 1080 + 40 * (1 - ease_out(p))
        d = ImageDraw.Draw(img)
        done = i < cfg["done"]
        col = BRAND_HI if done else AMBER_DIM
        if done:
            d.ellipse([xx, yy - 24, xx + 12, yy - 12], fill=col + (a,))
        else:
            d.ellipse([xx, yy - 24, xx + 12, yy - 12], outline=col + (a,), width=2)
        d.text((xx + 34, yy - 10), nm, font=font(27, "reg"),
               fill=(TXT if done else TXT_FAINT) + (a,), anchor="ls")

    return img.convert("RGB")


# ---------------------------------------------------------------- 工具定义（19 个）

CALC = "计算工具"
REF = "查询手册"
AUDIO = "音频工具"
NET = "联网工具"

TOOLS = [
    # ============ 计算工具（已实现） ============
    dict(idx="01", cat=CALC, status="done", title="频率波长",
         desc="输入任意频率，立刻得到波长与两个常用分数波长。做天线、切馈线、算谐振长度都用得上，不用再背 300/λ 的换算。",
         bullets=["覆盖 1.8 MHz – 5.8 GHz 常用业余频段", "一次给出 λ、λ/4、λ/2 三个值",
                  "自动判断所属频段与带宽", "结果可一键复制"],
         head="145.000 MHz", big=("2.067", "m"), label="波长 λ",
         rows=[("λ / 4", "0.517 m"), ("λ / 2", "1.034 m"), ("所属频段", "2 m 波段")]),
    dict(idx="02", cat=CALC, status="done", title="天线长度",
         desc="偶极子、倒 V、垂直天线，输入频率直接给出振子长度。按材料与架设方式选了缩短系数，比死记公式准。",
         bullets=["偶极子按 0.95 缩短系数修正", "倒 V 按 120° 夹角换算",
                  "垂直天线取 1/4λ", "结果同时给出米与英尺"],
         head="7.050 MHz · 40 m 波段", big=("20.20", "m"), label="偶极子全长",
         rows=[("单臂长度", "10.10 m"), ("倒 V（120°）", "19.59 m"), ("垂直 1/4λ", "10.10 m")]),
    dict(idx="03", cat=CALC, status="done", title="驻波比回损",
         desc="VSWR、回波损耗、反射系数三者互算。调天线时看到一个数就知道另外两个，不必再翻对照表。",
         bullets=["三者任意输入，其余自动换算", "直接给出反射功率百分比",
                  "覆盖 1.0 – 10.0 全区间", "附常见天线状态的参考值"],
         head="VSWR 1.50", big=("14.0", "dB"), label="回波损耗",
         rows=[("反射系数 Γ", "0.200"), ("反射功率", "4.0 %"), ("传输功率", "96.0 %")]),
    dict(idx="04", cat=CALC, status="done", title="馈线损耗",
         desc="按线缆型号与长度估算衰减。用厂商公布的七个频点做双对数插值，不是凭经验凑出来的公式。",
         bullets=["内置 -50 / -7 / -9 等常见同轴线", "七频点双对数插值",
                  "给出末端剩余功率与折合 S 表", "可反推最长可用长度"],
         head="-50 电缆 · 30 m · 145 MHz", big=("1.6", "dB"), label="总衰减",
         rows=[("-50 每 100 m", "5.5 dB"), ("末端剩余功率", "69 %"), ("折合 S 表", "−0.3 S")]),
    dict(idx="05", cat=CALC, status="done", title="链路预算",
         desc="弗里斯的传输方程。收发功率、天线增益、路径损耗一路算下来，最后得到一个数：这条链路还剩下多少余量。",
         bullets=["自由空间路径损耗模型", "含收发两端天线增益",
                  "直接给出链路余量", "可估算最大可用距离"],
         head="145 MHz · 50 km · 5 W", big=("−70.3", "dBm"), label="接收电平",
         rows=[("发射功率", "+37.0 dBm"), ("收发天线增益", "+4.3 dBi"),
               ("自由空间损耗", "−109.7 dB"), ("链路余量", "49.6 dB")]),

    # ============ 查询手册（已实现） ============
    dict(idx="06", cat=REF, status="done", title="业余频段划分",
         desc="中国与 IARU 三个区的业余频段划分，按区域切换，支持关键词检索。每一段都标了带宽与主要用途。",
         bullets=["中国 / 一区 / 二区 / 三区四个视角", "标注带宽、模式与主要用途",
                  "关键词直接过滤（如「2 m」「CW」）", "数据内置，无需联网"],
         head="IARU 第三区 · 中国", big=None, label=None,
         rows=[("20 m", "14.000 – 14.350 MHz"), ("15 m", "21.000 – 21.450 MHz"),
               ("10 m", "28.000 – 29.700 MHz"), ("6 m", "50.000 – 54.000 MHz")]),
    dict(idx="07", cat=REF, status="done", title="呼号前缀",
         desc="输入呼号解析国家与地区，也可以按大洲浏览约 300 个 DXCC 实体。斜杠后缀会单独识别。",
         bullets=["最长前缀优先匹配", "支持 /P /M /MM /QRP 后缀",
                  "约 300 个 DXCC 实体", "按大洲筛选浏览"],
         head="BA4IGG", big=None, label=None,
         rows=[("国家 / 地区", "中国"), ("CQ 区", "24"), ("ITU 区", "44"), ("省份", "山东省")]),
    dict(idx="08", cat=REF, status="done", title="Q 简语",
         desc="常用 Q 简语，同时列出提问与陈述两种用法，另附 ICAO 字母解释法。听不懂对方说的简语时随手一查。",
         bullets=["提问 / 陈述两面并列", "常用简语约 45 条",
                  "附 26 个字母解释法", "支持按字母检索"],
         head="常用 Q 简语", big=None, label=None,
         rows=[("QTH", "我的位置是 …"), ("QRM", "受到人为干扰"),
               ("QSY", "改频到 …"), ("QSL", "收到 / 交换卡片")]),
    dict(idx="09", cat=REF, status="done", title="摩尔斯对照",
         desc="字符与电码互转，可以现场发声。速度 5 – 40 WPM 连续可调，波形实时合成，几乎不占安装包体积。",
         bullets=["波形现场合成，不打包音频", "支持文字转电码与反查",
                  "起止留 5 ms 斜坡避免爆音", "速度 5 – 40 WPM 连续可调"],
         head="MORSE", big=None, label=None, rows=[], special="morse"),
    dict(idx="10", cat=REF, status="done", title="卫星与中继",
         desc="业余卫星的上下行频率与模式，以及国内常用中继名录，按大区分组。页面内附数据时效提醒。",
         bullets=["含 FM 卫星亚音参数", "中继按省份分组",
                  "上下行差频自动核对", "附数据时效提醒"],
         head="业余卫星与中继", big=None, label=None,
         rows=[("ISS", "145.800 FM"), ("SO-50", "145.850 ↑ / 436.795 ↓"),
               ("AO-91", "145.960 ↑ / 435.250 ↓"), ("国内中继", "438.500 −5 MHz")]),

    # ============ 音频工具（开发中） ============
    dict(idx="11", cat=AUDIO, status="wip", title="CW 电键",
         desc="把手机变成一只电子键。双桨与直键两种模式，速度和侧音现场可调，插上耳机就能练，不用带额外硬件。",
         bullets=["双桨 / 直键两种键型", "5 – 35 WPM 速度调节",
                  "侧音频率 400 – 1000 Hz 可调", "支持蓝牙与有线耳机"],
         head="功能规划 · CW 电键", big=None, label=None,
         rows=["双桨 / 直键两种键型", "5 – 35 WPM 速度调节",
               "侧音 400 – 1000 Hz 可调", "点划时长自动校正"],
         note="计划在 1.2 版本提供"),
    dict(idx="12", cat=AUDIO, status="wip", title="CW 听抄训练",
         desc="按科赫法从两个字符起步，逐步扩充字符集，配合随机呼号与常用词组练习，并记录正确率曲线。",
         bullets=["科赫法渐进字符集", "随机呼号与词组听抄",
                  "正确率与速度曲线", "可自定义练习时长"],
         head="功能规划 · 听抄训练", big=None, label=None,
         rows=["科赫法渐进字符集", "随机呼号 / 词组听抄",
               "正确率与速度曲线", "错字自动重练"],
         note="计划在 1.2 版本提供"),
    dict(idx="13", cat=AUDIO, status="wip", title="DTMF",
         desc="生成标准的双音多频信号，也能从麦克风收音解码。测试中继与设备音频通路时很实用。",
         bullets=["16 个标准 DTMF 码", "双音精确到 0.1 Hz",
                  "麦克风实时解码", "显示检测到的频偏"],
         head="功能规划 · DTMF", big=None, label=None,
         rows=["16 个标准 DTMF 码", "双音频率精确到 0.1 Hz",
               "麦克风实时解码", "显示收发频偏"],
         note="计划在 1.2 版本提供"),
    dict(idx="14", cat=AUDIO, status="wip", title="音调发生器",
         desc="单音、双音与扫频输出。测中继、试天线、找共振点，一个信号源就够，不必再翻出信号发生器。",
         bullets=["20 Hz – 20 kHz 单音", "线性 / 对数扫频",
                  "双音与白噪声", "音量精确可调"],
         head="功能规划 · 音调发生器", big=None, label=None,
         rows=["20 Hz – 20 kHz 单音输出", "线性 / 对数扫频",
               "双音与白噪声", "音量按 dB 精确调节"],
         note="计划在 1.2 版本提供"),

    # ============ 联网工具（开发中） ============
    dict(idx="15", cat=NET, status="wip", title="传播指数",
         desc="太阳通量、K 指数、A 指数与各波段开通概率。出门前看一眼，就知道今天该守哪个波段。",
         bullets=["SFI / K / A 三指数", "逐波段开通概率",
                  "MUF 与临界频率估算", "离线缓存最近一次数据"],
         head="功能规划 · 传播指数", big=None, label=None,
         rows=["SFI / K / A 三指数", "逐波段开通概率",
               "MUF 与临界频率估算", "断网时读缓存数据"],
         note="需要联网 · 计划在 1.3 版本提供"),
    dict(idx="16", cat=NET, status="wip", title="DX 集群",
         desc="实时抓取 DX 集群的 spots，按波段与模式过滤。稀有台一出现就能看到，不必守着网页刷。",
         bullets=["实时 spots 数据流", "按波段 / 模式过滤",
                  "呼号黑白名单", "点击跳到呼号查询"],
         head="功能规划 · DX 集群", big=None, label=None,
         rows=["实时 spots 数据流", "按波段 / 模式过滤",
               "呼号黑白名单", "点击直接跳到呼号查询"],
         note="需要联网 · 计划在 1.3 版本提供"),
    dict(idx="17", cat=NET, status="wip", title="网格与方位",
         desc="梅登海德网格与大圆距离方位。输入两个网格或坐标，正算反算都给，通联前先知道对方在哪个方向。",
         bullets=["网格 ↔ 经纬度互转", "大圆距离与正方位",
                  "支持 6 位与 8 位网格", "可切换短路径 / 长路径"],
         head="功能规划 · 网格与方位", big=None, label=None,
         rows=["网格 ↔ 经纬度互转", "大圆距离与正方位角",
               "支持 6 位与 8 位网格", "短路径 / 长路径切换"],
         note="纯本地计算 · 计划在 1.3 版本提供"),
    dict(idx="18", cat=NET, status="wip", title="APRS 地图",
         desc="在离线地图上展示 APRS 台站位置与轨迹。没有网络也能看自己走过哪里，以及附近有哪些台在发信标。",
         bullets=["离线地图底图", "APRS-IS 实时数据",
                  "台站轨迹回放", "支持自定义符号集"],
         head="功能规划 · APRS 地图", big=None, label=None,
         rows=["离线地图底图", "APRS-IS 实时数据接入",
               "台站轨迹回放", "自定义 APRS 符号集"],
         note="需要联网 · 计划在 1.3 版本提供"),
    dict(idx="19", cat=NET, status="wip", title="呼号查询",
         desc="联网检索呼号的归属、执照信息与历史记录。收到陌生台时先看一眼再回应，比事后查更从容。",
         bullets=["全球主要国家数据库", "执照等级与有效期",
                  "历史呼号变更", "结果可保存到本地"],
         head="功能规划 · 呼号查询", big=None, label=None,
         rows=["全球主要国家呼号库", "执照等级与有效期",
               "历史呼号变更记录", "查询结果存本地"],
         note="需要联网 · 计划在 1.3 版本提供"),
]

PANEL_X, PANEL_Y, PANEL_W, PANEL_H = 980, 240, 800, 620


def draw_data_panel(img, cfg, t, appear):
    """已实现工具：标题 → 大数字（滚动）→ 明细行。"""
    x, y, w, h = PANEL_X + 70 * (1 - appear), PANEL_Y, PANEL_W, PANEL_H
    a = clamp(appear)
    img = glow(img, x + w / 2, y + h / 2, 420, BRAND, alpha=int(42 * a), softness=0.5)
    img.alpha_composite(panel((w, h), 34, CARD + (int(250 * a),),
                              outline=LINE + (int(235 * a),)), (int(x), int(y)))
    d = ImageDraw.Draw(img)

    d.text((x + 56, y + 74), cfg["head"], font=font(30, "reg"),
           fill=TXT_DIM + (int(240 * a),), anchor="ls")
    d.line([(x + 56, y + 106), (x + w - 56, y + 106)], fill=LINE + (int(200 * a),), width=1)

    p_big = clamp((appear - 0.25) / 0.6)
    ry = y + 210
    if cfg["big"]:
        val, unit = cfg["big"]
        text = val
        try:
            target = float(val.replace("−", "-").replace("+", ""))
            cur = target * ease_out(p_big, 2.6)
            dec = len(val.split(".")[1]) if "." in val else 0
            text = ("−" if target < 0 else "") + f"{abs(cur):.{dec}f}"
        except ValueError:
            text = val
        fa = int(255 * clamp(appear * 1.2))
        if p_big > 0.85:
            img = glow(img, x + w / 2, ry - 40, 220, BRAND_HI,
                       alpha=int(46 * clamp((p_big - 0.85) * 6)), softness=0.55)
            d = ImageDraw.Draw(img)
        f_b = font(118, "bold")
        tw = d.textlength(text, font=f_b)
        d.text((x + 56, ry), text, font=f_b, fill=BRAND_SOFT + (fa,), anchor="ls")
        d.text((x + 56 + tw + 14, ry), unit, font=font(38, "reg"),
               fill=TXT_DIM + (fa,), anchor="ls")
        if cfg["label"]:
            d.text((x + 56, ry + 52), cfg["label"], font=font(24, "reg"),
                   fill=TXT_FAINT + (fa,), anchor="ls")
        row_top = y + 330
    else:
        row_top = y + 178

    n = len(cfg["rows"])
    step = 70 if n >= 4 else 78
    for i, (k, v) in enumerate(cfg["rows"]):
        p = clamp((appear - 0.34 - i * 0.08) / 0.34)
        if p <= 0:
            continue
        ra = int(255 * p)
        yy = row_top + i * step + 18 * (1 - ease_out(p))
        d.text((x + 56, yy), k, font=font(26, "reg"), fill=TXT_DIM + (ra,), anchor="ls")
        d.text((x + w - 56, yy), v, font=font(29, "bold"), fill=TXT + (ra,), anchor="rs")
        if i < n - 1:
            d.line([(x + 56, yy + 24), (x + w - 56, yy + 24)],
                   fill=(36, 48, 45, int(150 * p)), width=1)
    return img


def draw_plan_panel(img, cfg, t, appear):
    """开发中工具：右上角琥珀徽章 + 能力清单，明确标注尚未提供。"""
    x, y, w, h = PANEL_X + 70 * (1 - appear), PANEL_Y, PANEL_W, PANEL_H
    a = clamp(appear)
    img = glow(img, x + w / 2, y + h / 2, 420, AMBER, alpha=int(20 * a), softness=0.5)
    img.alpha_composite(panel((w, h), 34, CARD + (int(250 * a),),
                              outline=(66, 58, 42, int(235 * a))), (int(x), int(y)))
    d = ImageDraw.Draw(img)

    d.text((x + 56, y + 74), cfg["head"], font=font(30, "reg"),
           fill=TXT_DIM + (int(240 * a),), anchor="ls")

    # 徽章
    p_b = seg(t, 0.75, 0.4)
    if p_b > 0:
        bw_, bh_ = 108, 40
        bx_ = x + w - 56 - bw_
        by_ = y + 48
        d.rounded_rectangle([bx_, by_, bx_ + bw_, by_ + bh_], radius=20,
                            fill=pre(AMBER, 34 * p_b, CARD), outline=AMBER + (int(150 * p_b),), width=1)
        d.text((bx_ + bw_ / 2, by_ + 20), "开发中", font=font(20, "bold"),
               fill=AMBER + (int(240 * p_b),), anchor="mm")

    d.line([(x + 56, y + 106), (x + w - 56, y + 106)],
           fill=(62, 54, 42, int(200 * a)), width=1)

    # 能力清单
    top = y + 172
    for i, item in enumerate(cfg["rows"]):
        p = clamp((appear - 0.30 - i * 0.07) / 0.32)
        if p <= 0:
            continue
        ra = int(255 * p)
        yy = top + i * 82 + 16 * (1 - ease_out(p))
        cx0 = x + 62
        d.ellipse([cx0, yy - 17, cx0 + 16, yy - 1], outline=AMBER_DIM + (ra,), width=2)
        d.text((cx0 + 42, yy), item, font=font(27, "reg"), fill=TXT + (ra,), anchor="ls")

    # 底部说明
    p_n = seg(t, 0.95, 0.6)
    if p_n > 0:
        ny = y + h - 62
        d.line([(x + 56, ny - 44), (x + w - 56, ny - 44)],
               fill=(50, 46, 38, int(180 * p_n)), width=1)
        d.text((x + 56, ny), cfg.get("note", ""), font=font(21, "reg"),
               fill=AMBER_DIM + (int(220 * p_n),), anchor="ls")
    return img


MORSE_CQ = [("C", "-·-·"), ("Q", "--·-")]
DOT_W, DASH_W, SYM_GAP, CHAR_ADV = 16, 64, 16, 84


def _group_w(code):
    return CHAR_ADV + sum((DASH_W if s == "-" else DOT_W) + SYM_GAP for s in code) - SYM_GAP


def draw_morse_panel(img, cfg, t, appear):
    x, y, w, h = PANEL_X + 70 * (1 - appear), PANEL_Y, PANEL_W, PANEL_H
    a = clamp(appear)
    img = glow(img, x + w / 2, y + h / 2, 420, BRAND, alpha=int(42 * a), softness=0.5)
    img.alpha_composite(panel((w, h), 34, CARD + (int(250 * a),),
                              outline=LINE + (int(235 * a),)), (int(x), int(y)))
    d = ImageDraw.Draw(img)
    d.text((x + 56, y + 74), "CQ CQ · 现场发声", font=font(30, "reg"),
           fill=TXT_DIM + (int(240 * a),), anchor="ls")
    d.line([(x + 56, y + 106), (x + w - 56, y + 106)], fill=LINE + (int(200 * a),), width=1)

    yb = y + 250
    lit = (t - 1.15) / 0.30
    total = _group_w(MORSE_CQ[0][1]) + 90 + _group_w(MORSE_CQ[1][1])
    x0 = x + (w - total) / 2
    idx = 0
    for ch, code in MORSE_CQ:
        d.text((x0, yb), ch, font=font(82, "bold"), fill=TXT + (int(255 * a),), anchor="ls")
        xx = x0 + CHAR_ADV
        for sym in code:
            if idx < lit - 1:
                col = mix((52, 66, 62), BRAND_HI, 0.72)
            elif idx < lit:
                col = mix(BRAND_HI, BRAND_SOFT, clamp((lit - idx) * 1.8))
            else:
                col = (52, 66, 62)
            if sym == "-":
                d.rounded_rectangle([xx, yb - 20, xx + DASH_W, yb - 4], radius=8, fill=col + (255,))
                xx += DASH_W + SYM_GAP
            else:
                d.ellipse([xx, yb - 20, xx + DOT_W, yb - 4], fill=col + (255,))
                xx += DOT_W + SYM_GAP
            idx += 1
        x0 += _group_w(code) + 90

    bars, bw2, by = 46, 13, y + 360
    bx0 = x + (w - bars * bw2) / 2
    for i in range(bars):
        amp = (0.3 + 0.7 * abs(math.sin(t * 7.0 + i * 0.5))) * (0.45 + 0.55 * math.sin(i / bars * math.pi))
        hh = 5 + amp * 54
        d.rounded_rectangle([bx0 + i * bw2, by - hh / 2, bx0 + i * bw2 + 6, by + hh / 2],
                            radius=3, fill=BRAND_HI + (int(140 * a),))

    d.text((x + 56, y + 470), "5 – 40 WPM 可调", font=font(26, "reg"),
           fill=TXT_DIM + (int(230 * a),), anchor="ls")
    d.text((x + 56, y + 522), "波形由 AudioTrack 现场合成，不打包音频；每个音起止留 5 ms 斜坡，避免爆音",
           font=font(20, "light"), fill=TXT_FAINT + (int(220 * a),), anchor="ls")
    return img


def scene_tool(t, cfg):
    img = BG.copy().convert("RGBA")
    d = ImageDraw.Draw(img)
    is_wip = cfg["status"] == "wip"
    img = glow(img, W * 0.44, H * 0.42, 460, BRAND if not is_wip else (140, 96, 30),
               alpha=32, softness=0.52)
    d = ImageDraw.Draw(img)

    # 序号 + 分类 + 状态
    p = seg(t, 0.0, 0.6)
    if p > 0:
        a = int(255 * p)
        x0 = 138 - 40 * (1 - ease_out(p))
        d.text((x0, H * 0.195), cfg["idx"], font=font(30, "bold"),
               fill=(AMBER if is_wip else BRAND_HI) + (a,), anchor="ls")
        d.text((x0 + 56, H * 0.195), cfg["cat"], font=font(24, "reg"),
               fill=TXT_FAINT + (int(230 * p),), anchor="ls")
        if is_wip:
            tx = x0 + 56 + d.textlength(cfg["cat"], font=font(24, "reg")) + 22
            d.rounded_rectangle([tx, H * 0.195 - 30, tx + 92, H * 0.195 + 2], radius=16,
                                fill=pre(AMBER, 30 * p, CARD), outline=AMBER + (int(140 * p),), width=1)
            d.text((tx + 46, H * 0.195 - 14), "开发中", font=font(18, "bold"),
                   fill=AMBER + (a,), anchor="mm")

    # 标题
    p = seg(t, 0.10, 0.7)
    if p > 0:
        tracked_text(d, (138, H * 0.300 + 26 * (1 - ease_out(p))), cfg["title"],
                     font(74, "bold"), TXT + (int(255 * p),), tracking=2)
        dw = d.textlength(cfg["title"], font=font(74, "bold")) + 2 * len(cfg["title"])
        d.line([(138, H * 0.402), (138 + dw * ease_out(p), H * 0.402)],
               fill=(AMBER if is_wip else BRAND_HI) + (int(200 * p),), width=3)

    # 描述
    p = seg(t, 0.28, 0.8)
    if p > 0:
        for i, ln in enumerate(wrap_text(d, cfg["desc"], font(26, "reg"), 700)):
            d.text((138, H * 0.442 + i * 44), ln, font=font(26, "reg"),
                   fill=TXT_DIM + (int(230 * p),), anchor="ls")

    # 要点
    for i, b in enumerate(cfg["bullets"]):
        p = seg(t, 0.60 + i * 0.13, 0.65)
        if p <= 0:
            continue
        a = int(255 * p)
        yy = H * 0.605 + i * 60 + 18 * (1 - ease_out(p))
        d = ImageDraw.Draw(img)
        col = AMBER_DIM if is_wip else BRAND_HI
        if is_wip:
            d.ellipse([138, yy - 15, 152, yy - 1], outline=col + (a,), width=2)
        else:
            d.rounded_rectangle([138, yy - 15, 148, yy - 5], radius=5, fill=col + (a,))
        d.text((172, yy), b, font=font(24, "reg"), fill=TXT + (int(240 * p),), anchor="ls")

    appear = ease_out(seg(t, 0.22, 0.95), 3)
    if cfg.get("special") == "morse":
        img = draw_morse_panel(img, cfg, t, appear)
    elif is_wip:
        img = draw_plan_panel(img, cfg, t, appear)
    else:
        img = draw_data_panel(img, cfg, t, appear)

    fade = 1 - 0.45 * seg(t, TOOL_DUR - 0.45, 0.45)
    if fade < 1:
        img = Image.blend(Image.new("RGBA", img.size, (0, 0, 0, 255)), img, clamp(fade))
    return img.convert("RGB")


# ---------------------------------------------------------------- 特性

FEATS = [
    ("palette", "深色 Material 3", "9 套主题配色，跟随壁纸取色"),
    ("offline", "离线优先", "核心功能断网可用，联网按需开启"),
    ("clean", "无广告无跟踪", "不收集任何使用数据"),
    ("code", "开源 MIT", "代码全部公开，可自行编译"),
]


def scene_features(t):
    img = BG.copy().convert("RGBA")
    d = ImageDraw.Draw(img)
    img = glow(img, W / 2, H * 0.42, 460, BRAND, alpha=36, softness=0.5)
    d = ImageDraw.Draw(img)
    cx = W / 2

    p = seg(t, 0.1, 0.8)
    if p > 0:
        tracked_text(d, (cx, H * 0.215), "除了算得准，还做了这些", font(26, "light"),
                     TXT_FAINT + (int(210 * p),), tracking=6, anchor="c")

    bw, bh, gap = 400, 320, 28
    total_w = bw * 4 + gap * 3
    bx0, by = (W - total_w) / 2, H * 0.325
    for i, (kind, title, sub) in enumerate(FEATS):
        p = seg(t, 0.45 + i * 0.18, 0.95)
        if p <= 0:
            continue
        a = int(255 * p)
        x = bx0 + i * (bw + gap)
        y = by + 40 * (1 - ease_out(p))
        img.alpha_composite(panel((bw, bh), 32, CARD + (a,), outline=LINE + (int(a * .85),)),
                            (int(x), int(y)))
        dd = ImageDraw.Draw(img)
        dd.ellipse([x + bw / 2 - 44, y + 56, x + bw / 2 + 44, y + 144],
                   fill=pre(BRAND_HI, 26 * p, CARD))
        if kind == "palette":
            for k, col in enumerate([BRAND_SOFT, (255, 214, 138), (198, 176, 255)]):
                dd.ellipse([x + bw / 2 - 26 + k * 18, y + 84, x + bw / 2 - 10 + k * 18, y + 100],
                           fill=col + (a,))
        elif kind == "offline":
            dd.line([(x + bw / 2 - 26, y + 100), (x + bw / 2 - 4, y + 122),
                     (x + bw / 2 + 28, y + 78)], fill=BRAND_HI + (a,), width=6, joint="curve")
        elif kind == "clean":
            dd.ellipse([x + bw / 2 - 24, y + 76, x + bw / 2 + 24, y + 124],
                       outline=BRAND_HI + (a,), width=5)
            dd.line([(x + bw / 2 - 36, y + 64), (x + bw / 2 + 36, y + 136)],
                    fill=BRAND_HI + (a,), width=5)
        else:
            dd.line([(x + bw / 2 - 26, y + 90), (x + bw / 2 - 6, y + 76),
                     (x + bw / 2 - 6, y + 124), (x + bw / 2 + 18, y + 110)],
                    fill=BRAND_HI + (a,), width=5, joint="curve")
        dd.text((x + bw / 2, y + 202), title, font=font(30, "bold"), fill=TXT + (a,), anchor="ms")
        for j, ln in enumerate(wrap_text(dd, sub, font(20, "reg"), bw - 70)):
            dd.text((x + bw / 2, y + 246 + j * 30), ln, font=font(20, "reg"),
                    fill=TXT_DIM + (int(a * 0.95),), anchor="ms")

    p = seg(t, 1.5, 0.8)
    if p > 0:
        tracked_text(d, (cx, H * 0.775), "没有一个按钮会问你要权限", font(24, "light"),
                     TXT_FAINT + (int(200 * p),), tracking=4, anchor="c")

    return img.convert("RGB")


# ---------------------------------------------------------------- 路线图

ROADMAP = [
    ("音频工具", "1.2 版本", [
        ("CW 电键", "触屏双桨与直键，侧音实时可调"),
        ("CW 听抄训练", "科赫法渐进字符集，正确率曲线"),
        ("DTMF", "标准双音生成，麦克风实时解码"),
        ("音调发生器", "20 Hz – 20 kHz，支持扫频"),
    ]),
    ("联网工具", "1.3 版本", [
        ("传播指数", "SFI / K / A 与逐波段开通概率"),
        ("DX 集群", "实时 spots，按波段与模式过滤"),
        ("网格与方位", "梅登海德网格与大圆方位"),
        ("APRS 地图", "离线底图上的台站与轨迹"),
        ("呼号查询", "执照信息与历史呼号变更"),
    ]),
]


def scene_roadmap(t):
    img = BG.copy().convert("RGBA")
    d = ImageDraw.Draw(img)
    img = glow(img, W * 0.5, H * 0.5, 500, (120, 88, 30), alpha=26, softness=0.55)
    d = ImageDraw.Draw(img)

    p = seg(t, 0.1, 0.8)
    if p > 0:
        d.text((140, H * 0.225 + 20 * (1 - ease_out(p))), "还没做完的部分", font=font(30, "light"),
               fill=AMBER + (int(230 * p),), anchor="ls")
        tracked_text(d, (140, H * 0.305 + 24 * (1 - ease_out(p))), "9 个工具在路上",
                     font(58, "bold"), TXT + (int(255 * p),), tracking=1)

    p = seg(t, 0.4, 0.8)
    if p > 0:
        body = "音频与联网两个板块尚未实现。下面是把它们放进版本计划的样子 —— 没有画饼，只是把待办写清楚。"
        for i, ln in enumerate(wrap_text(d, body, font(24, "reg"), 1560)):
            d.text((140, H * 0.428 + i * 40), ln, font=font(24, "reg"),
                   fill=TXT_DIM + (int(225 * p),), anchor="ls")

    colx = [140, 1010]
    for ci, (cat, ver, items) in enumerate(ROADMAP):
        p = seg(t, 0.7 + ci * 0.15, 0.8)
        if p <= 0:
            continue
        a = int(255 * p)
        x = colx[ci]
        y = H * 0.475
        d = ImageDraw.Draw(img)
        d.text((x, y), cat, font=font(27, "bold"), fill=AMBER + (a,), anchor="ls")
        vt = ver
        vw = d.textlength(vt, font=font(19, "reg"))
        d.rounded_rectangle([x + 146, y - 24, x + 146 + vw + 28, y + 6], radius=15,
                            fill=pre(AMBER, 22 * p, CARD), outline=AMBER_DIM + (int(140 * p),), width=1)
        d.text((x + 146 + vw / 2 + 14, y - 9), vt, font=font(19, "reg"),
               fill=AMBER_DIM + (int(230 * p),), anchor="mm")
        d.line([(x, y + 22), (x + 760, y + 22)], fill=(62, 56, 44, int(170 * p)), width=1)

        for i, (nm, ds) in enumerate(items):
            p2 = seg(t, 0.9 + ci * 0.15 + i * 0.09, 0.5)
            if p2 <= 0:
                continue
            a2 = int(255 * p2)
            yy = y + 68 + i * 74 + 14 * (1 - ease_out(p2))
            d2 = ImageDraw.Draw(img)
            d2.ellipse([x, yy - 16, x + 13, yy - 3], outline=AMBER_DIM + (a2,), width=2)
            d2.text((x + 32, yy), nm, font=font(26, "bold"), fill=TXT + (a2,), anchor="ls")
            nw = d2.textlength(nm, font=font(26, "bold"))
            d2.text((x + 32 + nw + 20, yy), ds, font=font(20, "reg"),
                    fill=TXT_FAINT + (a2,), anchor="ls")

    p = seg(t, 2.4, 0.8)
    if p > 0:
        tracked_text(d, (W / 2, H * 0.895), "已实现的 10 个随时可用，不必等",
                     font(22, "light"), TXT_FAINT + (int(190 * p),), tracking=4, anchor="c")

    return img.convert("RGB")


# ---------------------------------------------------------------- 片尾


def scene_outro(t):
    img = BG.copy().convert("RGBA")
    cx = W / 2
    cy = H * 0.355
    img = glow(img, cx, cy, 340, BRAND, alpha=46, softness=0.46)
    img = glow(img, cx, cy, 200, BRAND_HI, alpha=20, softness=0.55)

    p = seg(t, 0.05, 0.8)
    if p > 0:
        sc = 0.82 + 0.18 * ease_out_back(p, 1.1)
        wave_logo(img, cx, cy, 176 * sc, color=(255, 255, 255, int(255 * p)),
                  bg=BRAND + (int(250 * p),))

    p = seg(t, 0.35, 0.8)
    if p > 0:
        tracked_text(ImageDraw.Draw(img), (cx, cy + 205), "HAM 工具合集",
                     font(72, "bold"), TXT + (int(255 * p),), tracking=6,
                     anchor="c", valign="m")

    p = seg(t, 0.75, 0.8)
    if p > 0:
        tracked_text(ImageDraw.Draw(img), (cx, cy + 295), "github.com/BA4IGG/hamtools",
                     font(34, "reg"), BRAND_SOFT + (int(240 * p),), tracking=1,
                     anchor="c", valign="m")

    p = seg(t, 1.15, 0.8)
    if p > 0:
        a = int(255 * p)
        d = ImageDraw.Draw(img)
        txt = "v1.1-beta · MIT 开源 · 作者 BA4IGG"
        tw = d.textlength(txt, font=font(23, "reg")) + 2 * len(txt)
        pw = tw + 76
        img.alpha_composite(panel((int(pw), 58), 29, pre(CARD, 240 * p, BG_DARK),
                                  outline=LINE + (int(220 * p),)), (int(cx - pw / 2), int(cy + 350)))
        tracked_text(ImageDraw.Draw(img), (cx, cy + 379), txt, font(23, "reg"),
                     TXT_DIM + (a,), tracking=2, anchor="c", valign="m")

    p = seg(t, 1.9, 0.9)
    if p > 0:
        tracked_text(ImageDraw.Draw(img), (cx, cy + 470),
                     "核心离线可用 · 联网能力持续扩展",
                     font(22, "light"), TXT_FAINT + (int(190 * p),), tracking=3,
                     anchor="c", valign="m")

    fade = 1 - seg(t, OUTRO_DUR - 0.9, 0.85)
    if fade < 1:
        img = Image.blend(Image.new("RGBA", img.size, (0, 0, 0, 255)), img, clamp(fade))
    return img.convert("RGB")


# ---------------------------------------------------------------- 快切回顾

def scene_recap(t):
    """19 个工具逐个快切：每页约 1.5 秒，避免长时间停在同一个画面。"""
    img = BG.copy().convert("RGBA")
    cx = W / 2
    head, tail = 0.45, 0.55
    page = (RECAP_DUR - head - tail) / len(TOOLS)
    i = min(len(TOOLS) - 1, max(0, int((t - head) / page)))
    cfg = TOOLS[i]
    local = t - head - i * page
    done = cfg["status"] == "done"
    acc = BRAND_HI if done else AMBER
    img = glow(img, cx, H * 0.48, 460, BRAND if done else (140, 96, 30),
               alpha=30, softness=0.55)
    d = ImageDraw.Draw(img)

    tracked_text(d, (cx, 150), "19 个工具 · 快速回顾", font(42, "bold"),
                 TXT + (255,), tracking=4, anchor="c", valign="m")
    tracked_text(d, (cx, 216), "已实现的核心离线可用 · 联网能力规划中", font(22, "light"),
                 TXT_FAINT + (200,), tracking=3, anchor="c", valign="m")

    p = ease_out(seg(local, 0.0, 0.38))
    a = int(255 * p)
    tracked_text(d, (cx, 330 + 26 * (1 - p)), f'{cfg["idx"]} / 19', font(30, "bold"),
                 acc + (a,), tracking=8, anchor="c", valign="m")
    tracked_text(d, (cx, 470 + 34 * (1 - p)), cfg["title"], font(96, "bold"),
                 TXT + (a,), tracking=4, anchor="c", valign="m")
    if done:
        state = "已实现 · 数据内置"
    elif "需要联网" in cfg.get("note", ""):
        state = "开发中 · 需要联网"
    else:
        state = "开发中 · 本地功能"
    tracked_text(d, (cx, 596 + 20 * (1 - p)), f'{cfg["cat"]}  ·  {state}', font(30, "reg"),
                 TXT_DIM + (a,), tracking=2, anchor="c", valign="m")

    seg_w, gap = 58, 12
    total_w = len(TOOLS) * seg_w + (len(TOOLS) - 1) * gap
    x0 = (W - total_w) / 2
    for k, tool in enumerate(TOOLS):
        x = x0 + k * (seg_w + gap)
        col = BRAND_HI if tool["status"] == "done" else AMBER_DIM
        if k == i:
            d.rounded_rectangle([x, 754, x + seg_w, 766], radius=6, fill=acc + (255,))
        else:
            d.rounded_rectangle([x, 757, x + seg_w, 763], radius=3,
                                fill=pre(col, 120, BG_DARK))

    tracked_text(d, (cx, 860), "离线核心 + 联网扩展", font(22, "light"),
                 TXT_FAINT + (180,), tracking=4, anchor="c", valign="m")

    fade = 1 - seg(t, RECAP_DUR - 0.55, 0.55)
    if fade < 1:
        img = Image.blend(Image.new("RGBA", img.size, (0, 0, 0, 255)), img, clamp(fade))
    return img.convert("RGB")


# ---------------------------------------------------------------- 时间轴

TOOL_DUR = 5.0
CHAPTER_DUR = 2.4
OUTRO_DUR = 9.0
INTRO_DUR = 6.0
PAIN_DUR = 6.0
OVERVIEW_DUR = 8.5
FEAT_DUR = 8.0
ROADMAP_DUR = 9.0
RECAP_DUR = 30.0        # 19 个工具快切回顾，每页约 1.5 秒

SCENES = []


def _add_scene(dur, fn, label=None):
    start = SCENES[-1][1] if SCENES else 0.0
    SCENES.append((start, start + dur, fn))
    if label:
        SECTION_MARKS.append((start, label))
    return start


_add_scene(INTRO_DUR, scene_intro, "开场")
_add_scene(PAIN_DUR, scene_pain, "缘起")
_add_scene(OVERVIEW_DUR, scene_overview, "总览")

_GROUPS = [
    ("01", "计算工具", CALC),
    ("02", "查询手册", REF),
    ("03", "音频工具", AUDIO),
    ("04", "联网工具", NET),
]

for _num, _name, _cat in _GROUPS:
    _tools = [t for t in TOOLS if t["cat"] == _cat]
    _done = sum(1 for t in _tools if t["status"] == "done")
    _sub = (f"{len(_tools)} 个工具 · 全部已实现" if _done == len(_tools)
            else f"{len(_tools)} 个工具 · 尚未实现")
    _cfg = dict(idx=_num, name=_name, sub=_sub, done=_done,
                items=[t["title"] for t in _tools])
    _add_scene(CHAPTER_DUR, (lambda t, c=_cfg: scene_chapter(t, c)), _name)

    for _tool in _tools:
        _st = _add_scene(TOOL_DUR, (lambda t, c=_tool: scene_tool(t, c)), _tool["title"])
        TOOL_SPANS[_tool["idx"]] = (_st, _st + TOOL_DUR)

_add_scene(FEAT_DUR, scene_features, "特性")
_add_scene(ROADMAP_DUR, scene_roadmap, "路线图")
_add_scene(RECAP_DUR, scene_recap, "速览")
_add_scene(OUTRO_DUR, scene_outro, "结尾")

DUR = SCENES[-1][1]
XFADE = 0.35
HUD_IN = 4.6


def render_at(t):
    frame = SCENES[-1][2](0.0)
    for i, (s, e, fn) in enumerate(SCENES):
        if s <= t < e or (i == len(SCENES) - 1 and t >= s):
            if t > e - XFADE and i + 1 < len(SCENES):
                w = ease_in_out(clamp((t - (e - XFADE)) / XFADE))
                frame = Image.blend(fn(t - s), SCENES[i + 1][2](max(0.0, t - e)), w)
            else:
                frame = fn(min(t - s, e - s))
            break

    img = frame.convert("RGBA")
    hud_a = clamp((t - HUD_IN) / 0.7) * clamp((DUR - 1.2 - t) / 1.2)
    draw_hud(img, t, hud_a)
    return img.convert("RGB")


# ---------------------------------------------------------------- 配乐：120 BPM 科技电子

SR = 44100
BPM = 120
BEAT = 60.0 / BPM

# 和弦进行：Fm – Db – Ab – Eb，每 6 秒一个（= 3 小节）
PROG = {
    "Fm": [87.31, 174.61, 207.65, 261.63, 311.13],   # F2 F3 Ab3 C4 Eb4
    "Db": [69.30, 174.61, 207.65, 261.63, 349.23],   # Db2 F3 Ab3 C4 F4
    "Ab": [103.83, 155.56, 207.65, 261.63, 415.30],  # Ab2 Eb3 Ab3 C4 Ab4
    "Eb": [77.78, 155.56, 233.08, 293.66, 349.23],   # Eb2 Eb3 Bb3 D4 F4
}
ORDER = ["Fm", "Db", "Ab", "Eb"]


def chord_at(t):
    return ORDER[int(max(0.0, t) // 6.0) % 4]


def _section_start(label, default=0.0):
    for s, nm in SECTION_MARKS:
        if nm == label:
            return s
    return default


def density(t):
    """按分镜决定各层是否发声，避免三分钟一个力度到底。"""
    t_band = _section_start("计算工具")
    t_feat = _section_start("特性")
    t_road = _section_start("路线图")
    t_outro = _section_start("结尾")
    if t < INTRO_DUR:
        return dict(kick=False, clap=False, hat=False, bass=False, arp=True, pad=True)
    if t < INTRO_DUR + PAIN_DUR:
        return dict(kick=False, clap=False, hat=False, bass=False, arp=False, pad=True)
    if t < t_band:
        return dict(kick=True, clap=False, hat=True, bass=True, arp=True, pad=True)
    if t < t_feat:
        return dict(kick=True, clap=True, hat=True, bass=True, arp=True, pad=True)
    if t < t_road:
        return dict(kick=False, clap=False, hat=True, bass=False, arp=True, pad=True)
    if t < t_outro:
        return dict(kick=True, clap=True, hat=True, bass=True, arp=True, pad=True)
    return dict(kick=False, clap=False, hat=False, bass=False, arp=False, pad=True)


def build_audio(path):
    n = int(DUR * SR)
    tt = np.arange(n) / SR
    out = np.zeros((n, 2), np.float32)
    rng = np.random.default_rng(20261105)

    def add(buf, i0, gain=1.0, pan=0.0):
        i0 = int(i0)
        i1 = min(n, i0 + len(buf))
        if i1 <= i0 or i0 < 0:
            return
        b = buf[: i1 - i0]
        out[i0:i1, 0] += (b * gain * (1 - max(0.0, pan) * 0.55)).astype(np.float32)
        out[i0:i1, 1] += (b * gain * (1 - max(0.0, -pan) * 0.55)).astype(np.float32)

    def env_ar(ln, atk, dec):
        lt = np.arange(ln) / SR
        return np.exp(-lt * dec) * (1 - np.exp(-lt * atk))

    # ---- Pad：每 6 秒换一次和弦的长音，作为和声底
    seg_t, k = 0.0, 0
    while seg_t < DUR:
        e = min(DUR, seg_t + 6.0)
        i0, i1 = int(seg_t * SR), int(e * SR)
        if i1 > i0:
            freqs = PROG[chord_at(seg_t)]
            lt = np.arange(i1 - i0) / SR
            buf = np.zeros(i1 - i0, np.float32)
            for j, f in enumerate(freqs):
                amp = 0.15 / (1 + j * 0.5)
                for det, da in ((1.0, 1.0), (1.0025, 0.45), (0.9975, 0.45)):
                    buf += (amp * da * np.sin(2 * np.pi * f * det * lt + j * 0.9)).astype(np.float32)
            ln = len(buf)
            aa, rr = int(1.4 * SR), int(1.6 * SR)
            if ln > aa + rr:
                buf[:aa] *= np.linspace(0, 1, aa) ** 1.3
                buf[-rr:] *= np.linspace(1, 0, rr) ** 1.3
            add(buf, i0, gain=0.30)
        seg_t = e
        k += 1

    # ---- 底鼓：每拍一次，正弦扫频 120 → 46 Hz
    tcur = 0.0
    ln_k = int(0.30 * SR)
    lt_k = np.arange(ln_k) / SR
    f_k = 120 * np.exp(-lt_k * 26) + 46
    ph_k = 2 * np.pi * np.cumsum(f_k) / SR
    kick = (np.sin(ph_k) * env_ar(ln_k, 400, 9)).astype(np.float32)
    while tcur < DUR - 0.5:
        if density(tcur)["kick"]:
            add(kick, tcur * SR, gain=0.62)
        tcur += BEAT

    # ---- 拍手：2、4 拍
    ln_c = int(0.20 * SR)
    nz = rng.normal(0, 1, ln_c)
    hp = np.diff(nz, prepend=0)
    ker = np.hanning(11)
    ker /= ker.sum()
    clap = (np.convolve(hp, ker, mode="same") * env_ar(ln_c, 260, 20)).astype(np.float32)
    clap /= np.max(np.abs(clap)) + 1e-9
    tcur = 0.0
    while tcur < DUR - 0.5:
        frac = (tcur / BEAT) % 4
        if density(tcur)["clap"] and (abs(frac - 1) < 0.01 or abs(frac - 3) < 0.01):
            add(clap, tcur * SR, gain=0.20, pan=0.06)
        tcur += BEAT

    # ---- 闭镲：每八分音符，反拍略重
    ln_h = int(0.06 * SR)
    nz_h = rng.normal(0, 1, ln_h)
    hat = (np.diff(nz_h, prepend=0) * env_ar(ln_h, 900, 65)).astype(np.float32)
    hat /= np.max(np.abs(hat)) + 1e-9
    tcur = 0.0
    i8 = 0
    while tcur < DUR - 0.5:
        if density(tcur)["hat"]:
            g = 0.075 if i8 % 2 else 0.045
            add(hat, tcur * SR, gain=g, pan=-0.18 if i8 % 2 else 0.12)
        tcur += BEAT / 2
        i8 += 1

    # ---- 贝斯：八分音符，根音与高八度交替
    ln_b = int(0.24 * SR)
    lt_b = np.arange(ln_b) / SR
    tcur = 0.0
    i8 = 0
    while tcur < DUR - 0.5:
        if density(tcur)["bass"]:
            root = PROG[chord_at(tcur)][0]
            f = root if i8 % 4 < 2 else root * 2
            wave = np.sin(2 * np.pi * f * lt_b) + 0.24 * np.sin(4 * np.pi * f * lt_b)
            buf = (np.tanh(wave * 1.7) * env_ar(ln_b, 500, 10)).astype(np.float32)
            add(buf, tcur * SR, gain=0.26)
        tcur += BEAT / 2
        i8 += 1

    # ---- 琶音：十六分音符，和弦音循环，声像左右摆
    ln_a = int(0.20 * SR)
    lt_a = np.arange(ln_a) / SR
    tcur = 0.0
    kk = 0
    while tcur < DUR - 0.5:
        if density(tcur)["arp"]:
            tones = PROG[chord_at(tcur)][1:]
            f = tones[(kk * 2) % len(tones)] * 2
            wave = np.sin(2 * np.pi * f * lt_a) - 0.16 * np.sin(6 * np.pi * f * lt_a)
            buf = (wave * env_ar(ln_a, 700, 20)).astype(np.float32)
            add(buf, tcur * SR, gain=0.072, pan=0.30 * math.sin(kk * 1.3))
        tcur += BEAT / 4
        kk += 1

    # ---- 章节切换：riser + 低频冲击
    switches = [s for s, _, _ in SCENES[1:]]
    for ts in switches:
        i0 = int((ts - 0.55) * SR)
        if i0 >= 0:
            ln_r = int(0.6 * SR)
            lt_r = np.arange(ln_r) / SR
            nzr = rng.normal(0, 1, ln_r)
            nzr = np.diff(nzr, prepend=0)
            sweep = np.sin(2 * np.pi * (300 * lt_r + 900 * lt_r ** 2) * 1.0)
            r = (nzr * 0.5 + sweep * 0.5) * (lt_r / 0.6) ** 2.2
            add(r, i0, gain=0.075)
        # 冲击
        ln_i = int(0.5 * SR)
        lt_i = np.arange(ln_i) / SR
        imp = np.sin(2 * np.pi * 58 * lt_i) * np.exp(-lt_i * 9)
        add(imp, ts * SR, gain=0.16)

    # ---- 面板数字落定 / 卡片出现的 tick
    for idx, (s, e) in TOOL_SPANS.items():
        for off in (0.55, 2.15):
            i0 = int((s + off) * SR)
            if i0 < n:
                lt_t = np.arange(int(0.10 * SR)) / SR
                buf = np.sin(2 * np.pi * 1180 * lt_t) * np.exp(-lt_t * 55)
                add(buf, i0, gain=0.040)
    # ---- 摩尔斯页：真实的 800 Hz 电码音
    if "09" in TOOL_SPANS:
        base = TOOL_SPANS["09"][0] + 1.15
        beat_m = 0.30
        idx = 0
        for ch, code in MORSE_CQ:
            for sym in code:
                ln_m = int((3 if sym == "-" else 1) * beat_m * 0.55 * SR)
                lt_m = np.arange(ln_m) / SR
                e_m = np.minimum(1, np.minimum(lt_m / 0.008, (lt_m[-1] - lt_m) / 0.008))
                buf = np.sin(2 * np.pi * 800 * lt_m) * np.clip(e_m, 0, 1)
                add(buf, (base + idx * beat_m) * SR, gain=0.10)
                idx += 1

    # ---- 片尾的提示音
    i0 = int((DUR - 3.4) * SR)
    ln_d = min(n - i0, int(3.2 * SR))
    if ln_d > 0:
        lt_d = np.arange(ln_d) / SR
        tone = (np.sin(2 * np.pi * 880 * lt_d) * 0.5
                + np.sin(2 * np.pi * 1320 * lt_d) * 0.26
                + np.sin(2 * np.pi * 1760 * lt_d) * 0.11)
        add(tone * np.exp(-lt_d * 1.9) * (1 - np.exp(-lt_d * 80)), i0, gain=0.12)

    # ---- 整体呼吸 + 轻混响
    out *= (0.90 + 0.10 * np.sin(2 * np.pi * 0.06 * tt))[:, None].astype(np.float32)
    wet = out.copy()
    dly = int(0.19 * SR)
    for k in (1, 2, 3):
        if dly * k < n:
            wet[dly * k:] += out[: n - dly * k] * (0.22 ** k)
    out = out * 0.78 + wet * 0.22

    # ---- 淡入淡出 + 软限幅
    fade_in = int(0.4 * SR)
    out[:fade_in] *= np.linspace(0, 1, fade_in)[:, None]
    fade_out = int(1.6 * SR)
    out[-fade_out:] *= np.linspace(1, 0, fade_out)[:, None]
    out = np.tanh(out * 1.25) * 0.76

    peak = float(np.max(np.abs(out)))
    if peak > 0:
        out = out / peak * 0.80
    pcm = (np.clip(out, -1, 1) * 32767).astype(np.int16)

    import wave
    with wave.open(path, "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


# ---------------------------------------------------------------- 主流程

def main():
    global BG
    mode = sys.argv[1] if len(sys.argv) > 1 else "preview"
    print(f"时长 {DUR:.1f}s / {int(DUR // 60)}:{int(DUR % 60):02d}，{len(SCENES)} 个分镜")
    print("[1/4] 构建背景…")
    BG = build_bg()

    if mode == "preview":
        shots = [round(s + (e - s) * 0.80, 1) for s, e, _ in SCENES]
        shots += [5.2, round(DUR - 2.0, 1)]
        os.makedirs(os.path.join(HERE, "preview_3m"), exist_ok=True)
        for t in shots:
            render_at(t).save(os.path.join(HERE, "preview_3m", f"f{t:06.1f}.png"))
            print(f"  {t:>6.1f}s")
        print("→ promo/preview_3m/")
        return

    from imageio_ffmpeg import get_ffmpeg_exe
    ffmpeg = get_ffmpeg_exe()

    print("[2/4] 生成音频…")
    audio = os.path.join(HERE, "audio_3m.wav")
    build_audio(audio)

    print("[3/4] 渲染帧…")
    out = os.path.join(HERE, "HAM-Tools-promo-3min.mp4")
    cmd = [ffmpeg, "-y", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}",
           "-r", str(FPS), "-i", "-", "-i", audio,
           "-c:v", "libx264", "-preset", "medium", "-crf", "19", "-pix_fmt", "yuv420p",
           "-profile:v", "high", "-level", "4.2", "-c:a", "aac", "-b:a", "192k",
           "-movflags", "+faststart", "-shortest", out]
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=subprocess.DEVNULL,
                            stderr=subprocess.PIPE)
    total = int(DUR * FPS)
    for i in range(total):
        proc.stdin.write(render_at(i / FPS).tobytes())
        if i % 300 == 0:
            print(f"  {i}/{total} ({i / total * 100:.1f}%)", flush=True)
    proc.stdin.close()
    err = proc.stderr.read().decode("utf-8", "replace")
    if proc.wait() != 0:
        print(err[-2500:])
        raise SystemExit("ffmpeg 失败")
    print(f"[4/4] 完成：{out} ({os.path.getsize(out) / 1024 / 1024:.2f} MB)")


if __name__ == "__main__":
    main()




