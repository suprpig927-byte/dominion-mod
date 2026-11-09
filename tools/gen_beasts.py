"""High-detail horses (Clydesdale, Mustang, Shetland pony) and deer.

Run: python3 tools/gen_beasts.py (gen_hd.py runs it too).

Each animal is described once here as a tree of model parts. From that description this script
writes BOTH the Java mesh code (client/render/wild/BeastMeshes.java) and the textures, so the
UV layout can never drift out of step with the model.

Textures are painted at 4 pixels per model unit. Every texture pixel is mapped back to the 3D
point it covers on the animal (at rest pose), and the coat is "sprayed on" in 3D: socks are
decided by height above the ground, pinto patches and dapples are 3D noise, the blaze follows
the face, so markings run seamlessly across box edges.
"""
import math
import os
import zlib

import numpy as np
from PIL import Image

HERE = os.path.dirname(__file__)
ROOT = os.path.join(HERE, "..")
ENTITY = os.path.join(ROOT, "src", "main", "resources", "assets", "dominion", "textures", "entity")
JAVA = os.path.join(ROOT, "src", "main", "java", "com", "dominionmod", "dominion", "client", "render", "wild", "BeastMeshes.java")
S = 4  # texture pixels per model unit


# ===================================================================== model description

class Box:
    def __init__(self, origin, size, grow=0.0, tag=None):
        self.origin, self.size, self.grow, self.tag = origin, size, grow, tag
        self.uv = None

    def footprint(self):
        w, h, d = self.size
        return math.ceil(2 * (d + w)), math.ceil(d + h)


class Part:
    def __init__(self, name, parent, pivot, rot, boxes, tag):
        self.name, self.parent, self.pivot, self.rot, self.boxes, self.tag = name, parent, pivot, rot, boxes, tag

    def matrix(self):
        """4x4 transform from this part's space to model space (rest pose)."""
        rx, ry, rz = self.rot
        cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
        Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
        Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
        Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
        M = np.eye(4)
        M[:3, :3] = Rz @ Ry @ Rx
        M[:3, 3] = self.pivot
        return M if self.parent is None else self.parent.matrix() @ M


class Model:
    def __init__(self, name, tw, th):
        self.name, self.tw, self.th, self.parts = name, tw, th, []

    def part(self, name, parent, pivot=(0, 0, 0), rot=(0, 0, 0), boxes=(), tag=None):
        p = Part(name, parent, tuple(float(v) for v in pivot), rot, list(boxes), tag or name)
        self.parts.append(p)
        return p

    def pack(self):
        boxes = [b for p in self.parts for b in p.boxes]
        boxes.sort(key=lambda b: (-b.footprint()[1], -b.footprint()[0]))
        x = y = row = 0
        for b in boxes:
            fw, fh = b.footprint()
            if x + fw > self.tw:
                x, y, row = 0, y + row, 0
            b.uv = (x, y)
            x += fw
            row = max(row, fh)
        if y + row > self.th:
            raise SystemExit(f"{self.name}: texture atlas overflow ({y + row} > {self.th})")

    # ------------------------------------------------------------------ java
    def java(self, method):
        f = lambda v: f"{float(v):g}f".replace("e", "E")
        out = [f"    static MeshDefinition {method}() {{",
               "        MeshDefinition mesh = new MeshDefinition();",
               "        PartDefinition root = mesh.getRoot();"]
        var = {}
        for i, p in enumerate(self.parts):
            cubes = "CubeListBuilder.create()"
            for b in p.boxes:
                (x, y, z), (w, h, d) = b.origin, b.size
                cubes += (f"\n                .texOffs({b.uv[0]}, {b.uv[1]}).addBox({f(x)}, {f(y)}, {f(z)}, {f(w)}, {f(h)}, {f(d)}, "
                          f"new CubeDeformation({f(b.grow)}))")
            px, py, pz = p.pivot
            rx, ry, rz = p.rot
            pose = f"PartPose.offsetAndRotation({f(px)}, {f(py)}, {f(pz)}, {f(rx)}, {f(ry)}, {f(rz)})"
            parent = "root" if p.parent is None else var[p.parent.name]
            v = f"p{i}"
            var[p.name] = v
            out.append(f"        PartDefinition {v} = {parent}.addOrReplaceChild(\"{p.name}\", {cubes}, {pose});")
        out.append("        return mesh;")
        out.append("    }")
        return "\n".join(out)

    # ------------------------------------------------------------------ painting
    def samples(self):
        """For every box face: pixel coords plus the 3D point/normal each pixel covers."""
        for p in self.parts:
            M = p.matrix()
            for b in p.boxes:
                (x0, y0, z0), (w, h, d), g = b.origin, b.size, b.grow
                u, v = b.uv
                lo = np.array([x0 - g, y0 - g, z0 - g])
                hi = np.array([x0 + w + g, y0 + h + g, z0 + d + g])
                c = lambda i, j, k: np.array([hi[0] if i else lo[0], hi[1] if j else lo[1], hi[2] if k else lo[2]])
                t0, t1, t2, t3 = c(0, 0, 0), c(1, 0, 0), c(1, 1, 0), c(0, 1, 0)
                l0, l1, l2, l3 = c(0, 0, 1), c(1, 0, 1), c(1, 1, 1), c(0, 1, 1)
                faces = {  # name: (rect u0,v0,u1,v1), corner at (u0,v0), at (u1,v0), at (u0,v1), outward normal
                    "top": ((u + d, v, u + d + w, v + d), l0, l1, t0, (0, -1, 0)),
                    "bottom": ((u + d + w, v, u + d + 2 * w, v + d), l3, l2, t3, (0, 1, 0)),
                    "right": ((u, v + d, u + d, v + d + h), l0, t0, l3, (-1, 0, 0)),
                    "front": ((u + d, v + d, u + d + w, v + d + h), t0, t1, t3, (0, 0, -1)),
                    "left": ((u + d + w, v + d, u + 2 * d + w, v + d + h), t1, l1, t2, (1, 0, 0)),
                    "back": ((u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h), l1, l0, l2, (0, 0, 1)),
                }
                for face, ((ua, va, ub, vb), C00, C10, C01, n) in faces.items():
                    if ub - ua <= 0 or vb - va <= 0:
                        continue
                    xs = np.arange(int(round(ua * S)), int(round(ub * S)))
                    ys = np.arange(int(round(va * S)), int(round(vb * S)))
                    if len(xs) == 0 or len(ys) == 0:
                        continue
                    X, Y = np.meshgrid(xs, ys)
                    a = ((X + 0.5) / S - ua) / (ub - ua)
                    bb = ((Y + 0.5) / S - va) / (vb - va)
                    local = C00 + a[..., None] * (C10 - C00) + bb[..., None] * (C01 - C00)
                    pts = local.reshape(-1, 3)
                    world = (M[:3, :3] @ pts.T).T + M[:3, 3]
                    normal = M[:3, :3] @ np.array(n, float)
                    yield dict(part=p, box=b, face=face, px=X.ravel(), py=Y.ravel(), P=world, L=pts,
                               N=np.broadcast_to(normal, world.shape), a=a.ravel(), b=bb.ravel(),
                               size=(w, h, d), lo=lo, hi=hi)

    def paint(self, shader, path):
        img = np.zeros((self.th * S, self.tw * S, 4), np.uint8)
        for s in self.samples():
            out = shader(s)
            alpha = np.full(len(s["px"]), 255, np.uint8)
            if isinstance(out, tuple):
                out, keep = out
                alpha = np.where(keep, 255, 0).astype(np.uint8)
            rgb = np.clip(out, 0, 255)
            img[s["py"], s["px"], :3] = rgb.astype(np.uint8)
            img[s["py"], s["px"], 3] = alpha
        Image.fromarray(img, "RGBA").save(path)


