"""Pets: the otter (mesh + two coats), the Creature Cage item (empty and full), and the Pet Bed in
all sixteen colours (block models, blockstates, item).

python3 tools/gen_pets.py
"""
import json
import os
import sys
import zlib

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from gen_beasts import Model, Box, vnoise, grain, smooth, col, mixc, ENTITY, ROOT  # noqa: E402

JAVA = os.path.join(ROOT, "src", "main", "java", "com", "dominionmod", "dominion", "client", "render", "wild", "PetMeshes.java")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "dominion")


# ===================================================================== otter

def otter_model():
    m = Model("otter", 64, 64)
    body = m.part("body", None, (0, 20, 0), boxes=[
        Box((-2.5, -2.5, -5), (5, 5, 10), tag="barrel"),
        Box((-2, -2, 4.5), (4, 4, 2), tag="hips")])
    tail = m.part("tail", body, (0, -1.2, 6.2), (-0.15, 0, 0), [Box((-1.5, -1, 0), (3, 2.4, 5))])
    m.part("tail_tip", tail, (0, 0.4, 5), (0.1, 0, 0), [Box((-1, -0.8, 0), (2, 1.6, 5), tag="tip")], tag="tail")
    head = m.part("head", None, (0, 19.2, -5), boxes=[
        Box((-2.5, -2.6, -4), (5, 4.4, 4.5), tag="cranium"),
        Box((-1.75, -0.6, -5.6), (3.5, 2.1, 1.8), tag="snout"),
        Box((-0.8, -0.75, -5.9), (1.6, 0.9, 0.6), tag="nose")])
    for side, sx in (("right", -1), ("left", 1)):
        m.part(side + "_ear", head, (sx * 2.2, -2.2, -1.2), (0, 0, sx * 0.3), [Box((-0.6, -0.9, -0.5), (1.2, 1.0, 1.0))], tag="ear")
    for name, x, z in (("right_front_leg", -1.8, -3.4), ("left_front_leg", 1.8, -3.4),
                       ("right_hind_leg", -1.8, 3.6), ("left_hind_leg", 1.8, 3.6)):
        m.part(name, None, (x, 21.5, z), boxes=[Box((-1, 0, -1), (2, 2.3, 2), tag="leg"),
                                               Box((-1.15, 2.0, -1.7), (2.3, 0.5, 2.6), tag="paw")], tag="leg")
    m.pack()
    return m


OTTER_COATS = {
    "otter": dict(back=(74, 50, 32), side=(110, 76, 48), cream=(214, 192, 158), paw=(46, 32, 24)),
    "otter_sea": dict(back=(58, 44, 36), side=(84, 64, 52), cream=(206, 196, 176), paw=(36, 28, 24)),
}


