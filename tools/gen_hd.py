"""Dominion's 32x32 ("HD") item and block textures, painted in code.

Run:  python3 tools/gen_hd.py   (after gen_textures.py, which paints the entity textures)

Every sprite is built from parts. Each part is a flat-colored shape that gets shaded on its
own (light from the top-left: a bright rim up-left, a dark rim down-right, a little grain),
then the parts are stacked and the whole sprite gets a dark outline.
"""
import math
import os
import random
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "dominion", "textures")
ITEM = os.path.join(ROOT, "item")
BLOCK = os.path.join(ROOT, "block")
os.makedirs(ITEM, exist_ok=True)
os.makedirs(BLOCK, exist_ok=True)

N = 32
rnd = random.Random(1234)

# ---------------------------------------------------------------- palette
STEEL = (178, 184, 194)
STEEL_D = (110, 116, 128)
IRON = (150, 154, 162)
DIRON = (88, 92, 102)
GOLD = (232, 186, 52)
GOLD_D = (168, 118, 26)
WOOD = (132, 92, 54)
WOOD_D = (92, 62, 36)
LEATHER = (110, 66, 40)
LEATHER_D = (72, 42, 26)
LEAD = (120, 124, 134)
RED = (176, 40, 36)
PAPER = (232, 222, 196)


def clamp(v):
    return max(0, min(255, int(v)))


def mul(c, f):
    return tuple(clamp(x * f) for x in c[:3])


def mix(a, b, t):
    return tuple(clamp(a[i] * (1 - t) + b[i] * t) for i in range(3))


class Part:
    """A mask drawn with PIL, filled with one base color, shaded by its own silhouette."""

    def __init__(self, color, grain=5, hi=1.22, lo=0.72, size=N):
        self.size = size
        self.mask = Image.new("L", (size, size), 0)
        self.d = ImageDraw.Draw(self.mask)
        self.color = color
        self.grain = grain
        self.hi, self.lo = hi, lo
        self.overrides = {}

    def line(self, pts, width=1):
        self.d.line(pts, fill=255, width=width)
        if width > 2:
            for p in (pts[0], pts[-1]):
                r = (width - 1) / 2
                self.d.ellipse((p[0] - r, p[1] - r, p[0] + r, p[1] + r), fill=255)
        return self

    def poly(self, pts):
        self.d.polygon(pts, fill=255)
        return self

    def rect(self, x0, y0, x1, y1):
        self.d.rectangle((x0, y0, x1, y1), fill=255)
        return self

    def circle(self, cx, cy, r):
        self.d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=255)
        return self

    def pixels(self, pts):
        for p in pts:
            self.mask.putpixel(p, 255)
        return self

    def where(self, fn):
        """Adds every pixel where fn(x, y) is true."""
        for y in range(self.size):
            for x in range(self.size):
                if fn(x, y):
                    self.mask.putpixel((x, y), 255)
        return self

    def tint(self, fn):
        """fn(x, y, color) -> color, applied after shading (for stripes, wraps, highlights)."""
        self.overrides.setdefault("fns", []).append(fn)
        return self

    def on(self, x, y):
        return 0 <= x < self.size and 0 <= y < self.size and self.mask.getpixel((x, y)) > 0

    def render(self, img):
        for y in range(self.size):
            for x in range(self.size):
                if not self.on(x, y):
                    continue
                c = self.color
                up = not self.on(x - 1, y) or not self.on(x, y - 1)
                down = not self.on(x + 1, y) or not self.on(x, y + 1)
                if up and not down:
                    c = mul(c, self.hi)
                elif down and not up:
                    c = mul(c, self.lo)
                for fn in self.overrides.get("fns", []):
                    c = fn(x, y, c)
                if self.grain:
                    g = rnd.randint(-self.grain, self.grain)
                    c = tuple(clamp(v + g) for v in c)
                img.putpixel((x, y), tuple(c) + (255,))


