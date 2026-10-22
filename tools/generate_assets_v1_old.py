"""
Генератор фирменных ресурсов Universal Client:
  - ресурспак для Minecraft 1.21.11 (панорама меню, логотип, кнопки, сплэши)
  - иконка лаунчера

Запуск:  python tools/generate_assets.py   (нужны Pillow и numpy)
Результат: src/main/resources/pack/UniversalClient-Pack.zip и src/main/resources/icon.png
"""
import io, json, math, os, random, zipfile
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
FONT_CANDIDATES = [
    "C:/Windows/Fonts/segoeuib.ttf",
    "/usr/share/fonts/truetype/google-fonts/Poppins-Bold.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
]

def font(size):
    for p in FONT_CANDIDATES:
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()

PURPLE = np.array([139, 92, 246], float)
INDIGO = np.array([99, 102, 241], float)
CYAN = np.array([34, 211, 238], float)

# ───────────── панорама (6 граней кубической карты) ─────────────
N = 512
FACES = [  # (forward, right, up)
    ((0, 0, -1), (1, 0, 0), (0, 1, 0)),
    ((1, 0, 0), (0, 0, 1), (0, 1, 0)),
    ((0, 0, 1), (-1, 0, 0), (0, 1, 0)),
    ((-1, 0, 0), (0, 0, -1), (0, 1, 0)),
    ((0, 1, 0), (1, 0, 0), (0, 0, 1)),
    ((0, -1, 0), (1, 0, 0), (0, 0, -1)),
]

rng = np.random.default_rng(2026)
WAVES = []
for _ in range(14):
    d = rng.normal(size=3); d /= np.linalg.norm(d)
    WAVES.append((d * rng.uniform(1.2, 4.2), rng.uniform(0, 6.28), rng.uniform(0.4, 1.0)))

def field(d):
    s = np.zeros(d.shape[:2])
    for w, ph, a in WAVES:
        s += a * np.sin(d @ w + ph)
    s /= sum(a for _, _, a in WAVES) * 0.55
    return s

def smooth(x, a, b):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)

def face_dirs(face):
    f, r, u = [np.array(v, float) for v in FACES[face]]
    lin = (np.arange(N) + 0.5) / N * 2 - 1
    uu, vv = np.meshgrid(lin, -lin)
    d = f[None, None, :] + uu[..., None] * r + vv[..., None] * u
    return d / np.linalg.norm(d, axis=2, keepdims=True)

stars = rng.normal(size=(1100, 3)); stars /= np.linalg.norm(stars, axis=1, keepdims=True)
star_b = rng.uniform(0.35, 1.0, len(stars)) ** 2
star_c = rng.choice(3, len(stars))

def make_face(face):
    d = face_dirs(face)
    a = field(d)
    b = field(d[..., [2, 0, 1]] * 1.7 + 3.1)
    base = np.array([7, 7, 16], float)
    img = np.ones((N, N, 3)) * base
    # туманности
    m1 = smooth(a, 0.15, 1.1)[..., None]
    m2 = smooth(b, 0.25, 1.1)[..., None]
    img += m1 * (PURPLE * 0.42) + m2 * (CYAN * 0.28)
    # мягкая "горизонтальная" дымка
    haze = np.exp(-(d[..., 1] ** 2) * 9)[..., None]
    img += haze * (INDIGO * 0.22)
    img = np.clip(img, 0, 255)
    pil = Image.fromarray(img.astype(np.uint8))
    # звёзды
    f, r, u = [np.array(v, float) for v in FACES[face]]
    layer = Image.new("RGB", (N, N), (0, 0, 0))
    dr = ImageDraw.Draw(layer)
    for v, br, c in zip(stars, star_b, star_c):
        t = v @ f
        if t <= 0.01:
            continue
        x, y = (v @ r) / t, (v @ u) / t
        if abs(x) > 0.97 or abs(y) > 0.97:
            continue
        px, py = (x + 1) / 2 * N, (1 - y) / 2 * N
        col = [(255, 255, 255), (196, 181, 253), (125, 211, 252)][c]
        col = tuple(int(k * br) for k in col)
        rad = 1.0 + br * 1.3
        dr.ellipse((px - rad, py - rad, px + rad, py + rad), fill=col)
    glow = layer.filter(ImageFilter.GaussianBlur(1.6))
    arr = np.clip(np.asarray(pil, float) + np.asarray(layer, float) + np.asarray(glow, float) * 0.8, 0, 255)
    return Image.fromarray(arr.astype(np.uint8))

# ───────────── кнопки ─────────────
def button(fill, border, inner, w=200, h=20):
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = im.load()
    for y in range(h):
        for x in range(w):
            corner = (x, y) in [(0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)]
            if corner:
                continue
            edge = x in (0, w - 1) or y in (0, h - 1)
            near = x in (1, w - 2) or y in (1, h - 2)
            if edge:
                px[x, y] = border + (255,)
            elif near:
                px[x, y] = inner + (255,)
            else:
                k = y / (h - 1)
                px[x, y] = tuple(int(c * (1.12 - 0.22 * k)) for c in fill) + (255,)
    return im

# ───────────── логотип ─────────────
def gradient_text(text, size, canvas_size, pos, colors=(PURPLE, INDIGO, CYAN)):
    W, H = canvas_size
    mask = Image.new("L", (W, H), 0)
    ImageDraw.Draw(mask).text(pos, text, font=font(size), fill=255)
    grad = np.zeros((H, W, 3))
    xs = np.linspace(0, 1, W)
    for i in range(W):
        t = xs[i] * (len(colors) - 1)
        k = min(int(t), len(colors) - 2)
        grad[:, i] = colors[k] * (1 - (t - k)) + colors[k + 1] * (t - k)
    grad_img = Image.fromarray(grad.astype(np.uint8)).convert("RGBA")
    out = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    glow_mask = mask.filter(ImageFilter.GaussianBlur(W / 90))
    glow = Image.new("RGBA", (W, H), tuple(int(c) for c in PURPLE) + (0,))
    glow.putalpha(glow_mask.point(lambda v: int(v * 0.9)))
    out = Image.alpha_composite(out, glow)
    out.paste(grad_img, (0, 0), mask)
    return out

