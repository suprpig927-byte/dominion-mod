"""The Dominion UI theme: parchment panels in a dark wood frame with brass corners, wooden
buttons and tabs, progress bars, a cloth title ribbon, and small icons. Written as GUI sprites
(nine-sliced through .mcmeta) under textures/gui/sprites/ui/.

python3 tools/gen_gui.py
"""
import json
import os
import zlib

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..")
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "dominion", "textures", "gui", "sprites", "ui")


def n(x, y, s=0):
    return zlib.crc32(bytes([x & 255, y & 255, s & 255, (x >> 8) & 255])) / 0xFFFFFFFF


def clamp(c):
    return tuple(max(0, min(255, int(v))) for v in c)


def shade(c, k):
    return clamp((c[0] * k, c[1] * k, c[2] * k))


def mix(a, b, t):
    return clamp((a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t))


def save(img, name, nine=None, size=None):
    img.save(os.path.join(OUT, name + ".png"))
    meta = os.path.join(OUT, name + ".png.mcmeta")
    if nine is not None:
        w, h = img.size
        with open(meta, "w") as f:
            json.dump({"gui": {"scaling": {"type": "nine_slice", "width": w, "height": h, "border": nine}}}, f, indent=2)
    elif os.path.exists(meta):
        os.remove(meta)


PARCH = (233, 216, 178)
PARCH_D = (214, 192, 148)
WOOD = (110, 72, 40)
WOOD_D = (62, 38, 20)
WOOD_L = (150, 104, 60)
BRASS = (214, 176, 84)
BRASS_D = (140, 104, 40)
INK = (40, 26, 14)


