# -*- coding: utf-8 -*-
"""
HAM 工具合集 —— 宣传片渲染脚本

逐帧用 Pillow 绘制，经 ffmpeg 管道编码为 H.264。
所有界面元素都按 App 真实的配色与内容重绘（品牌色 #006C5B、19 个工具的真实名称），
不是凭想象编的示意图。

用法：
    python render_promo.py preview     # 只导出几张关键帧 PNG，用于检查画面
    python render_promo.py full        # 渲染整片
"""

import math
import os
import subprocess
import sys
from functools import lru_cache

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

# ---------------------------------------------------------------- 基本参数

W, H = 1080, 1920
FPS = 30
DUR = 31.0

HERE = os.path.dirname(os.path.abspath(__file__))

# ---------------------------------------------------------------- 配色
# 品牌色取自 app/src/main/res/drawable/ic_launcher_background.xml (#006C5B)，
# 深色主题下按 MD3 tone 规则提亮到 tone 80 左右使用。

BRAND = (0, 108, 91)          # 品牌原色，用于图标底
BRAND_HI = (42, 217, 176)     # tone 80，暗色主题下的 primary
BRAND_SOFT = (124, 245, 218)  # 高亮强调
CARD_BG = (18, 26, 24)
CARD_LINE = (38, 50, 47)
TXT = (233, 242, 239)
TXT_DIM = (139, 158, 152)
TXT_FAINT = (86, 102, 98)

FONT_BOLD = "C:/Windows/Fonts/msyhbd.ttc"
FONT_REG = "C:/Windows/Fonts/msyh.ttc"
FONT_LIGHT = "C:/Windows/Fonts/msyhl.ttc"


@lru_cache(maxsize=64)
def font(size, weight="bold"):
    path = {"bold": FONT_BOLD, "reg": FONT_REG, "light": FONT_LIGHT}[weight]
    return ImageFont.truetype(path, size, index=0)


# ---------------------------------------------------------------- 缓动


def clamp(x, lo=0.0, hi=1.0):
    return max(lo, min(hi, x))


def seg(t, start, dur):
    """把绝对时间映射为 0..1 的局部进度，用于「第 n 秒开始、持续 dur 秒」的动画。"""
    if dur <= 0:
        return 1.0 if t >= start else 0.0
    return clamp((t - start) / dur)


def ease_out(t, p=3.0):
    return 1.0 - (1.0 - t) ** p


def ease_in(t, p=3.0):
    return t ** p


def ease_in_out(t):
    return 3 * t * t - 2 * t * t * t


def ease_out_back(t, s=1.55):
    t = t - 1.0
    return 1.0 + (s + 1.0) * t * t * t + s * t * t


def lerp(a, b, t):
    return a + (b - a) * t


def mix(c1, c2, t):
    t = clamp(t)
    return tuple(int(round(lerp(c1[i], c2[i], t))) for i in range(3))


# ---------------------------------------------------------------- 绘制辅助


def tracked_text(draw, xy, s, fnt, fill, tracking=0.0, anchor="l", baseline=None):
    """带字距的文字。PIL 没有 letter-spacing，逐字排。"""
    widths = [draw.textlength(ch, font=fnt) for ch in s]
    total = sum(widths) + tracking * max(0, len(s) - 1)
    x = xy[0]
    if anchor == "c":
        x -= total / 2.0
    elif anchor == "r":
        x -= total
    y = xy[1] if baseline is None else baseline
    for ch, w in zip(s, widths):
        draw.text((x, y), ch, font=fnt, fill=fill, anchor="ls")
        x += w + tracking
    return total


def text_width(draw, s, fnt, tracking=0.0):
    return sum(draw.textlength(ch, font=fnt) for ch in s) + tracking * max(0, len(s) - 1)


def alpha_layer(size):
    return Image.new("RGBA", size, (0, 0, 0, 0))


def paste_alpha(base, layer, pos=(0, 0)):
    """把 RGBA 图层叠加到 RGB 底图上。"""
    return Image.alpha_composite(
        base.convert("RGBA"), layer, dest=(0, 0)
    ) if pos == (0, 0) else _paste_offset(base, layer, pos)


def _paste_offset(base, layer, pos):
    canvas = base.convert("RGBA")
    tmp = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    tmp.paste(layer, pos)
    return Image.alpha_composite(canvas, tmp)


def add_glow(img, cx, cy, r, color, alpha=170, softness=0.30, down=4):
    """
    光晕：直接用径向平方衰减生成 alpha 蒙版。
    高斯模糊在画布边缘收敛不到 0，大半径时会露出方块边界；径向衰减天然为 0。
    softness 越大越弥散。
    """
    size = int(2 * r)
    if size < 8 or alpha <= 0:
        return img
    yy, xx = np.mgrid[0:size, 0:size].astype(np.float32)
    d = np.sqrt((xx - r) ** 2 + (yy - r) ** 2) / r
    p = 1.6 / max(0.12, softness)
    a = (np.clip(1.0 - d, 0.0, 1.0) ** p * alpha).astype(np.uint8)
    lay = Image.fromarray(a, "L")
    col = Image.new("RGB", lay.size, color)
    img.paste(col, (int(cx - r), int(cy - r)), lay)
    return img


def rounded_panel(size, radius, fill, outline=None, outline_w=1):
    w, h = size
    lay = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(lay)
    d.rounded_rectangle(
        [0, 0, w - 1, h - 1],
        radius=radius,
        fill=fill if len(fill) == 4 else fill + (255,),
        outline=(outline + (255,)) if outline and len(outline) == 3 else outline,
        width=outline_w,
    )
    return lay


# ---------------------------------------------------------------- 背景


def build_background():
    """深色径向渐变 + 极淡网格 + 暗角 + 固定噪点。只算一次，后面复用。"""
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    cx, cy = W * 0.5, H * 0.38
    d = np.sqrt(((xx - cx) / (W * 1.00)) ** 2 + ((yy - cy) / (H * 0.78)) ** 2)
    v = np.clip(1.0 - d, 0, 1) ** 2.3

    base = np.array([5, 9, 9], np.float32)
    glow = np.array([13, 31, 28], np.float32)
    arr = base + v[..., None] * (glow - base)

    dv = np.sqrt(((xx - W / 2) / (W * 0.80)) ** 2 + ((yy - H / 2) / (H * 0.80)) ** 2)
    vig = np.clip(1.0 - 0.60 * np.clip(dv - 0.40, 0, None) ** 1.5, 0.0, 1.0)
    arr = arr * vig[..., None]

    rng = np.random.default_rng(11)
    arr = arr + rng.normal(0, 2.0, (H, W, 1)).astype(np.float32)

    img = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGB")

    grid = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    gd = ImageDraw.Draw(grid)
    for x in range(-1, W + 1, 120):
        gd.line([(x, 0), (x, H)], fill=(120, 190, 175, 8))
    for y in range(-1, H + 1, 120):
        gd.line([(0, y), (W, y)], fill=(120, 190, 175, 8))
    return Image.alpha_composite(img.convert("RGBA"), grid).convert("RGB")


