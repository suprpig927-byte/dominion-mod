"""Generates Dominion's textures (pixel art, painted in code).

Run:  python3 tools/gen_textures.py
Writes into src/main/resources/assets/dominion/textures/.
Grayscale textures (tunic, trim, tabard) are tinted in game with each kingdom's colors.
"""
import os
import random
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "dominion", "textures")
ENT = os.path.join(ROOT, "entity", "citizen")
os.makedirs(ENT, exist_ok=True)
os.makedirs(os.path.join(ROOT, "item"), exist_ok=True)
os.makedirs(os.path.join(ROOT, "block"), exist_ok=True)


def faces(u, v, w, h, d):
    """UV rectangles (x, y, w, h) of a Minecraft model box at texture offset (u, v)."""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }


def jitter(c, amt, rnd):
    return tuple(max(0, min(255, x + rnd.randint(-amt, amt))) for x in c[:3]) + ((c[3],) if len(c) > 3 else (255,))


def fill(img, rect, color, rnd=None, amt=0, shade=None):
    x0, y0, w, h = rect
    for y in range(h):
        for x in range(w):
            c = color
            if shade:
                c = shade(x, y, w, h, c)
            if rnd is not None and amt:
                c = jitter(c, amt, rnd)
            img.putpixel((x0 + x, y0 + y), tuple(c) if len(c) == 4 else tuple(c) + (255,))


def px(img, rect, x, y, color):
    x0, y0, w, h = rect
    if 0 <= x < w and 0 <= y < h:
        img.putpixel((x0 + x, y0 + y), tuple(color) if len(color) == 4 else tuple(color) + (255,))


def dark(c, f):
    return tuple(int(x * f) for x in c[:3])


def side_faces(f):
    return [f["front"], f["back"], f["left"], f["right"]]


HEAD = faces(0, 0, 8, 10, 8)
NOSE = faces(24, 0, 2, 4, 2)
HAT = faces(32, 0, 8, 10, 8)
RIM = faces(30, 47, 16, 16, 1)
BODY = faces(16, 20, 8, 12, 6)
ARM = faces(44, 22, 4, 12, 4)
LEG = faces(0, 22, 4, 12, 4)

SKINS = [(196, 140, 104), (156, 106, 76), (226, 176, 140), (118, 82, 58)]
HAIR = [(74, 48, 28), (34, 28, 24), (156, 112, 62), (92, 62, 40)]
EYES = [(56, 120, 60), (70, 90, 140), (90, 70, 40), (60, 110, 100)]


def skin(look):
    rnd = random.Random(100 + look)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    s, hair, eye = SKINS[look], HAIR[look], EYES[look]
    for r in HEAD.values():
        fill(img, r, s, rnd, 6)
    fill(img, HEAD["top"], hair, rnd, 8)
    # hair: back, sides, a fringe in front
    for y in range(7):
        for x in range(8):
            px(img, HEAD["back"], x, y, jitter(hair, 8, rnd))
    for side in ("left", "right"):
        for y in range(3):
            for x in range(8):
                px(img, HEAD[side], x, y, jitter(hair, 8, rnd))
        px(img, HEAD[side], 0 if side == "left" else 7, 3, jitter(hair, 8, rnd))
    f = HEAD["front"]
    for x in range(8):
        px(img, f, x, 0, jitter(hair, 8, rnd))
    for x in (0, 1, 6, 7):
        px(img, f, x, 1, jitter(hair, 8, rnd))
    # the villager unibrow, eyes, mouth
    for x in range(1, 7):
        px(img, f, x, 3, dark(hair, 0.8))
    for x in (1, 2, 5, 6):
        px(img, f, x, 4, (235, 235, 230))
    px(img, f, 2, 4, eye)
    px(img, f, 5, 4, eye)
    for x in (3, 4):
        px(img, f, x, 8, dark(s, 0.72))
    for r in NOSE.values():
        fill(img, r, dark(s, 0.88), rnd, 4)
    # linen shirt
    linen = (206, 190, 156)
    for r in BODY.values():
        fill(img, r, linen, rnd, 7)
    # arms: sleeves, then hands
    for name, r in ARM.items():
        fill(img, r, linen, rnd, 7)
    for r in side_faces(ARM):
        for y in range(9, 12):
            for x in range(r[2]):
                px(img, r, x, y, jitter(s, 6, rnd))
    fill(img, ARM["bottom"], s, rnd, 6)
    # trousers and shoes
    trousers, shoes = (96, 72, 48), (54, 38, 28)
    for r in LEG.values():
        fill(img, r, trousers, rnd, 6)
    for r in side_faces(LEG):
        for y in range(10, 12):
            for x in range(r[2]):
                px(img, r, x, y, jitter(shoes, 5, rnd))
    fill(img, LEG["bottom"], shoes, rnd, 5)
    img.save(os.path.join(ENT, f"skin_{look}.png"))


