"""Wagons and war machines: the supply cart, battering ram, trebuchet and cannon.

Run: python3 tools/gen_engines.py (gen_hd.py runs it too). Like gen_beasts.py, each model is
described once and this writes both its Java mesh (client/render/siege/EngineMeshes.java) and its
texture, painted at 4 pixels per unit.
"""
import math
import os

import numpy as np
from PIL import Image

from gen_beasts import Model, Box, vnoise, grain, smooth, col, mixc, _hash, S, ENTITY, ROOT

JAVA = os.path.join(ROOT, "src", "main", "java", "com", "dominionmod", "dominion", "client", "render", "siege", "EngineMeshes.java")
OUT = os.path.join(ENTITY, "siege")

WOOD = (132, 92, 54)
DARK_WOOD = (86, 58, 34)
IRON = (92, 94, 100)
CANVAS = (214, 204, 176)
BRONZE = (168, 120, 58)
ROPE = (176, 150, 104)


def wood(s, base=WOOD, along=1, boards=True):
    """Planks: grain running along the box's long axis, with board seams."""
    n = len(s["P"])
    L = s["L"]
    a = L[:, along]
    others = [i for i in range(3) if i != along]
    grainn = vnoise(np.stack([L[:, others[0]] * 3.0, a * 0.25, L[:, others[1]] * 3.0], 1), 11)
    c = col(base, n) * (0.78 + 0.32 * grainn)[:, None]
    if boards:
        across = L[:, others[0]] + L[:, others[1]]
        seam = np.abs(np.mod(across, 2.0) - 1.0) > 0.88
        c = mixc(c, c * 0.6, seam.astype(float))
    return c * (0.92 + 0.14 * grain(s, 3))[:, None]


def iron(s, base=IRON):
    n = len(s["P"])
    return col(base, n) * (0.8 + 0.3 * vnoise(s["L"] * 2.5, 21))[:, None] * (0.95 + 0.1 * grain(s, 4))[:, None]


def wheel(s, radius):
    """A spoked cartwheel painted on the side faces of a square box; everything outside the rim is cut away."""
    n = len(s["P"])
    face = s["face"]
    if face not in ("left", "right"):
        return col(IRON, n), np.zeros(n, bool)
    L = s["L"]
    lo, hi = s["lo"], s["hi"]
    cy, cz = (lo[1] + hi[1]) / 2, (lo[2] + hi[2]) / 2
    dy, dz = L[:, 1] - cy, L[:, 2] - cz
    r = np.sqrt(dy * dy + dz * dz)
    ang = np.arctan2(dy, dz)
    keep = r <= radius
    c = wood(s, DARK_WOOD, along=1, boards=False)
    rim = r > radius - 0.9
    c = mixc(c, iron(s), rim.astype(float))
    spoke = (np.abs(np.sin(ang * 4)) < 0.22 / np.maximum(r, 0.5) * 3.0) & (r > 1.0) & ~rim
    felloe = (r > radius - 2.0) & ~rim
    hub = r < 1.3
    keep &= rim | spoke | felloe | hub
    c = mixc(c, iron(s, (70, 70, 74)), (r < 0.6).astype(float))
    return c, keep


# ===================================================================== supply cart

def cart_model():
    m = Model("supply_cart", 128, 128)
    body = m.part("body", None, (0, 0, 0), boxes=[
        Box((-7, 15, -11), (14, 2, 22), tag="bed"),
        Box((-7, 9, -11), (1, 6, 22), tag="side"), Box((6, 9, -11), (1, 6, 22), tag="side"),
        Box((-6, 9, -11), (12, 6, 1), tag="end"), Box((-6, 9, 10), (12, 6, 1), tag="end"),
        Box((-6, 12.5, -14), (12, 1, 3), tag="bench"),
        Box((-5.5, 15.5, -28), (1, 1, 17), tag="shaft"), Box((4.5, 15.5, -28), (1, 1, 17), tag="shaft"),
        Box((-8.5, 17.5, 0.5), (17, 1, 1), tag="axle")])
    m.part("cover", body, (0, 0, 0), boxes=[Box((-7.5, 1, -9), (15, 9, 16), tag="canvas")])
    m.part("cargo", body, (0, 0, 0), boxes=[Box((-5, 5, 3), (5, 7, 5), tag="barrel"), Box((1, 8, 4), (4, 4, 5), tag="crate")])
    m.part("left_wheel", None, (8.6, 18, 1), boxes=[Box((-0.5, -6, -6), (1, 12, 12), tag="wheel")], tag="wheel")
    m.part("right_wheel", None, (-8.6, 18, 1), boxes=[Box((-0.5, -6, -6), (1, 12, 12), tag="wheel")], tag="wheel")
    m.pack()
    return m