BG = None  # 延迟构建


# ---------------------------------------------------------------- 图标（按 vector 源文件等比重绘）


def _rgba(c, a=255):
    """统一成 4 元组，避免「调用方传了 3 元组 / 4 元组」时拼出 5 元组。"""
    c = tuple(c)
    return c if len(c) == 4 else c + (int(clamp(a, 0, 255)),)


def draw_wave_logo(img, cx, cy, diameter, color=(255, 255, 255), bg=None, bg_alpha=255):
    """
    App 图标：中心馈源 + 三道对称波瓣。
    角度与半径直接取自 ic_launcher_foreground.xml（viewBox 108×108，
    半径 32/23/14，笔宽 5.5，波瓣张角 ±50°）。
    """
    if diameter < 4:
        return img
    r_out = diameter / 2.0
    pad = int(diameter * 0.12) + 6
    lay = Image.new("RGBA", (int(diameter) + pad * 2, int(diameter) + pad * 2), (0, 0, 0, 0))
    d = ImageDraw.Draw(lay)
    s = diameter / 108.0
    c = lay.size[0] / 2.0

    if bg is not None:
        d.ellipse([c - r_out, c - r_out, c + r_out, c + r_out], fill=_rgba(bg, bg_alpha))

    fg = _rgba(color)
    w = max(2, int(round(5.5 * s)))
    for rad in (32, 23, 14):
        rr = rad * s
        box = [c - rr, c - rr, c + rr, c + rr]
        d.arc(box, start=-50, end=50, fill=fg, width=w)
        d.arc(box, start=130, end=230, fill=fg, width=w)
    core = 5 * s
    d.ellipse([c - core, c - core, c + core, c + core], fill=fg)

    # PIL 的 arc 不做圆头收尾，这里补端点圆点
    for rad in (32, 23, 14):
        rr = rad * s
        for ang in (-50, 50, 130, 230):
            a = math.radians(ang)
            px, py = c + rr * math.cos(a), c + rr * math.sin(a)
            d.ellipse([px - w / 2, py - w / 2, px + w / 2, py + w / 2], fill=fg)

    img.alpha_composite(lay, (int(cx - lay.size[0] / 2), int(cy - lay.size[1] / 2)))
    return img


def draw_tool_glyph(draw, cx, cy, size, kind, color):
    """工具卡片小图标，线宽统一，风格接近 Material Symbols。"""
    w = max(2, int(round(size * 0.085)))
    r = size / 2.0
    if kind == "wave":
        pts = []
        for i in range(25):
            x = cx - r + 2 * r * i / 24.0
            pts.append((x, cy - r * 0.55 * math.sin(i / 24.0 * math.pi * 2.2)))
        draw.line(pts, fill=color, width=w, joint="curve")
    elif kind == "antenna":
        draw.line([(cx, cy - r * 0.85), (cx, cy + r * 0.75)], fill=color, width=w)
        draw.line([(cx - r * 0.62, cy + r * 0.80), (cx + r * 0.62, cy + r * 0.80)], fill=color, width=w)
        draw.line([(cx - r * 0.42, cy - r * 0.55), (cx, cy - r * 0.05)], fill=color, width=w)
        draw.line([(cx + r * 0.42, cy - r * 0.55), (cx, cy - r * 0.05)], fill=color, width=w)
    elif kind == "vswr":
        draw.arc([cx - r * 0.85, cy - r * 0.85, cx + r * 0.85, cy + r * 0.85], 200, 340, fill=color, width=w)
        draw.ellipse([cx - w, cy - w, cx + w, cy + w], fill=color)
        draw.line([(cx, cy), (cx + r * 0.55, cy - r * 0.55)], fill=color, width=w)
    elif kind == "cable":
        draw.line([(cx - r * 0.8, cy + r * 0.6), (cx + r * 0.1, cy - r * 0.3)], fill=color, width=w)
        draw.ellipse([cx + r * 0.0, cy - r * 0.55, cx + r * 0.55, cy], outline=color, width=w)
        draw.line([(cx - r * 0.2, cy + r * 0.75), (cx + r * 0.7, cy + r * 0.75)], fill=color, width=w)
    elif kind == "band":
        for i, dy in enumerate((-0.55, 0.0, 0.55)):
            half = r * (0.85 - abs(dy) * 0.35)
            draw.line([(cx - half, cy + r * dy), (cx + half, cy + r * dy)], fill=color, width=w)
    elif kind == "globe":
        draw.ellipse([cx - r * 0.82, cy - r * 0.82, cx + r * 0.82, cy + r * 0.82], outline=color, width=w)
        draw.arc([cx - r * 0.32, cy - r * 0.82, cx + r * 0.32, cy + r * 0.82], 90, 270, fill=color, width=w)
        draw.arc([cx - r * 0.32, cy - r * 0.82, cx + r * 0.32, cy + r * 0.82], -90, 90, fill=color, width=w)
        draw.line([(cx - r * 0.82, cy), (cx + r * 0.82, cy)], fill=color, width=w)
    elif kind == "q":
        draw.ellipse([cx - r * 0.72, cy - r * 0.78, cx + r * 0.62, cy + r * 0.56], outline=color, width=w)
        draw.line([(cx + r * 0.2, cy + r * 0.2), (cx + r * 0.78, cy + r * 0.78)], fill=color, width=w)
    elif kind == "morse":
        draw.ellipse([cx - r * 0.85, cy - w * 1.2, cx - r * 0.85 + w * 2.4, cy + w * 1.2], fill=color)
        draw.line([(cx - r * 0.30, cy), (cx + r * 0.85, cy)], fill=color, width=w)
        draw.ellipse([cx + r * 0.55, cy - w * 1.2, cx + r * 0.55 + w * 2.4, cy + w * 1.2], fill=color)
    elif kind == "sat":
        draw.rounded_rectangle([cx - r * 0.3, cy - r * 0.3, cx + r * 0.3, cy + r * 0.3], radius=r * 0.12,
                               outline=color, width=w)
        draw.line([(cx - r * 0.85, cy), (cx - r * 0.35, cy)], fill=color, width=w)
        draw.line([(cx + r * 0.35, cy), (cx + r * 0.85, cy)], fill=color, width=w)
        draw.arc([cx - r * 0.9, cy - r * 0.9, cx + r * 0.9, cy + r * 0.9], 200, 250, fill=color, width=w)
    elif kind == "key":
        draw.line([(cx - r * 0.82, cy + r * 0.62), (cx + r * 0.82, cy + r * 0.62)], fill=color, width=w)
        draw.line([(cx - r * 0.5, cy + r * 0.6), (cx + r * 0.55, cy - r * 0.55)], fill=color, width=w)
        draw.ellipse([cx + r * 0.38, cy - r * 0.78, cx + r * 0.76, cy - r * 0.40], outline=color, width=w)
    elif kind == "ear":
        draw.arc([cx - r * 0.72, cy - r * 0.85, cx + r * 0.72, cy + r * 0.85], 30, 320, fill=color, width=w)
        draw.arc([cx - r * 0.28, cy - r * 0.34, cx + r * 0.28, cy + r * 0.34], 20, 300, fill=color, width=w)
        draw.ellipse([cx - w, cy + r * 0.32, cx + w, cy + r * 0.32 + w * 2], fill=color)
    elif kind == "dtmf":
        for row in range(3):
            for col in range(3):
                bx = cx - r * 0.62 + col * r * 0.62
                by = cy - r * 0.62 + row * r * 0.62
                draw.rounded_rectangle([bx - r * 0.17, by - r * 0.17, bx + r * 0.17, by + r * 0.17],
                                       radius=r * 0.07, fill=color)
    elif kind == "tone":
        for i, hh in enumerate((0.35, 0.75, 1.0, 0.6, 0.85)):
            bx = cx - r * 0.78 + i * r * 0.39
            draw.rounded_rectangle([bx, cy - r * hh * 0.8, bx + r * 0.20, cy + r * hh * 0.8],
                                   radius=r * 0.1, fill=color)