def gray_shade(x, y, w, h, c):
    # gentle vertical folds so the cloth doesn't look flat once tinted
    g = 236 - (14 if x % 3 == 2 else 0) - y
    return (g, g, g)


def tunic():
    rnd = random.Random(7)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for r in BODY.values():
        fill(img, r, (230, 230, 230), rnd, 4, gray_shade)
    # a V neck shows the shirt beneath
    for (x, y) in ((3, 0), (4, 0), (3, 1), (4, 1), (3, 2), (4, 2)):
        img.putpixel((BODY["front"][0] + x, BODY["front"][1] + y), (0, 0, 0, 0))
    for r in side_faces(ARM):
        for y in range(5):
            for x in range(r[2]):
                px(img, r, x, y, jitter((225, 225, 225), 4, rnd))
    fill(img, ARM["top"], (225, 225, 225), rnd, 4)
    img.save(os.path.join(ENT, "tunic.png"))

    trim = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for r in side_faces(BODY):
        for x in range(r[2]):
            px(trim, r, x, 7, jitter((235, 235, 235), 3, rnd))
            px(trim, r, x, 8, jitter((205, 205, 205), 3, rnd))
            px(trim, r, x, 11, jitter((220, 220, 220), 3, rnd))
    f = BODY["front"]
    for (x, y) in ((2, 0), (5, 0), (2, 1), (5, 1), (3, 3), (4, 3)):
        px(trim, f, x, y, (240, 240, 240))
    for r in side_faces(ARM):
        for x in range(r[2]):
            px(trim, r, x, 4, jitter((228, 228, 228), 3, rnd))
    trim.save(os.path.join(ENT, "tunic_trim.png"))


def straw(rnd):
    return jitter((214, 182, 96), 14, rnd)