def parchment(x, y, base=PARCH, s=0):
    g = n(x // 2, y // 2, s) * 0.6 + n(x, y, s + 1) * 0.4
    fib = 0.03 * ((x * 7 + y * 3) % 11 == 0)
    return shade(base, 0.94 + 0.08 * g - fib)


def wood(x, y, base=WOOD, s=0, along_x=True):
    a, b = (x, y) if along_x else (y, x)
    grain = 0.85 + 0.15 * n(b, a // 6, s) + 0.06 * ((b + int(a / 9)) % 4 == 0)
    return shade(base, grain)


def panel():
    S, B = 48, 9
    img = Image.new("RGBA", (S, S))
    px = img.load()
    for y in range(S):
        for x in range(S):
            d = min(x, y, S - 1 - x, S - 1 - y)
            if d == 0:
                c = (24, 15, 8)
            elif d <= 5:
                along = min(y, S - 1 - y) > min(x, S - 1 - x)
                c = wood(x, y, WOOD, 3, along_x=not along)
                if d == 1:
                    c = mix(c, WOOD_L, 0.45)
                if d == 5:
                    c = shade(c, 0.7)
            elif d == 6:
                c = BRASS_D
            elif d == 7:
                c = shade(PARCH_D, 0.82)
            else:
                c = parchment(x, y)
                if d == 8:
                    c = shade(c, 0.92)
            px[x, y] = c + (255,)
    # brass corner studs
    for cx, cy in ((3, 3), (S - 4, 3), (3, S - 4), (S - 4, S - 4)):
        for dy in range(-2, 3):
            for dx in range(-2, 3):
                if dx * dx + dy * dy <= 5:
                    hi = (dx + dy) < 0
                    px[cx + dx, cy + dy] = (shade(BRASS, 1.15) if hi else BRASS if dx * dx + dy * dy < 4 else BRASS_D) + (255,)
    save(img, "panel", B)


def inset(name="inset", base=PARCH_D, edge=(168, 132, 86)):
    S = 16
    img = Image.new("RGBA", (S, S))
    px = img.load()
    for y in range(S):
        for x in range(S):
            d = min(x, y, S - 1 - x, S - 1 - y)
            if d == 0:
                c = edge
            else:
                c = parchment(x, y, base, 5)
                if x == 1 or y == 1:
                    c = shade(c, 0.9)
            px[x, y] = c + (255,)
    save(img, name, 2)


def plank(name, base, edge_hi, edge_lo, border, gold=False):
    W, H = 48, 16
    img = Image.new("RGBA", (W, H))
    px = img.load()
    for y in range(H):
        for x in range(W):
            d = min(x, y, W - 1 - x, H - 1 - y)
            if d == 0:
                c = (26, 16, 8) if not gold else (110, 82, 24)
            else:
                c = wood(x, y, base, 7)
                if y == 1:
                    c = mix(c, edge_hi, 0.6)
                if y == H - 2:
                    c = mix(c, edge_lo, 0.6)
                if x == 1:
                    c = mix(c, edge_hi, 0.35)
                if x == W - 2:
                    c = mix(c, edge_lo, 0.35)
            px[x, y] = c + (255,)
    # two little nails
    for nx in (3, W - 4):
        px[nx, H // 2] = (60, 56, 52, 255)
        px[nx, H // 2 - 1] = (140, 134, 126, 255)
    save(img, name, border)


def tab(name, active):
    W, H = 48, 16
    img = Image.new("RGBA", (W, H))
    px = img.load()
    for y in range(H):
        for x in range(W):
            d = min(x, y, W - 1 - x)
            if d == 0:
                c = (24, 15, 8)
            elif active:
                c = parchment(x, y)
                if y == 1:
                    c = mix(c, (255, 250, 230), 0.4)
                if x == 1:
                    c = shade(c, 0.95)
            else:
                c = wood(x, y, (86, 56, 30), 9)
                if y == 1:
                    c = mix(c, WOOD_L, 0.4)
                if y >= H - 2:
                    c = shade(c, 0.75)
            px[x, y] = c + (255,)
    save(img, name, 3)


def bar(name, color, empty=False):
    W, H = 16, 6
    img = Image.new("RGBA", (W, H))
    px = img.load()
    for y in range(H):
        for x in range(W):
            if empty:
                d = min(x, y, W - 1 - x, H - 1 - y)
                c = (60, 40, 22) if d == 0 else (120, 96, 66) if y == 1 else (138, 112, 80)
            else:
                k = 1.25 if y <= 1 else 1.0 if y <= 3 else 0.78
                c = shade(color, k)
            px[x, y] = c + (255,)
    save(img, name, 2 if empty else 1)


def ribbon():
    W, H = 64, 16
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = img.load()
    red = (150, 30, 30)
    for y in range(H):
        for x in range(W):
            # swallow-tail ends
            if x < 6 and abs(y - 7.5) < (6 - x) * 0.9 - 1.5:
                continue
            if x > W - 7 and abs(y - 7.5) < (x - (W - 7)) * 0.9 - 1.5:
                continue
            c = red
            if y in (1, 2):
                c = shade(red, 1.25)
            if y >= H - 3:
                c = shade(red, 0.7)
            if y == 0 or y == H - 1:
                c = (60, 10, 10)
            if y == 3 or y == H - 4:
                c = BRASS
            c = shade(c, 0.95 + 0.08 * n(x, y, 11))
            px[x, y] = c + (255,)
    save(img, "ribbon", 7)


def scroll_bg():
    W, H = 16, 16
    img = Image.new("RGBA", (W, H))
    px = img.load()
    for y in range(H):
        for x in range(W):
            px[x, y] = parchment(x, y, (226, 206, 166), 13) + (255,)
    save(img, "row", 1)


# ---------------------------------------------------------------- icons (12x12 pixel art)

ICONS = {
    "crown": [
        "............",
        "..y..y..y...",
        "..yy.yy.yy..",
        "..yyyyyyyy..",
        "..yryyybyy..",
        "..yyyyyyyy..",
        "..YYYYYYYY..",
        "............",
    ],
    "people": [
        "....o....o..",
        "...ooo..ooo.",
        "...ooo..ooo.",
        "....o....o..",
        "..bbbbbgggg.",
        "..bbbbbgggg.",
        "..bbbbbgggg.",
        "..bb.bbgg.g.",
    ],
    "land": [
        "..k.........",
        "..kRRRR.....",
        "..kRRRRRR...",
        "..kRRRR.....",
        "..k.........",
        ".GGGGGGGGG..",
        "GGgGGGGgGGG.",
        "GGGGGgGGGGG.",
    ],
    "bell": [
        ".....k......",
        "....yyy.....",
        "...yyyyy....",
        "...yyyyy....",
        "...yyyyy....",
        "..yyyyyyy...",
        "..YYYYYYY...",
        ".....Y......",
    ],
    "warn": [
        ".....r......",
        "....rrr.....",
        "....rwr.....",
        "...rrwrr....",
        "...rrwrr....",
        "..rrrrrrr...",
        "..rrrwrrr...",
        ".rrrrrrrrr..",
    ],
    "lock": [
        "....kkk.....",
        "...k...k....",
        "...k...k....",
        "..yyyyyyy...",
        "..yyykyyy...",
        "..yyykyyy...",
        "..YYYYYYY...",
        "............",
    ],
    "check": [
        "..........G.",
        ".........GG.",
        "........GG..",
        ".G.....GG...",
        ".GG...GG....",
        "..GG.GG.....",
        "...GGG......",
        "....G.......",
    ],
    "eye": [
        "............",
        "...kkkkkk...",
        "..kwwbbwwk..",
        ".kwwbkkbwwk.",
        ".kwwbkkbwwk.",
        "..kwwbbwwk..",
        "...kkkkkk...",
        "............",
    ],
    "tower": [
        "..k.k.k.k...",
        "..kkkkkkk...",
        "...sssss....",
        "...sKsKs....",
        "...sssss....",
        "...ssKss....",
        "...ssKss....",
        "..SSSSSSS...",
    ],
    "swords": [
        "s.........s.",
        ".s.......s..",
        "..s.....s...",
        "...s...s....",
        "....s.s.....",
        ".....b......",
        "....b.b.....",
        "...b...b....",
    ],
    "hammer": [
        "..SSSSS.....",
        "..SSSSSS....",
        "..SS.b......",
        ".....b......",
        ".....b......",
        ".....b......",
        ".....b......",
        ".....b......",
    ],
    "scroll": [
        ".YYYYYYYYY..",
        ".YwwwwwwwY..",
        "..wkkkkkw...",
        "..wwwwwww...",
        "..wkkkkw....",
        "..wwwwwww...",
        ".YwwwwwwwY..",
        ".YYYYYYYYY..",
    ],
}
PAL = {"y": (236, 192, 60), "Y": (168, 122, 30), "r": (196, 40, 40), "R": (176, 40, 40), "b": (60, 90, 170), "g": (70, 140, 70),
       "G": (70, 150, 60), "o": (226, 180, 140), "k": (40, 28, 20), "K": (30, 22, 16), "w": (250, 246, 236), "s": (176, 176, 180),
       "S": (120, 120, 126)}


def icons():
    for name, rows in ICONS.items():
        img = Image.new("RGBA", (12, 12), (0, 0, 0, 0))
        px = img.load()
        top = (12 - len(rows)) // 2
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch in PAL:
                    px[x, y + top] = PAL[ch] + (255,)
        # a soft dark outline so the icon reads on parchment
        out = img.copy()
        op = out.load()
        for y in range(12):
            for x in range(12):
                if px[x, y][3]:
                    continue
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    xx, yy = x + dx, y + dy
                    if 0 <= xx < 12 and 0 <= yy < 12 and px[xx, yy][3]:
                        op[x, y] = (40, 26, 14, 140)
                        break
        save(out, "icon_" + name)


def write():
    os.makedirs(OUT, exist_ok=True)
    panel()
    inset()
    inset("inset_hot", (240, 224, 186), (190, 150, 70))
    plank("button", (132, 90, 50), (196, 150, 96), (70, 44, 22), 3)
    plank("button_hover", (156, 108, 62), (232, 196, 120), (80, 50, 24), 3, gold=True)
    plank("button_disabled", (104, 92, 80), (140, 128, 116), (64, 56, 50), 3)
    tab("tab", False)
    tab("tab_active", True)
    bar("bar_empty", (0, 0, 0), True)
    bar("bar_gold", (222, 172, 52))
    bar("bar_green", (96, 168, 72))
    bar("bar_red", (190, 60, 50))
    bar("bar_blue", (80, 120, 200))
    ribbon()
    scroll_bg()
    icons()


if __name__ == "__main__":
    write()