# ---------------------------------------------------------------- 场景 1：品牌开场


def scene_intro(t):
    global BG
    img = BG.copy().convert("RGBA")
    cx, cy = W / 2, H * 0.40

    # 中心微光
    rise = ease_out(seg(t, 0.0, 1.4))
    img = add_glow(img, cx, cy, 300 + 120 * rise, BRAND, alpha=int(40 + 60 * rise), softness=0.38)
    pulse = 0.5 + 0.5 * math.sin(t * 2.4)
    img = add_glow(img, cx, cy, 210, BRAND_HI, alpha=int(28 + 26 * pulse), softness=0.5)

    # 扩散波环
    for i, base in enumerate((1.05, 1.45, 1.85)):
        p = seg(t, base, 1.9)
        if 0 < p < 1:
            r = 120 + 520 * ease_out(p, 2.2)
            a = int(120 * (1 - p) ** 1.6)
            ring = Image.new("RGBA", (int(r * 2) + 8, int(r * 2) + 8), (0, 0, 0, 0))
            ImageDraw.Draw(ring).ellipse(
                [4, 4, ring.size[0] - 5, ring.size[1] - 5],
                outline=BRAND_HI + (a,), width=max(1, int(3 - 1.6 * p)))
            img.alpha_composite(ring, (int(cx - ring.size[0] / 2), int(cy - ring.size[1] / 2)))

    # Logo
    p_logo = seg(t, 0.35, 1.05)
    if p_logo > 0:
        sc = 0.55 + 0.45 * ease_out_back(p_logo, 1.2)
        dia = 300 * sc
        a = int(255 * clamp(p_logo * 1.7))
        draw_wave_logo(img, cx, cy, dia,
                       color=(255, 255, 255, a), bg=BRAND + (int(a * 0.95),))

    # 主标题
    p_t = seg(t, 1.42, 0.85)
    if p_t > 0:
        dy = 34 * (1 - ease_out(p_t))
        a = int(255 * p_t)
        f = font(92, "bold")
        tracked_text(ImageDraw.Draw(img), (cx, cy + 250 + dy), "HAM 工具合集",
                     f, TXT + (a,), tracking=lerp(26, 6, ease_out(p_t)), anchor="c")

    # 副标题
    p_s = seg(t, 2.05, 0.80)
    if p_s > 0:
        dy = 22 * (1 - ease_out(p_s))
        a = int(215 * p_s)
        f = font(36, "reg")
        tracked_text(ImageDraw.Draw(img), (cx, cy + 318 + dy), "业余无线电 · 口袋工具箱",
                     f, TXT_DIM + (a,), tracking=6, anchor="c")

    # 分隔线
    p_l = seg(t, 2.55, 0.75)
    if p_l > 0:
        half = 150 * ease_out(p_l)
        a = int(90 * p_l)
        ImageDraw.Draw(img).line([(cx - half, cy + 372), (cx + half, cy + 372)],
                                 fill=BRAND_HI + (a,), width=2)

    # 底部一行
    p_b = seg(t, 2.95, 0.80)
    if p_b > 0:
        a = int(150 * p_b)
        f = font(26, "light")
        tracked_text(ImageDraw.Draw(img), (cx, cy + 430), "19 个工具 · 离线优先可用",
                     f, TXT_DIM + (a,), tracking=3, anchor="c")

    # 结尾整体淡出（交给交叉淡化，但这里压一点亮度让过渡更自然）
    fade = 1 - 0.55 * seg(t, 4.05, 0.55)
    if fade < 1:
        img = Image.blend(Image.new("RGBA", img.size, (0, 0, 0, 255)), img, fade)

    return img.convert("RGB")


# ---------------------------------------------------------------- 场景 2：首页

PHONE_W, PHONE_H = 706, 1452
PHONE_R = 86

HOME_TOOLS = [
    ("频率波长", "频率与波长互算", "wave"),
    ("天线长度", "偶极子 · 垂直", "antenna"),
    ("驻波比回损", "VSWR 与回损", "vswr"),
    ("馈线损耗", "按型号估衰减", "cable"),
    ("业余频段划分", "中国与 IARU", "band"),
    ("呼号前缀", "约 300 个实体", "globe"),
    ("Q 简语", "含字母解释法", "q"),
    ("摩尔斯对照", "可发声", "morse"),
    ("CW 电键", "手动拍发练习", "key"),
    ("CW 听抄训练", "随机报底", "ear"),
    ("DTMF", "双音多频", "dtmf"),
    ("音调发生器", "正弦 · 白噪", "tone"),
]