def outfits():
    rnd = random.Random(11)
    leather, dleather = (122, 82, 48), (84, 56, 34)

    # Farmer: a wide straw hat.
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    fill(img, HAT["top"], (214, 182, 96), rnd, 14)
    for r in side_faces(HAT):
        for y in range(2):
            for x in range(r[2]):
                px(img, r, x, y, straw(rnd))
        for x in range(r[2]):
            px(img, r, x, 2, (150, 60, 40))
    for key in ("front", "back"):
        r = RIM[key]
        for y in range(16):
            for x in range(16):
                dx, dy = x - 7.5, y - 7.5
                if dx * dx + dy * dy <= 7.6 * 7.6:
                    px(img, r, x, y, straw(rnd))
    for key in ("top", "bottom", "left", "right"):
        fill(img, RIM[key], (190, 158, 80), rnd, 10)
    # straw gaps at the corners of the rim edge strips read as round
    img.save(os.path.join(ENT, "job_farmer.png"))

    # Lumberjack: knit cap, leather apron, boots.
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    fill(img, HAT["top"], (150, 44, 38), rnd, 10)
    for r in side_faces(HAT):
        for y in range(3):
            for x in range(r[2]):
                c = (150, 44, 38) if (x + y) % 2 == 0 else (112, 32, 28)
                px(img, r, x, y, c)
    for key in ("front", "back"):
        r = BODY[key]
        for y in range(2, 12):
            for x in range(1, 7):
                px(img, r, x, y, jitter(leather, 8, rnd))
        px(img, r, 1, 0, dleather)
        px(img, r, 6, 0, dleather)
        px(img, r, 1, 1, dleather)
        px(img, r, 6, 1, dleather)
    boots(img, rnd, leather)
    img.save(os.path.join(ENT, "job_lumberjack.png"))

    # Militia: boots, bracers and a sword belt.
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    boots(img, rnd, dleather)
    for r in side_faces(ARM):
        for y in range(7, 9):
            for x in range(r[2]):
                px(img, r, x, y, jitter(leather, 6, rnd))
    for r in side_faces(BODY):
        for x in range(r[2]):
            px(img, r, x, 9, jitter(dleather, 5, rnd))
    px(img, BODY["front"], 3, 9, (200, 170, 70))
    px(img, BODY["front"], 4, 9, (200, 170, 70))
    img.save(os.path.join(ENT, "job_militia.png"))

    # Archer: a quiver on the back with its strap across the chest, a green hood.
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    boots(img, rnd, leather)
    b = BODY["back"]
    for y in range(1, 12):
        for x in (4, 5):
            px(img, b, x, y, jitter(leather, 6, rnd))
    for x, c in ((4, (230, 230, 230)), (5, (190, 50, 40))):
        px(img, b, x, 0, c)
    for i in range(8):
        px(img, BODY["front"], i, min(11, int(i * 1.4)), dleather)
    hood = (62, 96, 52)
    fill(img, HAT["top"], hood, rnd, 8)
    for key in ("back", "left", "right"):
        r = HAT[key]
        for y in range(10 if key == "back" else 7):
            for x in range(r[2]):
                px(img, r, x, y, jitter(hood, 8, rnd))
    for x in range(8):
        px(img, HAT["front"], x, 0, jitter(hood, 8, rnd))
    img.save(os.path.join(ENT, "job_archer.png"))


def boots(img, rnd, color):
    for r in side_faces(LEG):
        for y in range(8, 12):
            for x in range(r[2]):
                px(img, r, x, y, jitter(color, 6, rnd))
    fill(img, LEG["bottom"], color, rnd, 4)


def tabard():
    rnd = random.Random(21)
    chest = faces(0, 0, 8, 12, 4)
    skirt = faces(32, 0, 8, 6, 4)
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    for r in (chest["front"], chest["back"], skirt["front"], skirt["back"]):
        fill(img, r, (230, 230, 230), rnd, 3, gray_shade)
    fill(img, chest["top"], (225, 225, 225), rnd, 3)
    for y in range(4):
        for x in (2, 3, 4, 5):
            img.putpixel((chest["top"][0] + x, chest["top"][1] + y), (0, 0, 0, 0))  # neck hole
    img.save(os.path.join(ENT, "tabard.png"))

    trim = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    for r in (chest["front"], chest["back"]):
        for y in range(12):
            px(trim, r, 0, y, (235, 235, 235))
            px(trim, r, 7, y, (235, 235, 235))
    for r in (skirt["front"], skirt["back"]):
        for y in range(6):
            px(trim, r, 0, y, (235, 235, 235))
            px(trim, r, 7, y, (235, 235, 235))
        for x in range(8):
            px(trim, r, x, 5, (215, 215, 215))
    # the kingdom's emblem: a diamond on the chest
    f = chest["front"]
    for y, xs in ((2, (3, 4)), (3, (2, 3, 4, 5)), (4, (1, 2, 3, 4, 5, 6)), (5, (1, 2, 3, 4, 5, 6)), (6, (2, 3, 4, 5)), (7, (3, 4))):
        for x in xs:
            px(trim, f, x, y, (245, 245, 245))
    trim.save(os.path.join(ENT, "tabard_trim.png"))


