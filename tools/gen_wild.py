"""Textures for Dominion's wild things: deer, centaurs, orcs, the war totem, and the new items.

Run: python3 tools/gen_wild.py (gen_hd.py runs it too).

Entity textures are painted box by box: each model box's six faces are filled with a colour
function, the same way Minecraft unwraps a cube (top, bottom, then the four sides in a row).
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from PIL import Image
from gen_hd import Part, sprite, mul, mix, ITEM, BLOCK, GOLD, GOLD_D, LEATHER, LEATHER_D, LEAD, RED, wrap  # noqa: E402

ENTITY = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "dominion", "textures", "entity")
os.makedirs(ENTITY, exist_ok=True)
rnd = random.Random(77)


def clamp(v):
    return max(0, min(255, int(v)))


def noise(c, n=6):
    g = rnd.randint(-n, n)
    return tuple(clamp(v + g) for v in c[:3]) + (255,)


def box(img, u, v, w, h, d, faces):
    """faces: dict of face -> fn(x, y, fw, fh) -> colour (RGB or None for transparent). Missing faces use 'all'."""
    rects = {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }
    for name, (x0, y0, fw, fh) in rects.items():
        fn = faces.get(name, faces.get("all"))
        if fn is None:
            continue
        for y in range(fh):
            for x in range(fw):
                c = fn(x, y, fw, fh)
                if c is None:
                    continue
                if len(c) == 4 and c[3] == 0:
                    continue
                img.putpixel((x0 + x, y0 + y), noise(c, 5))


def solid(c):
    return lambda x, y, w, h: c


# ---------------------------------------------------------------- deer

def deer():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    coat, dark, belly, cream = (154, 104, 62), (112, 74, 44), (214, 186, 150), (236, 226, 206)
    side = lambda x, y, w, h: belly if y >= h - 2 else (mul(coat, 1.08) if y < 2 else coat)
    box(img, 0, 0, 8, 8, 16, {
        "top": lambda x, y, w, h: mul(coat, 0.92) if x in (3, 4) else coat,
        "bottom": solid(belly), "right": side, "left": side,
        "front": lambda x, y, w, h: mix(coat, belly, 0.5) if y > 3 else coat,
        "back": lambda x, y, w, h: cream if 1 < x < 6 and y < 6 else coat,      # white rump patch
    })
    box(img, 48, 0, 3, 7, 3, {"all": lambda x, y, w, h: mix(coat, cream, 0.4) if y > 4 else coat})          # neck
    eye = lambda x, y, w, h: (20, 16, 14) if (x == w - 2 and y == 1) else coat
    box(img, 0, 24, 5, 4, 6, {"all": solid(coat), "right": lambda x, y, w, h: (20, 16, 14) if (x == 1 and y == 1) else coat,
                              "left": lambda x, y, w, h: (20, 16, 14) if (x == w - 2 and y == 1) else coat,
                              "front": lambda x, y, w, h: mul(coat, 1.05), "bottom": solid(belly)})
    box(img, 24, 24, 3, 3, 3, {"all": solid(mix(coat, dark, 0.4)), "front": lambda x, y, w, h: (30, 24, 22) if y < 2 else (60, 44, 34),
                               "bottom": solid(cream)})
    ear = lambda x, y, w, h: (222, 170, 150) if x == 0 else dark
    box(img, 40, 24, 2, 1, 1, {"all": ear})
    box(img, 40, 27, 2, 1, 1, {"all": ear})
    for x0 in (48,):
        box(img, x0, 16, 1, 6, 1, {"all": solid((214, 198, 160))})
        for y in range(16, 32):
            for x in range(48, 64):
                img.putpixel((x, y), noise((214, 198, 160) if (x + y) % 5 else (186, 168, 128), 4))
    leg = lambda x, y, w, h: (52, 40, 32) if y >= h - 1 else (mix(coat, dark, 0.6) if y > h - 5 else coat)
    for u in (0, 8, 16, 24):
        box(img, u, 34, 2, 12, 2, {"all": leg, "bottom": solid((44, 34, 28))})
    box(img, 32, 34, 2, 3, 2, {"all": solid(coat), "back": solid(cream), "bottom": solid(cream)})
    img.save(os.path.join(ENTITY, "deer.png"))


# ---------------------------------------------------------------- centaur

def centaur():
    img = Image.new("RGBA", (128, 64), (0, 0, 0, 0))
    coat, dark, light = (138, 84, 46), (86, 50, 28), (176, 120, 76)
    skin, skin_d, hair, leather = (214, 164, 126), (176, 128, 96), (70, 44, 26), (110, 70, 40)
    barrel_side = lambda x, y, w, h: mix(coat, light, 0.4) if y >= h - 2 else (dark if (x * 7 + y * 3) % 23 == 0 else coat)
    box(img, 0, 0, 10, 10, 20, {"all": solid(coat), "right": barrel_side, "left": barrel_side, "bottom": solid(light),
                                "top": lambda x, y, w, h: (150, 40, 34) if 4 <= y <= 9 else coat})   # a red saddle blanket
    box(img, 96, 16, 2, 9, 2, {"all": lambda x, y, w, h: mul(hair, 0.8 + (x % 2) * 0.2)})
    # torso: bare chest crossed by a quiver strap, with a leather belt
    def torso(x, y, w, h):
        if y >= h - 2:
            return leather
        if abs((x - y * 0.6) - 1) < 1.0:
            return (90, 56, 32)
        return skin if y > 1 else mix(skin, hair, 0.2)
    box(img, 96, 0, 8, 12, 4, {"all": torso, "top": solid(skin)})
    def face(x, y, w, h):
        if y < 2:
            return hair
        if y == 3 and x in (1, 2, 5, 6):
            return (250, 250, 250) if x in (1, 6) else (40, 70, 120)
        if y >= 6 and 1 <= x <= 6:
            return mul(hair, 1.1)             # beard
        if y == 5 and x in (3, 4):
            return skin_d
        return skin
    box(img, 64, 0, 8, 8, 8, {"front": face, "top": solid(hair), "back": solid(hair),
                              "right": lambda x, y, w, h: hair if y < 5 or x > 5 else skin,
                              "left": lambda x, y, w, h: hair if y < 5 or x < 2 else skin, "bottom": solid(skin_d)})
    arm = lambda x, y, w, h: leather if 8 <= y <= 10 else skin
    box(img, 64, 16, 4, 12, 4, {"all": arm})
    box(img, 80, 16, 4, 12, 4, {"all": arm})
    leg = lambda x, y, w, h: (40, 32, 26) if y >= h - 2 else (dark if y >= h - 4 else coat)
    for u in (0, 16, 32, 48):
        box(img, u, 32, 3, 8, 3, {"all": leg, "bottom": solid((40, 32, 26))})
    box(img, 112, 16, 1, 12, 1, {"all": lambda x, y, w, h: (120, 80, 40) if y % 4 else (80, 54, 30)})
    box(img, 118, 16, 1, 11, 1, {"all": solid((220, 220, 210))})
    img.save(os.path.join(ENTITY, "centaur.png"))


# ---------------------------------------------------------------- orcs

def orc(chief=False):
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    skin = (84, 120, 58) if not chief else (70, 100, 50)
    skin_d, hide, hide_d = mul(skin, 0.78), (120, 82, 50), (84, 56, 34)
    paint = (170, 30, 26)

    def face(x, y, w, h):
        if y == 2:
            return mul(skin, 0.7)                                   # heavy brow
        if y == 3 and x in (1, 2, 5, 6):
            return (230, 200, 40) if x in (2, 5) else (40, 20, 10)  # yellow eyes
        if y == 6 and 2 <= x <= 5:
            return (40, 26, 20)                                     # mouth
        if chief and y in (4, 5) and x in (1, 6):
            return paint                                            # war paint
        if y == 5 and x in (3, 4):
            return skin_d                                           # broad nose
        return skin
    box(img, 0, 0, 8, 8, 8, {"all": solid(skin), "front": face, "bottom": solid(skin_d),
                             "top": lambda x, y, w, h: (30, 26, 24) if x in (3, 4) else skin})
    # hat layer: a black mohawk (and, for the chieftain, a crest of feathers)
    def hat(x, y, w, h, side):
        if side == "top":
            if x in (3, 4):
                return (24, 20, 20) if not chief else ((200, 40, 30) if y % 2 else (240, 240, 230))
            return None
        if side in ("front", "back") and x in (3, 4) and y < 2:
            return (24, 20, 20) if not chief else (200, 40, 30)
        return None
    box(img, 32, 0, 8, 8, 8, {k: (lambda kk: (lambda x, y, w, h: hat(x, y, w, h, kk)))(k)
                              for k in ("top", "front", "back", "left", "right")})
    # body: bare chest, a leather strap, a hide loincloth; the chief wears a bone necklace
    def body(x, y, w, h):
        if y >= 9:
            return hide if (x + y) % 3 else hide_d
        if abs(x - y) < 1:
            return (70, 46, 28)
        if chief and y == 1 and x % 2 == 0:
            return (226, 220, 196)
        return skin
    box(img, 16, 16, 8, 12, 4, {"all": body, "top": solid(skin)})
    arm = lambda x, y, w, h: hide_d if 7 <= y <= 9 else (skin_d if y >= 11 else skin)
    box(img, 40, 16, 4, 12, 4, {"all": arm})
    box(img, 32, 48, 4, 12, 4, {"all": arm})
    leg = lambda x, y, w, h: (52, 40, 32) if y >= 10 else (hide if y < 4 else mul(skin, 0.9))
    box(img, 0, 16, 4, 12, 4, {"all": leg})
    box(img, 16, 48, 4, 12, 4, {"all": leg})
    box(img, 56, 16, 1, 2, 1, {"all": solid((236, 228, 200))})          # tusks
    box(img, 16, 32, 8, 1, 1, {"all": solid(mul(skin, 0.72))})          # brow ridge
    img.save(os.path.join(ENTITY, "orc_chieftain.png" if chief else "orc.png"))


# ---------------------------------------------------------------- items and the totem

def spell_book():
    cover = Part((92, 40, 120), grain=4).rect(6, 4, 27, 28)
    cover.tint(lambda x, y, c: mul(c, 0.8) if (x * 3 + y) % 13 == 0 else c)
    pages = Part((236, 226, 196), grain=3).rect(8, 26, 27, 29)
    spine = Part((64, 26, 86), grain=3).rect(4, 4, 7, 28)
    corners = Part(GOLD, grain=3).pixels([(26, 4), (27, 4), (27, 5), (26, 28), (27, 28), (27, 27), (8, 4), (8, 28)])
    rune = Part((250, 210, 90), grain=0, hi=1, lo=1)
    cx, cy = 17, 15
    for a in range(0, 360, 12):
        rune.pixels([(int(cx + math.cos(math.radians(a)) * 6), int(cy + math.sin(math.radians(a)) * 6))])
    rune.line([(cx, cy - 4), (cx + 3, cy + 3), (cx - 4, cy - 1), (cx + 4, cy - 1), (cx - 3, cy + 3), (cx, cy - 4)])
    clasp = Part(GOLD, grain=3).rect(26, 13, 28, 17)
    return sprite([spine, cover, pages, corners, rune, clasp])


def ammo_pouch():
    bag = Part(LEATHER, grain=5).poly([(7, 11), (25, 11), (27, 26), (23, 29), (9, 29), (5, 26)])
    bag.tint(lambda x, y, c: mul(c, 0.82) if (x + 2 * y) % 9 == 0 else c)
    flap = Part(LEATHER_D, grain=4).poly([(6, 10), (26, 10), (24, 18), (16, 21), (8, 18)])
    buckle = Part(GOLD, grain=3, hi=1.4).rect(14, 17, 18, 21)
    strap = Part((80, 52, 30), grain=3).line([(7, 10), (11, 3), (21, 3), (25, 10)], width=2)
    balls = [Part(LEAD, grain=2, hi=1.5, lo=0.6).circle(x, 10, 1.8) for x in (12, 16, 20)]
    stitch = Part((200, 170, 120), grain=0, hi=1, lo=1).pixels([(x, 27) for x in range(8, 25, 2)])
    return sprite([strap] + balls + [bag, flap, buckle, stitch])


def venison(cooked=False):
    base = (176, 52, 48) if not cooked else (128, 72, 40)
    fat = (238, 214, 200) if not cooked else (210, 160, 100)
    meat = Part(base, grain=6).poly([(5, 14), (11, 7), (22, 6), (28, 11), (27, 21), (20, 27), (9, 26), (4, 20)])
    meat.tint(lambda x, y, c: fat if (x * 2 + y * 3) % 11 == 0 and 7 < x < 25 else c)
    meat.tint(lambda x, y, c: mul(c, 0.82) if cooked and (x - y) % 6 == 0 else c)  # grill marks
    rim = Part(fat, grain=3).line([(5, 14), (11, 7), (22, 6), (28, 11)], width=2)
    bone = Part((240, 232, 214), grain=2).circle(13, 17, 2.4)
    return sprite([meat, rim, bone])


def totem_side():
    img = Image.new("RGBA", (32, 32))
    wood, dark = (104, 74, 46), (62, 42, 26)
    for y in range(32):
        for x in range(32):
            c = wood if (x * 5 + y) % 17 else mul(wood, 0.85)
            if x in (0, 31):
                c = dark
            # a carved, painted face
            if 6 <= y <= 9 and (5 <= x <= 12 or 19 <= x <= 26):
                c = (200, 40, 30) if y > 6 else dark                      # brows
            if 10 <= y <= 13 and (7 <= x <= 10 or 21 <= x <= 24):
                c = (250, 230, 120) if (x in (8, 9, 22, 23) and y in (11, 12)) else (20, 14, 10)   # eyes
            if 15 <= y <= 19 and 14 <= x <= 17:
                c = mul(wood, 0.7)                                         # nose
            if 22 <= y <= 27 and 7 <= x <= 24:
                c = (24, 16, 12)                                           # mouth
                if y in (22, 23) and x in (9, 10, 21, 22):
                    c = (236, 228, 200)                                     # tusks
                if y in (26, 27) and x % 3 == 0:
                    c = (236, 228, 200)
            if y in (2, 3, 29, 30):
                c = (200, 40, 30) if x % 4 < 2 else (230, 200, 60)        # painted bands
            img.putpixel((x, y), noise(c, 4))
    return img


def totem_top():
    img = Image.new("RGBA", (32, 32))
    for y in range(32):
        for x in range(32):
            r = math.hypot(x - 15.5, y - 15.5)
            c = (120, 86, 52) if int(r) % 4 else (90, 62, 38)
            if r > 14:
                c = (62, 42, 26)
            img.putpixel((x, y), noise(c, 4))
    return img


def write():
    # deer.png now comes from gen_beasts.py
    # centaur textures now come from gen_beasts.py
    orc(False)
    orc(True)
    spell_book().save(os.path.join(ITEM, "spell_book.png"))
    ammo_pouch().save(os.path.join(ITEM, "ammo_pouch.png"))
    venison(False).save(os.path.join(ITEM, "venison.png"))
    venison(True).save(os.path.join(ITEM, "cooked_venison.png"))
    totem_side().save(os.path.join(BLOCK, "orc_totem_side.png"))
    totem_top().save(os.path.join(BLOCK, "orc_totem_top.png"))


if __name__ == "__main__":
    write()
    print("wild textures written")