HOME_SECTIONS = [
    ("计算工具", HOME_TOOLS[0:4]),
    ("查询手册", HOME_TOOLS[4:8]),
    ("音频工具", HOME_TOOLS[8:12]),
]

HEADER_H = 150  # 状态栏 + 标题栏占位，滚动层从这里开始


def draw_home_content(scroll=0.0, t=0.0):
    """在 PHONE_W × PHONE_H 画布上绘制首页：标题栏固定，下方内容独立滚动。"""
    cw, ch = PHONE_W, PHONE_H
    lay = Image.new("RGBA", (cw, ch), (13, 18, 17, 255))
    d = ImageDraw.Draw(lay)

    # 顶部品牌色微光
    top = Image.new("RGBA", (cw, 260), (0, 0, 0, 0))
    td = ImageDraw.Draw(top)
    for i in range(260):
        td.line([(0, i), (cw, i)], fill=(42, 217, 176, int(15 * (1 - i / 260.0) ** 1.6)))
    lay.alpha_composite(top, (0, 0))

    # 状态栏
    d.text((34, 46), "9:41", font=font(20, "reg"), fill=TXT + (200,), anchor="ls")
    for i in range(4):
        h = 4 + i * 3
        d.rounded_rectangle([cw - 112 + i * 9, 46 - h, cw - 108 + i * 9, 46],
                            radius=2, fill=TXT + (150,))
    d.rounded_rectangle([cw - 58, 32, cw - 24, 50], radius=4, outline=TXT + (110,), width=2)
    d.rounded_rectangle([cw - 55, 35, cw - 31, 47], radius=2, fill=TXT + (110,))

    # 标题栏
    tracked_text(d, (34, 108), "HAM 工具合集", font(33, "bold"), TXT + (255,), tracking=1)
    d.text((cw - 34, 104), "v1.1 · BA4IGG", font=font(17, "reg"),
           fill=TXT_DIM + (255,), anchor="rs")

    margin, gap = 24, 14
    card_w = (cw - margin * 2 - gap) // 2
    card_h = 168

    # 滚动层
    slay = Image.new("RGBA", (cw, ch - HEADER_H), (0, 0, 0, 0))
    sd = ImageDraw.Draw(slay)

    def card(x, y, name, desc, glyph, alpha=255):
        if alpha <= 3:
            return
        panel = rounded_panel((card_w, card_h), 24, CARD_BG + (alpha,),
                              outline=CARD_LINE + (int(alpha * 0.7),), outline_w=1)
        slay.alpha_composite(panel, (int(x), int(y)))
        dd = ImageDraw.Draw(slay)
        ib = 44
        dd.rounded_rectangle([x + 20, y + 22, x + 20 + ib, y + 22 + ib], radius=13,
                             fill=BRAND_HI + (int(alpha * 0.13),))
        draw_tool_glyph(dd, x + 20 + ib / 2, y + 22 + ib / 2, 26, glyph,
                        BRAND_HI + (int(alpha * 0.95),))
        dd.text((x + 20, y + 96), name, font=font(25, "bold"), fill=TXT + (alpha,), anchor="ls")
        dd.text((x + 20, y + 130), desc, font=font(16, "reg"),
                fill=TXT_DIM + (int(alpha * 0.95),), anchor="ls")

    y_cur = 20 - scroll
    idx = 0
    for sec_title, tools in HOME_SECTIONS:
        p_sec = seg(t, 0.30 + idx * 0.045, 0.55)
        if p_sec > 0:
            tracked_text(sd, (margin + 4, y_cur + 26), sec_title, font(19, "bold"),
                         TXT_DIM + (int(230 * p_sec),), tracking=3)
        y_cur += 34
        for i, (n, ds, g) in enumerate(tools):
            col, row = i % 2, i // 2
            x = margin + col * (card_w + gap)
            y = y_cur + row * (card_h + gap)
            p_c = seg(t, 0.42 + idx * 0.05, 0.55)
            if p_c > 0:
                card(x, y + 24 * (1 - ease_out(p_c, 3)), n, ds, g, alpha=int(255 * p_c))
            idx += 1
        y_cur += ((len(tools) + 1) // 2) * (card_h + gap) + 18

    # 顶部渐隐，避免卡片被标题栏硬切
    fade = Image.new("RGBA", (cw, 34), (0, 0, 0, 0))
    fd = ImageDraw.Draw(fade)
    for i in range(34):
        fd.line([(0, i), (cw, i)], fill=(13, 18, 17, int(255 * (1 - i / 34.0))))
    slay.alpha_composite(fade, (0, 0))

    lay.alpha_composite(slay, (0, HEADER_H))

    # 底部 Dock（对应 App 的三页导航）
    dock_h = 128
    dock = Image.new("RGBA", (cw, dock_h), (0, 0, 0, 0))
    dk = ImageDraw.Draw(dock)
    dk.rounded_rectangle([110, 8, cw - 110, dock_h - 12], radius=54,
                         fill=(24, 32, 30, 245), outline=(56, 72, 68, 255), width=1)
    items = [cw / 2 - 150, cw / 2, cw / 2 + 150]
    for i, ix in enumerate(items):
        if i == 0:  # 首页：品牌色胶囊
            dk.rounded_rectangle([ix - 74, 22, ix + 74, dock_h - 26], radius=40,
                                 fill=BRAND_HI + (36,))
            dk.ellipse([ix - 9, dock_h / 2 - 9, ix + 9, dock_h / 2 + 9], fill=BRAND_SOFT + (255,))
        elif i == 1:
            dk.rounded_rectangle([ix - 11, dock_h / 2 - 11, ix + 11, dock_h / 2 + 11],
                                 radius=6, outline=TXT_DIM + (190,), width=3)
        else:
            dk.ellipse([ix - 11, dock_h / 2 - 14, ix + 11, dock_h / 2 + 4],
                       outline=TXT_DIM + (190,), width=3)
            dk.arc([ix - 15, dock_h / 2 + 2, ix + 15, dock_h / 2 + 34], 180, 360,
                   fill=TXT_DIM + (190,), width=3)
    lay.alpha_composite(dock, (0, ch - dock_h))
    return lay



def scene_home(t):
    img = BG.copy().convert("RGBA")
    cx, cy = W / 2, H / 2 + 40

    p = seg(t, 0.0, 0.85)
    if p <= 0:
        return img.convert("RGB")

    # 手机外框
    dy = 96 * (1 - ease_out(p, 3))
    a = clamp(p * 1.25)
    px, py = cx - PHONE_W / 2, cy - PHONE_H / 2 + dy

    img = add_glow(img, cx, cy - 40, 470, BRAND, alpha=int(46 * a), softness=0.42)

    # 屏幕内容（带滚动）
    scroll = 210 * ease_in_out(seg(t, 3.55, 2.0))
    content = draw_home_content(scroll, t)

    shell = Image.new("RGBA", (PHONE_W + 18, PHONE_H + 18), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shell)
    sd.rounded_rectangle([0, 0, shell.size[0] - 1, shell.size[1] - 1], radius=PHONE_R + 9,
                         fill=(36, 46, 44, int(255 * a)))
    sd.rounded_rectangle([0, 0, shell.size[0] - 1, shell.size[1] - 1], radius=PHONE_R + 9,
                         outline=(88, 112, 106, int(190 * a)), width=2)
    sd.rounded_rectangle([4, 4, shell.size[0] - 5, shell.size[1] - 5], radius=PHONE_R + 5,
                         outline=(16, 22, 21, int(220 * a)), width=2)
    img.alpha_composite(shell, (int(px - 9), int(py - 9)))

    mask = Image.new("L", (PHONE_W, PHONE_H), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, PHONE_W - 1, PHONE_H - 1], radius=PHONE_R, fill=255)
    if a < 1:
        content.putalpha(Image.eval(mask, lambda v: int(v * a)))
    else:
        content.putalpha(mask)
    img.alpha_composite(content, (int(px), int(py)))

    # 边框扫光
    sp = seg(t, 0.55, 1.5)
    if 0 < sp < 1:
        hl = Image.new("RGBA", (PHONE_W + 18, PHONE_H + 18), (0, 0, 0, 0))
        hd = ImageDraw.Draw(hl)
        yh = -200 + (PHONE_H + 400) * ease_out(sp, 1.6)
        hd.rounded_rectangle([0, yh, hl.size[0], yh + 90], radius=PHONE_R,
                             fill=(124, 245, 218, 26))
        hl = hl.filter(ImageFilter.GaussianBlur(26))
        m2 = Image.new("L", hl.size, 0)
        ImageDraw.Draw(m2).rounded_rectangle([0, 0, hl.size[0] - 1, hl.size[1] - 1],
                                             radius=PHONE_R + 9, fill=255)
        hl.putalpha(Image.composite(hl.getchannel("A"), Image.new("L", hl.size, 0), m2))
        img.alpha_composite(hl, (int(px - 9), int(py - 9)))

    return img.convert("RGB")