# ===================================================================== noise helpers

def _hash(ix, iy, iz, seed):
    h = (ix * 374761393 + iy * 668265263 + iz * 1274126177 + seed * 951274213) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def vnoise(P, seed=0):
    P = np.asarray(P, float)
    i = np.floor(P).astype(np.int64)
    f = P - i
    f = f * f * (3 - 2 * f)
    out = 0.0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (f[:, 0] if dx else 1 - f[:, 0]) * (f[:, 1] if dy else 1 - f[:, 1]) * (f[:, 2] if dz else 1 - f[:, 2])
                out = out + w * _hash(i[:, 0] + dx, i[:, 1] + dy, i[:, 2] + dz, seed)
    return out


def fbm(P, seed=0, octaves=4):
    total, amp, norm = 0.0, 1.0, 0.0
    for o in range(octaves):
        total = total + amp * vnoise(P * (2 ** o), seed + o * 17)
        norm += amp
        amp *= 0.5
    return total / norm


def worley(P, seed=0):
    """Distance to the nearest random feature point (cells of size 1)."""
    i = np.floor(P).astype(np.int64)
    best = np.full(len(P), 9.0)
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for dz in (-1, 0, 1):
                cx, cy, cz = i[:, 0] + dx, i[:, 1] + dy, i[:, 2] + dz
                fx = cx + _hash(cx, cy, cz, seed)
                fy = cy + _hash(cx, cy, cz, seed + 1)
                fz = cz + _hash(cx, cy, cz, seed + 2)
                d = np.sqrt((P[:, 0] - fx) ** 2 + (P[:, 1] - fy) ** 2 + (P[:, 2] - fz) ** 2)
                best = np.minimum(best, d)
    return best


def grain(s, seed=0):
    """Per-pixel speckle so close-ups read as fur rather than flat colour."""
    return _hash(s["px"].astype(np.int64), s["py"].astype(np.int64), np.full(len(s["px"]), seed, np.int64), 5)


def smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def col(c, n=None):
    c = np.array(c, float)
    return c if n is None else np.broadcast_to(c, (n, 3)).copy()


def mixc(a, b, t):
    t = np.asarray(t, float)[..., None]
    return a * (1 - t) + b * t


# ===================================================================== horses

HORSE_COATS = {
    # Clydesdale: big draft horse. Bay or black with a bald face, white stockings to the knee,
    # white feathering over the hooves and white splashes on the belly.
    "clydesdale": {
        "brown": dict(base=(118, 60, 30), points=(26, 20, 18), mane=(30, 22, 18), muzzle=(60, 40, 32), blaze="bald",
                      socks=(9, 9, 9, 9), belly=True),
        "black": dict(base=(36, 31, 33), points=(20, 18, 20), mane=(22, 20, 22), muzzle=(44, 38, 38), blaze="bald",
                      socks=(9, 9, 9, 9), belly=True, sheen=(70, 60, 75)),
        "dark_brown": dict(base=(74, 42, 26), points=(24, 18, 16), mane=(26, 19, 16), muzzle=(56, 40, 32), blaze="bald",
                           socks=(10, 8, 9, 10), belly=True),
    },
    # Mustang: lean wild horse. Buckskin, grullo, pinto, sorrel and bay, with primitive markings.
    "mustang": {
        "creamy": dict(base=(198, 160, 98), points=(34, 26, 22), mane=(30, 24, 20), muzzle=(80, 62, 50), blaze="snip",
                       socks=(0, 0, 0, 0), dorsal=(120, 90, 55)),
        "gray": dict(base=(126, 116, 102), points=(32, 28, 26), mane=(30, 27, 26), muzzle=(54, 48, 44), blaze=None,
                     socks=(0, 0, 0, 0), dorsal=(38, 32, 28), bars=True, darkhead=(70, 64, 58)),
        "white": dict(base=(132, 64, 34), points=None, mane=(40, 26, 20), muzzle=(80, 52, 40), blaze="blaze",
                      socks=(11, 11, 11, 11), pinto=True),
        "chestnut": dict(base=(170, 80, 38), points=None, mane=(150, 66, 30), muzzle=(110, 62, 42), blaze="star",
                         socks=(0, 0, 4, 0)),
        "brown": dict(base=(112, 58, 30), points=(28, 21, 18), mane=(28, 21, 18), muzzle=(62, 42, 32), blaze="star",
                      socks=(0, 3, 0, 0)),
    },
    # Shetland pony: tiny, stocky and shaggy, with a thick mane, a big forelock and a tail to the ground.
    "shetland": {
        "black": dict(base=(40, 33, 30), points=None, mane=(30, 25, 23), muzzle=(56, 46, 40), blaze=None, socks=(0, 0, 0, 0),
                      shaggy=True, tips=(92, 66, 46)),
        "chestnut": dict(base=(146, 76, 38), points=None, mane=(226, 200, 150), muzzle=(110, 70, 50), blaze="blaze",
                         socks=(0, 0, 0, 0), shaggy=True, tips=(200, 130, 74)),
        "white": dict(base=(36, 31, 30), points=None, mane=(36, 31, 30), muzzle=(60, 50, 46), blaze="blaze", socks=(5, 5, 5, 5),
                      shaggy=True, tips=(80, 70, 66), piebald=True),
        "gray": dict(base=(160, 160, 164), points=(78, 76, 80), mane=(222, 222, 220), muzzle=(70, 66, 68), blaze=None,
                     socks=(0, 0, 0, 0), shaggy=True, tips=(206, 206, 208), dapple=True),
        "creamy": dict(base=(214, 168, 92), points=None, mane=(244, 236, 214), muzzle=(150, 112, 80), blaze="blaze",
                       socks=(0, 0, 0, 0), shaggy=True, tips=(236, 200, 136)),
    },
}

HORSE = dict(
    clydesdale=dict(leg=11, body_h=11, neck=(-2.5, -6, -2.5, 5, 12, 7.5), muzzle=(-2, -11, -7.5, 4, 5, 5.5),
                    mane=[((-1.5, -11.5, 4.5), (3, 17, 2.5))], forelock=((-1.5, -12, -2.5), (3, 2, 3.5)),
                    tail=((-2, 0, -0.5), (4, 13, 4.5)), feather=(4.5, 0.6), ears=(2, 3)),
    mustang=dict(leg=11, body_h=10, neck=(-2, -6, -2, 4, 12, 7), muzzle=(-2, -11, -7, 4, 5, 5),
                 mane=[((-1, -11.5, 4.8), (2, 17, 2.5)), ((-2.6, -10.5, 0.5), (1, 14, 5))],
                 forelock=((-1.5, -12, -3), (3, 3, 2.5)), tail=((-1.5, 0, 0), (3, 18, 4)), feather=None, ears=(2, 3)),
    shetland=dict(leg=8, body_h=11, neck=(-2.5, -6, -2, 5, 12, 7), muzzle=(-2, -11, -6, 4, 5, 4),
                  mane=[((-2, -12, 4), (4, 17, 4)), ((2, -10.5, 0.5), (1, 13, 4))],
                  forelock=((-2.5, -12.5, -4), (5, 5, 3)), tail=((-2, 0, -0.5), (4, 16, 5)), feather=(3, 0.6), ears=(2, 2)),
)
LEGS = [("right_front_leg", -4, -10, -1, -1.9), ("left_front_leg", 4, -10, -3, -1.9),
        ("right_hind_leg", -4, 7, -1, -1), ("left_hind_leg", 4, 7, -3, -1)]