def cart_shader(s):
    tag, bt, face = s["part"].tag, s["box"].tag, s["face"]
    n = len(s["P"])
    if tag == "wheel":
        return wheel(s, 6)
    if bt == "canvas":
        L = s["L"]
        weave = vnoise(np.stack([L[:, 0] * 6, L[:, 1] * 6, L[:, 2] * 6], 1), 5)
        c = col(CANVAS, n) * (0.88 + 0.16 * weave)[:, None]
        hoop = np.abs(np.mod(L[:, 2] + 9, 5.3) - 2.65) > 2.35
        c = mixc(c, c * 0.72, hoop.astype(float) * (face != "top"))
        patch = (vnoise(L * 0.6, 77) > 0.72).astype(float)
        c = mixc(c, col((190, 170, 128), n), patch * 0.6)
        if face in ("left", "right", "front", "back"):
            c = c * (1.0 - 0.18 * smooth(0.6, 1.0, s["b"]))[:, None]
        return c
    if bt == "barrel":
        c = wood(s, (150, 104, 60), along=1)
        band = (np.abs(s["b"] - 0.22) < 0.06) | (np.abs(s["b"] - 0.78) < 0.06)
        return mixc(c, iron(s), band.astype(float) * (face not in ("top", "bottom")))
    if bt in ("shaft", "axle"):
        return wood(s, DARK_WOOD, along=2 if bt == "shaft" else 0, boards=False)
    c = wood(s, WOOD, along=2 if bt in ("bed", "side") else 0)
    if bt == "side" and face in ("left", "right"):
        strap = np.abs(np.mod(s["L"][:, 2] + 11, 7.33) - 3.66) > 3.3
        c = mixc(c, iron(s), strap.astype(float))
    return c


# ===================================================================== battering ram

def ram_model():
    m = Model("battering_ram", 256, 128)
    body = m.part("body", None, boxes=[
        Box((-8, 17, -14), (16, 2, 28), tag="frame"),
        Box((-8, 3, -13), (2, 14, 2), tag="post"), Box((6, 3, -13), (2, 14, 2), tag="post"),
        Box((-8, 3, 11), (2, 14, 2), tag="post"), Box((6, 3, 11), (2, 14, 2), tag="post"),
        Box((-8, 2, -14), (16, 1, 28), tag="beam")])
    m.part("roof_left", body, (0, 0, 0), (0, 0, -0.62), [Box((-12, -1, -15), (12, 1.5, 30), tag="hide")])
    m.part("roof_right", body, (0, 0, 0), (0, 0, 0.62), [Box((0, -1, -15), (12, 1.5, 30), tag="hide")])
    m.part("ram", body, (0, 10, 0), boxes=[Box((-2, -2, -22), (4, 4, 34), tag="log"), Box((-2.5, -2.5, -25), (5, 5, 3), tag="head")])
    m.part("chains", body, (0, 0, 0), boxes=[Box((-0.5, 3, -6), (1, 5, 1), tag="chain"), Box((-0.5, 3, 6), (1, 5, 1), tag="chain")])
    for name, x, z in (("wheel_fl", 8.6, -9), ("wheel_fr", -8.6, -9), ("wheel_bl", 8.6, 9), ("wheel_br", -8.6, 9)):
        m.part(name, None, (x, 19.5, z), boxes=[Box((-0.5, -4.5, -4.5), (1, 9, 9), tag="wheel")], tag="wheel")
    m.pack()
    return m