# ---------------------------------------------------------------- 场景 3：计算


def scene_calc(t):
    img = BG.copy().convert("RGBA")
    cx = W / 2

    img = add_glow(img, cx, H * 0.50, 430, BRAND, alpha=44, softness=0.45)
    d = ImageDraw.Draw(img)

    # 底部波形
    wl = Image.new("RGBA", (W, 420), (0, 0, 0, 0))
    wd = ImageDraw.Draw(wl)
    for k in range(3):
        pts = []
        for i in range(0, W + 8, 8):
            ph = i / 108.0 - t * 1.05 + k * 0.55
            pts.append((i, 210 + 34 * math.sin(ph) * math.exp(-((i - W / 2) / (W * 0.55)) ** 2)))
        wd.line(pts, fill=BRAND_HI + (18 - k * 5,), width=2)
    img.alpha_composite(wl, (0, H - 470))

    # 顶部标签
    p_in = seg(t, 0.0, 0.7)
    a_in = int(255 * p_in)
    pill_f = font(24, "bold")
    label = "频率 → 波长"
    tw = text_width(d, label, pill_f, 2)
    pw, ph = tw + 62, 56
    pill = rounded_panel((int(pw), int(ph)), 28, (20, 40, 36, int(215 * p_in)),
                         outline=BRAND_HI + (int(90 * p_in),), outline_w=1)
    img.alpha_composite(pill, (int(cx - pw / 2), int(H * 0.235)))
    tracked_text(d, (cx, H * 0.235 + 26), label, pill_f, BRAND_SOFT + (a_in,), tracking=2, anchor="c")

    # 输入频率数字
    v1 = 145.000 * ease_out(seg(t, 0.25, 1.35), 2.4)
    f_big = font(126, "bold")
    f_unit = font(38, "reg")
    y1 = H * 0.335
    s1 = f"{v1:.3f}"
    w1 = d.textlength(s1, font=f_big)
    wu = d.textlength("MHz", font=f_unit)
    total = w1 + 14 + wu
    x0 = cx - total / 2
    d.text((x0, y1), s1, font=f_big, fill=TXT + (255,), anchor="ls")
    d.text((x0 + w1 + 14, y1), "MHz", font=f_unit, fill=TXT_DIM + (255,), anchor="ls")

    # 分隔线
    p_l = seg(t, 1.30, 0.9)
    if p_l > 0:
        half = 300 * ease_out(p_l)
        d.line([(cx - half, H * 0.395), (cx + half, H * 0.395)], fill=(70, 92, 86, int(255 * p_l)), width=2)

    # 结果
    p_r = seg(t, 1.55, 1.0)
    a_r = int(255 * p_r)
    if p_r > 0:
        glow_a = int((52 + 40 * (0.5 + 0.5 * math.sin(t * 3.0))) * clamp((p_r - 0.35) * 2.2))
        if glow_a > 0:
            add_glow(img, cx, H * 0.530, 260, BRAND_HI, alpha=glow_a, softness=0.52)
            d = ImageDraw.Draw(img)
        d.text((cx, H * 0.432), "波长 λ", font=font(26, "reg"), fill=TXT_DIM + (a_r,), anchor="ms")
        v2 = 2.067 * ease_out(seg(t, 1.7, 1.35), 2.6)
        s2 = f"{v2:.3f}"
        f_res = font(150, "bold")
        w2 = d.textlength(s2, font=f_res)
        wm = d.textlength("m", font=font(44, "reg"))
        tot2 = w2 + 16 + wm
        x2 = cx - tot2 / 2
        d.text((x2, H * 0.530), s2, font=f_res, fill=BRAND_SOFT + (a_r,), anchor="ls")
        d.text((x2 + w2 + 16, H * 0.530), "m", font=font(44, "reg"), fill=TXT_DIM + (a_r,), anchor="ls")

    # 两个派生值
    chips = [("λ / 4", "0.517 m"), ("λ / 2", "1.034 m")]
    for i, (k, v) in enumerate(chips):
        p_c = seg(t, 2.55 + i * 0.16, 0.65)
        if p_c <= 0:
            continue
        a_c = int(255 * p_c)
        yy = H * 0.625 + 26 * (1 - ease_out(p_c))
        cwid = 250
        xx = cx - cwid - 12 if i == 0 else cx + 12
        panel = rounded_panel((cwid, 116), 26, (17, 25, 23, int(240 * p_c)),
                              outline=(44, 58, 54, int(210 * p_c)), outline_w=1)
        img.alpha_composite(panel, (int(xx), int(yy)))
        dd = ImageDraw.Draw(img)
        dd.text((xx + 26, yy + 40), k, font=font(22, "reg"), fill=TXT_DIM + (a_c,), anchor="ls")
        dd.text((xx + 26, yy + 84), v, font=font(34, "bold"), fill=TXT + (a_c,), anchor="ls")

    # 底部说明
    p_n = seg(t, 3.15, 0.7)
    if p_n > 0:
        d = ImageDraw.Draw(img)
        tracked_text(d, (cx, H * 0.760), "5 个计算工具 · 输入即时出结果", font(24, "light"),
                     TXT_DIM + (int(190 * p_n),), tracking=3, anchor="c")

    return img.convert("RGB")