def otter_shader(coat):
    c0 = OTTER_COATS[coat]
    back, side, cream, paw = col(c0["back"]), col(c0["side"]), col(c0["cream"]), col(c0["paw"])
    sea = coat == "otter_sea"

    def shader(s):
        P, tag, face, bt = s["P"], s["part"].tag, s["face"], s["box"].tag
        n = len(P)
        x, y, z = P[:, 0], P[:, 1], P[:, 2]
        g = grain(s, 77 if sea else 71)
        Lc = s["L"]
        sheen = 0.9 + 0.2 * vnoise(np.stack([x * 1.2, y * 3, z * 0.8], 1), 5)
        if tag in ("body", "tail"):
            t = np.clip((y - 17.4) / 5.0, 0, 1)  # 0 at the top of the back, 1 at the belly
            c = mixc(back[None].repeat(n, 0), side[None].repeat(n, 0), smooth(0.05, 0.55, t))
            belly = smooth(0.78, 0.95, t) * (tag == "body")
            chest = smooth(-2.5, -4.5, z) * smooth(0.35, 0.7, t) * (tag == "body")
            c = mixc(c, cream[None].repeat(n, 0) * 0.92, np.clip(belly * 0.5 + chest, 0, 1))
            if tag == "tail":
                c = mixc(back[None].repeat(n, 0), side[None].repeat(n, 0), 0.35 + 0.3 * (face == "bottom"))
        elif tag == "head":
            c = mixc(back[None].repeat(n, 0) * 1.05, side[None].repeat(n, 0), 0.5)
            if sea:  # sea otters go pale about the head
                c = mixc(c, cream[None].repeat(n, 0), 0.6 + 0.2 * (face != "top"))
            if bt in ("cranium", "snout"):
                low = smooth(-0.1, 0.9, Lc[:, 1]) if bt == "cranium" else np.ones(n) * 0.7
                c = mixc(c, cream[None].repeat(n, 0), np.clip(low * (face != "top"), 0, 1) * 0.85)
            if bt == "snout":
                if face in ("left", "right", "front"):
                    # whisker pads with little dark dots
                    dots = (np.mod(np.floor(Lc[:, 2] * 3) + np.floor(Lc[:, 1] * 3), 2) == 0) * smooth(0.3, 0.6, s["b"])
                    c = mixc(c, col((90, 70, 56), n), dots * 0.35)
                if face == "front":
                    mouth = smooth(0.07, 0.0, np.abs(s["b"] - 0.85)) * smooth(0.22, 0.1, np.abs(s["a"] - 0.5))
                    c = mixc(c, col((60, 40, 32), n), mouth)
            if bt == "nose":
                c = col((22, 18, 18), n) * (1.2 if face == "top" else 0.9)
                return c
            if bt == "cranium" and face in ("left", "right", "front"):
                # eyes: small, dark and shiny, set forward and high
                if face == "front":
                    for ex in (-1.35, 1.35):
                        e = ((Lc[:, 0] - ex) / 0.5) ** 2 + ((Lc[:, 1] + 1.15) / 0.5) ** 2
                        c = mixc(c, col((14, 10, 10), n), smooth(1.0, 0.7, e))
                        hl = ((Lc[:, 0] - ex + 0.18) / 0.16) ** 2 + ((Lc[:, 1] + 1.32) / 0.16) ** 2
                        c = mixc(c, col((230, 226, 220), n), smooth(1.0, 0.3, hl))
                else:
                    e = ((Lc[:, 2] + 3.4) / 0.45) ** 2 + ((Lc[:, 1] + 1.15) / 0.45) ** 2
                    c = mixc(c, col((14, 10, 10), n), smooth(1.0, 0.7, e))
        elif tag == "ear":
            c = back[None].repeat(n, 0) * 0.9
            if face == "front":
                c = mixc(c, col((150, 110, 90), n), 0.5)
        elif tag == "leg":
            c = mixc(side[None].repeat(n, 0), paw[None].repeat(n, 0), smooth(21.8, 23.6, y))
            if bt == "paw":
                c = paw[None].repeat(n, 0)
                if face == "top":  # webbed toes
                    toes = smooth(0.06, 0.0, np.abs(np.mod(s["a"] * 4, 1) - 0.5) - 0.38)
                    c = mixc(c, col((24, 18, 14), n), toes * smooth(0.7, 0.2, s["b"]))
        else:
            c = side[None].repeat(n, 0)
        fur = (0.86 + 0.24 * g) * sheen
        return c * fur[:, None]

    return shader


# ===================================================================== creature cage (item, 32x32)