def make_logo():
    W, H = 1024, 256            # пропорция 256x64, используется верхняя часть 256x44
    size = 142
    f = font(size)
    bbox = ImageDraw.Draw(Image.new("L", (1, 1))).textbbox((0, 0), "UNIVERSAL", font=f)
    tw = bbox[2] - bbox[0]
    while tw > 960 and size > 40:
        size -= 4; f = font(size)
        bbox = ImageDraw.Draw(Image.new("L", (1, 1))).textbbox((0, 0), "UNIVERSAL", font=f)
        tw = bbox[2] - bbox[0]
    x = (W - tw) // 2 - bbox[0]
    y = (176 - (bbox[3] - bbox[1])) // 2 - bbox[1]
    return gradient_text("UNIVERSAL", size, (W, H), (x, y))

def make_edition():
    W, H = 512, 64              # пропорция 128x16
    f = font(34)
    text = "V I S U A L   C L I E N T"
    bbox = ImageDraw.Draw(Image.new("L", (1, 1))).textbbox((0, 0), text, font=f)
    x = (W - (bbox[2] - bbox[0])) // 2 - bbox[0]
    y = (56 - (bbox[3] - bbox[1])) // 2 - bbox[1]
    return gradient_text(text, 34, (W, H), (x, y), colors=(CYAN, INDIGO, PURPLE))

def overlay():
    im = Image.new("RGBA", (16, 128))
    px = im.load()
    for y in range(128):
        a = int(40 + 150 * (y / 127) ** 2)
        for x in range(16):
            px[x, y] = (14, 8, 34, a)
    return im

def png_bytes(im):
    b = io.BytesIO(); im.save(b, "PNG", optimize=True); return b.getvalue()

def nine(w=200, h=20, border=3):
    return json.dumps({"gui": {"scaling": {"type": "nine_slice", "width": w, "height": h, "border": border}}}, indent=2)

SPLASHES = [
    "Universal Client!",
    "Визуалы нового уровня!",
    "Чистый FPS, красивая картинка!",
    "Стиль — это всё!",
    "Сделано с любовью!",
    "Играй красиво!",
    "Фиолетовый — новый чёрный!",
    "Минимум лагов, максимум визуала!",
    "Добро пожаловать!",
    "Один клик до игры!",
]

def build_pack(path):
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("pack.mcmeta", json.dumps({
            "pack": {"description": "§dUniversal Client §7• фирменное оформление", "min_format": 75, "max_format": 999}
        }, ensure_ascii=False, indent=2))
        icon = Image.new("RGBA", (64, 64)); icon.paste(make_icon(64), (0, 0))
        z.writestr("pack.png", png_bytes(icon))
        for i in range(6):
            z.writestr(f"assets/minecraft/textures/gui/title/background/panorama_{i}.png", png_bytes(make_face(i)))
        z.writestr("assets/minecraft/textures/gui/title/background/panorama_overlay.png", png_bytes(overlay()))
        z.writestr("assets/minecraft/textures/gui/title/minecraft.png", png_bytes(make_logo()))
        z.writestr("assets/minecraft/textures/gui/title/edition.png", png_bytes(make_edition()))
        base = "assets/minecraft/textures/gui/sprites/widget/"
        sprites = {
            "button": button((24, 22, 44), (74, 58, 140), (40, 34, 78)),
            "button_highlighted": button((72, 48, 150), (34, 211, 238), (130, 96, 230)),
            "button_disabled": button((16, 16, 24), (40, 40, 56), (26, 26, 38)),
        }
        for name, im in sprites.items():
            z.writestr(base + name + ".png", png_bytes(im))
            z.writestr(base + name + ".png.mcmeta", nine())
        z.writestr("assets/minecraft/texts/splashes.txt", "\n".join(SPLASHES) + "\n")

# ───────────── иконка лаунчера ─────────────
def make_icon(size=256):
    S = size * 4
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    pad = S // 16
    # скруглённый градиентный квадрат
    grad = np.zeros((S, S, 3))
    for y in range(S):
        t = y / S
        grad[y, :] = PURPLE * (1 - t) + INDIGO * t
    for x in range(S):
        grad[:, x] = grad[:, x] * 0.85 + CYAN * (x / S) * 0.15
    g = Image.fromarray(grad.astype(np.uint8)).convert("RGBA")
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).rounded_rectangle((pad, pad, S - pad, S - pad), radius=S // 4, fill=255)
    im.paste(g, (0, 0), mask)
    # буква U
    f = ImageFont.truetype(next(p for p in FONT_CANDIDATES if os.path.exists(p)), int(S * 0.62))
    bb = d.textbbox((0, 0), "U", font=f)
    d.text(((S - (bb[2] - bb[0])) // 2 - bb[0], (S - (bb[3] - bb[1])) // 2 - bb[1] - S // 40), "U", font=f, fill=(255, 255, 255, 255))
    return im.resize((size, size), Image.LANCZOS)

if __name__ == "__main__":
    os.makedirs(os.path.join(RES, "pack"), exist_ok=True)
    build_pack(os.path.join(RES, "pack", "UniversalClient-Pack.zip"))
    make_icon(256).save(os.path.join(RES, "icon.png"))
    print("готово")