# ---------------------------------------------------------------- 场景 4：查询手册

BANDS = [
    ("14.000 – 14.350", "20 m", "14.0 – 14.35 MHz 全模式"),
    ("21.000 – 21.450", "15 m", "21.0 – 21.45 MHz 全模式"),
    ("28.000 – 29.700", "10 m", "28.0 – 29.7 MHz 全模式"),
]


def draw_band_rows(img, t):
    d = ImageDraw.Draw(img)
    cx = W / 2
    d.text((cx, H * 0.245), "业余频段划分", font=font(30, "reg"), fill=TXT + (255,), anchor="ms")
    top = H * 0.305
    for i, (rng, band, note) in enumerate(BANDS):
        p = seg(t, 0.15 + i * 0.14, 0.85)
        if p <= 0:
            continue
        a = int(255 * p)
        dy = 46 * (1 - ease_out(p, 2.5))
        x, y = 96, top + i * 176 + dy
        wid = W - 192
        panel = rounded_panel((wid, 148), 30, (17, 25, 23, int(245 * p)),
                              outline=(42, 56, 52, int(230 * p)), outline_w=1)
        img.alpha_composite(panel, (int(x), int(y)))
        dd = ImageDraw.Draw(img)
        dd.text((x + 40, y + 62), rng, font=font(46, "bold"), fill=TXT + (a,), anchor="ls")
        dd.text((x + 40, y + 108), note, font=font(20, "reg"), fill=TXT_FAINT + (a,), anchor="ls")
        # 右侧波段标签
        bw = 116
        dd.rounded_rectangle([x + wid - bw - 32, y + 42, x + wid - 32, y + 106], radius=18,
                             fill=BRAND_HI + (int(30 * p),), outline=BRAND_HI + (int(90 * p),), width=1)
        dd.text((x + wid - bw / 2 - 32, y + 84), band, font=font(30, "bold"),
                fill=BRAND_SOFT + (a,), anchor="ms")

    p_n = seg(t, 0.90, 0.7)
    if p_n > 0:
        dd.text((cx, H * 0.680), "中国与 IARU 三区 · 区域切换 · 关键词检索", font=font(23, "light"),
                fill=TXT_DIM + (int(190 * p_n),), anchor="ms")


MORSE_CQ = [("C", "-·-·"), ("Q", "--·-")]

DOT_W, DASH_W, SYM_GAP, CHAR_ADV = 18, 74, 20, 96


def _group_width(code):
    return CHAR_ADV + sum((DASH_W if s == "-" else DOT_W) + SYM_GAP for s in code) - SYM_GAP


def draw_morse_block(img, t):
    d = ImageDraw.Draw(img)
    cx = W / 2
    d.text((cx, H * 0.245), "摩尔斯对照 · 可发声", font=font(30, "reg"),
           fill=TXT + (255,), anchor="ms")

    y = H * 0.365          # 字符基线
    beat = 0.235           # 每格 0.235s，8 格约 1.9s，正好在 3s 内播完
    lit = (t - 0.40) / beat

    total = _group_width(MORSE_CQ[0][1]) + 110 + _group_width(MORSE_CQ[1][1])
    x = cx - total / 2

    idx = 0
    for ch, code in MORSE_CQ:
        p = seg(t, 0.15, 0.45)
        if p > 0:
            d.text((x, y), ch, font=font(104, "bold"), fill=TXT + (int(255 * p),), anchor="ls")
        xx = x + CHAR_ADV
        for sym in code:
            if idx < lit - 1:
                col = mix((52, 66, 62), BRAND_HI, 0.72)
            elif idx < lit:
                # 刚点亮：先全亮，再回落到常亮
                col = mix(BRAND_HI, BRAND_SOFT, clamp((lit - idx) * 1.8))
            else:
                col = (52, 66, 62)
            if sym == "-":
                d.rounded_rectangle([xx, y - 24, xx + DASH_W, y - 6], radius=9, fill=col + (255,))
                xx += DASH_W + SYM_GAP
            else:
                d.ellipse([xx, y - 24, xx + DOT_W, y - 6], fill=col + (255,))
                xx += DOT_W + SYM_GAP
            idx += 1
        x += _group_width(code) + 110

    # 声波条
    p_w = seg(t, 0.55, 0.6)
    if p_w > 0:
        bars, bw2 = 32, 12
        bx0, by = cx - (bars * bw2) / 2, H * 0.455
        for i in range(bars):
            phase = t * 7.5 + i * 0.55
            amp = (0.30 + 0.70 * abs(math.sin(phase))) * (0.5 + 0.5 * math.sin(i / bars * math.pi))
            hh = 6 + amp * 46
            d.rounded_rectangle([bx0 + i * bw2, by - hh / 2, bx0 + i * bw2 + 5, by + hh / 2],
                                radius=3, fill=BRAND_HI + (int(150 * p_w),))

    p_n = seg(t, 1.0, 0.7)
    if p_n > 0:
        d.text((cx, H * 0.520), "5 – 40 WPM 可调", font=font(26, "reg"),
               fill=TXT_DIM + (int(220 * p_n),), anchor="ms")
        d.text((cx, H * 0.556), "波形由 AudioTrack 现场合成，不打包音频文件", font=font(22, "light"),
               fill=TXT_DIM + (int(200 * p_n),), anchor="ms")