def items():
    rnd = random.Random(31)
    coin = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            d = (dx * dx + dy * dy) ** 0.5
            if d <= 6.2:
                c = (236, 190, 52)
                if d > 5.0:
                    c = (178, 128, 24)
                elif dx + dy < -4:
                    c = (255, 230, 120)
                coin.putpixel((x, y), jitter(c, 6, rnd))
    # a little crown stamped in the middle
    for (x, y) in ((5, 6), (7, 5), (8, 5), (10, 6), (5, 7), (6, 7), (7, 7), (8, 7), (9, 7), (10, 7), (5, 8), (6, 8), (7, 8), (8, 8),
                   (9, 8), (10, 8), (5, 9), (6, 9), (7, 9), (8, 9), (9, 9), (10, 9)):
        coin.putpixel((x, y), (190, 136, 26, 255))
    coin.save(os.path.join(ROOT, "item", "gold_coin.png"))

    baton = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for i in range(3, 13):
        x, y = i, 15 - i
        baton.putpixel((x, y), (88, 52, 30, 255))
        baton.putpixel((x - 1, y), (60, 34, 20, 255))
    for (x, y) in ((12, 2), (13, 2), (12, 3), (13, 3), (13, 1), (14, 2), (11, 3), (12, 4)):
        baton.putpixel((x, y), (240, 196, 60, 255))
    baton.putpixel((13, 2), (255, 236, 140, 255))
    for (x, y) in ((2, 13), (3, 13), (2, 14), (3, 12)):
        baton.putpixel((x, y), (220, 180, 50, 255))
    for (x, y) in ((9, 6), (10, 6), (10, 7), (11, 7), (11, 8), (12, 8), (12, 9)):
        baton.putpixel((x, y), (180, 30, 30, 255))
    baton.save(os.path.join(ROOT, "item", "command_baton.png"))

    hammer = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for i in range(2, 11):
        hammer.putpixel((i, 15 - i - 1), (110, 72, 40, 255))
        hammer.putpixel((i + 1, 15 - i - 1), (82, 52, 28, 255))
    head = [(9, 1), (10, 1), (11, 1), (12, 1), (8, 2), (9, 2), (10, 2), (11, 2), (12, 2), (13, 2), (9, 3), (10, 3), (11, 3), (12, 3), (13, 3), (14, 3),
            (10, 4), (11, 4), (12, 4), (13, 4), (14, 4), (11, 5), (12, 5), (13, 5)]
    for (x, y) in head:
        hammer.putpixel((x, y), jitter((150, 150, 158), 10, rnd))
    for (x, y) in ((9, 1), (8, 2), (10, 1)):
        hammer.putpixel((x, y), (205, 205, 212, 255))
    hammer.save(os.path.join(ROOT, "item", "builders_hammer.png"))

    bp = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(2, 14):
        for x in range(2, 14):
            c = (52, 92, 170) if (x + y) % 7 else (60, 100, 180)
            bp.putpixel((x, y), jitter(c, 6, rnd))
    for x in range(2, 14):
        bp.putpixel((x, 2), (200, 200, 190, 255))
        bp.putpixel((x, 13), (150, 150, 140, 255))
    for x in range(4, 12):
        bp.putpixel((x, 5), (225, 235, 255, 255))
        bp.putpixel((x, 11), (225, 235, 255, 255))
    for y in range(5, 12):
        bp.putpixel((4, y), (225, 235, 255, 255))
        bp.putpixel((11, y), (225, 235, 255, 255))
    for i in range(4):
        bp.putpixel((5 + i, 5 - i if 5 - i > 2 else 3), (225, 235, 255, 255))
        bp.putpixel((10 - i, 5 - i if 5 - i > 2 else 3), (225, 235, 255, 255))
    bp.putpixel((7, 9), (225, 235, 255, 255))
    bp.putpixel((7, 10), (225, 235, 255, 255))
    bp.putpixel((8, 9), (225, 235, 255, 255))
    bp.putpixel((8, 10), (225, 235, 255, 255))
    bp.save(os.path.join(ROOT, "item", "blueprint.png"))