def cage_icon(full):
    n = 32
    img = np.zeros((n, n, 4), np.uint8)
    wood = np.array([128, 86, 48])
    dark = np.array([70, 44, 24])
    iron = np.array([150, 152, 158])
    for y in range(n):
        for x in range(n):
            h = zlib.crc32(bytes([x, y, 3])) / 0xFFFFFFFF
            c = None
            # top and base planks
            if 5 <= y <= 7 and 4 <= x <= 27:
                c = wood * (1.15 if y == 5 else 0.95 if y == 7 else 1.0)
            elif 25 <= y <= 28 and 3 <= x <= 28:
                c = wood * (1.1 if y == 25 else 0.85 if y == 28 else 1.0)
            # corner posts
            elif 8 <= y <= 24 and (x in (4, 5) or x in (26, 27)):
                c = wood * (1.1 if x in (4, 26) else 0.85)
            # bars
            elif 8 <= y <= 24 and 7 <= x <= 24 and (x - 7) % 4 == 1:
                c = iron * (1.2 if (x - 7) % 4 == 1 and y % 5 == 0 else 1.0)
            # handle ring on top
            elif 1 <= y <= 4 and 13 <= x <= 18 and (y == 1 or x in (13, 18)):
                c = iron * 0.9
            if c is None and full and 13 <= y <= 24 and 8 <= x <= 23:
                # a little curled-up animal inside, seen between the bars
                cx, cy = 15.5, 20.0
                d = ((x - cx) / 6.5) ** 2 + ((y - cy) / 4.0) ** 2
                hd = ((x - 20.0) / 2.8) ** 2 + ((y - 16.8) / 2.5) ** 2
                if d <= 1 or hd <= 1:
                    fur = np.array([176, 120, 66]) if d <= 1 else np.array([196, 140, 80])
                    c = fur * (0.85 + 0.25 * h)
                    if hd <= 1 and abs(x - 21) <= 0 and y == 16:
                        c = np.array([20, 16, 14])
                    if ((x - 22.5) / 0.7) ** 2 + ((y - 14.2) / 1.2) ** 2 <= 1 or ((x - 18.8) / 0.7) ** 2 + ((y - 14.4) / 1.2) ** 2 <= 1:
                        c = np.array([150, 96, 52])
            if c is None and 8 <= y <= 24 and 6 <= x <= 25:
                c = np.array([40, 30, 22]) * (1.0 + 0.2 * h)  # the dark inside
                img[y, x] = list(np.clip(c, 0, 255).astype(int)) + [150]
                continue
            if c is not None:
                c = c * (0.92 + 0.12 * h)
                img[y, x] = list(np.clip(c, 0, 255).astype(int)) + [255]
    # outline
    a = img[..., 3] > 0
    edge = np.zeros_like(a)
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        edge |= np.roll(np.roll(a, dx, 1), dy, 0)
    edge &= ~a
    img[edge] = [30, 20, 12, 255]
    return Image.fromarray(img, "RGBA")


# ===================================================================== pet bed (block, 16x16 textures)

DYES = {"white": (233, 236, 236), "orange": (240, 118, 19), "magenta": (189, 68, 179), "light_blue": (58, 175, 217),
        "yellow": (248, 197, 39), "lime": (112, 185, 25), "pink": (237, 141, 172), "gray": (62, 68, 71),
        "light_gray": (142, 142, 134), "cyan": (21, 137, 145), "purple": (121, 42, 172), "blue": (53, 57, 157),
        "brown": (114, 71, 40), "green": (84, 109, 27), "red": (160, 39, 34), "black": (30, 27, 27)}