def sprite(parts, outline=0.5, size=N):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for p in parts:
        p.render(img)
    if outline:
        out = img.copy()
        for y in range(size):
            for x in range(size):
                c = img.getpixel((x, y))
                if c[3] == 0:
                    continue
                edge = any(not (0 <= x + dx < size and 0 <= y + dy < size) or img.getpixel((x + dx, y + dy))[3] == 0
                           for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                if edge:
                    out.putpixel((x, y), mul(c, outline) + (255,))
        img = out
    return img


def along(p0, p1, t):
    return (p0[0] + (p1[0] - p0[0]) * t, p0[1] + (p1[1] - p0[1]) * t)


def wrap(period=2, dark=0.7):
    """Diagonal leather wrapping on a grip."""
    return lambda x, y, c: mul(c, dark) if (x + y) % period == 0 else c


def edge_shine(fn_dist, width=1.0, f=1.3):
    return lambda x, y, c: mul(c, f) if fn_dist(x, y) < width else c


def lobe(cx, cy, dirx, diry, reach, spread=14, flare=4.2, inner=1.5):
    """An axe blade fanning out from (cx, cy) toward (dirx, diry)."""
    base = math.atan2(diry, dirx)

    def inside(x, y):
        dx, dy = x + 0.5 - cx, y + 0.5 - cy
        r = math.hypot(dx, dy)
        if r < inner or r > reach:
            return False
        a = math.atan2(dy, dx) - base
        a = (a + math.pi) % (2 * math.pi) - math.pi
        return abs(math.degrees(a)) <= spread + r * flare

    return inside


# ---------------------------------------------------------------- weapons

def blade(p0, p1, width, color=STEEL):
    """A straight blade with a darker fuller down the middle and a bright edge."""
    b = Part(color, grain=3, hi=1.25, lo=0.68)
    b.line([p0, p1], width=width)
    tipx, tipy = p1
    dx, dy = p1[0] - p0[0], p1[1] - p0[1]
    L = math.hypot(dx, dy)
    ux, uy = dx / L, dy / L
    # pointed tip
    px_, py_ = -uy, ux
    hw = width / 2
    b.poly([(tipx + px_ * hw, tipy + py_ * hw), (tipx - px_ * hw, tipy - py_ * hw), (tipx + ux * (hw + 2), tipy + uy * (hw + 2))])
    return b


def fuller(p0, p1):
    f = Part(STEEL_D, grain=2, hi=1.0, lo=1.0)
    f.line([p0, p1], width=1)
    return f


def longsword():
    parts = [
        blade((11, 20), (27, 4), 3),
        fuller((12, 19), (23, 8)),
        Part(GOLD, grain=4).line([(6, 16), (15, 25)], width=2),
        Part(LEATHER, grain=4).line([(5, 26), (10, 21)], width=2).tint(wrap()),
        Part(GOLD, grain=4).circle(4, 27, 1.6),
    ]
    return sprite(parts)


def greatsword():
    parts = [
        blade((10, 21), (27, 4), 7),
        fuller((11, 20), (24, 7)),
        fuller((12, 20), (24, 8)),
        Part(STEEL_D, grain=4, hi=1.4).line([(4, 15), (16, 27)], width=3),
        Part(GOLD, grain=4).pixels([(3, 14), (4, 14), (16, 28), (17, 27)]),
        Part(LEATHER, grain=4).line([(3, 28), (9, 22)], width=3).tint(wrap(3)),
        Part(GOLD, grain=4).circle(2.5, 29.5, 2),
    ]
    return sprite(parts)


def battle_axe():
    head_c = (21, 10)
    a = Part(STEEL, grain=3, hi=1.3, lo=0.7).where(lobe(*head_c, 1, 1, 10.5, spread=10, flare=4.5, inner=1.0))
    b = Part(STEEL, grain=3, hi=1.3, lo=0.7).where(lobe(*head_c, -1, -1, 10.5, spread=10, flare=4.5, inner=1.0))
    shine = lambda x, y, c: mul(c, 1.25) if math.hypot(x + .5 - head_c[0], y + .5 - head_c[1]) > 9 else c
    a.tint(shine)
    b.tint(shine)
    parts = [
        Part(WOOD, grain=5).line([(3, 29), (25, 7)], width=2),
        a, b,
        Part(DIRON, grain=3).circle(head_c[0], head_c[1], 2.2),
        Part(LEATHER, grain=4).line([(4, 28), (8, 24)], width=3).tint(wrap()),
        Part(DIRON, grain=3).pixels([(25, 6), (26, 5), (26, 6), (25, 5)]),
    ]
    return sprite(parts)


def halberd():
    c = (21, 11)
    axe = Part(STEEL, grain=3, hi=1.3, lo=0.7).where(lobe(*c, 1, 1, 9.0, spread=6, flare=4.8, inner=1.0))
    axe.tint(lambda x, y, col: mul(col, 1.25) if math.hypot(x + .5 - c[0], y + .5 - c[1]) > 7.6 else col)
    hook = Part(STEEL_D, grain=3).poly([(19, 9), (17, 7), (13, 5), (16, 9), (18, 11)])
    parts = [
        Part(WOOD, grain=5).line([(1, 31), (26, 6)], width=2),
        axe, hook,
        blade((24, 8), (29, 3), 2),
        Part(DIRON, grain=3).line([(19, 13), (23, 9)], width=3),
        Part(DIRON, grain=3).pixels([(1, 30), (2, 30), (1, 29), (2, 31)]),
    ]
    return sprite(parts)


def glaive():
    # a curved, single-edged blade swept along a bezier
    pts = []
    p0, p1, p2 = (20, 13), (29, 9), (30, 1)
    for i in range(41):
        t = i / 40
        x = (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0]
        y = (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]
        pts.append((x, y, 2.6 * (1 - t) + 0.6))
    bl = Part(STEEL, grain=3, hi=1.3, lo=0.68)
    for x, y, r in pts:
        bl.circle(x - 0.6, y - 0.6, r)
    bl.tint(lambda x, y, c: mul(c, 1.25) if (x - 20) * 0.9 + (y - 13) * -0.1 > 6 and x > 25 else c)
    parts = [
        Part(WOOD, grain=5).line([(1, 31), (21, 11)], width=2),
        bl,
        Part(GOLD, grain=4).line([(18, 15), (22, 11)], width=3),
        Part(DIRON, grain=3).pixels([(1, 30), (2, 30), (1, 29), (2, 31)]),
    ]
    return sprite(parts)


def flail():
    ball_c = (23.5, 8.5)
    ball = Part(IRON, grain=4, hi=1.35, lo=0.65).circle(ball_c[0], ball_c[1], 5)
    spikes = Part(STEEL, grain=2, hi=1.2, lo=0.8)
    for i in range(8):
        a = i * math.pi / 4 + math.pi / 8
        for r in (6.0, 7.0):
            spikes.pixels([(int(ball_c[0] + math.cos(a) * r), int(ball_c[1] + math.sin(a) * r))])
    chain = Part(STEEL, grain=2, hi=1.2, lo=0.6)
    for i in range(5):
        x, y = along((12, 19), (19, 12.5), i / 4)
        if i % 2 == 0:
            chain.rect(int(x), int(y), int(x) + 1, int(y) + 1)
        else:
            chain.pixels([(int(x), int(y)), (int(x) + 1, int(y) + 1)])
    parts = [
        Part(WOOD, grain=5).line([(3, 29), (11, 21)], width=3),
        Part(LEATHER, grain=4).line([(3, 29), (7, 25)], width=3).tint(wrap()),
        Part(DIRON, grain=3).circle(11.5, 20.5, 1.6),
        chain, spikes, ball,
    ]
    return sprite(parts)


def iron_shield_icon():
    pass


def flintlock():
    parts = [
        # barrel, pointing right
        Part(STEEL, grain=3, hi=1.3, lo=0.65).rect(10, 10, 29, 12),
        Part(DIRON, grain=2).rect(28, 9, 29, 13),
        # wooden stock under the barrel and the curved grip
        Part(WOOD, grain=5).poly([(9, 12), (25, 12), (25, 14), (13, 15), (11, 18), (9, 25), (4, 26), (3, 24), (6, 15)]),
        Part(GOLD, grain=4).rect(3, 24, 6, 26),
        # lock: hammer with its flint, and the pan
        Part(DIRON, grain=3).poly([(8, 6), (11, 6), (12, 10), (10, 11), (8, 9)]),
        Part((60, 60, 64), grain=2).rect(9, 5, 11, 6),
        Part(STEEL_D, grain=2).rect(12, 9, 14, 10),
        # trigger guard and trigger
        Part(GOLD, grain=4).line([(12, 15), (14, 18), (17, 18), (18, 15)], width=1),
        Part(DIRON, grain=2).pixels([(15, 15), (15, 16)]),
        Part(GOLD, grain=4).rect(22, 12, 23, 14),
    ]
    return sprite(parts)


def musket():
    parts = [
        # stock: butt in the bottom-left
        Part(WOOD, grain=5).poly([(0, 27), (4, 31), (12, 24), (11, 20), (7, 21)]),
        Part(WOOD_D, grain=4).poly([(0, 27), (2, 29), (6, 25), (4, 24)]),
        # wooden fore-end under the barrel
        Part(WOOD, grain=5).line([(9, 23), (24, 8)], width=3),
        # barrel
        Part(STEEL, grain=3, hi=1.3, lo=0.65).line([(10, 20), (30, 1)], width=2),
        # bands
        Part(GOLD, grain=3).line([(15, 17), (17, 19)], width=1),
        Part(GOLD, grain=3).line([(21, 11), (23, 13)], width=1),
        # lock plate, hammer and trigger
        Part(DIRON, grain=3).poly([(9, 18), (13, 18), (13, 21), (9, 21)]),
        Part(DIRON, grain=3).pixels([(10, 16), (11, 16), (10, 17)]),
        Part(GOLD, grain=4).line([(12, 23), (13, 25), (15, 24)], width=1),
    ]
    return sprite(parts)


def bullet():
    parts = []
    for cx, cy in ((10, 20), (21, 21), (15.5, 11.5)):
        b = Part(LEAD, grain=3, hi=1.45, lo=0.6).circle(cx, cy, 4.2)
        b.tint(lambda x, y, c, cx=cx, cy=cy: mul(c, 1.35) if (x - cx + 2) ** 2 + (y - cy + 2) ** 2 < 2 else c)
        parts.append(b)
    return sprite(parts)


def iron_shield_texture():
    """The 32x32 texture the iron shield's 3D model is cut from (1 texel = 1 model pixel / 2)."""
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    # front plate: 12 x 22 at (0, 0)
    front = Part(IRON, grain=4, hi=1.25, lo=0.7, size=32).rect(0, 0, 11, 21)
    front.tint(lambda x, y, c: mul(c, 0.72) if x in (0, 11) or y in (0, 21) else c)
    front.tint(lambda x, y, c: mul(c, 1.18) if (x in (1, 10) or y in (1, 20)) and 0 < x < 11 and 0 < y < 21 else c)
    front.tint(lambda x, y, c: mix(c, (160, 36, 34), 0.8) if 4 <= x <= 7 and 2 <= y <= 19 or 2 <= x <= 9 and 7 <= y <= 10 else c)
    front.render(img)
    for (x, y) in ((2, 2), (9, 2), (2, 19), (9, 19), (5, 5), (6, 16)):
        img.putpixel((x, y), (215, 218, 226, 255))
    for (x, y, c) in ((5, 8, (220, 222, 230)), (6, 8, (190, 192, 200)), (5, 9, (150, 152, 160)), (6, 9, (120, 122, 130))):
        img.putpixel((x, y), c + (255,))  # the boss
    # back: wooden planks with an iron rim, at (13, 0)
    back = Part(WOOD, grain=6, size=32).rect(13, 0, 24, 21)
    back.tint(lambda x, y, c: mul(c, 0.75) if (x - 13) % 4 == 0 else c)
    back.tint(lambda x, y, c: IRON if x in (13, 24) or y in (0, 21) else c)
    back.render(img)
    # edges: rim strip (26, 0) 2x22, top/bottom (0, 23) 12x2, handle (28, 0) 4x8
    Part(DIRON, grain=4, size=32).rect(26, 0, 27, 21).render(img)
    Part(DIRON, grain=4, size=32).rect(0, 23, 11, 24).render(img)
    Part(LEATHER, grain=4, size=32).rect(28, 0, 31, 7).render(img)
    return img


# ---------------------------------------------------------------- tools and goods

def gold_coin():
    c = (15.5, 15.5)
    coin = Part(GOLD, grain=5, hi=1.25, lo=0.72).circle(15.5, 15.5, 12.5)
    coin.tint(lambda x, y, col: mul(col, 0.82) if 10.2 < math.hypot(x + .5 - c[0], y + .5 - c[1]) < 11.5 else col)
    coin.tint(lambda x, y, col: mul(col, 1.12) if (x - 8) ** 2 + (y - 8) ** 2 < 18 else col)
    crown = Part(GOLD_D, grain=2, hi=0.9, lo=1.1).poly([(9, 21), (9, 12), (12, 15), (15.5, 9), (19, 15), (22, 12), (22, 21)])
    gems = Part(RED, grain=4).pixels([(15, 16), (16, 16), (15, 17), (16, 17), (11, 18), (20, 18)])
    shine = Part((255, 240, 160), grain=0).pixels([(7, 10), (8, 9), (9, 8), (10, 7)])
    return sprite([coin, crown, gems, shine], outline=0.6)


def command_baton():
    parts = [
        Part(WOOD_D, grain=4).line([(6, 25), (24, 7)], width=3),
        Part(RED, grain=5).line([(9, 22), (21, 10)], width=3).tint(lambda x, y, c: mul(c, 0.7) if (x + y) % 3 == 0 else c),
        Part(GOLD, grain=4).circle(25, 6, 3.2),
        Part(GOLD, grain=4).line([(5, 26), (7, 24)], width=3),
        Part(GOLD, grain=4).line([(20, 11), (22, 9)], width=3),
        Part(GOLD, grain=4).line([(8, 23), (10, 21)], width=3),
        Part((120, 200, 255), grain=3, hi=1.5).circle(25, 6, 1.2),
    ]
    return sprite(parts)


def builders_hammer():
    parts = [
        Part(WOOD, grain=5).line([(4, 28), (19, 13)], width=3),
        Part(LEATHER, grain=4).line([(4, 28), (9, 23)], width=3).tint(wrap()),
        Part(IRON, grain=4, hi=1.3, lo=0.65).poly([(14, 8), (21, 1), (29, 9), (22, 16)]),
        Part(DIRON, grain=3).poly([(25, 5), (29, 1), (31, 3), (27, 7)]),
        Part(STEEL, grain=2).line([(15, 9), (21, 3)], width=1),
    ]
    return sprite(parts)


def blueprint():
    paper = Part((52, 92, 172), grain=5).rect(3, 5, 28, 27)
    paper.tint(lambda x, y, c: mul(c, 1.1) if (x + y) % 9 == 0 else c)
    lines = Part((220, 232, 255), grain=0, hi=1, lo=1)
    lines.line([(8, 22), (8, 14), (15.5, 9), (23, 14), (23, 22), (8, 22)], width=1)
    lines.rect(14, 17, 17, 22)
    lines.line([(19, 15), (21, 15)])
    for x in range(5, 27, 4):
        lines.pixels([(x, 25)])
    roll = Part(PAPER, grain=4).rect(2, 3, 29, 5)
    roll2 = Part(mul(PAPER, 0.8), grain=4).rect(2, 27, 29, 29)
    return sprite([paper, lines, roll, roll2])


def blacksmith_tongs():
    parts = [
        Part(DIRON, grain=3, hi=1.4).line([(4, 27), (20, 11), (27, 6)], width=2),
        Part(IRON, grain=3, hi=1.4).line([(7, 29), (19, 14), (28, 10)], width=2),
        Part(DIRON, grain=3).circle(17.5, 14.5, 2),
        Part(LEATHER, grain=4).line([(3, 28), (6, 25)], width=3),
        Part(LEATHER, grain=4).line([(6, 30), (9, 27)], width=3),
        Part((255, 150, 40), grain=8, hi=1.2).rect(26, 5, 29, 9),
    ]
    return sprite(parts)


def storage_label():
    tag = Part(PAPER, grain=5).poly([(6, 12), (12, 6), (27, 6), (27, 27), (6, 27)])
    hole = Part((120, 100, 70), grain=0).circle(11, 12, 1.3)
    text = Part((86, 64, 44), grain=3, hi=1, lo=1)
    for y in (15, 19, 23):
        text.line([(10, y), (24 if y != 23 else 18, y)], width=1)
    string = Part(RED, grain=5).line([(11, 12), (6, 5), (2, 3)], width=1)
    return sprite([tag, hole, text, string])


# ---------------------------------------------------------------- blocks

def tile(fn):
    img = Image.new("RGBA", (N, N))
    for y in range(N):
        for x in range(N):
            c = fn(x, y)
            g = rnd.randint(-5, 5)
            img.putpixel((x, y), tuple(clamp(v + g) for v in c) + (255,))
    return img


def planks(x, y, base=(116, 86, 54), rows=8):
    row = y // rows
    seam = y % rows == 0
    off = (row * 11) % 32
    joint = (x + off) % 32 == 0
    c = mul(base, 0.68) if seam or joint else base
    if not seam and (x * 7 + y * 3 + row * 5) % 23 == 0:
        c = mul(c, 0.86)  # grain
    return c


def stone_bricks(x, y, base=(132, 132, 134)):
    row = y // 8
    seam = y % 8 == 0 or (x + (8 if row % 2 else 0)) % 16 == 0
    c = mul(base, 0.62) if seam else base
    if not seam and y % 8 == 1:
        c = mul(c, 1.12)
    return c


def town_hall():
    red, dred = (176, 40, 36), (120, 24, 22)

    def side(x, y):
        if y >= 24:
            return stone_bricks(x, y)
        c = planks(x, y, (114, 84, 52))
        if x < 2 or x > 29:
            c = (80, 58, 34)
        # a red banner with a gold crown, hung from a gold rail
        if 9 <= x <= 22 and 3 <= y <= 20:
            c = red if x not in (9, 22) else dred
            if y > 17 and (x - 9) % 4 in (0, 1) and y > 17 + (x % 2):
                c = (114, 84, 52) if y == 20 else c
            if 12 <= x <= 19 and 8 <= y <= 13:
                crown = (y >= 11) or (x in (12, 15, 16, 19) and y >= 8) or (x in (13, 14, 17, 18) and y >= 10)
                if crown:
                    c = GOLD if y != 13 else GOLD_D
        if y == 2 and 7 <= x <= 24:
            c = GOLD
        return c

    def top(x, y):
        edge = x < 2 or x > 29 or y < 2 or y > 29
        if edge:
            return GOLD if (x + y) % 2 else GOLD_D
        c = planks(y, x, (110, 80, 50))
        d = abs(x - 15.5) + abs(y - 15.5)
        if d < 7:
            c = GOLD if d < 5.5 else GOLD_D
        if d < 2.5:
            c = red
        return c

    tile(side).save(os.path.join(BLOCK, "town_hall_side.png"))
    tile(top).save(os.path.join(BLOCK, "town_hall_top.png"))


def market():
    red, white = (186, 44, 40), (234, 228, 216)

    def side(x, y):
        if y < 9:
            c = red if (x // 4) % 2 == 0 else white
            if y == 8 and x % 4 in (1, 2):
                return (64, 46, 28)
            if y < 2:
                c = mul(c, 0.8)
            return c
        c = planks(x, y + 1, (162, 128, 78))
        if x < 2 or x > 29:
            c = (110, 84, 50)
        # goods on the counter: apples, bread, a melon
        if y in range(16, 21):
            if 4 <= x <= 7 and (x - 5.5) ** 2 + (y - 18.5) ** 2 < 5:
                c = (200, 36, 30)
            if 11 <= x <= 18 and 17 <= y <= 20:
                c = (196, 140, 62) if y > 17 else (222, 172, 92)
            if 22 <= x <= 27 and (x - 24.5) ** 2 + (y - 18.5) ** 2 < 7:
                c = (88, 150, 52) if (x + y) % 3 else (60, 110, 36)
        if y == 21:
            c = (96, 70, 40)
        return c

    def top(x, y):
        return red if ((x + y) // 6) % 2 == 0 else white

    tile(side).save(os.path.join(BLOCK, "market_side.png"))
    tile(top).save(os.path.join(BLOCK, "market_top.png"))


def research_table():
    books = [(150, 40, 40), (40, 80, 150), (60, 120, 60), (140, 110, 40), (90, 50, 120), (40, 110, 110)]

    def top(x, y):
        if x < 2 or x > 29 or y < 2 or y > 29:
            return (82, 56, 34)
        c = (38, 66, 160) if (x + y) % 6 else (48, 78, 176)
        # an open book
        if 8 <= x <= 23 and 10 <= y <= 21:
            c = PAPER if x not in (15, 16) else (160, 128, 92)
            if y in (12, 14, 16, 18) and x not in (15, 16) and x not in (8, 23):
                c = (120, 100, 80)
        # quill and inkpot
        if 25 <= x <= 27 and 4 <= y <= 7:
            c = (24, 24, 30)
        return c

    def side(x, y):
        if y in (0, 1, 15, 16, 30, 31) or x < 2 or x > 29:
            return (82, 56, 34)
        shelf = y // 16
        b = books[((x - 2) // 3 + shelf * 2) % len(books)]
        if (x - 2) % 3 == 2:
            return mul(b, 0.6)
        h = 3 + ((x // 3 + shelf) % 3)
        if (y % 16) < h:
            return (42, 30, 20)
        return b if y % 16 != 7 else mul(b, 1.3)

    tile(top).save(os.path.join(BLOCK, "research_table_top.png"))
    tile(side).save(os.path.join(BLOCK, "research_table_side.png"))


def training_desk():
    purple, gold = (96, 52, 140), (230, 190, 60)

    def top(x, y):
        if x < 2 or x > 29 or y < 2 or y > 29:
            return gold if (x + y) % 2 else GOLD_D
        c = purple if (x * 3 + y) % 11 else mul(purple, 1.15)
        d = abs(x - 15.5) + abs(y - 15.5)
        if 6 < d < 8:
            c = gold
        # a scroll and a staff
        if 11 <= x <= 20 and 13 <= y <= 18:
            c = PAPER if 12 <= x <= 19 else (170, 130, 80)
        return c

    def side(x, y):
        if y < 6:
            return purple if y < 4 else gold
        c = planks(x, y, (162, 130, 78))
        if x < 3 or x > 28 or y > 29:
            c = (110, 84, 50)
        if 8 <= x <= 23 and 11 <= y <= 24:
            c = (70, 40, 110) if (x + y) % 4 else (110, 70, 160)
            if x in (8, 23) or y in (11, 24):
                c = gold
            if 14 <= x <= 17 and 15 <= y <= 20:
                c = gold if (x + y) % 2 else GOLD_D
        return c

    tile(top).save(os.path.join(BLOCK, "training_desk_top.png"))
    tile(side).save(os.path.join(BLOCK, "training_desk_side.png"))


if __name__ == "__main__":
    for name, fn in (("longsword", longsword), ("greatsword", greatsword), ("battle_axe", battle_axe), ("halberd", halberd),
                     ("glaive", glaive), ("flail", flail), ("flintlock", flintlock), ("musket", musket), ("bullet", bullet),
                     ("gold_coin", gold_coin), ("command_baton", command_baton), ("builders_hammer", builders_hammer),
                     ("blueprint", blueprint), ("blacksmith_tongs", blacksmith_tongs), ("storage_label", storage_label)):
        fn().save(os.path.join(ITEM, name + ".png"))
    iron_shield_texture().save(os.path.join(ITEM, "iron_shield.png"))
    town_hall()
    market()
    research_table()
    training_desk()
    print("HD textures written")