def blocks():
    rnd = random.Random(41)
    spruce, dspruce, stone, dstone = (114, 84, 52), (82, 60, 36), (130, 130, 130), (96, 96, 96)
    gold = (232, 186, 50)

    side = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            if y >= 11:
                c = dstone if y in (11, 15) or (x + (4 if y >= 13 else 0)) % 8 == 0 else stone
            else:
                c = dspruce if y % 4 == 0 or x in (0, 15) else spruce
            side.putpixel((x, y), jitter(c, 6, rnd))
    for y in range(3, 9):
        for x in range(5, 11):
            edge = x in (5, 10) or y in (3, 8)
            side.putpixel((x, y), jitter(gold if edge else (60, 40, 24), 6, rnd))
    for y in range(4, 8):
        side.putpixel((7, y), jitter((200, 40, 40), 8, rnd))
        side.putpixel((8, y), jitter((200, 40, 40), 8, rnd))
    side.save(os.path.join(ROOT, "block", "town_hall_side.png"))

    top = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            c = gold if edge else (dspruce if x % 4 == 0 else spruce)
            top.putpixel((x, y), jitter(c, 6, rnd))
    for y in range(5, 11):
        for x in range(5, 11):
            if abs(x - 7.5) + abs(y - 7.5) <= 3.5:
                top.putpixel((x, y), jitter(gold, 8, rnd))
    top.save(os.path.join(ROOT, "block", "town_hall_top.png"))

    oak, doak = (162, 130, 78), (120, 94, 56)
    red, white = (186, 44, 40), (232, 228, 220)
    mside = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            if y < 5:
                c = red if (x // 2) % 2 == 0 else white
                if y == 4 and x % 2 == 1:
                    c = (0, 0, 0)
            else:
                c = doak if y in (5, 10, 15) or x in (0, 15) else oak
            mside.putpixel((x, y), jitter(c, 6, rnd) if c != (0, 0, 0) else (70, 52, 30, 255))
    for (x, y) in ((4, 7), (5, 7), (10, 7), (11, 7), (4, 8), (11, 8)):
        mside.putpixel((x, y), jitter(gold, 8, rnd))
    for (x, y) in ((6, 12), (7, 12), (8, 12), (9, 12)):
        mside.putpixel((x, y), jitter((80, 170, 70), 8, rnd))
    mside.save(os.path.join(ROOT, "block", "market_side.png"))

    mtop = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = red if ((x + y) // 3) % 2 == 0 else white
            mtop.putpixel((x, y), jitter(c, 5, rnd))
    mtop.save(os.path.join(ROOT, "block", "market_top.png"))


def sash():
    """Kingdom colors for the villager layout: a sash (primary) and a belt (secondary), both tinted in game."""
    rnd = random.Random(51)
    body = faces(16, 20, 8, 12, 6)
    robe = faces(0, 38, 8, 20, 6)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for f in (body, robe):
        for key, mirror in (("front", False), ("back", True)):
            r = f[key]
            for y in range(12):
                cx = int(y * 7 / 11)
                if mirror:
                    cx = 7 - cx
                for x in (cx - 1, cx, cx + 1):
                    if 0 <= x < 8:
                        g = 236 if x == cx else 205
                        px(img, r, x, y, jitter((g, g, g), 3, rnd))
    img.save(os.path.join(ENT, "sash.png"))
    belt = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for f in (body, robe):
        for r in side_faces(f):
            for x in range(r[2]):
                px(belt, r, x, 8, jitter((230, 230, 230), 3, rnd))
                px(belt, r, x, 9, jitter((196, 196, 196), 3, rnd))
        # a buckle
        px(belt, f["front"], 3, 8, (255, 255, 255))
        px(belt, f["front"], 4, 8, (255, 255, 255))
        px(belt, f["front"], 3, 9, (255, 255, 255))
        px(belt, f["front"], 4, 9, (255, 255, 255))
    # a hem stripe on the robe
    for r in side_faces(robe):
        for x in range(r[2]):
            px(belt, r, x, 18, jitter((220, 220, 220), 3, rnd))
    belt.save(os.path.join(ENT, "belt.png"))


def new_items_and_blocks():
    rnd = random.Random(61)
    # Blacksmith's tongs
    t = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for i in range(3, 13):
        t.putpixel((i, 15 - i), (60, 60, 66, 255))
        t.putpixel((i - 2 if i > 4 else i, 15 - i), (60, 60, 66, 255))
    for i in range(3, 9):
        t.putpixel((i, 15 - i + 1), (150, 150, 160, 255))
    for (x, y) in ((11, 2), (12, 2), (13, 3), (12, 4), (9, 2), (10, 3)):
        t.putpixel((x, y), (170, 170, 180, 255))
    for (x, y) in ((2, 13), (3, 14), (2, 14), (3, 13)):
        t.putpixel((x, y), (110, 70, 40, 255))
    t.save(os.path.join(ROOT, "item", "blacksmith_tongs.png"))
    # Storage label: a paper tag on a string
    lab = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(5, 13):
        for x in range(3, 13):
            if (x - 3) + (y - 5) < 1 or (12 - x) + (y - 5) < 1:
                continue
            lab.putpixel((x, y), jitter((232, 220, 190), 6, rnd))
    for x in range(4, 12):
        lab.putpixel((x, 12), (180, 160, 120, 255))
    for (x, y) in ((5, 8), (6, 8), (7, 8), (8, 8), (9, 8), (10, 8), (5, 10), (6, 10), (7, 10), (8, 10)):
        lab.putpixel((x, y), (90, 70, 50, 255))
    lab.putpixel((8, 6), (60, 50, 40, 255))
    for (x, y) in ((8, 5), (9, 4), (10, 3), (11, 2), (12, 2)):
        lab.putpixel((x, y), (200, 40, 40, 255))
    lab.save(os.path.join(ROOT, "item", "storage_label.png"))

    oak, doak = (162, 130, 78), (120, 94, 56)
    # Research table: a book and a lapis-blue cloth on top, bookshelf sides
    top = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = (38, 66, 160) if 1 <= x <= 14 and 1 <= y <= 14 else doak
            if 4 <= x <= 11 and 5 <= y <= 10:
                c = (235, 228, 210) if x != 7 and x != 8 else (150, 120, 90)
            if (x, y) in ((12, 3), (13, 3), (12, 4)):
                c = (30, 30, 30)
            top.putpixel((x, y), jitter(c, 5, rnd))
    top.save(os.path.join(ROOT, "block", "research_table_top.png"))
    side = Image.new("RGBA", (16, 16))
    books = [(150, 40, 40), (40, 80, 150), (60, 120, 60), (140, 110, 40), (90, 50, 120)]
    for y in range(16):
        for x in range(16):
            if y in (0, 7, 8, 15) or x in (0, 15):
                c = doak
            else:
                c = books[(x // 2 + (y // 8) * 2) % len(books)] if x % 2 == 1 or y % 8 == 2 else dark(books[(x // 2) % len(books)], 0.8)
            side.putpixel((x, y), jitter(c, 6, rnd))
    side.save(os.path.join(ROOT, "block", "research_table_side.png"))
    # Training desk: purple cloth, gold trim
    top = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            c = (230, 190, 60) if edge else (96, 52, 140)
            if 5 <= x <= 10 and 5 <= y <= 10 and abs(x - 7.5) + abs(y - 7.5) <= 3.5:
                c = (230, 190, 60)
            top.putpixel((x, y), jitter(c, 5, rnd))
    top.save(os.path.join(ROOT, "block", "training_desk_top.png"))
    side = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            if y < 3:
                c = (96, 52, 140) if y < 2 else (230, 190, 60)
            else:
                c = doak if x in (0, 1, 14, 15) or y == 15 else oak
                if 4 <= x <= 11 and 6 <= y <= 11:
                    c = (70, 40, 110) if (x + y) % 3 else (110, 70, 160)
            side.putpixel((x, y), jitter(c, 5, rnd))
    side.save(os.path.join(ROOT, "block", "training_desk_side.png"))


if __name__ == "__main__":
    new_items_and_blocks()
    sash()
    items()
    blocks()
    print("textures written to", os.path.abspath(ROOT))
