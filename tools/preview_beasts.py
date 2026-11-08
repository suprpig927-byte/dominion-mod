"""Quick 3D preview of the gen_beasts models: splats every texel at its 3D point (orthographic,
z-buffered, Minecraft-style face shading). Not shipped; just for checking shapes.

python3 tools/preview_beasts.py out.png
"""
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
import gen_beasts as gb  # noqa: E402

FACE_LIGHT = {"top": 1.0, "bottom": 0.5, "front": 0.8, "back": 0.8, "left": 0.6, "right": 0.6}


def render(model, tex_path, yaw, pitch=0.35, size=360, zoom=9.0, hide=()):
    tex = np.asarray(Image.open(tex_path).convert("RGBA"))
    img = np.zeros((size, size, 3), np.uint8) + 70
    zb = np.full((size, size), 1e9)
    cy, sy, cp, sp = math.cos(yaw), math.sin(yaw), math.cos(pitch), math.sin(pitch)
    for s in model.samples():
        if s["part"].name in hide or (s["part"].parent and s["part"].parent.name in hide):
            continue
        P = s["P"].copy()
        P[:, 1] = -(P[:, 1] - 12)  # y up, centred
        x = P[:, 0] * cy + P[:, 2] * sy
        z = -P[:, 0] * sy + P[:, 2] * cy
        y = P[:, 1] * cp - z * sp
        z = P[:, 1] * sp + z * cp
        u = (size / 2 + x * zoom).astype(int)
        v = (size / 2 - y * zoom).astype(int)
        c = tex[s["py"], s["px"]]
        light = FACE_LIGHT[s["face"]]
        for du in range(3):
            for dv in range(3):
                uu, vv = u + du - 1, v + dv - 1
                ok = (uu >= 0) & (uu < size) & (vv >= 0) & (vv < size) & (c[:, 3] > 0)
                uu, vv, zz, cc = uu[ok], vv[ok], z[ok], c[ok]
                order = np.argsort(-zz)
                uu, vv, zz, cc = uu[order], vv[order], zz[order], cc[order]
                front = zz < zb[vv, uu]
                zb[vv[front], uu[front]] = zz[front]
                img[vv[front], uu[front]] = (cc[front, :3] * light).astype(np.uint8)
    return Image.fromarray(img)


def sheet(out):
    tiles = []
    E = gb.ENTITY
    for breed, coats in (("clydesdale", ["brown", "black"]), ("mustang", ["creamy", "white", "gray"]), ("shetland", ["gray", "chestnut", "white"])):
        m, dy = gb.horse_model(breed)
        for coat in coats:
            tiles.append(render(m, os.path.join(E, "horse", f"{breed}_{coat}.png"), 2.3))
    d = gb.deer_model()
    tiles.append(render(d, os.path.join(E, "deer.png"), 2.3, zoom=11))
    tiles.append(render(d, os.path.join(E, "deer.png"), 0.9, zoom=11))
    tiles.append(render(d, os.path.join(E, "deer_fawn.png"), 2.3, zoom=11, hide=("antlers",)))
    tiles.append(render(d, os.path.join(E, "deer.png"), 3.14159 + 0.0, zoom=11, pitch=0.15))
    cols = 4
    rows = (len(tiles) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * 360, rows * 360))
    for i, t in enumerate(tiles):
        sheet.paste(t, ((i % cols) * 360, (i // cols) * 360))
    sheet.save(out)


if __name__ == "__main__":
    sheet(sys.argv[1] if len(sys.argv) > 1 else "/tmp/beasts.png")
