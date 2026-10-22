#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Universal Client — генератор фирменного ресурспака Minecraft «Rounded UI».

Создаёт 5 паков (по одному на цветовую тему лаунчера):
    src/main/resources/pack/UniversalClient-Pack-<theme>.zip
и копию по умолчанию UniversalClient-Pack.zip (фиолетовая тема).

Запуск:        python tools/generate_assets.py            (нужны Pillow и numpy)
Предпросмотр:  python tools/generate_assets.py --preview  -> tools/preview_<theme>.png

Все спрайты рисуются в высоком разрешении (x4 ... x16): Minecraft масштабирует 9-slice
по долям, поэтому скругления получаются гладкими, а не «лесенкой».
Цвета тем взяты из Theme.java лаунчера — игра совпадает с лаунчером.
"""
import colorsys
import io
import json
import os
import sys
import zipfile

import numpy as np
from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TOOLS = os.path.join(ROOT, "tools")
OUT = os.path.join(ROOT, "src", "main", "resources", "pack")
FONT_BOLD = os.path.join(TOOLS, "fonts", "Poppins-Bold.ttf")
FONT_MED = os.path.join(TOOLS, "fonts", "Poppins-Medium.ttf")

# id, название, §-код цвета в описании, a, b, c  (как в Theme.java)
THEMES = [
    ("purple", "Фиолетовый", "§d", "#8b5cf6", "#6366f1", "#22d3ee"),
    ("ocean", "Океан", "§b", "#0ea5e9", "#3b82f6", "#a78bfa"),
    ("rose", "Роза", "§c", "#ec4899", "#f43f5e", "#fb923c"),
    ("emerald", "Изумруд", "§a", "#10b981", "#14b8a6", "#a3e635"),
    ("sunset", "Закат", "§6", "#f97316", "#ef4444", "#facc15"),
]

SPLASHES = [
    "Universal Client!", "Визуалы нового уровня!", "Скруглённые кнопки!", "Чистый FPS, красивая картинка!",
    "Стиль — это всё!", "Сделано с любовью!", "Играй красиво!", "Минимум лагов, максимум визуала!",
    "Добро пожаловать!", "Один клик до игры!", "Тема подстроена под лаунчер!", "Гладкие углы — гладкая игра!",
    "Эстетика на максималках!", "Выглядит дорого!", "Пять тем на любой вкус!", "Космос внутри меню!",
    "Не просто Minecraft!", "Ещё один блок — ещё один шаг!", "Крафти красиво!", "Всё на своих местах!",
    "Тёмная тема — светлое будущее!", "Идеальные скругления!", "Попробуй все цвета!", "Свечение включено!",
    "Каждый пиксель на месте!", "Построй что-нибудь красивое!", "Креатив без границ!", "Лови момент!",
    "Сегодня точно повезёт!", "Больше блоков, больше стиля!", "Ночь, звёзды и Minecraft!", "Играем!",
    "Любимая игра в новом свете!", "Пиксели тоже могут быть мягкими!", "Выбери свою тему!",
    "Тут всё сделано аккуратно!", "Гладко, как шёлк!", "С запуском!", "Фиолетовый — новый чёрный!",
    "Удачной игры!",
]


# ───────────────────────────── утилиты ─────────────────────────────

def C(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mix(c1, c2, t):
    return tuple(int(round(c1[i] + (c2[i] - c1[i]) * t)) for i in range(4))


def al(c, a):
    return (c[0], c[1], c[2], int(a))


def lighten(c, t):
    return mix(c, (255, 255, 255, c[3]), t)


def vgrad(W, H, top, bot):
    t = np.linspace(0, 1, H)[:, None, None]
    arr = np.array(top, float)[None, None, :] + (np.array(bot, float) - np.array(top, float))[None, None, :] * t
    arr = np.repeat(arr, W, axis=1)
    return Image.fromarray(np.clip(arr.round(), 0, 255).astype(np.uint8), "RGBA")


def hgrad(W, H, stops):
    xs = np.linspace(0, 1, W)
    out = np.zeros((1, W, 4), float)
    for ch in range(4):
        out[0, :, ch] = np.interp(xs, [s[0] for s in stops], [s[1][ch] for s in stops])
    out = np.repeat(out, H, axis=0)
    return Image.fromarray(np.clip(out.round(), 0, 255).astype(np.uint8), "RGBA")


def put(base, layer, mask=None):
    lay = layer.copy()
    if mask is not None:
        lay.putalpha(ImageChops.multiply(lay.getchannel("A"), mask))
    base.alpha_composite(lay)


def rect_mask(W, H, sc, x, y, w, h, r, corners=(True, True, True, True), K=3):
    """Маска скруглённого прямоугольника в логических единицах."""
    img = Image.new("L", (W * K, H * K), 0)
    d = ImageDraw.Draw(img)
    x0, y0 = x * sc * K, y * sc * K
    x1, y1 = (x + w) * sc * K - 1, (y + h) * sc * K - 1
    rad = max(0.0, min(r, w / 2.0, h / 2.0)) * sc * K
    d.rounded_rectangle([x0, y0, x1, y1], radius=rad, fill=255, corners=corners)
    return img.resize((W, H), Image.LANCZOS)


def layer_of(fill, W, H):
    return fill if isinstance(fill, Image.Image) else vgrad(W, H, fill[0], fill[1])


def panel(wL, hL, sc, r, fill=None, border=None, bw=1.0, pad=0.0, gloss=0.0, corners=(True, True, True, True)):
    """Скруглённая панель: заливка (градиент/картинка), обводка-градиент, блик сверху."""
    W, H = int(round(wL * sc)), int(round(hL * sc))
    out = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    outer = rect_mask(W, H, sc, pad, pad, wL - 2 * pad, hL - 2 * pad, r, corners)
    if border:
        inner = rect_mask(W, H, sc, pad + bw, pad + bw, wL - 2 * pad - 2 * bw, hL - 2 * pad - 2 * bw, r - bw, corners)
        put(out, layer_of(border, W, H), ImageChops.subtract(outer, inner))
    else:
        inner = outer
    if fill is not None:
        put(out, layer_of(fill, W, H), inner)
    if gloss > 0:
        gh = max(1, int(H * 0.5))
        g = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        g.paste(vgrad(W, gh, (255, 255, 255, int(255 * gloss)), (255, 255, 255, 0)), (0, 0))
        put(out, g, inner)
    return out


def nine(w, h, border):
    return {"gui": {"scaling": {"type": "nine_slice", "width": w, "height": h, "border": border}}}


class Pack:
    """Собирает zip в памяти."""

    def __init__(self):
        self.files = {}

    def png(self, rel, img):
        buf = io.BytesIO()
        img.save(buf, "PNG", optimize=True)
        self.files[rel] = buf.getvalue()

    def text(self, rel, s):
        self.files[rel] = s.encode("utf-8")

    def meta(self, rel, obj):
        self.text(rel, json.dumps(obj, indent=2, ensure_ascii=False))

    def sprite(self, name, img, scaling=None):
        rel = "assets/minecraft/textures/gui/sprites/" + name + ".png"
        self.png(rel, img)
        if scaling:
            self.meta(rel + ".mcmeta", scaling)

    def write(self, path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
            for k in sorted(self.files):
                z.writestr(k, self.files[k])


# ───────────────────────────── палитра ─────────────────────────────

BASE_TOP = C("#1f1f3a")
BASE_BOT = C("#141428")
EDGE_TOP = C("#3b3b66")
EDGE_BOT = C("#26264a")
FIELD = C("#0b0b16")


# ───────────────────────────── панорама (куб, 6 граней) ─────────────────────────────

PN = 512
FACES = [  # (forward, right, up)
    ((0, 0, -1), (1, 0, 0), (0, 1, 0)),
    ((1, 0, 0), (0, 0, 1), (0, 1, 0)),
    ((0, 0, 1), (-1, 0, 0), (0, 1, 0)),
    ((-1, 0, 0), (0, 0, -1), (0, 1, 0)),
    ((0, 1, 0), (1, 0, 0), (0, 0, 1)),
    ((0, -1, 0), (1, 0, 0), (0, 0, -1)),
]
_rng = np.random.default_rng(2026)
WAVES = []
for _ in range(14):
    _d = _rng.normal(size=3)
    _d /= np.linalg.norm(_d)
    WAVES.append((_d * _rng.uniform(1.2, 4.2), _rng.uniform(0, 6.28), _rng.uniform(0.4, 1.0)))
STARS = _rng.normal(size=(1100, 3))
STARS /= np.linalg.norm(STARS, axis=1, keepdims=True)
STAR_B = _rng.uniform(0.35, 1.0, len(STARS)) ** 2
STAR_C = _rng.choice(3, len(STARS))


def _field(d):
    s = np.zeros(d.shape[:2])
    for w, ph, a in WAVES:
        s += a * np.sin(d @ w + ph)
    s /= sum(a for _, _, a in WAVES) * 0.55
    return s


def _smooth(x, a, b):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def _face_dirs(face):
    f, r, u = [np.array(v, float) for v in FACES[face]]
    lin = (np.arange(PN) + 0.5) / PN * 2 - 1
    uu, vv = np.meshgrid(lin, -lin)
    d = f[None, None, :] + uu[..., None] * r + vv[..., None] * u
    return d / np.linalg.norm(d, axis=2, keepdims=True)


def make_face(face, A, B, Cc):
    ca, cb, cc = np.array(A[:3], float), np.array(B[:3], float), np.array(Cc[:3], float)
    d = _face_dirs(face)
    a = _field(d)
    b = _field(d[..., [2, 0, 1]] * 1.7 + 3.1)
    img = np.ones((PN, PN, 3)) * np.array([7, 7, 16], float)
    img += _smooth(a, 0.15, 1.1)[..., None] * (ca * 0.42) + _smooth(b, 0.25, 1.1)[..., None] * (cc * 0.28)
    img += np.exp(-(d[..., 1] ** 2) * 9)[..., None] * (cb * 0.22)
    pil = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))
    f, r, u = [np.array(v, float) for v in FACES[face]]
    layer = Image.new("RGB", (PN, PN), (0, 0, 0))
    dr = ImageDraw.Draw(layer)
    star_cols = [(255, 255, 255), tuple(int(v) for v in lighten(A, 0.6)[:3]), tuple(int(v) for v in lighten(Cc, 0.5)[:3])]
    for v, br, ci in zip(STARS, STAR_B, STAR_C):
        t = v @ f
        if t <= 0.01:
            continue
        x, y = (v @ r) / t, (v @ u) / t
        if abs(x) > 0.97 or abs(y) > 0.97:
            continue
        px, py = (x + 1) / 2 * PN, (1 - y) / 2 * PN
        col = tuple(int(k * br) for k in star_cols[ci])
        rad = 1.0 + br * 1.3
        dr.ellipse((px - rad, py - rad, px + rad, py + rad), fill=col)
    glow = layer.filter(ImageFilter.GaussianBlur(1.6))
    arr = np.clip(np.asarray(pil, float) + np.asarray(layer, float) + np.asarray(glow, float) * 0.8, 0, 255)
    return Image.fromarray(arr.astype(np.uint8))


def make_overlay(A):
    im = Image.new("RGBA", (16, 128))
    px = im.load()
    tint = mix((14, 8, 34, 255), A, 0.12)
    for y in range(128):
        a = int(40 + 150 * (y / 127) ** 2)
        for x in range(16):
            px[x, y] = (tint[0], tint[1], tint[2], a)
    return im


# ───────────────────────────── сборка темы ─────────────────────────────

def make_theme(tid, tname, code, ha, hb, hc):
    A, B, Cc = C(ha), C(hb), C(hc)
    P = Pack()

    # ═════════ кнопки (200x20, x4) ═════════
    sc, R, BO = 4, 5.0, 6
    P.sprite("widget/button", panel(200, 20, sc, R, (BASE_TOP, BASE_BOT), (EDGE_TOP, EDGE_BOT), 1.0, gloss=0.07), nine(200, 20, BO))
    hi_top, hi_bot = mix(BASE_TOP, A, 0.62), mix(BASE_BOT, B, 0.55)
    P.sprite("widget/button_highlighted",
             panel(200, 20, sc, R, (hi_top, hi_bot), (lighten(A, 0.45), mix(Cc, B, 0.35)), 1.2, gloss=0.16), nine(200, 20, BO))
    P.sprite("widget/button_disabled",
             panel(200, 20, sc, R, (C("#121222"), C("#0e0e1a")), (C("#1d1d32"), C("#171728")), 1.0), nine(200, 20, BO))

    # ═════════ слайдеры ═════════
    P.sprite("widget/slider", panel(200, 20, sc, R, (C("#0d0d1a"), C("#12121f")), (C("#2c2c4e"), C("#22223e")), 1.0), nine(200, 20, BO))
    P.sprite("widget/slider_highlighted",
             panel(200, 20, sc, R, (C("#0f0f1e"), C("#14142a")), (mix(C("#2c2c4e"), A, 0.75), mix(C("#22223e"), B, 0.6)), 1.1), nine(200, 20, BO))
    P.sprite("widget/slider_handle",
             panel(8, 20, 8, 3.6, (mix(BASE_TOP, A, 0.8), mix(BASE_BOT, B, 0.8)), (lighten(A, 0.35), lighten(B, 0.1)), 0.9, gloss=0.18), nine(8, 20, 3))
    P.sprite("widget/slider_handle_highlighted",
             panel(8, 20, 8, 3.6, (lighten(A, 0.25), mix(A, B, 0.6)), (C("#ffffff", 235), lighten(Cc, 0.4)), 0.9, gloss=0.28), nine(8, 20, 3))

    # ═════════ поле ввода ═════════
    P.sprite("widget/text_field", panel(200, 20, sc, R, (FIELD, C("#10101e")), (C("#34345a"), C("#2a2a4a")), 1.0), nine(200, 20, BO))
    P.sprite("widget/text_field_highlighted",
             panel(200, 20, sc, R, (C("#0c0c1a"), C("#12122a")), (lighten(A, 0.35), mix(A, B, 0.5)), 1.3), nine(200, 20, BO))

    # ═════════ чекбоксы (квадрат, без 9-slice) ═════════
    def checkbox(selected, hover):
        sz, k = 17, 8
        if selected:
            fill = (mix(BASE_TOP, A, 0.85), mix(BASE_BOT, B, 0.85)) if not hover else (lighten(A, 0.22), mix(A, B, 0.55))
            border = (lighten(A, 0.45), mix(Cc, B, 0.4))
        else:
            fill = (FIELD, C("#10101e")) if not hover else (C("#12122a"), C("#16163a"))
            border = (C("#3b3b66"), C("#2a2a4a")) if not hover else (lighten(A, 0.35), mix(A, B, 0.5))
        img = panel(sz, sz, k, 4.0, fill, border, 1.0 if not hover else 1.2, gloss=0.1 if selected else 0.0)
        if selected:
            K = 3
            big = Image.new("RGBA", (sz * k * K, sz * k * K), (0, 0, 0, 0))
            d = ImageDraw.Draw(big)
            pts = [(4.4, 8.9), (7.3, 11.9), (12.8, 5.3)]
            pts = [(x * k * K, y * k * K) for x, y in pts]
            d.line(pts, fill=(255, 255, 255, 255), width=int(1.9 * k * K), joint="curve")
            for (x, y) in (pts[0], pts[-1]):
                rr = 0.95 * k * K
                d.ellipse([x - rr, y - rr, x + rr, y + rr], fill=(255, 255, 255, 255))
            img.alpha_composite(big.resize(img.size, Image.LANCZOS))
        return img

    P.sprite("widget/checkbox", checkbox(False, False))
    P.sprite("widget/checkbox_highlighted", checkbox(False, True))
    P.sprite("widget/checkbox_selected", checkbox(True, False))
    P.sprite("widget/checkbox_selected_highlighted", checkbox(True, True))

    # ═════════ вкладки (130x24) ═════════
    def tab(selected, hover):
        W_, H_, k = 130, 24, 4
        full = H_ + 10
        if selected:
            fill = (mix(BASE_TOP, A, 0.35 if hover else 0.22), BASE_BOT)
            border = (lighten(A, 0.3), mix(EDGE_BOT, A, 0.5))
        else:
            fill = (C("#171730") if hover else C("#101020"), C("#0d0d1a"))
            border = (mix(EDGE_TOP, A, 0.5), EDGE_BOT) if hover else (C("#25254a"), C("#1b1b33"))
        img = panel(W_, full, k, 6.0, fill, border, 1.0, corners=(True, True, False, False), gloss=0.06 if selected else 0.0)
        return img.crop((0, 0, W_ * k, H_ * k))

    P.sprite("widget/tab_selected", tab(True, False), nine(130, 24, 6))
    P.sprite("widget/tab_selected_highlighted", tab(True, True), nine(130, 24, 6))
    P.sprite("widget/tab_unselected", tab(False, False), nine(130, 24, 6))
    P.sprite("widget/tab_unselected_highlighted", tab(False, True), nine(130, 24, 6))

    # ═════════ полоса прокрутки ═════════
    P.sprite("widget/scroller_background",
             panel(6, 32, 16, 3.0, (C("#0b0b16", 230), C("#0b0b16", 230)), (C("#1f1f38"), C("#1f1f38")), 0.5), nine(6, 32, 2))
    P.sprite("widget/scroller",
             panel(6, 32, 16, 3.0, (lighten(A, 0.15), mix(A, B, 0.8)), (lighten(A, 0.5), lighten(B, 0.2)), 0.5), nine(6, 32, 2))

    # ═════════ подсказки (tooltip) ═════════
    P.sprite("tooltip/background",
             panel(32, 32, 8, 4.0, (C("#0c0c1a", 238), C("#100e22", 238)), None, pad=5.0), nine(32, 32, 9))
    P.sprite("tooltip/frame",
             panel(32, 32, 8, 4.0, None, (mix(A, (255, 255, 255, 255), 0.15), mix(B, (0, 0, 0, 255), 0.1)), 1.2, pad=5.0),
             nine(32, 32, 9))

    # ═════════ хотбар и опыт ═════════
    sc = 4
    hb = panel(182, 22, sc, 6.0, (C("#10101f", 205), C("#0b0b16", 215)), (C("#34345a", 235), C("#222242", 235)), 0.9)
    W_, H_ = hb.size
    cells = Image.new("RGBA", (W_, H_), (0, 0, 0, 0))
    for i in range(9):
        m = rect_mask(W_, H_, sc, 2 + i * 20, 2, 18, 18, 3.5)
        put(cells, vgrad(W_, H_, (255, 255, 255, 20), (255, 255, 255, 8)), m)
        ring = ImageChops.subtract(m, rect_mask(W_, H_, sc, 2.6 + i * 20, 2.6, 16.8, 16.8, 3.0))
        put(cells, Image.new("RGBA", (W_, H_), (255, 255, 255, 22)), ring)
    hb.alpha_composite(cells)
    P.sprite("hud/hotbar", hb)

    sel = Image.new("RGBA", (24 * 8, 23 * 8), (0, 0, 0, 0))
    ringm = ImageChops.subtract(rect_mask(sel.width, sel.height, 8, 1.2, 1.2, 21.6, 20.6, 4.5),
                                rect_mask(sel.width, sel.height, 8, 2.5, 2.5, 19.0, 18.0, 3.4))
    glow = ringm.filter(ImageFilter.GaussianBlur(10))
    put(sel, Image.new("RGBA", sel.size, al(A, 150)), glow)
    put(sel, Image.new("RGBA", sel.size, al(A, 28)), rect_mask(sel.width, sel.height, 8, 2.5, 2.5, 19.0, 18.0, 3.4))
    put(sel, vgrad(sel.width, sel.height, lighten(A, 0.5), mix(Cc, B, 0.4)), ringm)
    P.sprite("hud/hotbar_selection", sel)

    xs = 8
    P.sprite("hud/experience_bar_background",
             panel(182, 5, xs, 2.5, (C("#08080f", 215), C("#0d0d18", 215)), (C("#2f2f55", 235), C("#222242", 235)), 0.7))
    xw, xh = 182 * xs, 5 * xs
    xp = Image.new("RGBA", (xw, xh), (0, 0, 0, 0))
    xm = rect_mask(xw, xh, xs, 0, 0, 182, 5, 2.5)
    put(xp, hgrad(xw, xh, [(0, A), (0.55, mix(A, Cc, 0.45)), (1, Cc)]), xm)
    shine = Image.new("RGBA", (xw, xh), (0, 0, 0, 0))
    shine.paste(vgrad(xw, int(xh * 0.5), (255, 255, 255, 80), (255, 255, 255, 0)), (0, 0))
    put(xp, shine, xm)
    P.sprite("hud/experience_bar_progress", xp)

    # ═════════ уведомления (toast) ═════════
    def toast(c1, c2):
        return panel(160, 32, 4, 7.0, (C("#14142a", 245), C("#0d0d1a", 245)), (lighten(c1, 0.2), c2), 1.2, gloss=0.05)

    P.sprite("toast/advancement", toast(A, B), nine(160, 32, 8))
    P.sprite("toast/recipe", toast(Cc, A), nine(160, 32, 8))
    P.sprite("toast/system", toast(C("#9ca3af"), C("#4b5563")), nine(160, 32, 8))
    P.sprite("toast/tutorial", toast(B, Cc), nine(160, 32, 8))

    # ═════════ фоны меню (бесшовные тайлы 32x32 логических) ═════════
    def bg_tile(base, alpha, dots, grid, noise, seed):
        sc_ = 16
        N = 32 * sc_
        rng = np.random.default_rng(seed)
        arr = np.zeros((N, N, 4), float)
        arr[..., :3] = np.array(base[:3], float)
        arr[..., 3] = alpha
        arr[..., :3] += rng.normal(0, noise, (N, N, 1))
        img = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGBA")
        K = 2
        ov = Image.new("RGBA", (N * K, N * K), (0, 0, 0, 0))
        d = ImageDraw.Draw(ov)
        for i in range(4):
            for j in range(4):
                cx, cy, rr = (i * 8 + 4) * sc_ * K, (j * 8 + 4) * sc_ * K, 0.7 * sc_ * K
                d.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=al(A, dots))
        t = int(0.5 * sc_ * K)
        gl = al(Cc, grid)
        d.rectangle([0, 0, N * K, t], fill=gl)
        d.rectangle([0, N * K - t, N * K, N * K], fill=gl)
        d.rectangle([0, 0, t, N * K], fill=gl)
        d.rectangle([N * K - t, 0, N * K, N * K], fill=gl)
        img.alpha_composite(ov.resize((N, N), Image.LANCZOS))
        return img

    gui = "assets/minecraft/textures/gui/"
    P.png(gui + "menu_background.png", bg_tile((10, 10, 20), 255, 70, 18, 1.6, 1))
    P.png(gui + "options_background.png", bg_tile((10, 10, 20), 255, 70, 18, 1.6, 1))
    P.png(gui + "menu_list_background.png", bg_tile((7, 7, 15), 255, 40, 10, 1.2, 2))
    P.png(gui + "inworld_menu_background.png", bg_tile((8, 8, 18), 170, 60, 14, 1.0, 3))
    P.png(gui + "inworld_menu_list_background.png", bg_tile((6, 6, 14), 150, 36, 8, 1.0, 4))

    def separator(top, bottom):
        sc_ = 16
        arr = np.zeros((2 * sc_, 32 * sc_, 4), np.uint8)
        arr[:sc_, :, :] = top
        arr[sc_:, :, :] = bottom
        return Image.fromarray(arr, "RGBA")

    sep_acc = al(mix(A, Cc, 0.25), 150)
    P.png(gui + "header_separator.png", separator(C("#000000", 190), sep_acc))
    P.png(gui + "footer_separator.png", separator(sep_acc, C("#000000", 190)))
    P.png(gui + "inworld_header_separator.png", separator(C("#000000", 150), al(sep_acc, 120)))
    P.png(gui + "inworld_footer_separator.png", separator(al(sep_acc, 120), C("#000000", 150)))

    # ═════════ панорама под цвета темы ═════════
    for i in range(6):
        P.png(gui + "title/background/panorama_%d.png" % i, make_face(i, A, B, Cc))
    P.png(gui + "title/background/panorama_overlay.png", make_overlay(A))

    # ═════════ логотип и «издание» ═════════
    P.png(gui + "title/minecraft.png", make_logo(A, B, Cc))
    P.png(gui + "title/edition.png", make_edition(A, B, Cc))

    # ═════════ тексты, иконка, mcmeta ═════════
    P.text("assets/minecraft/texts/splashes.txt", "\n".join(SPLASHES) + "\n")
    P.png("pack.png", make_icon(A, B, Cc))
    P.meta("pack.mcmeta", {
        "pack": {
            "description": "%sUniversal Client §7• Rounded UI §8(%s)" % (code, tname),
            "pack_format": 34,
            "supported_formats": {"min_inclusive": 34, "max_inclusive": 999},
            "min_format": 34,
            "max_format": 999,
        }
    })
    return P


# ───────────────────────────── логотип ─────────────────────────────

def gradient_text_layer(size, text, font, stops, center, spacing=0):
    W, H = size
    m = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(m)
    if spacing:
        widths = [d.textlength(ch, font=font) for ch in text]
        total = sum(widths) + spacing * (len(text) - 1)
        x = center[0] - total / 2
        for ch, w in zip(text, widths):
            d.text((x, center[1]), ch, font=font, fill=255, anchor="lm")
            x += w + spacing
    else:
        d.text(center, text, font=font, fill=255, anchor="mm")
    bbox = m.getbbox() or (0, 0, W, H)
    gx = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    part = hgrad(max(1, bbox[2] - bbox[0]), H, [(0, stops[0]), (0.5, stops[1]), (1, stops[2])])
    gx.paste(part, (bbox[0], 0))
    return m, gx


def make_logo(A, B, Cc):
    # Minecraft 1.20.5+ рисует логотип как область 256x44 из текстуры 256x64 -> здесь x4
    W, H = 1024, 256
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    font = ImageFont.truetype(FONT_BOLD, 150)
    m, gx = gradient_text_layer((W, H), "UNIVERSAL", font, (A, B, Cc), (W // 2, 84))
    shadow = ImageChops.offset(m, 0, 8).filter(ImageFilter.GaussianBlur(7))
    put(img, Image.new("RGBA", (W, H), (0, 0, 0, 170)), shadow)
    put(img, Image.new("RGBA", (W, H), al(A, 150)), m.filter(ImageFilter.GaussianBlur(16)))
    stroke = ImageChops.subtract(m.filter(ImageFilter.MaxFilter(7)), m)
    put(img, Image.new("RGBA", (W, H), (255, 255, 255, 28)), stroke)
    put(img, gx, m)
    bb = m.getbbox()
    if bb:
        sh = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        hh = int((bb[3] - bb[1]) * 0.46)
        sh.paste(vgrad(W, hh, (255, 255, 255, 85), (255, 255, 255, 0)), (0, bb[1]))
        put(img, sh, m)
    return img


def make_edition(A, B, Cc):
    W, H = 512, 56  # 128x14 x4
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    font = ImageFont.truetype(FONT_BOLD, 30)
    m, gx = gradient_text_layer((W, H), "VISUAL CLIENT", font, (Cc, B, A), (W // 2, H // 2), spacing=9)
    put(img, Image.new("RGBA", (W, H), al(A, 110)), m.filter(ImageFilter.GaussianBlur(6)))
    put(img, gx, m)
    return img


def make_icon(A, B, Cc):
    S, K = 128, 3
    img = panel(S, S, 1, 26, (mix(C("#1d1b3a"), A, 0.35), mix(C("#0c0c1a"), B, 0.25)), (lighten(A, 0.35), B), 3.0)
    big = Image.new("RGBA", (S * K, S * K), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    cx, cy, s = 64 * K, 70 * K, 34 * K
    dx, h = s * 0.866, s * 0.5
    top = [(cx, cy - s), (cx + dx, cy - h), (cx, cy), (cx - dx, cy - h)]
    left = [(cx - dx, cy - h), (cx, cy), (cx, cy + s), (cx - dx, cy + s - h)]
    right = [(cx, cy), (cx + dx, cy - h), (cx + dx, cy + s - h), (cx, cy + s)]
    d.polygon(top, fill=lighten(A, 0.5))
    d.polygon(left, fill=mix(A, B, 0.3))
    d.polygon(right, fill=mix(B, (0, 0, 0, 255), 0.4))
    for poly in (top, left, right):
        d.line(poly + [poly[0]], fill=(255, 255, 255, 110), width=K * 2)
    big = big.resize((S, S), Image.LANCZOS)
    put(img, Image.new("RGBA", (S, S), al(Cc, 90)), big.getchannel("A").filter(ImageFilter.GaussianBlur(7)))
    img.alpha_composite(big)
    return img.resize((64, 64), Image.LANCZOS)


# ───────────────────────────── предпросмотр ─────────────────────────────

def nine_slice(img, lw, lh, border, tw, th, scale):
    """Эмуляция Minecraft 9-slice: цель tw x th логических пикселей, итог x scale."""
    k = img.width / lw
    b = int(round(border * k))
    TW, TH = int(tw * scale), int(th * scale)
    bo = int(round(border * scale))
    out = Image.new("RGBA", (TW, TH), (0, 0, 0, 0))
    W, H = img.size
    xs_src, ys_src = [0, b, W - b, W], [0, b, H - b, H]
    xs_dst, ys_dst = [0, bo, TW - bo, TW], [0, bo, TH - bo, TH]
    for i in range(3):
        for j in range(3):
            s = img.crop((xs_src[i], ys_src[j], xs_src[i + 1], ys_src[j + 1]))
            dw, dh = xs_dst[i + 1] - xs_dst[i], ys_dst[j + 1] - ys_dst[j]
            if dw > 0 and dh > 0:
                out.alpha_composite(s.resize((dw, dh), Image.NEAREST), (xs_dst[i], ys_dst[j]))
    return out


def preview(tid, P):
    def ld(rel):
        return Image.open(io.BytesIO(P.files[rel])).convert("RGBA")

    sp = "assets/minecraft/textures/gui/sprites/"
    scale = 3
    GW, GH = 427, 240
    img = ld("assets/minecraft/textures/gui/title/background/panorama_1.png").convert("RGB").resize((GW * scale, GH * scale))
    img = img.convert("RGBA")
    lg = ld("assets/minecraft/textures/gui/title/minecraft.png").resize((256 * scale, 64 * scale), Image.LANCZOS)
    img.alpha_composite(lg, ((GW * scale - lg.width) // 2, 24 * scale))
    ed = ld("assets/minecraft/textures/gui/title/edition.png").resize((128 * scale, 14 * scale), Image.LANCZOS)
    img.alpha_composite(ed, ((GW * scale - ed.width) // 2 + 30 * scale, 62 * scale))
    try:  # в Poppins нет кириллицы — для предпросмотра берём шрифт с ней
        font = ImageFont.truetype("DejaVuSans.ttf", 8 * scale)
    except OSError:
        font = ImageFont.truetype(FONT_MED, 8 * scale)

    def button(x, y, w, label, state="button"):
        b = nine_slice(ld(sp + "widget/" + state + ".png"), 200, 20, 6, w, 20, scale)
        img.alpha_composite(b, (x * scale, y * scale))
        col = (255, 255, 255, 255) if state != "button_disabled" else (130, 130, 150, 255)
        ImageDraw.Draw(img).text(((x + w / 2) * scale, (y + 10) * scale), label, font=font, fill=col, anchor="mm")

    cx = GW // 2
    button(cx - 100, 90, 200, "Одиночная игра")
    button(cx - 100, 114, 200, "Сетевая игра", "button_highlighted")
    button(cx - 100, 138, 200, "Minecraft Realms", "button_disabled")
    button(cx - 100, 170, 98, "Настройки")
    button(cx + 2, 170, 98, "Выход")
    img.alpha_composite(nine_slice(ld(sp + "widget/text_field_highlighted.png"), 200, 20, 6, 110, 20, scale), (8 * scale, 8 * scale))
    img.alpha_composite(nine_slice(ld(sp + "widget/slider.png"), 200, 20, 6, 110, 20, scale), (8 * scale, 32 * scale))
    img.alpha_composite(nine_slice(ld(sp + "widget/slider_handle_highlighted.png"), 8, 20, 3, 8, 20, scale), (68 * scale, 32 * scale))
    for i, nm in enumerate(["checkbox", "checkbox_highlighted", "checkbox_selected", "checkbox_selected_highlighted"]):
        c = ld(sp + "widget/" + nm + ".png").resize((17 * scale, 17 * scale), Image.NEAREST)
        img.alpha_composite(c, ((8 + i * 22) * scale, 58 * scale))
    img.alpha_composite(nine_slice(ld(sp + "widget/tab_selected.png"), 130, 24, 6, 62, 24, scale), (290 * scale, 8 * scale))
    img.alpha_composite(nine_slice(ld(sp + "widget/tab_unselected.png"), 130, 24, 6, 62, 24, scale), (354 * scale, 8 * scale))
    tb = nine_slice(ld(sp + "tooltip/background.png"), 32, 32, 9, 110, 40, scale)
    tf = nine_slice(ld(sp + "tooltip/frame.png"), 32, 32, 9, 110, 40, scale)
    img.alpha_composite(tb, (300 * scale, 50 * scale))
    img.alpha_composite(tf, (300 * scale, 50 * scale))
    ImageDraw.Draw(img).text((312 * scale, 70 * scale), "Алмазный меч", font=font, fill=(90, 255, 255, 255), anchor="lm")
    img.alpha_composite(ld(sp + "hud/hotbar.png").resize((182 * scale, 22 * scale), Image.LANCZOS), (8 * scale, 214 * scale))
    img.alpha_composite(ld(sp + "hud/hotbar_selection.png").resize((24 * scale, 23 * scale), Image.LANCZOS), ((8 - 1 + 40) * scale, 213 * scale))
    img.alpha_composite(ld(sp + "hud/experience_bar_background.png").resize((182 * scale, 5 * scale), Image.LANCZOS), (8 * scale, 206 * scale))
    xp = ld(sp + "hud/experience_bar_progress.png").resize((182 * scale, 5 * scale), Image.LANCZOS).crop((0, 0, 110 * scale, 5 * scale))
    img.alpha_composite(xp, (8 * scale, 206 * scale))
    tile = ld("assets/minecraft/textures/gui/menu_background.png").resize((32 * scale, 32 * scale), Image.LANCZOS)
    strip = Image.new("RGBA", (140 * scale, 36 * scale))
    for x in range(0, strip.width, tile.width):
        for y in range(0, strip.height, tile.height):
            strip.paste(tile, (x, y))
    img.alpha_composite(strip, (140 * scale, 190 * scale))
    out = os.path.join(TOOLS, "preview_%s.png" % tid)
    img.convert("RGB").save(out)
    return out


def main():
    want_preview = "--preview" in sys.argv
    os.makedirs(OUT, exist_ok=True)
    for tid, tname, code, a, b, c in THEMES:
        P = make_theme(tid, tname, code, a, b, c)
        path = os.path.join(OUT, "UniversalClient-Pack-%s.zip" % tid)
        P.write(path)
        print("OK", path, "%.0f KB" % (os.path.getsize(path) / 1024))
        if tid == "purple":
            P.write(os.path.join(OUT, "UniversalClient-Pack.zip"))
        if want_preview:
            print("   preview:", preview(tid, P))


if __name__ == "__main__":
    main()