def ram_shader(s):
    tag, bt, face = s["part"].tag, s["box"].tag, s["face"]
    n = len(s["P"])
    if tag == "wheel":
        return wheel(s, 4.5)
    if bt == "hide":
        L = s["L"]
        c = col((120, 82, 52), n) * (0.75 + 0.35 * vnoise(L * 1.3, 8))[:, None]
        stitch = np.abs(np.mod(L[:, 2] + 15, 6) - 3) > 2.85
        c = mixc(c, col((200, 180, 140), n), stitch.astype(float) * 0.7)
        return c
    if bt == "head":
        return iron(s, (70, 72, 78))
    if bt == "log":
        L = s["L"]
        if face in ("front", "back"):
            r = np.sqrt(L[:, 0] ** 2 + L[:, 1] ** 2)
            return col((168, 130, 84), n) * (0.85 + 0.2 * np.sin(r * 5))[:, None]
        c = col((96, 70, 46), n) * (0.75 + 0.35 * vnoise(np.stack([L[:, 0] * 3, L[:, 1] * 3, L[:, 2] * 0.3], 1), 9))[:, None]
        band = np.abs(np.mod(L[:, 2] + 22, 11) - 5.5) > 5.0
        return mixc(c, iron(s), band.astype(float))
    if bt == "chain":
        return iron(s) * (0.7 + 0.5 * (np.mod(s["py"] // 2, 2)))[:, None]
    return wood(s, DARK_WOOD if bt in ("post", "beam") else WOOD, along=1 if bt == "post" else 2)


# ===================================================================== trebuchet

def trebuchet_model():
    m = Model("trebuchet", 256, 128)
    base = m.part("base", None, boxes=[
        Box((-10, 21, -18), (3, 3, 36), tag="skid"), Box((7, 21, -18), (3, 3, 36), tag="skid"),
        Box((-10, 19, -15), (20, 2, 3), tag="cross"), Box((-10, 19, 12), (20, 2, 3), tag="cross"),
        Box((-9, -4, -1.5), (3, 23, 3), tag="upright"), Box((6, -4, -1.5), (3, 23, 3), tag="upright"),
        Box((-9, 1, -12), (2, 2, 24), tag="brace"), Box((7, 1, -12), (2, 2, 24), tag="brace"),
        Box((-10, -6, -1), (20, 2, 2), tag="axle")])
    arm = m.part("arm", base, (0, -5, 0), (0, 0, 0), [Box((-1.5, -1.5, -30), (3, 3, 40), tag="beam"),
                                                     Box((-4.5, -1, 7), (9, 9, 6), tag="weight")], tag="arm")
    m.part("sling", arm, (0, 0, -30), boxes=[Box((-0.5, 0, -0.5), (1, 8, 1), tag="rope"), Box((-2, 8, -2), (4, 3, 4), tag="stone")], tag="sling")
    m.pack()
    return m


def trebuchet_shader(s):
    tag, bt, face = s["part"].tag, s["box"].tag, s["face"]
    n = len(s["P"])
    if bt == "rope":
        return col(ROPE, n) * (0.8 + 0.3 * (np.mod(s["py"] // 2, 2)))[:, None]
    if bt == "stone":
        return col((128, 126, 122), n) * (0.75 + 0.35 * vnoise(s["L"] * 1.5, 3))[:, None]
    if bt == "weight":
        c = wood(s, DARK_WOOD, along=0)
        band = np.abs(s["b"] - 0.5) > 0.42
        return mixc(c, iron(s), band.astype(float) * (face not in ("top", "bottom")))
    if bt == "axle":
        return iron(s)
    if bt == "beam":
        c = wood(s, WOOD, along=2, boards=False)
        band = np.abs(np.mod(s["L"][:, 2] + 30, 8) - 4) > 3.6
        return mixc(c, iron(s), band.astype(float))
    return wood(s, DARK_WOOD if bt in ("skid", "cross") else WOOD, along=1 if bt == "upright" else (2 if bt in ("skid", "brace") else 0), boards=False)


# ===================================================================== cannon

def cannon_model():
    m = Model("cannon", 128, 64)
    carriage = m.part("carriage", None, boxes=[
        Box((-4.5, 13, -8), (2, 6, 14), tag="cheek"), Box((2.5, 13, -8), (2, 6, 14), tag="cheek"),
        Box((-6.5, 18, -2), (13, 2, 2), tag="axle")])
    m.part("trail", carriage, (0, 17, 5), (-0.32, 0, 0), [Box((-2, 0, 0), (4, 2, 16), tag="trail")])
    barrel = m.part("barrel", carriage, (0, 13, -1), boxes=[
        Box((-2.5, -2.5, -15), (5, 5, 21), tag="tube"), Box((-3, -3, -16), (6, 6, 2), tag="muzzle"),
        Box((-3, -3, 3), (6, 6, 3), tag="breech"), Box((-1, -1, 6), (2, 2, 2), tag="knob"),
        Box((-4, -0.75, -0.75), (8, 1.5, 1.5), tag="trunnion")], tag="barrel")
    for name, x in (("left_wheel", 6.8), ("right_wheel", -6.8)):
        m.part(name, None, (x, 18.5, -1), boxes=[Box((-0.75, -5.5, -5.5), (1.5, 11, 11), tag="wheel")], tag="wheel")
    m.pack()
    return m


def cannon_shader(s):
    tag, bt, face = s["part"].tag, s["box"].tag, s["face"]
    n = len(s["P"])
    if tag == "wheel":
        return wheel(s, 5.5)
    if tag == "barrel":
        L = s["L"]
        c = col(BRONZE, n) * (0.78 + 0.3 * vnoise(L * 2.0, 31))[:, None]
        c = c * (1.0 + 0.25 * smooth(0.3, 0.0, np.abs(s["a"] - 0.35)))[:, None] if face in ("left", "right", "top", "bottom") else c
        if bt == "tube":
            ring = np.abs(np.mod(L[:, 2] + 15, 7) - 3.5) > 3.1
            c = mixc(c, c * 1.15, ring.astype(float))
        if bt == "muzzle" and face == "front":
            r = np.sqrt(L[:, 0] ** 2 + L[:, 1] ** 2)
            c = mixc(c, col((16, 14, 12), n), smooth(2.0, 1.6, r))
        if bt == "trunnion":
            c = iron(s)
        return c
    if bt == "axle":
        return iron(s)
    return wood(s, (110, 70, 40), along=2 if bt in ("cheek", "trail") else 0, boards=False)


# ===================================================================== output

ENGINES = [
    ("supplyCart", cart_model, cart_shader, "supply_cart"),
    ("batteringRam", ram_model, ram_shader, "battering_ram"),
    ("trebuchet", trebuchet_model, trebuchet_shader, "trebuchet"),
    ("cannon", cannon_model, cannon_shader, "cannon"),
]


def write():
    os.makedirs(OUT, exist_ok=True)
    os.makedirs(os.path.dirname(JAVA), exist_ok=True)
    java = ["// Generated by tools/gen_engines.py from the same description as the textures. Do not edit by hand.",
            "package com.dominionmod.dominion.client.render.siege;", "",
            "import net.minecraft.client.model.geom.PartPose;",
            "import net.minecraft.client.model.geom.builders.CubeDeformation;",
            "import net.minecraft.client.model.geom.builders.CubeListBuilder;",
            "import net.minecraft.client.model.geom.builders.MeshDefinition;",
            "import net.minecraft.client.model.geom.builders.PartDefinition;", "",
            "/** Meshes for the supply cart and the siege engines. */",
            "public final class EngineMeshes {", "    private EngineMeshes() {}", ""]
    for method, make, shader, tex in ENGINES:
        m = make()
        java.append(m.java(method).replace("    static MeshDefinition", "    public static MeshDefinition", 1))
        java.append(f"    public static final int {method.upper()}_W = {m.tw}, {method.upper()}_H = {m.th};")
        java.append("")
        m.paint(shader, os.path.join(OUT, tex + ".png"))
    java.append("}")
    with open(JAVA, "w") as fh:
        fh.write("\n".join(java) + "\n")


# ===================================================================== item icons

ICON_VIEW = {"supply_cart": (2.4, 0.5), "battering_ram": (2.3, 0.45), "trebuchet": (2.5, 0.3), "cannon": (2.3, 0.45)}


def icon(model, tex_path, yaw, pitch, out=32, ss=8):
    """Render the actual 3D model into a transparent item icon (supersampled, then box-filtered)."""
    import math
    import numpy as np
    tex = np.asarray(Image.open(tex_path).convert("RGBA"))
    pts = []
    cy, sy, cp, sp = math.cos(yaw), math.sin(yaw), math.cos(pitch), math.sin(pitch)
    light = {"top": 1.0, "bottom": 0.5, "front": 0.82, "back": 0.82, "left": 0.64, "right": 0.64}
    for smp in model.samples():
        P = smp["P"].copy()
        P[:, 1] = -P[:, 1]
        x = P[:, 0] * cy + P[:, 2] * sy
        z = -P[:, 0] * sy + P[:, 2] * cy
        y = P[:, 1] * cp - z * sp
        z = P[:, 1] * sp + z * cp
        c = tex[smp["py"], smp["px"]]
        ok = c[:, 3] > 0
        pts.append((x[ok], y[ok], z[ok], c[ok, :3] * light[smp["face"]]))
    X = np.concatenate([p[0] for p in pts]); Y = np.concatenate([p[1] for p in pts])
    Z = np.concatenate([p[2] for p in pts]); C = np.concatenate([p[3] for p in pts])
    size = out * ss
    span = max(X.max() - X.min(), Y.max() - Y.min()) * 1.06
    zoom = size / span
    u = ((X - (X.max() + X.min()) / 2) * zoom + size / 2).astype(int)
    v = (size / 2 - (Y - (Y.max() + Y.min()) / 2) * zoom).astype(int)
    img = np.zeros((size, size, 4), float)
    zb = np.full((size, size), 1e9)
    r = max(1, int(zoom / S * 0.75))
    order = np.argsort(-Z)
    for du in range(-r, r + 1):
        for dv in range(-r, r + 1):
            uu, vv = u[order] + du, v[order] + dv
            ok = (uu >= 0) & (uu < size) & (vv >= 0) & (vv < size)
            uu, vv, zz, cc = uu[ok], vv[ok], Z[order][ok], C[order][ok]
            front = zz < zb[vv, uu]
            zb[vv[front], uu[front]] = zz[front]
            img[vv[front], uu[front], :3] = cc[front]
            img[vv[front], uu[front], 3] = 255
    small = img.reshape(out, ss, out, ss, 4)
    a = small[..., 3].mean(axis=(1, 3))
    rgb = (small[..., :3] * small[..., 3:4]).sum(axis=(1, 3)) / np.maximum(small[..., 3].sum(axis=(1, 3)), 1)[..., None]
    res = np.dstack([rgb, np.where(a > 90, 255, 0)]).astype(np.uint8)
    # dark outline so the icon reads on any slot background
    alpha = res[..., 3] > 0
    edge = np.zeros_like(alpha)
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        edge |= np.roll(np.roll(alpha, dx, 1), dy, 0)
    edge &= ~alpha
    res[edge] = (34, 26, 20, 255)
    return Image.fromarray(res, "RGBA")


def cannonball_icon():
    import numpy as np
    n = 16
    img = np.zeros((n, n, 4), np.uint8)
    for y in range(n):
        for x in range(n):
            dx, dy = (x - 7.5) / 5.5, (y - 8.5) / 5.5
            d = dx * dx + dy * dy
            if d <= 1:
                nz = (1 - d) ** 0.5
                lit = max(0, -0.5 * dx - 0.6 * dy + 0.62 * nz)
                g = int(30 + 120 * lit ** 1.4 + (_hash_px(x, y) % 9))
                img[y, x] = (g, g, int(g * 1.08), 255)
            elif d <= 1.35:
                img[y, x] = (24, 22, 24, 255)
    return Image.fromarray(img, "RGBA")


def _hash_px(x, y):
    import zlib
    return zlib.crc32(bytes([x, y, 7]))


def write_items():
    import json
    assets = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "dominion")
    icons = {}
    for method, make, shader, tex in ENGINES:
        yaw, pitch = ICON_VIEW[tex]
        icons[tex] = icon(make(), os.path.join(OUT, tex + ".png"), yaw, pitch)
    icons["cannonball"] = cannonball_icon()
    for name, im in icons.items():
        im.save(os.path.join(assets, "textures", "item", name + ".png"))
        with open(os.path.join(assets, "models", "item", name + ".json"), "w") as f:
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"dominion:item/{name}"}}, f, indent=2)
        with open(os.path.join(assets, "items", name + ".json"), "w") as f:
            json.dump({"model": {"type": "minecraft:model", "model": f"dominion:item/{name}"}}, f, indent=2)


if __name__ == "__main__":
    write()
    write_items()