def scene_reference(t):
    img = BG.copy().convert("RGBA")
    img = add_glow(img, W / 2, H * 0.45, 420, BRAND, alpha=40, softness=0.46)

    # 两拍交叉淡化
    xf = 0.6
    if t < 3.0 - xf:
        draw_band_rows(img, t)
    elif t > 3.0 + xf:
        draw_morse_block(img, t - 3.0)
    else:
        w = (t - (3.0 - xf)) / (2 * xf)
        a_img = BG.copy().convert("RGBA")
        draw_band_rows(a_img, t)
        b_img = BG.copy().convert("RGBA")
        draw_morse_block(b_img, t - 3.0 + 0.15)
        img = Image.blend(a_img, b_img, clamp(w))

    return img.convert("RGB")


# ---------------------------------------------------------------- 场景 5：卖点

FEATURES = [
    ("19", "个工具", "计算 · 查询 · 音频 · 联网，数据全部内置"),
    ("100%", "离线可用", "断网、飞行模式、无信号的山顶都能算"),
    ("MIT", "开源免费", "无广告，无跟踪，代码全部公开"),
]


def scene_features(t):
    img = BG.copy().convert("RGBA")
    cx = W / 2

    img = add_glow(img, cx, H * 0.42, 460, BRAND, alpha=42, softness=0.48)
    d = ImageDraw.Draw(img)

    p0 = seg(t, 0.0, 0.6)
    if p0 > 0:
        tracked_text(d, (cx, H * 0.295), "为什么用它", font(26, "light"),
                     TXT_FAINT + (int(200 * p0),), tracking=6, anchor="c")

    for i, (big, mid, sub) in enumerate(FEATURES):
        p = seg(t, 0.35 + i * 0.42, 0.9)
        if p <= 0:
            continue
        a = int(255 * p)
        dy = 40 * (1 - ease_out(p, 3))
        yy = H * 0.405 + i * 330 + dy

        f_big = font(96, "bold")
        f_mid = font(38, "bold")
        wb = d.textlength(big, font=f_big)
        wm2 = d.textlength(mid, font=f_mid)
        total = wb + 16 + wm2
        x0 = cx - total / 2
        d.text((x0, yy), big, font=f_big, fill=BRAND_SOFT + (a,), anchor="ls")
        d.text((x0 + wb + 16, yy), mid, font=f_mid, fill=TXT + (a,), anchor="ls")
        d.text((cx, yy + 62), sub, font=font(24, "reg"), fill=TXT_DIM + (int(215 * p),), anchor="ms")

        if i < len(FEATURES) - 1:
            pl = seg(t, 0.55 + i * 0.42, 0.7)
            if pl > 0:
                half = 130 * ease_out(pl)
                d.line([(cx - half, yy + 128), (cx + half, yy + 128)],
                       fill=(58, 74, 70, int(140 * pl)), width=1)

    return img.convert("RGB")


# ---------------------------------------------------------------- 场景 6：片尾


def scene_outro(t):
    img = BG.copy().convert("RGBA")
    cx = W / 2
    cy = H * 0.435

    img = add_glow(img, cx, cy, 340, BRAND, alpha=46, softness=0.46)
    d = ImageDraw.Draw(img)

    p_l = seg(t, 0.0, 0.9)
    if p_l > 0:
        sc = 0.86 + 0.14 * ease_out_back(p_l, 1.1)
        draw_wave_logo(img, cx, cy, 196 * sc, color=(255, 255, 255, int(255 * p_l)),
                       bg=BRAND + (int(250 * p_l),))

    p_t = seg(t, 0.35, 0.8)
    if p_t > 0:
        a = int(255 * p_t)
        tracked_text(d, (cx, cy + 196), "HAM 工具合集", font(64, "bold"),
                     TXT + (a,), tracking=lerp(18, 4, ease_out(p_t)), anchor="c")

    p_g = seg(t, 0.80, 0.8)
    if p_g > 0:
        a = int(230 * p_g)
        d.rounded_rectangle([cx - 268, cy + 250, cx + 268, cy + 326], radius=38,
                            fill=(17, 25, 23, int(240 * p_g)),
                            outline=(44, 58, 54, int(220 * p_g)), width=1)
        d.text((cx, cy + 296), "github.com/BA4IGG/hamtools", font=font(27, "reg"),
               fill=BRAND_SOFT + (a,), anchor="ms")

    p_s = seg(t, 1.15, 0.8)
    if p_s > 0:
        tracked_text(d, (cx, cy + 392), "作者 BA4IGG · MIT License · 2026", font(23, "light"),
                     TXT_FAINT + (int(220 * p_s),), tracking=2, anchor="c")

    # 收尾淡出
    fade = 1 - seg(t, 3.15, 0.80)
    if fade < 1:
        img = Image.blend(Image.new("RGBA", img.size, (0, 0, 0, 255)), img, clamp(fade))

    return img.convert("RGB")


# ---------------------------------------------------------------- 时间轴

SCENES = [
    (0.00, 4.60, scene_intro),
    (4.60, 10.45, scene_home),
    (10.45, 16.70, scene_calc),
    (16.70, 22.75, scene_reference),
    (22.75, 26.95, scene_features),
    (26.95, 31.00, scene_outro),
]
XFADE = 0.55


def render_at(t):
    for i, (s, e, fn) in enumerate(SCENES):
        if s <= t < e or (i == len(SCENES) - 1 and t >= s):
            dur = e - s
            if t > e - XFADE and i + 1 < len(SCENES):
                w = (t - (e - XFADE)) / XFADE
                a = fn(t - s)
                b = SCENES[i + 1][2](max(0.0, t - e))
                return Image.blend(a, b, ease_in_out(clamp(w)))
            return fn(min(t - s, dur))
    return SCENES[-1][2](0.0)


# ---------------------------------------------------------------- 音频