def horse_model(breed):
    spec = HORSE[breed]
    L = spec["leg"]
    dy = 11 - L
    m = Model("horse_" + breed, 128, 128)
    body = m.part("body", None, (0, 11 + dy, 5), boxes=[Box((-5, -8, -17), (10, spec["body_h"], 22), 0.05)])
    (tx, ty, tz), tsz = spec["tail"]
    m.part("tail", body, (0, -5, 2), (math.pi / 6, 0, 0), [Box((tx, ty, tz), tsz)])
    hp = m.part("head_parts", None, (0, 4 + dy, -12), (math.pi / 6, 0, 0),
                [Box(spec["neck"][:3], spec["neck"][3:], tag="neck")], tag="neck")
    head = m.part("head", hp, boxes=[Box((-3, -11, -2), (6, 5, 7))])
    ew, eh = spec["ears"]
    m.part("left_ear", head, boxes=[Box((0.5, -11 - eh, 4), (ew, eh, 1))], tag="ear")
    m.part("right_ear", head, boxes=[Box((-0.5 - ew, -11 - eh, 4), (ew, eh, 1))], tag="ear")
    mo, ms = spec["muzzle"][:3], spec["muzzle"][3:]
    m.part("upper_mouth", hp, boxes=[Box(mo, ms)], tag="muzzle")
    m.part("mane", hp, boxes=[Box(o, s) for o, s in spec["mane"]])
    fo, fs = spec["forelock"]
    m.part("forelock", head, boxes=[Box(fo, fs)])
    for i, (name, x, z, bx, bz) in enumerate(LEGS):
        boxes = [Box((bx, -1.01, bz), (4, L, 4), tag="leg")]
        if spec["feather"]:
            fh, fg = spec["feather"]
            boxes.append(Box((bx, L - 1 - fh, bz), (4, fh, 4), fg, tag="feather"))
        m.part(name, None, (x, 14 + dy, z), boxes=boxes, tag="leg%d" % i)
    m.pack()
    return m, dy


