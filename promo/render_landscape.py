# -*- coding: utf-8 -*-
"""
HAM 工具合集 —— 横屏宣传片（2 分钟版）

1920×1080 / 30fps / 120s，15 个分镜，逐个介绍 10 个已实现的工具。
界面色值与图标取自 App 真实资源（品牌色 #006C5B、波瓣半径 32/23/14、±50°、笔宽 5.5），
数值全部按 App 内部公式实算，不是编的。

用法：
    python render_landscape.py preview   # 关键帧 PNG
    python render_landscape.py full      # 整片
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
DUR = 120.0
HERE = os.path.dirname(os.path.abspath(__file__))

BRAND = (0, 108, 91)
BRAND_HI = (42, 217, 176)
BRAND_SOFT = (124, 245, 218)
CARD = (18, 26, 24)
LINE = (44, 58, 54)
TXT = (233, 242, 239)
TXT_DIM = (140, 159, 153)
TXT_FAINT = (92, 108, 104)

FONT_BOLD = "C:/Windows/Fonts/msyhbd.ttc"
FONT_REG = "C:/Windows/Fonts/msyh.ttc"
FONT_LIGHT = "C:/Windows/Fonts/msyhl.ttc"


@lru_cache(maxsize=96)
def font(size, weight="bold"):
    return ImageFont.truetype({"bold": FONT_BOLD, "reg": FONT_REG, "light": FONT_LIGHT}[weight],
                              size, index=0)


# ---------------------------------------------------------------- 缓动


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
    return tuple(int(round(lerp(c1[i], c2[i], t))) for i in range(3))


# ---------------------------------------------------------------- 文本与形状


def tracked_text(d, xy, s, fnt, fill, tracking=0.0, anchor="l"):
    widths = [d.textlength(ch, font=fnt) for ch in s]
    total = sum(widths) + tracking * max(0, len(s) - 1)
    x = xy[0]
    if anchor == "c":
        x -= total / 2.0
    elif anchor == "r":
        x -= total
    for ch, w in zip(s, widths):
        d.text((x, xy[1]), ch, font=fnt, fill=fill, anchor="ls")
        x += w + tracking
    return total


def wrap_text(d, s, fnt, max_w):
    """按像素宽度折行，中文按字符切。"""
    lines, cur = [], ""
    for ch in s:
        if d.textlength(cur + ch, font=fnt) > max_w and cur:
            lines.append(cur)
            cur = ch
        else:
            cur += ch
    if cur:
        lines.append(cur)
    return lines


def rgba(c, a=255):
    c = tuple(c)
    return c if len(c) == 4 else c + (int(clamp(a, 0, 255)),)


def panel(size, radius, fill, outline=None, width=1):
    w, h = size
    lay = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(lay)
    d.rounded_rectangle([0, 0, w - 1, h - 1], radius=radius, fill=rgba(fill),
                        outline=rgba(outline) if outline else None, width=width)
    return lay


def glow(img, cx, cy, r, color, alpha=150, softness=0.35):
    """径向平方衰减做光晕；高斯模糊在画布边缘收敛不到 0，会露出方块。"""
    size = int(2 * r)
    if size < 8 or alpha <= 0:
        return img
    yy, xx = np.mgrid[0:size, 0:size].astype(np.float32)
    d = np.sqrt((xx - r) ** 2 + (yy - r) ** 2) / r
    a = (np.clip(1.0 - d, 0.0, 1.0) ** (1.6 / max(0.12, softness)) * alpha).astype(np.uint8)
    lay = Image.fromarray(a, "L")
    img.paste(Image.new("RGB", lay.size, color), (int(cx - r), int(cy - r)), lay)
    return img


def build_bg():
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    cx, cy = W * 0.62, H * 0.40
    d = np.sqrt(((xx - cx) / (W * 0.95)) ** 2 + ((yy - cy) / (H * 1.10)) ** 2)
    v = np.clip(1.0 - d, 0, 1) ** 2.4
    arr = np.array([5, 9, 9], np.float32) + v[..., None] * (
        np.array([14, 32, 29], np.float32) - np.array([5, 9, 9], np.float32))
    dv = np.sqrt(((xx - W / 2) / (W * 0.85)) ** 2 + ((yy - H / 2) / (H * 0.85)) ** 2)
    arr *= np.clip(1.0 - 0.55 * np.clip(dv - 0.42, 0, None) ** 1.5, 0, 1)[..., None]
    rng = np.random.default_rng(21)
    arr += rng.normal(0, 1.8, (H, W, 1)).astype(np.float32)
    img = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGB")

    grid = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    gd = ImageDraw.Draw(grid)
    for x in range(-1, W + 1, 120):
        gd.line([(x, 0), (x, H)], fill=(120, 190, 175, 7))
    for y in range(-1, H + 1, 120):
        gd.line([(0, y), (W, y)], fill=(120, 190, 175, 7))
    return Image.alpha_composite(img.convert("RGBA"), grid).convert("RGB")


BG = None


# ---------------------------------------------------------------- 图标


def wave_logo(img, cx, cy, dia, color=(255, 255, 255), bg=None):
    if dia < 6:
        return img
    r = dia / 2.0
    pad = int(dia * 0.12) + 6
    lay = Image.new("RGBA", (int(dia) + pad * 2, int(dia) + pad * 2), (0, 0, 0, 0))
    d = ImageDraw.Draw(lay)
    s, c = dia / 108.0, (int(dia) + pad * 2) / 2.0
    if bg is not None:
        d.ellipse([c - r, c - r, c + r, c + r], fill=rgba(bg))
    fg, w = rgba(color), max(2, int(round(5.5 * s)))
    for rad in (32, 23, 14):
        rr = rad * s
        d.arc([c - rr, c - rr, c + rr, c + rr], -50, 50, fill=fg, width=w)
        d.arc([c - rr, c - rr, c + rr, c + rr], 130, 230, fill=fg, width=w)
    d.ellipse([c - 5 * s, c - 5 * s, c + 5 * s, c + 5 * s], fill=fg)
    for rad in (32, 23, 14):
        rr = rad * s
        for ang in (-50, 50, 130, 230):
            a = math.radians(ang)
            px, py = c + rr * math.cos(a), c + rr * math.sin(a)
            d.ellipse([px - w / 2, py - w / 2, px + w / 2, py + w / 2], fill=fg)
    img.alpha_composite(lay, (int(cx - lay.size[0] / 2), int(cy - lay.size[1] / 2)))
    return img


def glyph(d, cx, cy, size, kind, color):
    w = max(2, int(round(size * 0.09)))
    r = size / 2.0
    if kind == "wave":
        d.line([(cx - r + 2 * r * i / 24.0, cy - r * 0.55 * math.sin(i / 24.0 * math.pi * 2.2))
                for i in range(25)], fill=color, width=w, joint="curve")
    elif kind == "antenna":
        d.line([(cx, cy - r * .85), (cx, cy + r * .75)], fill=color, width=w)
        d.line([(cx - r * .62, cy + r * .8), (cx + r * .62, cy + r * .8)], fill=color, width=w)
        d.line([(cx - r * .42, cy - r * .55), (cx, cy - r * .05)], fill=color, width=w)
        d.line([(cx + r * .42, cy - r * .55), (cx, cy - r * .05)], fill=color, width=w)
    elif kind == "vswr":
        d.arc([cx - r * .85, cy - r * .85, cx + r * .85, cy + r * .85], 200, 340, fill=color, width=w)
        d.ellipse([cx - w, cy - w, cx + w, cy + w], fill=color)
        d.line([(cx, cy), (cx + r * .55, cy - r * .55)], fill=color, width=w)
    elif kind == "cable":
        d.line([(cx - r * .8, cy + r * .6), (cx + r * .1, cy - r * .3)], fill=color, width=w)
        d.ellipse([cx, cy - r * .55, cx + r * .55, cy], outline=color, width=w)
        d.line([(cx - r * .2, cy + r * .75), (cx + r * .7, cy + r * .75)], fill=color, width=w)
    elif kind == "link":
        d.ellipse([cx - r * .85, cy - r * .85, cx - r * .1, cy - r * .1], outline=color, width=w)
        d.ellipse([cx + r * .1, cy + r * .1, cx + r * .85, cy + r * .85], outline=color, width=w)
        d.line([(cx - r * .15, cy - r * .15), (cx + r * .15, cy + r * .15)], fill=color, width=w)
    elif kind == "band":
        for dy in (-.55, 0, .55):
            d.line([(cx - r * (.85 - abs(dy) * .35), cy + r * dy),
                    (cx + r * (.85 - abs(dy) * .35), cy + r * dy)], fill=color, width=w)
    elif kind == "globe":
        d.ellipse([cx - r * .82, cy - r * .82, cx + r * .82, cy + r * .82], outline=color, width=w)
        d.arc([cx - r * .32, cy - r * .82, cx + r * .32, cy + r * .82], 90, 270, fill=color, width=w)
        d.arc([cx - r * .32, cy - r * .82, cx + r * .32, cy + r * .82], -90, 90, fill=color, width=w)
        d.line([(cx - r * .82, cy), (cx + r * .82, cy)], fill=color, width=w)
    elif kind == "q":
        d.ellipse([cx - r * .72, cy - r * .78, cx + r * .62, cy + r * .56], outline=color, width=w)
        d.line([(cx + r * .2, cy + r * .2), (cx + r * .78, cy + r * .78)], fill=color, width=w)
    elif kind == "morse":
        d.ellipse([cx - r * .85, cy - w * 1.2, cx - r * .85 + w * 2.4, cy + w * 1.2], fill=color)
        d.line([(cx - r * .3, cy), (cx + r * .85, cy)], fill=color, width=w)
        d.ellipse([cx + r * .55, cy - w * 1.2, cx + r * .55 + w * 2.4, cy + w * 1.2], fill=color)
    elif kind == "sat":
        d.rounded_rectangle([cx - r * .3, cy - r * .3, cx + r * .3, cy + r * .3], radius=r * .12,
                            outline=color, width=w)
        d.line([(cx - r * .85, cy), (cx - r * .35, cy)], fill=color, width=w)
        d.line([(cx + r * .35, cy), (cx + r * .85, cy)], fill=color, width=w)
        d.arc([cx - r * .9, cy - r * .9, cx + r * .9, cy + r * .9], 200, 250, fill=color, width=w)
    elif kind == "calc":
        d.rounded_rectangle([cx - r * .62, cy - r * .82, cx + r * .62, cy + r * .82], radius=r * .18,
                            outline=color, width=w)
        d.line([(cx - r * .3, cy - r * .38), (cx + r * .3, cy - r * .38)], fill=color, width=w)
        for i in range(2):
            for j in range(2):
                d.ellipse([cx - r * .38 + j * r * .5 - w, cy + r * .05 + i * r * .42 - w,
                           cx - r * .38 + j * r * .5 + w, cy + r * .05 + i * r * .42 + w], fill=color)
    elif kind == "book":
        d.line([(cx - r * .8, cy - r * .6), (cx - r * .8, cy + r * .6)], fill=color, width=w)
        d.line([(cx + r * .8, cy - r * .6), (cx + r * .8, cy + r * .6)], fill=color, width=w)
        d.line([(cx, cy - r * .55), (cx, cy + r * .55)], fill=color, width=w)
        d.line([(cx - r * .8, cy - r * .6), (cx, cy - r * .45)], fill=color, width=w)
        d.line([(cx + r * .8, cy - r * .6), (cx, cy - r * .45)], fill=color, width=w)
    elif kind == "audio":
        for i, hh in enumerate((.35, .75, 1.0, .6, .85)):
            bx = cx - r * .78 + i * r * .39
            d.rounded_rectangle([bx, cy - r * hh * .8, bx + r * .2, cy + r * hh * .8],
                                radius=r * .1, fill=color)
    elif kind == "net":
        for i, (dx, dy) in enumerate(((.55, -.5), (0, .55), (-.55, -.5))):
            d.ellipse([cx + r * dx - w * 1.6, cy + r * dy - w * 1.6,
                       cx + r * dx + w * 1.6, cy + r * dy + w * 1.6], fill=color)
        d.line([(cx + r * .55, cy - r * .5), (cx, cy + r * .55)], fill=color, width=w)
        d.line([(cx - r * .55, cy - r * .5), (cx, cy + r * .55)], fill=color, width=w)
        d.line([(cx - r * .55, cy - r * .5), (cx + r * .55, cy - r * .5)], fill=color, width=w)


# ---------------------------------------------------------------- HUD

SECTIONS = [
    (0, "开场"), (8, "为什么"), (16, "总览"),
    (29, "频率波长"), (36, "天线长度"), (43, "驻波比回损"), (50, "馈线损耗"), (57, "链路预算"),
    (64, "频段划分"), (71, "呼号前缀"), (78, "Q 简语"), (85, "摩尔斯"), (92, "卫星与中继"),
    (99, "特性"), (109, "结尾"),
]


def section_name(t):
    name = SECTIONS[0][1]
    for start, nm in SECTIONS:
        if t >= start:
            name = nm
    return name


def draw_hud(img, t, alpha=1.0):
    if alpha <= 0.01:
        return img
    d = ImageDraw.Draw(img)

    def hc(c, al=None):
        """HUD 专用取色：把 alpha 预混进颜色里，绕开 PIL 半透明替换的问题。"""
        return mix((10, 15, 14), c, clamp(al if al is not None else alpha)) + (255,)

    wave_logo(img, 88, 62, 40, color=hc((255, 255, 255), alpha * 0.9),
              bg=hc(BRAND, alpha * 0.94))
    d = ImageDraw.Draw(img)
    tracked_text(d, (126, 72), "HAM 工具合集", font(24, "bold"), hc(TXT, alpha * 0.78), tracking=1)

    nm = section_name(t)
    d.text((W - 90, 72), nm, font=font(20, "reg"), fill=hc(TXT_FAINT, alpha * 0.82), anchor="rs")
    nx = W - 90 - d.textlength(nm, font=font(20, "reg")) - 26
    d.line([(nx, 64), (nx, 80)], fill=hc(BRAND_HI, alpha * 0.35), width=2)

    # 底部进度条（预混合色，避免 RGBA 直接绘制变实色）
    prog = clamp(t / DUR)
    track = mix((10, 15, 14), (255, 255, 255), 0.05)
    fill_c = mix((10, 15, 14), BRAND_HI, 0.55)
    d.rectangle([0, H - 3, W, H], fill=track + (255,))
    d.rectangle([0, H - 3, int(W * prog), H], fill=fill_c + (255,))
    return img


# ---------------------------------------------------------------- 场景 1：开场


def scene_intro(t):
    img = BG.copy().convert("RGBA")
    cx, cy = W / 2, H * 0.40

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
        wave_logo(img, cx, cy, 230 * sc, color=(255, 255, 255, int(255 * p)),
                  bg=BRAND + (int(250 * p),))

    p = seg(t, 1.35, 0.9)
    if p > 0:
        tracked_text(ImageDraw.Draw(img), (cx, cy + 250 + 30 * (1 - ease_out(p))), "HAM 工具合集",
                     font(96, "bold"), TXT + (int(255 * p),),
                     tracking=lerp(24, 8, ease_out(p)), anchor="c")

    p = seg(t, 2.0, 0.85)
    if p > 0:
        tracked_text(ImageDraw.Draw(img), (cx, cy + 322 + 20 * (1 - ease_out(p))),
                     "业余无线电 · 口袋工具箱", font(38, "reg"),
                     TXT_DIM + (int(220 * p),), tracking=8, anchor="c")

    # 三个数据点
    stats = [("19", "个工具"), ("4", "大板块"), ("100%", "离线可用")]
    for i, (num, label) in enumerate(stats):
        p = seg(t, 2.75 + i * 0.13, 0.7)
        if p <= 0:
            continue
        a = int(255 * p)
        x = cx + (i - 1) * 330
        d = ImageDraw.Draw(img)
        d.text((x, cy + 430), num, font=font(64, "bold"), fill=BRAND_SOFT + (a,), anchor="ms")
        d.text((x, cy + 478), label, font=font(24, "reg"), fill=TXT_DIM + (a,), anchor="ms")
        if i:
            d.line([(x - 165, cy + 404), (x - 165, cy + 452)], fill=(58, 74, 70, int(120 * p)), width=1)

    p = seg(t, 3.6, 0.8)
    if p > 0:
        tracked_text(ImageDraw.Draw(img), (cx, cy + 560), "作者 BA4IGG · 开源 MIT",
                     font(22, "light"), TXT_FAINT + (int(190 * p),), tracking=4, anchor="c")

    fade = 1 - 0.5 * seg(t, 7.3, 0.65)
    if fade < 1:
        img = Image.blend(Image.new("RGBA", img.size, (0, 0, 0, 255)), img, fade)
    return img.convert("RGB")


# ---------------------------------------------------------------- 场景 2：痛点


def scene_pain(t):
    img = BG.copy().convert("RGBA")
    d = ImageDraw.Draw(img)
    cx = W / 2
    img = glow(img, cx, H * 0.45, 420, BRAND, alpha=36, softness=0.5)

    p = seg(t, 0.15, 0.8)
    if p > 0:
        tracked_text(d, (cx, H * 0.255), "有没有过这样的时刻", font(28, "light"),
                     TXT_FAINT + (int(210 * p),), tracking=8, anchor="c")

    p = seg(t, 0.4, 0.9)
    if p > 0:
        tracked_text(d, (cx, H * 0.355 + 26 * (1 - ease_out(p))), "山顶架好天线，却没带计算器",
                     font(68, "bold"), TXT + (int(255 * p),), tracking=2, anchor="c")

    lines = [
        "想算半波长，手机却没信号，在线工具打不开",
        "好不容易连上网，先看三十秒广告",
        "查到一组中继频率，却不知道是哪一年登记的",
    ]
    for i, text in enumerate(lines):
        p = seg(t, 1.5 + i * 0.55, 0.85)
        if p <= 0:
            continue
        a = int(255 * p)
        y = H * 0.475 + i * 74 + 24 * (1 - ease_out(p))
        d = ImageDraw.Draw(img)
        d.ellipse([cx - 430, y - 16, cx - 430 + 8, y - 8], fill=(96, 116, 110, a))
        d.text((cx - 398, y), text, font=font(32, "reg"), fill=TXT_DIM + (a,), anchor="ls")

    p = seg(t, 3.5, 0.9)
    if p > 0:
        a = int(255 * p)
        yy = H * 0.735
        d = ImageDraw.Draw(img)
        tracked_text(d, (cx, yy), "所以做了这个 ——", font(30, "light"),
                     TXT_FAINT + (a,), tracking=4, anchor="c")
        tracked_text(d, (cx, yy + 82), "一个离线优先、按需联网的业余无线电工具箱",
                     font(52, "bold"), BRAND_SOFT + (a,), tracking=2, anchor="c")

    return img.convert("RGB")


# ---------------------------------------------------------------- 场景 3：总览

BLOCKS = [
    ("计算工具", "5 个", "calc", "频率波长 · 天线长度 · 驻波比"),
    ("查询手册", "5 个", "book", "频段划分 · 呼号前缀 · Q 简语"),
    ("音频工具", "4 个", "audio", "CW 电键 · 听抄 · DTMF"),
    ("联网工具", "5 个", "net", "传播指数 · DX 集群 · APRS"),
]


def scene_overview(t):
    img = BG.copy().convert("RGBA")
    d = ImageDraw.Draw(img)

    p = seg(t, 0.1, 0.9)
    if p > 0:
        d.text((140, H * 0.30 + 22 * (1 - ease_out(p))), "一个入口",
               font=font(34, "light"), fill=BRAND_HI + (int(230 * p),), anchor="ls")
        tracked_text(d, (140, H * 0.395 + 26 * (1 - ease_out(p))), "19 个工具，按场景分组",
                     font(60, "bold"), TXT + (int(255 * p),), tracking=1)

    p = seg(t, 0.45, 0.9)
    if p > 0:
        body = "计算、查询、音频、联网四大板块。已实现的计算与查询工具数据全部内置，不需要联网。"
        for i, ln in enumerate(wrap_text(d, body, font(27, "reg"), 620)):
            d.text((140, H * 0.50 + i * 44), ln, font=font(27, "reg"),
                   fill=TXT_DIM + (int(225 * p),), anchor="ls")

    p = seg(t, 1.1, 0.8)
    if p > 0:
        d.rounded_rectangle([140, H * 0.665, 140 + 380 * ease_out(p), H * 0.665 + 56], radius=28,
                            fill=BRAND_HI + (int(26 * p),), outline=BRAND_HI + (int(90 * p),), width=1)
        d.text((170, H * 0.665 + 36), "离线优先 · 无广告 · 无需注册",
               font=font(23, "reg"), fill=BRAND_SOFT + (int(240 * p),), anchor="ls")

    # 右侧 2×2 板块卡
    bx, by, bw, bh, gap = 830, 210, 440, 320, 26
    for i, (name, cnt, gk, tools) in enumerate(BLOCKS):
        p = seg(t, 0.55 + i * 0.16, 0.95)
        if p <= 0:
            continue
        a = int(255 * p)
        x = bx + (i % 2) * (bw + gap)
        y = by + (i // 2) * (bh + gap) + 34 * (1 - ease_out(p))
        img.alpha_composite(panel((bw, bh), 30, CARD + (a,), outline=LINE + (int(a * .85),)), (int(x), int(y)))
        dd = ImageDraw.Draw(img)
        dd.rounded_rectangle([x + 34, y + 34, x + 34 + 62, y + 34 + 62], radius=18,
                             fill=BRAND_HI + (int(28 * p),))
        glyph(dd, x + 34 + 31, y + 34 + 31, 36, gk, BRAND_HI + (a,))
        dd.text((x + 34, y + 158), name, font=font(32, "bold"), fill=TXT + (a,), anchor="ls")
        dd.text((x + 34, y + 196), cnt, font=font(22, "reg"), fill=BRAND_HI + (a,), anchor="ls")
        dd.text((x + 34, y + 250), tools, font=font(20, "reg"), fill=TXT_FAINT + (a,), anchor="ls")

    return img.convert("RGB")


# ---------------------------------------------------------------- 工具页模板

TOOLS = [
    dict(idx="01", cat="计算工具", title="频率波长",
         desc="输入任意频率，立刻得到波长、四分之一波长与半波长。做天线、切馈线、算谐振长度都用得上。",
         bullets=["覆盖 1.8 MHz – 5.8 GHz 常用业余频段", "一次给出 λ、λ/4、λ/2 三个值", "自动判断所属频段"],
         head="145.000 MHz", big=("2.067", "m"), label="波长 λ",
         rows=[("λ / 4", "0.517 m"), ("λ / 2", "1.034 m"), ("所属频段", "2 m 波段")]),
    dict(idx="02", cat="计算工具", title="天线长度",
         desc="偶极子、倒 V、垂直天线，输入频率直接给出振子长度，省掉每次翻公式。",
         bullets=["偶极子按 0.95 缩短系数修正", "倒 V 按 120° 夹角换算", "垂直天线取 1/4λ"],
         head="7.050 MHz · 40 m 波段", big=("20.20", "m"), label="偶极子全长",
         rows=[("单臂长度", "10.10 m"), ("倒 V（120°）", "19.59 m"), ("垂直 1/4λ", "10.10 m")]),
    dict(idx="03", cat="计算工具", title="驻波比回损",
         desc="VSWR、回波损耗、反射系数三者互算。调天线的时候不用再翻表。",
         bullets=["三者任意输入，其余自动换算", "直接给出反射功率百分比", "1.0 – 10.0 全区间"],
         head="VSWR 1.50", big=("14.0", "dB"), label="回波损耗",
         rows=[("反射系数 Γ", "0.200"), ("反射功率", "4.0 %"), ("传输功率", "96.0 %")]),
    dict(idx="04", cat="计算工具", title="馈线损耗",
         desc="按线缆型号与长度估算衰减。用厂商公布的频点做双对数插值，不是拍脑袋的经验公式。",
         bullets=["内置常见同轴线型号", "七个频点双对数插值", "给出末端剩余功率"],
         head="-50 电缆 · 30 m · 145 MHz", big=("1.6", "dB"), label="总衰减",
         rows=[("每 100 m 衰减", "5.5 dB"), ("末端剩余功率", "69 %"), ("折合 S 表", "−0.3 S")]),
    dict(idx="05", cat="计算工具", title="链路预算",
         desc="弗里斯的传输方程。收发功率、天线增益、路径损耗一路算下来，最后看有没有余量。",
         bullets=["自由空间路径损耗模型", "含收发两端天线增益", "直接给出链路余量"],
         head="145 MHz · 50 km · 5 W", big=("−70.3", "dBm"), label="接收电平",
         rows=[("发射功率", "+37.0 dBm"), ("收发天线增益", "+4.3 dBi"),
               ("自由空间损耗", "−109.7 dB"), ("链路余量", "49.6 dB")]),
    dict(idx="06", cat="查询手册", title="业余频段划分",
         desc="中国与 IARU 三个区的业余频段划分，按区域切换，支持关键词检索。",
         bullets=["中国 / 一区 / 二区 / 三区", "标注带宽与主要用途", "关键词直接过滤"],
         head="IARU 第三区 · 中国", big=None, label=None,
         rows=[("20 m", "14.000 – 14.350 MHz"), ("15 m", "21.000 – 21.450 MHz"),
               ("10 m", "28.000 – 29.700 MHz"), ("6 m", "50.000 – 54.000 MHz")]),
    dict(idx="07", cat="查询手册", title="呼号前缀",
         desc="输入呼号解析国家与地区，也可以按大洲浏览 DXCC 名录。",
         bullets=["最长前缀优先匹配", "支持 /P /M /QRP 后缀", "约 300 个实体"],
         head="BA4IGG", big=None, label=None,
         rows=[("国家 / 地区", "中国"), ("CQ 区", "24"), ("ITU 区", "44"), ("前缀 BA", "山东省")]),
    dict(idx="08", cat="查询手册", title="Q 简语",
         desc="常用 Q 简语，同时列出提问与陈述两种用法，另附 ICAO 字母解释法。",
         bullets=["提问 / 陈述两面并列", "常用简语约 45 条", "附 26 个字母解释法"],
         head="常用 Q 简语", big=None, label=None,
         rows=[("QTH", "我的位置是 …"), ("QRM", "受到人为干扰"),
               ("QSY", "改频到 …"), ("QSL", "收到 / 交换卡片")]),
    dict(idx="09", cat="查询手册", title="摩尔斯对照",
         desc="字符与电码互转，可以现场发声。速度 5 – 40 WPM 连续可调。",
         bullets=["波形现场合成，不打包音频", "支持文字转电码", "起止留斜坡避免爆音"],
         head="MORSE", big=None, label=None, rows=[], special="morse"),
    dict(idx="10", cat="查询手册", title="卫星与中继",
         desc="业余卫星的上下行频率与模式，以及国内常用中继名录，按大区分组。",
         bullets=["含 FM 卫星亚音参数", "中继按省份分组", "页面内附核实提醒"],
         head="业余卫星与中继", big=None, label=None,
         rows=[("ISS", "145.800 FM"), ("SO-50", "145.850 ↑ / 436.795 ↓"),
               ("AO-91", "145.960 ↑ / 435.250 ↓"), ("国内中继", "438.500 −5 MHz")]),
]

PANEL_X, PANEL_Y, PANEL_W, PANEL_H = 980, 240, 800, 620


def draw_data_panel(img, cfg, t, appear):
    """统一的右侧数据面板：标题 → 大数字 → 明细行。"""
    x, y, w, h = PANEL_X + 70 * (1 - appear), PANEL_Y, PANEL_W, PANEL_H
    a = clamp(appear)

    img = glow(img, x + w / 2, y + h / 2, 420, BRAND, alpha=int(42 * a), softness=0.5)

    img.alpha_composite(panel((w, h), 34, CARD + (int(250 * a),),
                              outline=LINE + (int(235 * a),)), (int(x), int(y)))
    d = ImageDraw.Draw(img)

    # 头部
    d.text((x + 56, y + 74), cfg["head"], font=font(30, "reg"), fill=TXT_DIM + (int(240 * a),), anchor="ls")
    d.line([(x + 56, y + 106), (x + w - 56, y + 106)], fill=LINE + (int(200 * a),), width=1)

    # 大数字
    p_big = clamp((appear - 0.25) / 0.6)
    ry = y + 210
    if cfg["big"]:
        val, unit = cfg["big"]
        text = val
        # 数字滚动：从 0 涨到目标
        try:
            target = float(val.replace("−", "-").replace("+", ""))
            cur = target * ease_out(p_big, 2.6)
            dec = len(val.split(".")[1]) if "." in val else 0
            fmt = f"{abs(cur):.{dec}f}"
            text = ("−" if target < 0 else "") + fmt
        except ValueError:
            text = val
        fa = int(255 * clamp(appear * 1.2))
        f_b = font(118, "bold")
        if p_big > 0.85:
            img = glow(img, x + w / 2, ry - 40, 220, BRAND_HI,
                       alpha=int(46 * clamp((p_big - 0.85) * 6)), softness=0.55)
            d = ImageDraw.Draw(img)
        tw = d.textlength(text, font=f_b)
        wu = d.textlength(unit, font=font(38, "reg"))
        x0 = x + 56
        d.text((x0, ry), text, font=f_b, fill=BRAND_SOFT + (fa,), anchor="ls")
        d.text((x0 + tw + 14, ry), unit, font=font(38, "reg"), fill=TXT_DIM + (fa,), anchor="ls")
        if cfg["label"]:
            d.text((x0, ry + 52), cfg["label"], font=font(24, "reg"), fill=TXT_FAINT + (fa,), anchor="ls")
        row_top = y + 330
    else:
        row_top = y + 170

    # 明细行
    for i, (k, v) in enumerate(cfg["rows"]):
        p = clamp((appear - 0.55 - i * 0.11) / 0.5)
        if p <= 0:
            continue
        ra = int(255 * p)
        yy = row_top + i * 78 + 18 * (1 - ease_out(p))
        d.text((x + 56, yy), k, font=font(26, "reg"), fill=TXT_DIM + (ra,), anchor="ls")
        d.text((x + w - 56, yy), v, font=font(30, "bold"), fill=TXT + (ra,), anchor="rs")
        if i < len(cfg["rows"]) - 1:
            d.line([(x + 56, yy + 26), (x + w - 56, yy + 26)],
                   fill=(36, 48, 45, int(150 * p)), width=1)
    return img


MORSE_CQ = [("C", "-·-·"), ("Q", "--·-")]
DOT_W, DASH_W, SYM_GAP, CHAR_ADV = 16, 64, 16, 84


def _group_w(code):
    return CHAR_ADV + sum((DASH_W if s == "-" else DOT_W) + SYM_GAP for s in code) - SYM_GAP


def draw_morse_panel(img, cfg, t, appear):
    x, y, w, h = PANEL_X + 70 * (1 - appear), PANEL_Y, PANEL_W, PANEL_H
    a = clamp(appear)
    img = glow(img, x + w / 2, y + h / 2, 420, BRAND, alpha=int(42 * a), softness=0.5)
    img.alpha_composite(panel((w, h), 34, CARD + (int(250 * a),), outline=LINE + (int(235 * a),)),
                        (int(x), int(y)))
    d = ImageDraw.Draw(img)
    d.text((x + 56, y + 74), "CQ CQ · 现场发声", font=font(30, "reg"),
           fill=TXT_DIM + (int(240 * a),), anchor="ls")
    d.line([(x + 56, y + 106), (x + w - 56, y + 106)], fill=LINE + (int(200 * a),), width=1)

    yb = y + 250
    beat, lit = 0.30, (t - 1.15) / 0.30
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

    # 声波
    bars, bw2, by = 46, 13, y + 360
    bx0 = x + (w - bars * bw2) / 2
    for i in range(bars):
        amp = (0.3 + 0.7 * abs(math.sin(t * 7.0 + i * 0.5))) * (0.45 + 0.55 * math.sin(i / bars * math.pi))
        hh = 5 + amp * 54
        d.rounded_rectangle([bx0 + i * bw2, by - hh / 2, bx0 + i * bw2 + 6, by + hh / 2],
                            radius=3, fill=BRAND_HI + (int(140 * a),))

    d.text((x + 56, y + 470), "5 – 40 WPM 可调", font=font(26, "reg"),
           fill=TXT_DIM + (int(230 * a),), anchor="ls")
    d.text((x + 56, y + 522), "波形由 AudioTrack 现场合成，不打包音频文件；每个音起止留 5 ms 斜坡，避免爆音",
           font=font(20, "light"), fill=TXT_DIM + (int(215 * a),), anchor="ls")
    return img


def scene_tool(t, cfg):
    img = BG.copy().convert("RGBA")
    d = ImageDraw.Draw(img)
    img = glow(img, W * 0.45, H * 0.42, 460, BRAND, alpha=34, softness=0.52)
    d = ImageDraw.Draw(img)

    # 左侧：序号 + 分类一行，避免大水印与标题打架
    p = seg(t, 0.0, 0.6)
    if p > 0:
        a = int(255 * p)
        x0 = 138 - 40 * (1 - ease_out(p))
        d.text((x0, H * 0.215), cfg["idx"], font=font(30, "bold"),
               fill=BRAND_HI + (a,), anchor="ls")
        d.text((x0 + 56, H * 0.215), cfg["cat"], font=font(24, "reg"),
               fill=TXT_FAINT + (int(230 * p),), anchor="ls")

    # 标题
    p = seg(t, 0.10, 0.7)
    if p > 0:
        tracked_text(d, (138, H * 0.325 + 26 * (1 - ease_out(p))), cfg["title"],
                     font(76, "bold"), TXT + (int(255 * p),), tracking=2)
        dw = d.textlength(cfg["title"], font=font(76, "bold")) + 2 * len(cfg["title"])
        d.line([(138, H * 0.362), (138 + dw * ease_out(p), H * 0.362)],
               fill=BRAND_HI + (int(200 * p),), width=3)

    # 描述
    p = seg(t, 0.30, 0.8)
    if p > 0:
        for i, ln in enumerate(wrap_text(d, cfg["desc"], font(27, "reg"), 680)):
            d.text((138, H * 0.445 + i * 44), ln, font=font(27, "reg"),
                   fill=TXT_DIM + (int(230 * p),), anchor="ls")

    # 要点
    for i, b in enumerate(cfg["bullets"]):
        p = seg(t, 0.62 + i * 0.14, 0.7)
        if p <= 0:
            continue
        a = int(255 * p)
        yy = H * 0.615 + i * 62 + 20 * (1 - ease_out(p))
        d = ImageDraw.Draw(img)
        d.rounded_rectangle([138, yy - 9, 148, yy + 1], radius=5, fill=BRAND_HI + (a,))
        d.text((172, yy), b, font=font(25, "reg"), fill=TXT + (int(240 * p),), anchor="ls")

    appear = ease_out(seg(t, 0.22, 0.95), 3)
    if cfg.get("special") == "morse":
        img = draw_morse_panel(img, cfg, t, appear)
    else:
        img = draw_data_panel(img, cfg, t, appear)

    fade = 1 - 0.45 * seg(t, 6.55, 0.45)
    if fade < 1:
        img = Image.blend(Image.new("RGBA", img.size, (0, 0, 0, 255)), img, fade)
    return img.convert("RGB")


# ---------------------------------------------------------------- 场景 14：特性

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
        tracked_text(d, (cx, H * 0.225), "除了算得准，还做了这些", font(26, "light"),
                     TXT_FAINT + (int(210 * p),), tracking=6, anchor="c")

    bw, bh, gap = 400, 320, 28
    total_w = bw * 4 + gap * 3
    bx0, by = (W - total_w) / 2, H * 0.34
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
        # PIL 在 RGBA 上直接画半透明色是「替换像素」，转 RGB 后会变成实心亮色；
        # 所以这里用预混合后的不透明等效色。
        circ = mix(CARD, BRAND_HI, 0.15)
        dd.ellipse([x + bw / 2 - 44, y + 56, x + bw / 2 + 44, y + 144], fill=circ + (255,))
        fg = BRAND_SOFT + (255,)
        if kind == "palette":
            for k, col in enumerate([(124, 245, 218), (255, 214, 138), (198, 176, 255)]):
                dd.ellipse([x + bw / 2 - 30 + k * 21, y + 88, x + bw / 2 - 12 + k * 21, y + 106],
                           fill=col + (255,))
        elif kind == "offline":
            dd.line([(x + bw / 2 - 26, y + 102), (x + bw / 2 - 4, y + 124),
                     (x + bw / 2 + 28, y + 78)], fill=fg, width=6, joint="curve")
        elif kind == "clean":
            dd.ellipse([x + bw / 2 - 24, y + 76, x + bw / 2 + 24, y + 124],
                       outline=fg, width=5)
            dd.line([(x + bw / 2 - 36, y + 64), (x + bw / 2 + 36, y + 136)], fill=fg, width=5)
        else:
            dd.line([(x + bw / 2 - 26, y + 90), (x + bw / 2 - 6, y + 76),
                     (x + bw / 2 - 6, y + 124), (x + bw / 2 + 18, y + 110)],
                    fill=fg, width=5, joint="curve")
        dd.text((x + bw / 2, y + 208), title, font=font(32, "bold"), fill=TXT + (a,), anchor="ms")
        for k, ln in enumerate(wrap_text(dd, sub, font(21, "reg"), bw - 70)):
            dd.text((x + bw / 2, y + 250 + k * 32), ln, font=font(21, "reg"),
                    fill=TXT_FAINT + (a,), anchor="ms")

    p = seg(t, 4.0, 0.9)
    if p > 0:
        tracked_text(d, (cx, H * 0.79), "minSdk 24 · 支持 Android 7.0 及以上", font(24, "light"),
                     TXT_DIM + (int(200 * p),), tracking=3, anchor="c")

    return img.convert("RGB")


# ---------------------------------------------------------------- 场景 15：结尾


def scene_outro(t):
    img = BG.copy().convert("RGBA")
    cx, cy = W / 2, H * 0.36
    img = glow(img, cx, cy, 340, BRAND, alpha=44, softness=0.48)

    p = seg(t, 0.0, 0.9)
    if p > 0:
        wave_logo(img, cx, cy, 170 * (0.86 + 0.14 * ease_out_back(p, 1.1)),
                  color=(255, 255, 255, int(255 * p)), bg=BRAND + (int(250 * p),))

    d = ImageDraw.Draw(img)
    p = seg(t, 0.35, 0.85)
    if p > 0:
        tracked_text(d, (cx, cy + 190), "HAM 工具合集", font(62, "bold"),
                     TXT + (int(255 * p),), tracking=lerp(16, 4, ease_out(p)), anchor="c")

    p = seg(t, 0.75, 0.8)
    if p > 0:
        d.text((cx, cy + 258), "下载 v1.1-beta · 直接覆盖安装即可升级", font=font(26, "reg"),
               fill=TXT_DIM + (int(230 * p),), anchor="ms")

    p = seg(t, 1.15, 0.85)
    if p > 0:
        d.rounded_rectangle([cx - 300, cy + 316, cx + 300, cy + 396], radius=40,
                            fill=(17, 25, 23, int(240 * p)), outline=LINE + (int(220 * p),), width=1)
        d.text((cx, cy + 366), "github.com/BA4IGG/hamtools", font=font(30, "reg"),
               fill=BRAND_SOFT + (int(240 * p),), anchor="ms")

    p = seg(t, 1.55, 0.85)
    if p > 0:
        tracked_text(d, (cx, cy + 470), "作者 BA4IGG · MIT License · 2026", font(23, "light"),
                     TXT_FAINT + (int(220 * p),), tracking=3, anchor="c")

    fade = 1 - seg(t, 10.2, 0.9)
    if fade < 1:
        img = Image.blend(Image.new("RGBA", img.size, (0, 0, 0, 255)), img, clamp(fade))
    return img.convert("RGB")


# ---------------------------------------------------------------- 时间轴

SCENES = [(0.0, 8.0, scene_intro), (8.0, 16.0, scene_pain), (16.0, 29.0, scene_overview)]
_start = 29.0
for _cfg in TOOLS:
    SCENES.append((_start, _start + 7.0, (lambda c: (lambda t: scene_tool(t, c)))(_cfg)))
    _start += 7.0
SCENES.append((99.0, 109.0, scene_features))
SCENES.append((109.0, 120.0, scene_outro))

XFADE = 0.5
HUD_FADE_IN = (0.35, 1.0)


def render_at(t):
    for i, (s, e, fn) in enumerate(SCENES):
        if s <= t < e or (i == len(SCENES) - 1 and t >= s):
            if t > e - XFADE and i + 1 < len(SCENES):
                w = ease_in_out(clamp((t - (e - XFADE)) / XFADE))
                frame = Image.blend(fn(t - s), SCENES[i + 1][2](max(0.0, t - e)), w)
                break
            frame = fn(min(t - s, e - s))
            break
    else:
        frame = SCENES[-1][2](0.0)

    img = frame.convert("RGBA")
    hud_a = clamp((t - HUD_FADE_IN[0]) / 0.8) if t < 5 else 1.0
    if t > 4.6:
        hud_a = clamp((t - 4.6) / 0.7) * clamp((DUR - 0.6 - t) / 1.0)
    draw_hud(img, t, hud_a)
    return img.convert("RGB")


# ---------------------------------------------------------------- 配乐与音效

SR = 44100
CHORDS = {
    "Am9": [110.00, 261.63, 329.63, 392.00, 493.88],
    "Fmaj7": [87.31, 220.00, 261.63, 329.63],
    "Cmaj7": [130.81, 261.63, 329.63, 493.88],
    "G6": [98.00, 246.94, 293.66, 329.63],
    "Em7": [82.41, 246.94, 293.66, 392.00],
}

# 每个分镜用哪个和弦，跟着画面走
PLAN = [
    (0.0, 8.0, "Am9"), (8.0, 16.0, "Fmaj7"), (16.0, 22.5, "Cmaj7"), (22.5, 29.0, "G6"),
    (29.0, 36.0, "Am9"), (36.0, 43.0, "Fmaj7"), (43.0, 50.0, "Cmaj7"), (50.0, 57.0, "G6"),
    (57.0, 64.0, "Em7"), (64.0, 71.0, "Am9"), (71.0, 78.0, "Fmaj7"), (78.0, 85.0, "Cmaj7"),
    (85.0, 92.0, "G6"), (92.0, 99.0, "Em7"), (99.0, 109.0, "Fmaj7"),
    (109.0, 115.0, "G6"), (115.0, 120.0, "Cmaj7"),
]


def build_audio(path):
    n = int(DUR * SR)
    tt = np.arange(n) / SR
    out = np.zeros((n, 2), np.float64)

    def add(buf, i0, gain=1.0, pan=0.0):
        i1 = min(n, i0 + len(buf))
        if i1 <= i0:
            return
        l = gain * (1 - max(0.0, pan) * 0.6)
        r = gain * (1 - max(0.0, -pan) * 0.6)
        out[i0:i1, 0] += buf[: i1 - i0] * l
        out[i0:i1, 1] += buf[: i1 - i0] * r

    # ---- Pad：每段的和弦长音
    for s, e, name in PLAN:
        i0, i1 = int(s * SR), min(n, int(e * SR))
        if i1 <= i0:
            continue
        freqs = CHORDS[name]
        lt = np.arange(i1 - i0) / SR
        buf = np.zeros(i1 - i0)
        for k, f in enumerate(freqs):
            amp = 0.17 / (1 + k * 0.42)
            for det, da in ((1.0, 1.0), (1.0022, 0.5), (0.9978, 0.5)):
                buf += amp * da * np.sin(2 * np.pi * f * det * lt + k * 0.8)
        env = np.ones(len(buf))
        f_at, f_rt = int(1.2 * SR), int(1.4 * SR)
        if len(env) > f_at + f_rt:
            env[:f_at] = np.linspace(0, 1, f_at) ** 1.4
            env[-f_rt:] = np.linspace(1, 0, f_rt) ** 1.4
        buf *= env
        add(buf, i0, gain=0.42 * (0.55 if s < 16 else 1.0))

    # ---- 低音：每 3 秒一拍根音
    for bar in range(int(DUR / 3.0)):
        tc = bar * 3.0
        name = next((nm for s, e, nm in PLAN if s <= tc < e), "Am9")
        root = CHORDS[name][0] / 2
        lt = np.arange(int(2.4 * SR)) / SR
        env = np.exp(-lt * 1.9) * (1 - np.exp(-lt * 60))
        buf = (np.sin(2 * np.pi * root * lt) + 0.3 * np.sin(4 * np.pi * root * lt)) * env
        g = 0.30 if tc >= 16 else 0.12
        add(buf, int(tc * SR), gain=g)

    # ---- 琶音：29s 之后进入，八分音符
    arp_on = 29.0
    step = 0.375
    k = 0
    tcur = arp_on
    while tcur < DUR - 2.0:
        name = next((nm for s, e, nm in PLAN if s <= tcur < e), "Am9")
        freqs = CHORDS[name]
        f = freqs[1 + (k % (len(freqs) - 1))] * 2
        lt = np.arange(int(0.5 * SR)) / SR
        env = np.exp(-lt * 7.5) * (1 - np.exp(-lt * 220))
        # 三角波近似：基频 + 三次谐波（负相位）
        buf = (np.sin(2 * np.pi * f * lt) - 0.14 * np.sin(6 * np.pi * f * lt)) * env
        add(buf, int(tcur * SR), gain=0.085, pan=0.28 * math.sin(k * 1.7))
        tcur += step
        k += 1

    # ---- 轻打击：64s 后，八分音符 hi-hat
    tcur = 64.0
    rng = np.random.default_rng(9)
    while tcur < 100.0:
        ln = int(0.06 * SR)
        nz = rng.normal(0, 1, ln)
        # 一阶高通（差分）让噪声尖锐
        nz = np.diff(nz, prepend=0)
        buf = nz * np.exp(-np.arange(ln) / SR * 90)
        add(buf, int(tcur * SR), gain=0.055, pan=0.35)
        tcur += 0.75

    # ---- 转场 whoosh
    for s, e, fn in SCENES[1:]:
        tc = s
        dur, ln = 0.8, int(0.8 * SR)
        i0 = int((tc - 0.5) * SR)
        if i0 < 0:
            continue
        noise = rng.normal(0, 1, ln)
        y, prev = np.zeros(ln), 0.0
        for j in range(ln):
            fc = 0.02 + 0.32 * (j / ln) ** 2
            prev += fc * (noise[j] - prev)
            y[j] = prev
        y = y / (np.max(np.abs(y)) + 1e-9) * np.sin(np.linspace(0, np.pi, ln)) ** 2 * 0.06
        add(y, i0, gain=1.0)

    # ---- 面板出现 / 数字落定 的细微 "tick"
    ticks = []
    for s, e, fn in SCENES:
        if e - s <= 7.5 and e > 29:          # 工具页
            ticks += [s + 0.55, s + 2.15]    # 面板滑入、数字落定
    ticks += [t0 for t0, _, _ in SCENES[1:]]
    for tc in ticks:
        i0 = int(tc * SR)
        lt = np.arange(int(0.10 * SR)) / SR
        buf = np.sin(2 * np.pi * 1180 * lt) * np.exp(-lt * 55)
        add(buf, i0, gain=0.045)

    # ---- 摩尔斯段的真实电码音（800 Hz，按 CQ 的节奏）
    i0 = int((85.0 + 1.15) * SR)
    beat = 0.30
    idx = 0
    for ch, code in MORSE_CQ:
        for sym in code:
            ln = int((3 if sym == "-" else 1) * beat * 0.55 * SR)
            lt = np.arange(ln) / SR
            env = np.minimum(1, np.minimum(lt / 0.008, (lt[-1] - lt) / 0.008))
            buf = np.sin(2 * np.pi * 800 * lt) * np.clip(env, 0, 1)
            add(buf, i0 + int(idx * beat * SR), gain=0.11)
            idx += 1

    # ---- 结尾提示音
    i0 = int(110.0 * SR)
    ln = min(n - i0, int(3.0 * SR))
    if ln > 0:
        lt = np.arange(ln) / SR
        tone = (np.sin(2 * np.pi * 880 * lt) * 0.5 + np.sin(2 * np.pi * 1320 * lt) * 0.26
                + np.sin(2 * np.pi * 1760 * lt) * 0.11)
        env = np.exp(-lt * 2.0) * (1 - np.exp(-lt * 80))
        add(tone * env, i0, gain=0.13)

    # ---- 整体起伏 + 延迟混响
    out *= (0.86 + 0.14 * np.sin(2 * np.pi * 0.07 * tt))[:, None]
    wet = out.copy()
    dly = int(0.21 * SR)
    for k in (1, 2, 3):
        if dly * k < n:
            wet[dly * k:] += out[: n - dly * k] * (0.24 ** k)
    out = out * 0.74 + wet * 0.26

    peak = np.max(np.abs(out))
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
    print("[1/4] 构建背景…")
    BG = build_bg()

    if mode == "preview":
        shots = [2.4, 5.5, 10.5, 14.0, 21.0, 26.0, 31.5, 38.5, 45.5, 52.5, 59.5,
                 66.5, 73.5, 80.5, 88.5, 95.5, 103.0, 113.0]
        os.makedirs(os.path.join(HERE, "preview_ls"), exist_ok=True)
        for t in shots:
            render_at(t).save(os.path.join(HERE, "preview_ls", f"f{t:06.1f}.png"))
            print(f"  {t:>5.1f}s")
        print("→ promo/preview_ls/")
        return

    from imageio_ffmpeg import get_ffmpeg_exe
    ffmpeg = get_ffmpeg_exe()

    print("[2/4] 生成音频…")
    audio = os.path.join(HERE, "audio_ls.wav")
    build_audio(audio)

    print("[3/4] 渲染帧…")
    out = os.path.join(HERE, "HAM-Tools-promo-2min.mp4")
    cmd = [ffmpeg, "-y", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}",
           "-r", str(FPS), "-i", "-", "-i", audio,
           "-c:v", "libx264", "-preset", "slow", "-crf", "18", "-pix_fmt", "yuv420p",
           "-profile:v", "high", "-level", "4.2", "-c:a", "aac", "-b:a", "192k",
           "-movflags", "+faststart", "-shortest", out]
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=subprocess.DEVNULL,
                            stderr=subprocess.PIPE)
    total = int(DUR * FPS)
    for i in range(total):
        proc.stdin.write(render_at(i / FPS).tobytes())
        if i % 150 == 0:
            print(f"  {i}/{total} ({i / total * 100:.1f}%)", flush=True)
    proc.stdin.close()
    err = proc.stderr.read().decode("utf-8", "replace")
    if proc.wait() != 0:
        print(err[-2500:])
        raise SystemExit("ffmpeg 失败")
    print(f"[4/4] 完成：{out} ({os.path.getsize(out) / 1024 / 1024:.2f} MB)")


if __name__ == "__main__":
    main()