def build_audio(path, sr=44100):
    n = int(DUR * sr)
    tt = np.arange(n) / sr
    out = np.zeros((n, 2), np.float64)

    # 和弦进行：Am9 → Fmaj7 → Cmaj7 → G6
    chords = [
        (0.0, 8.2, [110.0, 261.6, 329.6, 392.0, 493.9]),
        (7.6, 15.6, [87.3, 220.0, 261.6, 329.6]),
        (15.0, 23.4, [130.8, 261.6, 329.6, 493.9]),
        (22.8, 26.6, [98.0, 246.9, 293.7, 329.6]),
        (26.6, DUR, [110.0, 220.0, 277.2, 329.6, 440.0]),
    ]
    for start, end, freqs in chords:
        i0, i1 = int(start * sr), min(n, int(end * sr))
        if i1 <= i0:
            continue
        seg_t = np.arange(i1 - i0) / sr
        env = np.ones(i1 - i0)
        fade = int(1.1 * sr)
        if len(env) > 2 * fade:
            env[:fade] = np.linspace(0, 1, fade) ** 1.5
            env[-fade:] = np.linspace(1, 0, fade) ** 1.5
        for k, f in enumerate(freqs):
            amp = 0.16 / (1 + k * 0.35)
            for det, da in ((1.0, 1.0), (1.0021, 0.55), (0.9979, 0.55)):
                ph = 2 * np.pi * f * det * seg_t + k * 0.7
                out[i0:i1, 0] += amp * da * np.sin(ph) * env * 0.5
                out[i0:i1, 1] += amp * da * np.sin(ph + 0.35) * env * 0.5
        # 低频铺底
        out[i0:i1, 0] += 0.10 * np.sin(2 * np.pi * freqs[0] / 2 * seg_t) * env
        out[i0:i1, 1] += 0.10 * np.sin(2 * np.pi * freqs[0] / 2 * seg_t) * env

    # 缓慢的整体起伏，避免像长音嗡鸣
    lfo = 0.86 + 0.14 * np.sin(2 * np.pi * 0.078 * tt)
    out *= lfo[:, None]

    # 转场 whoosh
    rng = np.random.default_rng(3)
    for tc in (4.6, 10.45, 16.7, 22.75, 26.95):
        dur = 0.75
        i0 = int((tc - 0.45) * sr)
        ln = int(dur * sr)
        if i0 < 0 or i0 + ln > n:
            continue
        noise = rng.normal(0, 1, ln)
        # 简易一阶低通，截止频率随时间上升 → 呼啸感
        y = np.zeros(ln)
        prev = 0.0
        for j in range(ln):
            fc = 0.02 + 0.30 * (j / ln) ** 2
            prev = prev + fc * (noise[j] - prev)
            y[j] = prev
        envw = np.sin(np.linspace(0, np.pi, ln)) ** 2
        y = y / (np.max(np.abs(y)) + 1e-9) * envw * 0.055
        out[i0:i0 + ln, 0] += y
        out[i0:i0 + ln, 1] += y * 0.9

    # 片尾提示音
    i0 = int(27.6 * sr)
    ln = min(n - i0, int(2.2 * sr))
    if ln > 0:
        seg_t = np.arange(ln) / sr
        tone = (np.sin(2 * np.pi * 880 * seg_t) * 0.5 + np.sin(2 * np.pi * 1320 * seg_t) * 0.28
                + np.sin(2 * np.pi * 1760 * seg_t) * 0.12)
        envd = np.exp(-seg_t * 2.6) * (1 - np.exp(-seg_t * 90))
        out[i0:i0 + ln, 0] += tone * envd * 0.10
        out[i0:i0 + ln, 1] += tone * envd * 0.10

    # 简单延迟混响
    dly = int(0.19 * sr)
    wet = out.copy()
    for k in (1, 2, 3):
        shift = dly * k
        if shift < n:
            wet[shift:] += out[:n - shift] * (0.26 ** k)
    out = out * 0.72 + wet * 0.28

    peak = np.max(np.abs(out))
    if peak > 0:
        out = out / peak * 0.78
    pcm = (np.clip(out, -1, 1) * 32767).astype(np.int16)

    import wave
    with wave.open(path, "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(sr)
        w.writeframes(pcm.tobytes())


# ---------------------------------------------------------------- 主流程


def main():
    global BG
    mode = sys.argv[1] if len(sys.argv) > 1 else "preview"

    print("[1/4] 构建背景…")
    BG = build_background()

    if mode == "preview":
        shots = [0.9, 2.6, 3.9, 6.2, 9.0, 12.2, 14.6, 18.2, 21.0, 24.6, 29.2]
        os.makedirs(os.path.join(HERE, "preview"), exist_ok=True)
        for t in shots:
            img = render_at(t)
            img.save(os.path.join(HERE, "preview", f"f{t:05.2f}.png"))
            print(f"  预览帧 {t:>5.2f}s")
        print("预览帧已输出到 promo/preview/")
        return

    from imageio_ffmpeg import get_ffmpeg_exe
    ffmpeg = get_ffmpeg_exe()

    print("[2/4] 生成音频…")
    audio = os.path.join(HERE, "audio.wav")
    build_audio(audio)

    print("[3/4] 渲染视频帧…")
    out = os.path.join(HERE, "HAM-Tools-promo.mp4")
    cmd = [
        ffmpeg, "-y",
        "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-",
        "-i", audio,
        "-c:v", "libx264", "-preset", "slow", "-crf", "18",
        "-pix_fmt", "yuv420p", "-profile:v", "high", "-level", "4.2",
        "-c:a", "aac", "-b:a", "192k",
        "-movflags", "+faststart",
        "-shortest", out,
    ]
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=subprocess.DEVNULL,
                            stderr=subprocess.PIPE)
    total = int(DUR * FPS)
    for i in range(total):
        t = i / FPS
        img = render_at(t)
        proc.stdin.write(img.tobytes())
        if i % 60 == 0:
            print(f"  {i}/{total} 帧  ({i / total * 100:5.1f}%)", flush=True)
    proc.stdin.close()
    err = proc.stderr.read().decode("utf-8", "replace")
    code = proc.wait()
    if code != 0:
        print(err[-3000:])
        raise SystemExit(f"ffmpeg 失败，退出码 {code}")

    print(f"[4/4] 完成：{out}  ({os.path.getsize(out) / 1024 / 1024:.2f} MB)")


if __name__ == "__main__":
    main()