def horse_shader(breed, coat, dy):
    c = HORSE_COATS[breed][coat]
    shaggy = c.get("shaggy", False)
    base = col(c["base"])
    hair = col(c["mane"])
    ground = 24.0
    L = HORSE[breed]["leg"]
    top_y = 3 + dy  # top of the back
    belly_y = 3 + dy + HORSE[breed]["body_h"]
    seed = zlib.crc32(f"{breed}/{coat}".encode()) & 0xFFF

    def shader(s):
        P, N, tag, face = s["P"], s["N"], s["part"].tag, s["face"]
        bt = s["box"].tag
        n = len(P)
        g = grain(s, seed)
        x, y, z = P[:, 0], P[:, 1], P[:, 2]

        # -------------------------------------------------- hair: mane, forelock, tail, feathering
        if tag in ("mane", "forelock", "tail") or bt == "feather":
            Lc = s["L"]
            strands = vnoise(np.stack([Lc[:, 0] * 3.2, Lc[:, 1] * 0.35, Lc[:, 2] * 3.2], 1), seed + 9)
            fine = _hash((s["px"] // 1).astype(np.int64), (s["py"] // 3).astype(np.int64), np.full(n, seed, np.int64), 11)
            k = 0.72 + 0.38 * strands + 0.16 * (fine - 0.5)
            if bt == "feather":
                hc = col((236, 232, 222), n)
                if breed == "shetland":
                    sock = c["socks"][int(tag[3])] > 0
                    hc = col((236, 232, 222) if sock else c["tips"], n) * (1.0 if sock else 0.85)
                k = 0.86 + 0.18 * strands + 0.1 * (fine - 0.5)
                k = k * (1 - 0.25 * smooth(ground - 1.2, ground + 0.6, y))
            else:
                hc = col(c["mane"], n)
                if c.get("piebald") or c.get("pinto"):
                    hc = mixc(hc, col((235, 230, 222), n), patch_mask(P) if c.get("piebald") else pinto_mask(P) * 0.6)
                if tag == "tail" and c.get("dapple"):
                    hc = mixc(hc, col((150, 148, 150), n), 0.35 * smooth(0.3, 0.8, s["b"]))
            # roots darker, light catches the outer surface of the hair
            if tag == "mane":
                k = k * (0.8 + 0.25 * smooth(0, 1, np.abs(s["L"][:, 0]) / 3))
            if face == "top":
                k = k * 1.08
            keep = np.ones(n, bool)
            if face in ("left", "right", "front", "back"):
                strand = _hash((s["px"] // 2).astype(np.int64), np.full(n, 7, np.int64), np.full(n, seed, np.int64), 3)
                bb = s["b"]
                if tag == "tail":
                    keep = bb < 1 - 0.28 * strand
                elif tag == "forelock":
                    keep = bb < 1 - 0.45 * strand
                elif bt == "feather":
                    keep = bb > 0.4 * strand
                elif tag == "mane" and s["size"][0] <= 1:  # the drape over the side of the neck
                    keep = bb < 1 - 0.35 * strand
                    keep &= ~((face in ("front", "back")) & (bb > 0.55 + 0.4 * strand))
                hc = hc * (1 - 0.18 * smooth(0.6, 1.0, bb))[:, None] if tag == "tail" else hc
            return hc * k[:, None], keep

        # -------------------------------------------------- coat colour field
        coat_c = col(c["base"], n)
        if shaggy:
            clump = vnoise(np.stack([x * 1.3, y * 0.5, z * 1.3], 1), seed + 3)
            tipmix = smooth(0.55, 0.95, clump) * 0.55
            coat_c = mixc(coat_c, col(c["tips"], n), tipmix)
        # dapple grey: lighter coins ringed with darker grey
        if c.get("dapple"):
            d = worley(P * 0.42, seed + 21)
            coat_c = mixc(coat_c * 0.84, col((226, 226, 228), n), smooth(0.55, 0.25, d) * 0.85)
            coat_c = mixc(coat_c, col((120, 120, 126), n), smooth(14, 22, y) * 0.8)
        # sheen on shoulder and hindquarter muscles
        if not shaggy:
            sh = np.exp(-(((z + 11) / 4.0) ** 2 + ((y - (8 + dy)) / 3.2) ** 2)) + np.exp(-(((z - 2) / 4.5) ** 2 + ((y - (6 + dy)) / 3.2) ** 2))
            side = np.abs(N[:, 0])
            coat_c = coat_c * (1 + 0.16 * sh * side)[:, None]
            if c.get("sheen"):
                coat_c = mixc(coat_c, col(c["sheen"], n), 0.25 * sh * side)
        # dorsal stripe (dun, grullo)
        if c.get("dorsal") is not None and tag == "body":
            stripe = smooth(1.1, 0.5, np.abs(x)) * (N[:, 1] < -0.5)
            coat_c = mixc(coat_c, col(c["dorsal"], n), stripe * 0.85)
        if c.get("darkhead") is not None and tag in ("head", "muzzle", "ear"):
            coat_c = mixc(coat_c, col(c["darkhead"], n), 0.8)
        # black points: lower legs, ear rims
        if c.get("points") is not None:
            if tag.startswith("leg"):
                knee = 14 + dy + L * 0.42 + (g - 0.5) * 0.6
                coat_c = mixc(coat_c, col(c["points"], n), smooth(knee - 1.5, knee + 0.5, y))
            if tag == "ear":
                coat_c = mixc(coat_c, col(c["points"], n), smooth(0.45, 0.1, s["b"]) * (face != "front"))
        # leg barring (grullo)
        if c.get("bars") and tag.startswith("leg"):
            band = (np.sin(y * 2.6) > 0.55) * smooth(14 + dy, 16 + dy, y) * smooth(22, 18, y)
            coat_c = mixc(coat_c, col(c["points"], n), band * 0.6)
        # pinto / piebald patches
        if c.get("pinto"):
            coat_c = mixc(coat_c, col((238, 234, 226), n), pinto_mask(P))
        if c.get("piebald"):
            coat_c = mixc(coat_c, col((236, 232, 226), n), patch_mask(P))
        # white socks / stockings (height above the ground, ragged edge)
        if tag.startswith("leg"):
            h = c["socks"][int(tag[3])]
            if h > 0:
                edge = ground - h + (vnoise(P * 1.7, seed + 5) - 0.5) * 2.2
                coat_c = mixc(coat_c, col((240, 236, 230), n), smooth(edge - 0.3, edge + 0.3, y))
        # white belly splashes (sabino Clydesdales)
        if c.get("belly") and tag == "body":
            nz = vnoise(P * 0.35, seed + 7)
            m = smooth(belly_y - 2.8, belly_y - 0.6, y + (nz - 0.5) * 3) * smooth(0.35, 0.6, nz + 0.25)
            coat_c = mixc(coat_c, col((238, 234, 228), n), np.clip(m * 1.4, 0, 1))

        # -------------------------------------------------- face: muzzle, blaze, eyes, nostrils
        if tag == "muzzle":
            soft = smooth(0.35, 0.95, s["b"]) * (face in ("left", "right", "front")) + (face == "bottom") * 1.0
            coat_c = mixc(coat_c, col(c["muzzle"], n), np.clip(soft, 0, 1) * 0.75)
        if c.get("blaze") and tag in ("head", "muzzle"):
            coat_c = blaze(coat_c, s, c["blaze"], n, g)
        if tag == "muzzle" and face == "front":
            # nostrils
            for sx in (-1.1, 1.1):
                lx, ly = s["L"][:, 0], s["L"][:, 1]
                lo = s["lo"]
                ny = lo[1] + (s["hi"][1] - lo[1]) * 0.58
                e = ((lx - sx) / 0.42) ** 2 + ((ly - ny) / 0.7) ** 2
                coat_c = mixc(coat_c, col((24, 18, 18), n), smooth(1.0, 0.6, e))
                coat_c = mixc(coat_c, coat_c * 0.7, smooth(2.2, 1.0, e) * 0.6)
        if tag == "muzzle" and face in ("left", "right"):
            ly = s["L"][:, 1]
            mouth = smooth(0.35, 0.0, np.abs(ly - (s["hi"][1] - 0.9))) * smooth(0.25, 0.6, s["a"] if face == "left" else 1 - s["a"])
            coat_c = mixc(coat_c, col((26, 20, 20), n), mouth * 0.8)
        if tag == "head" and face in ("left", "right"):
            lz, ly = s["L"][:, 2], s["L"][:, 1]
            e = ((lz - (-0.3)) / 0.85) ** 2 + ((ly - (-9.2)) / 0.62) ** 2
            coat_c = mixc(coat_c, coat_c * 0.62, smooth(2.6, 1.2, e))      # socket shadow
            coat_c = mixc(coat_c, col((22, 14, 12), n), smooth(1.0, 0.75, e))  # eye
            hl = ((lz - (-0.55)) / 0.22) ** 2 + ((ly - (-9.45)) / 0.18) ** 2
            coat_c = mixc(coat_c, col((210, 200, 190), n), smooth(1.0, 0.4, hl) * smooth(1.0, 0.75, e))
            # brow ridge
            br = smooth(0.35, 0.0, np.abs(ly - (-10.15) - 0.15 * (lz + 0.3))) * smooth(1.6, 0.6, np.abs(lz + 0.3))
            coat_c = mixc(coat_c, coat_c * 1.15, br * 0.6)
        if tag == "ear" and face == "front":
            inner = smooth(0.42, 0.25, np.abs(s["a"] - 0.5)) * smooth(0.12, 0.3, s["b"])
            coat_c = mixc(coat_c, coat_c * 0.45, inner)

        # -------------------------------------------------- hooves
        if tag.startswith("leg"):
            hoof = smooth(ground - 1.25, ground - 1.0, y)
            light = c["socks"][int(tag[3])] > 0
            hc = col((178, 160, 132) if light else (46, 40, 38), n)
            hc = hc * (0.9 + 0.2 * vnoise(np.stack([x * 6, y * 0.5, z * 6], 1), seed + 8))[:, None]
            coat_c = mixc(coat_c, hc, hoof)
            if face == "bottom":
                coat_c = col((70, 62, 54) if not light else (150, 134, 112), n)

        # -------------------------------------------------- lighting baked into the coat
        k = np.ones(n)
        if tag == "body":
            t = np.clip((y - top_y) / (belly_y - top_y), 0, 1)
            k *= 1.07 - 0.28 * t ** 1.6
        if tag.startswith("leg"):
            k *= 0.9 + 0.12 * smooth(14 + dy, 20, y)
            k *= 1 - 0.12 * (np.sign(x) * N[:, 0] < -0.5)  # inner leg faces sit in shadow
        if tag == "neck":
            k *= 1.0 + 0.06 * (face == "back")
        fur = 0.9 + 0.2 * g
        if shaggy:
            fur = 0.8 + 0.4 * (0.6 * g + 0.4 * vnoise(np.stack([x * 4, y * 1.2, z * 4], 1), seed + 13))
        else:
            fur = fur * (0.95 + 0.1 * vnoise(np.stack([x * 1.5, y * 3.5, z * 1.5], 1), seed + 14))
        return coat_c * (k * fur)[:, None]

    return shader


def pinto_mask(P):
    x, y, z = P[:, 0], P[:, 1], P[:, 2]
    n = fbm(P * 0.13, 101, 3)
    bias = 0.12 * np.cos((z + 5) / 6.5) + 0.25 * smooth(15, 20, y) - 0.2 * smooth(-14, -24, z)
    return smooth(0.52, 0.58, n + bias)


def patch_mask(P):
    n = fbm(P * 0.11, 202, 3)
    return smooth(0.5, 0.56, n + 0.08 * smooth(15, 20, P[:, 1]))


def blaze(coat_c, s, kind, n, g):
    face, L, tag = s["face"], s["L"], s["part"].tag
    lx, ly, lz = L[:, 0], L[:, 1], L[:, 2]
    white = col((240, 236, 230), n)
    jag = (g - 0.5) * 0.25
    m = np.zeros(n)
    if tag == "head" and face == "top":
        # forehead: star or the top of the blaze, toward the front of the skull
        if kind == "star":
            m = smooth(1.0, 0.6, np.abs(lx) + np.abs(lz - 0.4) * 0.8 + jag)
        else:
            w = {"bald": 2.0, "blaze": 1.2, "snip": 0.0}[kind]
            m = smooth(w + 0.2, w - 0.2, np.abs(lx) + jag) * smooth(2.5, 1.0, lz)
    elif tag == "muzzle" and face == "top" and kind in ("bald", "blaze"):
        w = 1.3 if kind == "bald" else 0.8
        m = smooth(w + 0.2, w - 0.2, np.abs(lx) + jag)
    elif tag == "muzzle" and face == "front":
        top = s["lo"][1]
        if kind in ("bald", "blaze"):
            w = 1.6 if kind == "bald" else 1.0
            m = smooth(w + 0.2, w - 0.2, np.abs(lx) + jag) * smooth(top + 3.6, top + 3.0, ly)
        elif kind == "snip":
            m = smooth(0.8, 0.5, np.abs(lx) + jag) * smooth(top + 1.2, top + 1.8, ly) * smooth(top + 3.4, top + 3.0, ly)
    elif tag == "muzzle" and face in ("left", "right") and kind == "bald":
        # a bald face wraps round onto the sides of the nose
        fwd = s["a"] if face == "left" else 1 - s["a"]
        m = smooth(0.55, 0.85, 1 - fwd) * smooth(0.2, 0.0, s["b"] - 0.35) * 0.0 + smooth(0.75, 0.95, 1 - fwd) * smooth(0.65, 0.5, s["b"])
    return mixc(coat_c, white, np.clip(m, 0, 1))


# ===================================================================== deer

def deer_model():
    m = Model("deer", 128, 64)
    body = m.part("body", None, (0, 9, 0), boxes=[
        Box((-3.5, -3.5, -7), (7, 7, 13), tag="barrel"),
        Box((-3, -3, -9.5), (6, 7.5, 3), tag="chest"),
        Box((-3.5, -4, 5), (7, 6.5, 3), tag="rump")])
    m.part("tail", body, (0, -3.5, 8), (0.45, 0, 0), [Box((-1.25, 0, 0), (2.5, 4, 1))])
    for name, x, z, hind in (("right_front_leg", -2, -7, False), ("left_front_leg", 2, -7, False),
                             ("right_hind_leg", -2, 5.5, True), ("left_hind_leg", 2, 5.5, True)):
        if hind:
            boxes = [Box((-1.5, -1, -2), (3, 7, 3.5), tag="thigh"), Box((-0.75, 5.5, 0), (1.5, 4.5, 1.5), tag="hock"),
                     Box((-0.75, 9.5, -0.5), (1.5, 3, 1.5), tag="cannon"), Box((-1, 12, -1), (2, 1, 2), tag="hoof")]
            m.part(name, None, (x, 11, z), boxes=boxes, tag="hind")
        else:
            boxes = [Box((-1.25, 0, -1.25), (2.5, 5.5, 2.5), tag="thigh"), Box((-0.75, 5.5, -0.75), (1.5, 5.5, 1.5), tag="cannon"),
                     Box((-1, 11, -1), (2, 1, 2), tag="hoof")]
            m.part(name, None, (x, 12, z), boxes=boxes, tag="front")
    head = m.part("head", None, (0, 7.5, -8.5))
    neck = m.part("neck", head, (0, 0, 0), (0.8, 0, 0), [Box((-1.5, -7.5, -1.5), (3, 8.5, 3.5)), Box((-2, -3, -2), (4, 4.5, 4.5), tag="base")])
    skull = m.part("skull", neck, (0, -7.5, 0.5), (-0.62, 0, 0), [
        Box((-2, -3, -2.5), (4, 3.5, 4.5), tag="cranium"), Box((-1.25, -2.5, -6.5), (2.5, 2.5, 4), tag="snout"),
        Box((-1, -0.5, -5.5), (2, 1, 3), tag="jaw")])
    for side, sx in (("right", -1), ("left", 1)):
        m.part(side + "_ear", skull, (sx * 1.7, -2.4, 1.0), (0.45, 0, sx * 1.1), [Box((-0.75, -3.5, -0.25), (1.5, 3.5, 0.5))], tag="ear")
    ant = m.part("antlers", skull, (0, -3, 0))
    for side, sx in (("right", -1), ("left", 1)):
        b1 = m.part(side + "_beam", ant, (sx * 1.2, 0, 0.6), (-0.3, 0, sx * 0.5), [Box((-0.5, -5, -0.5), (1, 5, 1))], tag="antler")
        m.part(side + "_brow_tine", b1, (0, -1.5, 0), (-1.1, 0, 0), [Box((-0.4, -2.5, -0.4), (1, 2.5, 1), -0.1)], tag="antler")
        b2 = m.part(side + "_beam_tip", b1, (0, -5, 0), (-0.95, 0, -sx * 0.25), [Box((-0.5, -6, -0.5), (1, 6, 1), -0.05)], tag="antler")
        for i, (yy, ln) in enumerate(((-1.2, 3.5), (-3.2, 4), (-5.2, 2.5))):
            m.part(f"{side}_tine{i}", b2, (0, yy, 0), (1.05, 0, sx * 0.1), [Box((-0.4, -ln, -0.4), (1, ln, 1), -0.15)], tag="antler")
    m.pack()
    return m


def deer_shader(fawn):
    base = col((168, 98, 52) if fawn else (150, 102, 62))
    back = col((112, 72, 44) if fawn else (104, 70, 44))
    white = col((236, 230, 220))
    seed = 4242 if fawn else 4141

    def shader(s):
        P, N, tag, face, bt = s["P"], s["N"], s["part"].tag, s["face"], s["box"].tag
        n = len(P)
        x, y, z = P[:, 0], P[:, 1], P[:, 2]
        g = grain(s, seed)
        Lc = s["L"]
        if tag == "antler":
            t = s["b"] if face not in ("top", "bottom") else np.full(n, 0.0 if face == "top" else 1.0)
            c = mixc(col((222, 210, 184), n), col((116, 90, 64), n), smooth(0.1, 0.95, t) * 0.8)
            grooves = vnoise(np.stack([Lc[:, 0] * 8, Lc[:, 1] * 1.2, Lc[:, 2] * 8], 1), seed)
            return c * (0.82 + 0.3 * grooves)[:, None]
        c = mixc(base, back, 0)[None].repeat(n, 0)
        # darker along the back, lighter flanks, white belly
        if tag == "body":
            t = np.clip((y - 5) / 7.5, 0, 1)
            c = mixc(back[None].repeat(n, 0), base[None].repeat(n, 0), smooth(0.0, 0.45, t))
            c = mixc(c, white[None].repeat(n, 0), smooth(0.82, 0.95, t + (g - 0.5) * 0.06))
            if face == "bottom":
                c = white[None].repeat(n, 0) * 0.96
            # white rump patch under the tail
            rump = smooth(7.2, 7.9, z) * smooth(8, 10, y)
            c = mixc(c, white[None].repeat(n, 0), rump)
            if fawn:
                # rows of spots down each side of the spine
                for row, (yy, step, off) in enumerate(((5.6, 2.2, 0.0), (7.2, 2.4, 1.1), (8.8, 2.6, 0.3))):
                    zz = np.mod(z + off, step) - step / 2
                    d = np.sqrt((zz / 0.55) ** 2 + ((y - yy - 0.3 * np.sin(z * 0.7 + row)) / 0.5) ** 2)
                    spot = smooth(1.0, 0.7, d) * (np.abs(x) > 1.2) * smooth(-7.5, -6, z) * smooth(7.5, 6.5, z)
                    c = mixc(c, col((240, 228, 205), n), spot * 0.9)
        elif tag == "tail":
            under = (face == "front") | (face == "bottom")
            edge = smooth(0.32, 0.45, np.abs(s["a"] - 0.5)) * (face == "back")
            c = mixc(col((94, 60, 36), n), white[None].repeat(n, 0), np.clip(under * 1.0 + edge, 0, 1))
        elif tag in ("front", "hind"):
            c = mixc(base[None].repeat(n, 0) * 1.05, col((176, 128, 84), n), smooth(15, 19, y))
            inner = np.sign(x) * N[:, 0] < -0.5
            c = mixc(c, white[None].repeat(n, 0) * 0.92, inner * smooth(17, 13, y) * (tag == "hind") * 0.8)
            if bt == "cannon":
                front = face == "front"
                c = mixc(c, col((96, 64, 42), n), front * 0.6)
            if bt == "hoof":
                c = col((30, 26, 24), n) * (0.85 + 0.25 * vnoise(np.stack([x * 5, y * 2, z * 5], 1), seed))[:, None]
                return c
            # dewclaw shadow just above the hoof, white band at the back of the pastern
            c = mixc(c, col((240, 232, 220), n), smooth(22.3, 22.8, y) * smooth(23.4, 23.0, y) * (face == "back") * 0.8)
        elif tag in ("neck", "head"):
            # neck: darker top, white throat patch under the chin
            c = mixc(back[None].repeat(n, 0) * 1.05, base[None].repeat(n, 0), 0.6)
            if bt == "base":
                c = mixc(c, back[None].repeat(n, 0), 0.4)
            if face == "front" and bt != "base":
                throat = smooth(0.25, 0.1, s["b"]) * smooth(0.5, 0.35, np.abs(s["a"] - 0.5))
                c = mixc(c, white[None].repeat(n, 0), throat)
        elif tag == "skull":
            c = mixc(base[None].repeat(n, 0), back[None].repeat(n, 0), 0.35)
            if bt == "snout":
                # darker nose bridge, white band behind the black nose
                if face == "top":
                    c = mixc(c, col((80, 56, 40), n), 0.55)
                fwd = {"left": 1 - s["a"], "right": s["a"], "top": 1 - s["b"], "bottom": s["b"]}.get(face, np.zeros(n))
                if face == "front":
                    fwd = np.ones(n)
                band = smooth(0.55, 0.62, fwd) * smooth(0.82, 0.76, fwd)
                c = mixc(c, white[None].repeat(n, 0), band * (face != "top") + band * 0.6 * (face == "top"))
                nose = smooth(0.8, 0.86, fwd)
                if face == "front":
                    nose = smooth(0.55, 0.85, 1 - np.abs(s["b"] - 0.35) * 1.4)
                c = mixc(c, col((24, 20, 20), n), np.clip(nose, 0, 1))
                if face == "front":
                    for sx in (-0.6, 0.6):
                        e = ((Lc[:, 0] - sx) / 0.28) ** 2 + ((Lc[:, 1] + 1.6) / 0.38) ** 2
                        c = mixc(c, col((8, 6, 6), n), smooth(1.0, 0.6, e))
            if bt == "jaw":
                c = mixc(c, white[None].repeat(n, 0), 0.8)
                fwd = {"left": 1 - s["a"], "right": s["a"]}.get(face, np.zeros(n))
                c = mixc(c, col((30, 24, 22), n), smooth(0.8, 0.9, fwd) * 0.8)
            if bt == "cranium" and face in ("left", "right"):
                lz, ly = Lc[:, 2], Lc[:, 1]
                e = ((lz + 1.2) / 0.75) ** 2 + ((ly + 1.6) / 0.6) ** 2
                c = mixc(c, white[None].repeat(n, 0), smooth(2.6, 1.6, e))   # eye ring
                c = mixc(c, col((14, 10, 10), n), smooth(1.05, 0.8, e))      # eye
                hl = ((lz + 1.45) / 0.2) ** 2 + ((ly + 1.85) / 0.17) ** 2
                c = mixc(c, col((220, 214, 206), n), smooth(1.0, 0.3, hl) * smooth(1.0, 0.8, e))
                # tear duct line toward the snout
                line = smooth(0.25, 0.0, np.abs(ly + 1.1 - (lz + 1.9) * 0.35)) * smooth(-2.0, -2.5, lz) * smooth(-3.2, -2.6, lz)
                c = mixc(c, col((60, 40, 30), n), line * 0.6)
            if bt == "cranium" and face == "top":
                c = mixc(c, back[None].repeat(n, 0) * 0.9, 0.5)
        elif tag == "ear":
            if face == "front":
                inner = smooth(0.42, 0.28, np.abs(s["a"] - 0.5)) * smooth(0.05, 0.2, s["b"]) * smooth(1.0, 0.85, s["b"])
                c = mixc(col((150, 104, 70), n), col((232, 214, 200), n), inner)
                c = mixc(c, col((50, 36, 28), n), smooth(0.4, 0.5, np.abs(s["a"] - 0.5)) * 0.8)
            else:
                c = mixc(base[None].repeat(n, 0), col((60, 42, 30), n), smooth(0.25, 0.0, s["b"]) * 0.8)
        # fur: grain along the hair (down the flanks, back along the neck)
        fur = 0.86 + 0.22 * g
        fur *= 0.93 + 0.14 * vnoise(np.stack([x * 1.4, y * 3.2, z * 1.4], 1), seed + 2)
        k = np.ones(n)
        if tag in ("front", "hind"):
            k *= 1 - 0.12 * (np.sign(x) * N[:, 0] < -0.5)
        return c * (k * fur)[:, None]

    return shader



# ===================================================================== centaur

def centaur_model():
    m = Model("centaur", 128, 128)
    body = m.part("body", None, (0, 11, 3), boxes=[Box((-5, -5, -9), (10, 10, 20), tag="barrel"),
                                                   Box((-4.5, -4.5, -10), (9, 9, 1.5), tag="breast")])
    m.part("tail", body, (0, -4, 11), (0.55, 0, 0), [Box((-1.5, 0, -1.5), (3, 13, 3))])
    for name, x, z in (("right_hind_leg", -3, 11), ("left_hind_leg", 3, 11), ("right_front_leg", -3, -4), ("left_front_leg", 3, -4)):
        m.part(name, None, (x, 15, z), boxes=[Box((-1.75, -1, -1.75), (3.5, 5.5, 3.5), tag="upper"),
                                              Box((-1.25, 4.5, -1.25), (2.5, 3.5, 2.5), tag="cannon"),
                                              Box((-1.5, 8, -1.5), (3, 1, 3), tag="hoof")], tag="leg")
    torso = m.part("torso", None, (0, 8, -5), boxes=[Box((-4, -12, -2), (8, 12, 4), tag="chest"),
                                                    Box((-4.5, -2.5, -2.5), (9, 3, 5), 0.0, tag="belt")])
    head = m.part("head", torso, (0, -12, 0), boxes=[Box((-4, -8, -4), (8, 8, 8))])
    m.part("hair", head, boxes=[Box((-4, -8, -4), (8, 8, 8), 0.45)])
    m.part("right_arm", torso, (-5, -10, 0), boxes=[Box((-3, -2, -2), (4, 12, 4))], tag="arm")
    left = m.part("left_arm", torso, (5, -10, 0), boxes=[Box((-1, -2, -2), (4, 12, 4))], tag="arm")
    bow = m.part("bow", left, (1, 9, -2.5), boxes=[Box((-0.5, -2, -0.5), (1, 4, 1), tag="grip")], tag="bow")
    m.part("bow_upper", bow, (0, -2, 0), (0.45, 0, 0), [Box((-0.5, -5, -0.5), (1, 5, 1), -0.1)], tag="bow")
    m.part("bow_lower", bow, (0, 2, 0), (-0.45, 0, 0), [Box((-0.5, 0, -0.5), (1, 5, 1), -0.1)], tag="bow")
    m.part("bow_string", bow, (0, 0, 2.15), boxes=[Box((-0.25, -6.2, -0.25), (0.5, 12.4, 0.5), -0.1)], tag="string")
    m.part("quiver", torso, (1.6, -10.5, 2.2), (0.0, 0, 0.42), [Box((-1.5, 0, 0), (3, 9, 2), tag="case"),
                                                              Box((-1.25, -2.5, 0.25), (2.5, 2.5, 1.5), tag="arrows")], tag="quiver")
    m.pack()
    return m


CENTAUR_LOOKS = {
    "centaur_bay": dict(coat=(116, 62, 34), points=(30, 22, 18), skin=(190, 136, 98), hair=(44, 30, 22), eyes=(70, 90, 60), dapple=False),
    "centaur_chestnut": dict(coat=(164, 80, 40), points=None, skin=(222, 172, 136), hair=(150, 66, 30), eyes=(60, 100, 140), dapple=False),
    "centaur_grey": dict(coat=(156, 156, 160), points=(70, 68, 72), skin=(140, 96, 66), hair=(218, 214, 206), eyes=(60, 50, 40), dapple=True),
}


def centaur_shader(look):
    L = CENTAUR_LOOKS[look]
    seed = zlib.crc32(look.encode()) & 0xFFF
    coat, skin, hairc = col(L["coat"]), col(L["skin"]), col(L["hair"])
    leather, dark_leather = col((122, 78, 44)), col((70, 44, 28))

    def shader(s):
        P, N, tag, face, bt = s["P"], s["N"], s["part"].tag, s["face"], s["box"].tag
        n = len(P)
        x, y, z = P[:, 0], P[:, 1], P[:, 2]
        Lc = s["L"]
        g = grain(s, seed)
        keep = np.ones(n, bool)

        if tag in ("body", "leg", "tail"):
            if tag == "tail":
                strands = vnoise(np.stack([Lc[:, 0] * 3.2, Lc[:, 1] * 0.35, Lc[:, 2] * 3.2], 1), seed + 9)
                c = col(L["points"], n) if L["points"] is not None else hairc[None].repeat(n, 0)
                c = c * (0.72 + 0.4 * strands)[:, None]
                if face in ("left", "right", "front", "back"):
                    strand = _hash((s["px"] // 2).astype(np.int64), np.full(n, 7, np.int64), np.full(n, seed, np.int64), 3)
                    keep = s["b"] < 1 - 0.3 * strand
                return c, keep
            c = coat[None].repeat(n, 0)
            if L["dapple"]:
                d = worley(P * 0.34, seed + 21)
                c = mixc(c * 0.88, col((222, 222, 224), n), smooth(0.55, 0.25, d) * 0.6)
            t = np.clip((y - 6) / 10, 0, 1)
            c = c * (1.08 - 0.3 * t ** 1.5)[:, None]
            if tag == "body":
                # a woven blanket across the back, behind the man's waist
                band = smooth(-5.6, -5.0, z) * smooth(1.6, 1.0, z) * smooth(13.5, 12.5, y)
                stripe = np.mod(np.floor((y + 0.5 * np.abs(x)) * 1.1), 3)
                weave = np.where(stripe == 0, 1, 0)[:, None] * col((176, 44, 36)) + np.where(stripe == 1, 1, 0)[:, None] * col((214, 170, 70)) \
                        + np.where(stripe == 2, 1, 0)[:, None] * col((40, 70, 110))
                tri = (np.mod(z * 1.4 + np.abs(y - 9) * 0.0, 2.0) < 1.0)
                weave = mixc(weave, col((236, 226, 200), n), (tri & (np.abs(y - 12.3) < 0.35)).astype(float))
                c = mixc(c, weave * (0.85 + 0.25 * g)[:, None], band)
                if face == "bottom":
                    c = c * 0.75
            if tag == "leg":
                if L["points"] is not None:
                    c = mixc(c, col(L["points"], n), smooth(18.0, 19.5, y))
                if bt == "hoof":
                    c = col((42, 36, 34), n) * (0.85 + 0.25 * vnoise(np.stack([x * 6, y, z * 6], 1), seed))[:, None]
                c = c * (1 - 0.12 * (np.sign(x) * N[:, 0] < -0.5))[:, None]
            return c * (0.9 + 0.2 * g)[:, None]

        if tag == "string":
            return col((210, 205, 190), n)
        if tag == "bow":
            c = col((128, 84, 46), n) * (0.8 + 0.35 * vnoise(np.stack([Lc[:, 0] * 4, Lc[:, 1] * 0.8, Lc[:, 2] * 4], 1), seed))[:, None]
            if bt == "grip":
                wrap = np.mod(np.floor((Lc[:, 1] + 2) * 3), 2) == 0
                c = mixc(c, dark_leather[None].repeat(n, 0), wrap.astype(float) * 0.9)
            return c
        if tag == "quiver":
            if bt == "arrows":
                fl = np.mod(np.floor((Lc[:, 0] + 2) * 2.2), 3)
                c = np.where((fl == 0)[:, None], col((226, 222, 210)), np.where((fl == 1)[:, None], col((176, 50, 40)), col((90, 80, 70))))
                return c * (0.85 + 0.25 * g)[:, None]
            c = leather[None].repeat(n, 0) * (0.85 + 0.25 * g)[:, None]
            stitch = (np.abs(s["a"] - 0.5) > 0.4) & (np.mod(s["py"], 3) == 0)
            c = mixc(c, col((220, 200, 160), n), stitch.astype(float) * 0.8)
            c = mixc(c, dark_leather[None].repeat(n, 0), smooth(0.15, 0.05, s["b"]))
            return c

        # ------------------------------------------------ the man
        if tag == "hair":
            # long hair and a beard over the head; the face shows through
            lx, ly, lz = Lc[:, 0], Lc[:, 1], Lc[:, 2]
            strands = vnoise(np.stack([lx * 3, ly * 0.4, lz * 3], 1), seed + 31)
            c = hairc[None].repeat(n, 0) * (0.7 + 0.45 * strands)[:, None]
            if face == "top" or face == "back":
                keep = np.ones(n, bool)
            elif face in ("left", "right"):
                back = lz > -1.0 + 1.5 * _hash((s["py"] // 2).astype(np.int64), np.full(n, 1, np.int64), np.full(n, seed, np.int64), 2)
                keep = (ly < -5.6) | back
            elif face == "front":
                fringe = ly < -7.0 + 0.9 * _hash((s["px"] // 2).astype(np.int64), np.full(n, 2, np.int64), np.full(n, seed, np.int64), 2)
                beard = (ly > -2.9) & ~((np.abs(lx) < 1.2) & (ly > -1.75) & (ly < -1.15))
                stache = (ly > -2.5) & (ly < -1.75) & (np.abs(lx) < 1.8)
                keep = fringe | beard | stache
                c = c * np.where(beard & (ly > -1.0), 0.85, 1.0)[:, None]
            else:
                keep = np.zeros(n, bool)
            return c, keep
        c = skin[None].repeat(n, 0) * (0.92 + 0.14 * g)[:, None]
        if tag == "head":
            lx, ly, lz = Lc[:, 0], Lc[:, 1], Lc[:, 2]
            if face == "front":
                for sx in (-1.8, 1.8):
                    ew = ((lx - sx) / 0.75) ** 2 + ((ly + 4.3) / 0.4) ** 2
                    c = mixc(c, col((236, 232, 226), n), smooth(1.0, 0.85, ew))
                    iris = ((lx - sx * 0.92) / 0.38) ** 2 + ((ly + 4.3) / 0.38) ** 2
                    c = mixc(c, col(L["eyes"], n), smooth(1.0, 0.7, iris))
                    c = mixc(c, col((16, 12, 12), n), smooth(0.35, 0.15, iris))
                    brow = smooth(0.3, 0.1, np.abs(ly + 5.25 - 0.12 * np.abs(lx - sx))) * smooth(1.1, 0.8, np.abs(lx - sx))
                    c = mixc(c, hairc[None].repeat(n, 0) * 0.8, brow)
                nose = smooth(0.7, 0.45, np.abs(lx)) * smooth(-4.6, -4.2, ly) * smooth(-2.0, -2.4, ly)
                c = mixc(c, c * 1.12, nose)
                c = mixc(c, c * 0.7, smooth(0.5, 0.3, np.abs(lx)) * smooth(-2.45, -2.25, ly) * smooth(-1.95, -2.15, ly))
                c = mixc(c, col((120, 60, 50), n), smooth(1.2, 0.95, np.abs(lx)) * smooth(0.22, 0.05, np.abs(ly + 1.45)))
            if face in ("left", "right"):
                ear = (((lz - 0.4) / 0.7) ** 2 + ((ly + 3.6) / 1.1) ** 2) < 1
                c = mixc(c, c * 0.82, ear.astype(float))
            return c
        if tag == "torso":
            lx, ly, lz = Lc[:, 0], Lc[:, 1], Lc[:, 2]
            if bt == "belt":
                c = dark_leather[None].repeat(n, 0) * (0.85 + 0.25 * g)[:, None]
                buckle = (face == "front") & (np.abs(lx) < 0.9) & (np.abs(ly + 1.0) < 0.7)
                return mixc(c, col((200, 170, 90), n), buckle.astype(float))
            if face in ("front", "back"):
                pec = smooth(0.25, 0.0, np.abs(ly + 8.6 + 0.25 * np.abs(lx))) * (np.abs(lx) > 0.3) * (face == "front")
                c = mixc(c, c * 0.8, pec * 0.8)
                mid = smooth(0.25, 0.0, np.abs(lx)) * smooth(-8.5, -8.0, ly) * (face == "front")
                c = mixc(c, c * 0.85, mid * 0.6)
                strap = smooth(0.95, 0.65, np.abs(lx + (ly + 6) * 0.55 * (1 if face == "front" else -1)))
                c = mixc(c, leather[None].repeat(n, 0) * (0.85 + 0.25 * g)[:, None], strap)
            if face == "top":
                c = c * 0.95
            # shading down to the waist
            c = c * (1.05 - 0.15 * smooth(-12, 0, ly))[:, None]
            return c
        if tag == "arm":
            ly = Lc[:, 1]
            bracer = smooth(4.8, 5.3, ly) * smooth(9.6, 9.1, ly)
            c = mixc(c, leather[None].repeat(n, 0) * (0.85 + 0.25 * g)[:, None], bracer)
            lace = bracer * (np.mod(s["py"], 4) == 0) * (np.abs(s["a"] - 0.5) < 0.15)
            c = mixc(c, col((220, 200, 160), n), lace * 0.8)
            c = mixc(c, c * 0.88, smooth(9.6, 10.0, ly))
            return c
        return c

    return shader


# ===================================================================== output

def write():
    os.makedirs(os.path.join(ENTITY, "horse"), exist_ok=True)
    java = ["// Generated by tools/gen_beasts.py from the same description as the textures. Do not edit by hand.",
            "package com.dominionmod.dominion.client.render.wild;", "",
            "import net.minecraft.client.model.geom.PartPose;",
            "import net.minecraft.client.model.geom.builders.CubeDeformation;",
            "import net.minecraft.client.model.geom.builders.CubeListBuilder;",
            "import net.minecraft.client.model.geom.builders.MeshDefinition;",
            "import net.minecraft.client.model.geom.builders.PartDefinition;", "",
            "/** Meshes for the breed horses and the deer. Horse textures are 128x128 units, the deer's 128x64. */",
            "final class BeastMeshes {", "    private BeastMeshes() {}", ""]
    for breed in ("clydesdale", "mustang", "shetland"):
        m, dy = horse_model(breed)
        java.append(m.java(breed))
        java.append("")
        for coat in HORSE_COATS[breed]:
            m.paint(horse_shader(breed, coat, dy), os.path.join(ENTITY, "horse", f"{breed}_{coat}.png"))
    d = deer_model()
    java.append(d.java("deer"))
    d.paint(deer_shader(False), os.path.join(ENTITY, "deer.png"))
    d.paint(deer_shader(True), os.path.join(ENTITY, "deer_fawn.png"))
    cm = centaur_model()
    java.append("")
    java.append(cm.java("centaur"))
    for look in CENTAUR_LOOKS:
        cm.paint(centaur_shader(look), os.path.join(ENTITY, look + ".png"))
    java.append("}")
    with open(JAVA, "w") as fh:
        fh.write("\n".join(java) + "\n")


if __name__ == "__main__":
    write()
