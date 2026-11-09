"""32x32 spawn eggs for the mod's mobs, each with a pattern that hints at the animal.

Run: python3 tools/gen_eggs.py (gen_hd.py runs it too). Also writes the item model files.
"""
import json
import math
import os

import numpy as np
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "dominion")
N = 32

# base, accent, pattern
EGGS = {
    "citizen": ((190, 142, 98), (64, 86, 156), "band"),        # villager skin, kingdom-blue tabard band
    "deer": ((156, 104, 62), (238, 230, 216), "spots"),        # fawn spots
    "orc": ((84, 116, 54), (46, 34, 26), "tusks"),             # green hide, war paint
    "orc_warlord": ((58, 66, 44), (150, 22, 18), "tusks"),     # ash hide, red war paint
    "illager_overlord": ((70, 30, 96), (236, 190, 54), "band"),  # purple robes, gold trim
    "centaur": ((214, 168, 128), (122, 78, 44), "half"),       # rider's skin over a bay horse body
    "clydesdale": ((120, 62, 32), (242, 238, 232), "feather"),  # bay with white feathered base
    "mustang": ((200, 162, 100), (40, 30, 24), "dorsal"),      # buckskin with a dark stripe
    "shetland_pony": ((150, 80, 40), (230, 206, 156), "shaggy"),  # chestnut with a flaxen mane
}


def h(x, y, s):
    v = (x * 374761393 + y * 668265263 + s * 982451653) & 0xFFFFFFFF
    v = ((v ^ (v >> 13)) * 1274126177) & 0xFFFFFFFF
    return (v & 0xFFFF) / 65535.0


def egg(name, base, accent, pattern):
    img = np.zeros((N, N, 4), np.uint8)
    cx, cy = 15.5, 17.0
    seed = sum(map(ord, name))
    for y in range(N):
        for x in range(N):
            dx = (x - cx) / 11.0
            dy = (y - cy) / 14.0
            # egg: narrower toward the top
            dx *= 1.0 + 0.22 * max(0.0, -dy)
            r = math.hypot(dx, dy)
            if r > 1.0:
                continue
            u, v = (x - cx) / 11.0, (y - cy) / 14.0  # -1..1
            c = np.array(base, float)
            a = np.array(accent, float)
            t = 0.0
            if pattern == "spots":
                for sx, sy in ((-0.45, -0.35), (0.3, -0.55), (0.5, 0.05), (-0.2, 0.25), (-0.6, 0.45), (0.15, 0.6), (0.0, -0.05)):
                    if math.hypot(u - sx, (v - sy) * 1.2) < 0.15:
                        t = 1.0
            elif pattern == "band":
                if abs(v - 0.15 - 0.08 * u) < 0.16:
                    t = 1.0
                elif abs(v - 0.15 - 0.08 * u) < 0.22:
                    c = c * 0.8
            elif pattern == "tusks":
                for sx in (-0.35, 0.35):
                    if abs(u - sx) < 0.09 and 0.1 < v < 0.55:
                        c, t = np.array((232, 222, 196.0)), 0.0
                if abs(v + 0.35 - 0.25 * abs(u)) < 0.09:
                    t = 1.0
            elif pattern == "half":
                t = 1.0 if v > 0.05 + 0.08 * math.sin(u * 6) else 0.0
                if -0.02 < v - 0.05 < 0.08:
                    c, t = np.array((150, 40, 36.0)), 0.0  # sash
            elif pattern == "feather":
                t = 1.0 if v > 0.45 + 0.12 * (h(x, 0, seed) - 0.5) else 0.0
                if abs(u) < 0.14 and v < -0.1:
                    t = 1.0  # blaze
            elif pattern == "dorsal":
                if abs(u) < 0.1:
                    t = 1.0
                if v > 0.62:
                    t = 1.0
            elif pattern == "shaggy":
                if u < -0.2 + 0.25 * math.sin(v * 5):
                    t = 0.85 + 0.15 * h(x // 1, y // 3, seed)
            c = c * (1 - t) + a * t
            # shading: light from the upper left, a hot highlight, darker rim
            light = 1.12 - 0.35 * (u * 0.6 + v * 0.8) * 0.5 - 0.3 * r ** 3
            c = c * light * (0.95 + 0.1 * h(x, y, seed))
            if math.hypot(u + 0.38, v + 0.45) < 0.16:
                c = c * 0.4 + np.array((255, 255, 250)) * 0.6
            if r > 0.88:
                c = c * 0.55
            img[y, x, :3] = np.clip(c, 0, 255)
            img[y, x, 3] = 255
    return Image.fromarray(img, "RGBA")


def write():
    for name, (base, accent, pattern) in EGGS.items():
        item = f"{name}_spawn_egg"
        egg(name, base, accent, pattern).save(os.path.join(ASSETS, "textures", "item", item + ".png"))
        with open(os.path.join(ASSETS, "models", "item", item + ".json"), "w") as f:
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"dominion:item/{item}"}}, f, indent=2)
        with open(os.path.join(ASSETS, "items", item + ".json"), "w") as f:
            json.dump({"model": {"type": "minecraft:model", "model": f"dominion:item/{item}"}}, f, indent=2)


if __name__ == "__main__":
    write()