def bed_tex(dye, part):
    base = np.array(DYES[dye], float)
    img = np.zeros((16, 16, 4), np.uint8)
    for y in range(16):
        for x in range(16):
            h = zlib.crc32(bytes([x, y, len(dye), ord(part[0])])) / 0xFFFFFFFF
            if part == "cushion":
                # quilted squares with stitched seams and a soft paler middle
                seam = (x % 8 == 0) or (y % 8 == 0)
                d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
                c = base * (1.12 - d / 50)
                if seam:
                    c = base * 0.78
                if (x % 8 == 4 and y % 8 == 4):
                    c = base * 0.7  # button
            elif part == "rim":
                # rolled fabric edge: light top, darker underside
                c = base * (1.15 - 0.05 * abs(y - 4))
                if y >= 9:
                    # wicker base
                    w = np.array([150, 110, 64])
                    c = w * (0.85 + 0.25 * ((x + (y // 2) * 2) % 4 < 2))
                if y == 8:
                    c = base * 0.7
            else:  # bottom: wicker
                w = np.array([140, 100, 58])
                c = w * (0.85 + 0.25 * (((x // 2) + (y // 2)) % 2))
            c = c * (0.94 + 0.1 * h)
            img[y, x] = list(np.clip(c, 0, 255).astype(int)) + [255]
    return Image.fromarray(img, "RGBA")


def bed_model(dye):
    t = lambda name: f"dominion:block/pet_bed/{dye}_{name}"
    face = lambda tex, uv, cull=None: {k: v for k, v in {"texture": tex, "uv": uv, "cullface": cull}.items() if v is not None}
    els = [
        # wicker base and cushion
        {"from": [1, 0, 1], "to": [15, 3, 15], "faces": {
            "down": face("#bottom", [1, 1, 15, 15], "down"), "up": face("#cushion", [1, 1, 15, 15]),
            "north": face("#rim", [1, 9, 15, 12]), "south": face("#rim", [1, 9, 15, 12]),
            "west": face("#rim", [1, 9, 15, 12]), "east": face("#rim", [1, 9, 15, 12])}},
    ]
    # the rolled rim: back and two sides, open at the front
    for frm, to in (([1, 3, 12], [15, 6, 15]), ([1, 3, 1], [4, 5, 12]), ([12, 3, 1], [15, 5, 12])):
        els.append({"from": frm, "to": to, "faces": {
            k: face("#rim", [0, 2, 16, 6]) for k in ("north", "south", "west", "east")} | {
            "up": face("#rim", [0, 0, 16, 3]), "down": face("#rim", [0, 8, 16, 9])}})
    return {"parent": "minecraft:block/block", "ambientocclusion": False,
            "textures": {"particle": t("cushion"), "cushion": t("cushion"), "rim": t("rim"), "bottom": t("bottom")},
            "elements": els}


def write():
    # otter
    m = otter_model()
    java = ["// Generated by tools/gen_pets.py from the same description as the textures. Do not edit by hand.",
            "package com.dominionmod.dominion.client.render.wild;", "",
            "import net.minecraft.client.model.geom.PartPose;",
            "import net.minecraft.client.model.geom.builders.CubeDeformation;",
            "import net.minecraft.client.model.geom.builders.CubeListBuilder;",
            "import net.minecraft.client.model.geom.builders.MeshDefinition;",
            "import net.minecraft.client.model.geom.builders.PartDefinition;", "",
            "/** The otter's mesh (texture 64x64 units). */",
            "public final class PetMeshes {", "    private PetMeshes() {}", "",
            m.java("otter").replace("    static MeshDefinition", "    public static MeshDefinition", 1), "}"]
    with open(JAVA, "w") as fh:
        fh.write("\n".join(java) + "\n")
    for coat in OTTER_COATS:
        m.paint(otter_shader(coat), os.path.join(ENTITY, coat + ".png"))
    # cage
    item = os.path.join(ASSETS, "textures", "item")
    cage_icon(False).save(os.path.join(item, "creature_cage.png"))
    cage_icon(True).save(os.path.join(item, "creature_cage_full.png"))
    for name in ("creature_cage", "creature_cage_full"):
        with open(os.path.join(ASSETS, "models", "item", name + ".json"), "w") as f:
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "dominion:item/" + name}}, f, indent=2)
    with open(os.path.join(ASSETS, "items", "creature_cage.json"), "w") as f:
        json.dump({"model": {"type": "minecraft:condition", "property": "minecraft:has_component", "component": "minecraft:custom_data",
                             "on_false": {"type": "minecraft:model", "model": "dominion:item/creature_cage"},
                             "on_true": {"type": "minecraft:model", "model": "dominion:item/creature_cage_full"}}}, f, indent=2)
    # pet beds
    tex = os.path.join(ASSETS, "textures", "block", "pet_bed")
    mdl = os.path.join(ASSETS, "models", "block", "pet_bed")
    os.makedirs(tex, exist_ok=True)
    os.makedirs(mdl, exist_ok=True)
    variants = {}
    for dye in DYES:
        for part in ("cushion", "rim", "bottom"):
            bed_tex(dye, part).save(os.path.join(tex, f"{dye}_{part}.png"))
        with open(os.path.join(mdl, dye + ".json"), "w") as f:
            json.dump(bed_model(dye), f, indent=2)
        for facing, rot in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            v = {"model": f"dominion:block/pet_bed/{dye}"}
            if rot:
                v["y"] = rot
            variants[f"color={dye},facing={facing}"] = v
    with open(os.path.join(ASSETS, "blockstates", "pet_bed.json"), "w") as f:
        json.dump({"variants": variants}, f, indent=2)
    with open(os.path.join(ASSETS, "items", "pet_bed.json"), "w") as f:
        json.dump({"model": {"type": "minecraft:model", "model": "dominion:block/pet_bed/red"}}, f, indent=2)


if __name__ == "__main__":
    write()
