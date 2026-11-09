"""Illager faction art: the Illager Standard block, the Overlord's robes, flour, and a few item icons.

python3 tools/gen_factions.py
"""
import colorsys
import json
import os
import zlib

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "dominion")
MC = "/home/claude/mc-src/assets/minecraft/textures"


def h(x, y, s=0):
    return zlib.crc32(bytes([x & 255, y & 255, s & 255])) / 0xFFFFFFFF


def standard_side():
    """Dark oak post with an ominous banner: a grey illager face on white, black and grey bands."""
    n = 32
    im = Image.new("RGBA", (n, n))
    px = im.load()
    for y in range(n):
        for x in range(n):
            g = h(x, y, 1)
            wood = (int(52 + 14 * g), int(34 + 9 * g), int(20 + 6 * g), 255)
            if x in (0, 1, 30, 31) or y in (0, 31):
                px[x, y] = (int(40 + 10 * g), int(26 + 6 * g), int(14 + 4 * g), 255)
            elif 6 <= x <= 25 and 3 <= y <= 28:
                # banner cloth
                c = (222, 222, 214)
                if y in (3, 4) or y >= 26:
                    c = (40, 40, 44)
                if 8 <= y <= 9 or 22 <= y <= 23:
                    c = (110, 110, 112)
                # the face: grey diamond with a brow and dark eyes
                cx, cy = 15.5, 14.5
                if abs(x - cx) / 7 + abs(y - cy) / 6 <= 1:
                    c = (138, 138, 140)
                    if y == 12 and 10 <= x <= 21:
                        c = (40, 40, 44)
                    if y in (13, 14) and (x in (12, 13) or x in (18, 19)):
                        c = (20, 20, 24)
                    if y == 18 and 13 <= x <= 18:
                        c = (60, 60, 64)
                k = 0.9 + 0.1 * h(x, y, 2)
                px[x, y] = (int(c[0] * k), int(c[1] * k), int(c[2] * k), 255)
            else:
                px[x, y] = wood
    return im


def standard_top():
    n = 32
    im = Image.new("RGBA", (n, n))
    px = im.load()
    for y in range(n):
        for x in range(n):
            g = h(x, y, 3)
            r = ((x - 15.5) ** 2 + (y - 15.5) ** 2) ** 0.5
            ring = 0.85 + 0.15 * ((int(r) % 3) == 0)
            px[x, y] = (int((70 + 18 * g) * ring), int((46 + 12 * g) * ring), int((26 + 8 * g) * ring), 255)
    return im


def flour():
    n = 32
    im = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    px = im.load()
    for y in range(n):
        for x in range(n):
            # a tied cloth sack, bulging at the bottom
            t = (y - 6) / 22
            if t < 0 or t > 1:
                continue
            half = 5 + 8 * (t ** 0.6) if t > 0.18 else 4 - 2 * abs(t - 0.09) * 20
            if abs(x - 15.5) > half:
                continue
            edge = abs(x - 15.5) > half - 1.2 or y >= 27
            g = h(x, y, 4)
            if 9 <= y <= 10:
                c = (150, 104, 60)  # the string
            elif edge:
                c = (120, 106, 84)
            else:
                shade = 1.0 - 0.25 * (x - 9) / 14
                c = (int((236 - 20 * g) * shade), int((226 - 22 * g) * shade), int((200 - 22 * g) * shade))
            px[x, y] = c + (255,)
    # a little puff of flour spilling over the top
    for (x, y) in [(14, 5), (16, 4), (17, 6), (13, 4), (15, 3)]:
        px[x, y] = (246, 244, 236, 255)
    return im


def overlord():
    """The vanilla evoker, robed in imperial purple trimmed with gold, and crowned."""
    im = Image.open(os.path.join(MC, "entity", "illager", "evoker.png")).convert("RGBA")
    px = im.load()
    W, H = im.size
    for y in range(H):
        for x in range(W):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            hh, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
            head = y < 20 and x < 32
            if head:
                continue
            if s < 0.25:
                if l > 0.55:  # pale trim -> gold
                    r2, g2, b2 = colorsys.hls_to_rgb(0.12, 0.55 + 0.1 * (l - 0.55), 0.75)
                else:  # robe -> deep purple
                    r2, g2, b2 = colorsys.hls_to_rgb(0.78, 0.12 + l * 0.7, 0.55)
                px[x, y] = (int(r2 * 255), int(g2 * 255), int(b2 * 255), a)
    # a gold crown band round the top of the head (head sides: y 8..15, x 0..31; crown on y 8..9)
    for x in range(0, 32):
        for y in (8, 9):
            if px[x, y][3]:
                gold = (236, 190, 54) if (x + y) % 3 else (255, 226, 110)
                px[x, y] = gold + (255,)
        if x % 4 == 1 and px[x, 8][3]:
            px[x, 8] = (180, 30, 60, 255)  # rubies
    return im


def write():
    blk = os.path.join(ASSETS, "textures", "block")
    standard_side().save(os.path.join(blk, "illager_standard_side.png"))
    standard_top().save(os.path.join(blk, "illager_standard_top.png"))
    flour().save(os.path.join(ASSETS, "textures", "item", "flour.png"))
    os.makedirs(os.path.join(ASSETS, "textures", "entity", "illager"), exist_ok=True)
    overlord().save(os.path.join(ASSETS, "textures", "entity", "illager", "overlord.png"))
    with open(os.path.join(ASSETS, "blockstates", "illager_standard.json"), "w") as f:
        json.dump({"variants": {"": {"model": "dominion:block/illager_standard"}}}, f, indent=2)
    with open(os.path.join(ASSETS, "models", "block", "illager_standard.json"), "w") as f:
        json.dump({"parent": "minecraft:block/cube_bottom_top", "textures": {
            "top": "dominion:block/illager_standard_top", "bottom": "dominion:block/illager_standard_top",
            "side": "dominion:block/illager_standard_side"}}, f, indent=2)
    for item, model in (("illager_standard", "dominion:block/illager_standard"), ("flour", "dominion:item/flour")):
        if item == "flour":
            with open(os.path.join(ASSETS, "models", "item", "flour.json"), "w") as f:
                json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "dominion:item/flour"}}, f, indent=2)
        with open(os.path.join(ASSETS, "items", item + ".json"), "w") as f:
            json.dump({"model": {"type": "minecraft:model", "model": model}}, f, indent=2)


if __name__ == "__main__":
    write()
