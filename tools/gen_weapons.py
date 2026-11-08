"""Dominion's weapons at 64x64, painted in code (run by gen_hd.py, or on its own).

Built from the same shaded parts as gen_hd.py, at twice the resolution: blades get a bevel
(lit half, shaded half, bright cutting edges and a fuller), grips get leather wraps and
the metalwork gets rivets, gems and brass fittings.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from gen_hd import (Part, sprite, mul, mix, lobe, ITEM, STEEL, STEEL_D, IRON, DIRON, GOLD, GOLD_D, WOOD, WOOD_D,  # noqa: E402
                    LEATHER, LEATHER_D, LEAD, RED)

M = 64
BRIGHT = (226, 232, 240)


def P(color, grain=3, hi=1.22, lo=0.72):
    return Part(color, grain=grain, hi=hi, lo=lo, size=M)


def side_of(p0, p1):
    """Signed distance of a pixel centre from the line p0->p1 (negative = up-left side)."""
    dx, dy = p1[0] - p0[0], p1[1] - p0[1]
    L = math.hypot(dx, dy)

    def d(x, y):
        return ((x + 0.5 - p0[0]) * dy - (y + 0.5 - p0[1]) * dx) / L
    return d


def along_t(p0, p1):
    dx, dy = p1[0] - p0[0], p1[1] - p0[1]
    L2 = dx * dx + dy * dy

    def t(x, y):
        return ((x + 0.5 - p0[0]) * dx + (y + 0.5 - p0[1]) * dy) / L2
    return t


def blade(p0, p1, width, color=STEEL, tip=None, fuller_to=0.7, fuller_w=2):
    """A straight double-edged blade: bevelled halves, bright edges, a dark fuller, a pointed tip."""
    b = P(color, grain=2, hi=1.12, lo=0.85)
    b.line([p0, p1], width=width)
    dx, dy = p1[0] - p0[0], p1[1] - p0[1]
    L = math.hypot(dx, dy)
    ux, uy = dx / L, dy / L
    px_, py_ = -uy, ux
    hw = width / 2
    tip = tip or hw + 3
    b.poly([(p1[0] + px_ * hw, p1[1] + py_ * hw), (p1[0] - px_ * hw, p1[1] - py_ * hw), (p1[0] + ux * tip, p1[1] + uy * tip)])
    d = side_of(p0, p1)
    t = along_t(p0, p1)

    def shade(x, y, c):
        s = d(x, y)
        if abs(s) > hw - 1.0:
            return mul(BRIGHT, 1.0 if s < 0 else 0.86)       # honed edges
        c = mul(c, 1.14) if s < 0 else mul(c, 0.82)           # bevel: light from the top-left
        if abs(s) < fuller_w / 2 and 0.04 < t(x, y) < fuller_to:
            c = mul(STEEL_D, 0.85 if s >= 0 else 1.0)         # fuller groove
        return c
    b.tint(shade)
    return b


def wrap(period=3):
    return lambda x, y, c: mul(c, 0.66) if (x + y) % period == 0 else (mul(c, 1.12) if (x + y) % period == 1 else c)


def grip(p0, p1, width):
    return P(LEATHER, grain=3).line([p0, p1], width=width).tint(wrap())


def gem(cx, cy, r, color=RED):
    g = P(color, grain=2, hi=1.5, lo=0.6).circle(cx, cy, r)
    g.tint(lambda x, y, c: (255, 230, 230) if (x - cx + r * 0.4) ** 2 + (y - cy + r * 0.4) ** 2 < 1.1 else c)
    return g


def rivets(pts, color=BRIGHT):
    return P(color, grain=0, hi=1, lo=1).pixels(pts)


def wood_grain(p0, p1):
    t = along_t(p0, p1)
    return lambda x, y, c: mul(c, 0.82) if int(t(x, y) * 40 + (x * 3 + y) % 5 * 0.3) % 6 == 0 else c


def pole(p0, p1, width=3):
    return P(WOOD, grain=4).line([p0, p1], width=width).tint(wood_grain(p0, p1))


def tassel(x, y):
    t = P(RED, grain=6, hi=1.3, lo=0.7)
    t.poly([(x, y), (x - 3, y + 2), (x - 6, y + 9), (x - 2, y + 8), (x + 1, y + 10), (x + 2, y + 3)])
    t.tint(lambda xx, yy, c: mul(c, 0.7) if (xx + yy) % 3 == 0 else c)
    return t


# ---------------------------------------------------------------- melee

def longsword():
    return sprite([
        blade((21, 42), (55, 8), 6),
        P(GOLD, grain=3, hi=1.3, lo=0.65).line([(11, 31), (31, 51)], width=4),
        P(GOLD_D, grain=2).circle(11, 31, 2.5),
        P(GOLD_D, grain=2).circle(31, 51, 2.5),
        gem(21, 41, 2.2),
        grip((10, 53), (19, 44), 5),
        P(GOLD, grain=3, hi=1.4, lo=0.6).circle(8, 55, 3.6),
        gem(8, 55, 1.3, (60, 110, 200)),
    ], size=M)


def greatsword():
    return sprite([
        blade((19, 44), (56, 7), 12, tip=8, fuller_w=3, fuller_to=0.75),
        P(DIRON, grain=3, hi=1.45, lo=0.6).line([(5, 30), (33, 58)], width=5),
        P(DIRON, grain=2, hi=1.4).circle(5, 30, 3.5),
        P(DIRON, grain=2, hi=1.4).circle(33, 58, 3.5),
        rivets([(19, 44), (20, 43), (12, 37), (26, 51)]),
        grip((5, 59), (16, 48), 6),
        P(DIRON, grain=3, hi=1.45, lo=0.6).circle(3.5, 60.5, 4),
    ], size=M)


def axe_head(c, dirs, reach, spread, flare):
    parts = []
    for d in dirs:
        head = P(STEEL, grain=2, hi=1.15, lo=0.8).where(lobe(c[0], c[1], d[0], d[1], reach, spread=spread, flare=flare, inner=2))

        def shade(x, y, col, d=d):
            r = math.hypot(x + .5 - c[0], y + .5 - c[1])
            if r > reach - 2.2:
                return BRIGHT if (x + y) % 7 else mul(BRIGHT, 0.9)    # honed edge
            if r > reach - 4:
                return mul(col, 1.05)
            # cheek of the blade: lit on the upper side of the lobe
            cross = (x + .5 - c[0]) * d[1] - (y + .5 - c[1]) * d[0]
            return mul(col, 0.95 if cross > 0 else 0.8)
        head.tint(shade)
        parts.append(head)
    return parts


def battle_axe():
    c = (42, 20)
    parts = [pole((5, 59), (52, 12), 4)]
    parts += axe_head(c, [(1, 1), (-1, -1)], 20, 6, 2.6)
    parts += [
        P(DIRON, grain=3, hi=1.4, lo=0.6).circle(c[0], c[1], 4),
        gem(c[0], c[1], 1.6, (60, 110, 200)),
        blade((50, 14), (57, 7), 3, tip=4, fuller_w=0),
        grip((5, 59), (14, 50), 6),
        P(DIRON, grain=2, hi=1.4).line([(15, 49), (17, 47)], width=5),
        P(DIRON, grain=2, hi=1.4).circle(4, 60, 2.5),
    ]
    return sprite(parts, size=M)


def halberd():
    c = (42, 22)
    parts = [pole((2, 62), (52, 12), 3)]
    parts += axe_head(c, [(1, 1)], 18, 4, 2.8)
    parts += [
        P(STEEL_D, grain=3, hi=1.3, lo=0.7).poly([(39, 25), (35, 20), (27, 15), (31, 22), (36, 27)]),   # back hook
        blade((47, 17), (59, 5), 5, tip=5, fuller_w=1),
        P(DIRON, grain=3, hi=1.4, lo=0.6).line([(36, 28), (47, 17)], width=5),                       # socket
        rivets([(39, 25), (43, 21)]),
        tassel(35, 29),
        P(DIRON, grain=2, hi=1.4).circle(2.5, 61.5, 2),
    ]
    return sprite(parts, size=M)


def glaive():
    p0, p1, p2 = (39, 25), (58, 18), (60, 2)
    bl = P(STEEL, grain=2, hi=1.15, lo=0.8)
    pts = []
    for i in range(81):
        t = i / 80
        x = (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0]
        y = (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]
        r = 5.0 * (1 - t) ** 0.8 + 0.7
        pts.append((x, y, r))
        bl.circle(x - 0.5, y - 0.5, r)

    def shade(x, y, c):
        best = min(pts, key=lambda q: (q[0] - x) ** 2 + (q[1] - y) ** 2)
        off = (x - best[0], y - best[1])
        # the convex (outer, right-hand) side is the edge
        if off[0] * 0.8 + off[1] * 0.2 > best[2] * 0.45:
            return BRIGHT
        return mul(c, 1.1) if off[0] < 0 else mul(c, 0.85)
    bl.tint(shade)
    return sprite([
        pole((2, 62), (41, 23), 3),
        bl,
        P(GOLD, grain=3, hi=1.35, lo=0.6).line([(34, 30), (41, 23)], width=5),
        rivets([(37, 27)], (255, 240, 170)),
        tassel(33, 32),
        P(DIRON, grain=2, hi=1.4).circle(2.5, 61.5, 2),
    ], size=M)


def flail():
    bc = (46, 17)
    ball = P(IRON, grain=3, hi=1.3, lo=0.6).circle(bc[0], bc[1], 9.5)
    ball.tint(lambda x, y, c: mul(c, 1.35) if (x - bc[0] + 4) ** 2 + (y - bc[1] + 4) ** 2 < 8 else
              (mul(c, 0.75) if (x - bc[0]) ** 2 + (y - bc[1]) ** 2 > 60 and x + y > bc[0] + bc[1] else c))
    spikes = P(STEEL, grain=2, hi=1.3, lo=0.75)
    for i in range(10):
        a = i * math.pi / 5 + 0.3
        bx, by = bc[0] + math.cos(a) * 8.5, bc[1] + math.sin(a) * 8.5
        tx, ty = bc[0] + math.cos(a) * 14, bc[1] + math.sin(a) * 14
        nx, ny = -math.sin(a) * 2.2, math.cos(a) * 2.2
        spikes.poly([(bx + nx, by + ny), (bx - nx, by - ny), (tx, ty)])
    chain = P(STEEL, grain=1, hi=1.3, lo=0.6)
    holes = []
    for i in range(6):
        x = 23 + i * 3.2
        y = 41 - i * 3.2
        if i % 2 == 0:
            chain.circle(x, y, 2.2)
            holes.append((int(x), int(y)))
        else:
            chain.line([(x - 1.5, y + 1.5), (x + 1.5, y - 1.5)], width=2)
    chain.tint(lambda x, y, c: (0, 0, 0) if (x, y) in holes else c)
    parts = [
        P(WOOD, grain=4).line([(6, 58), (21, 43)], width=6).tint(wood_grain((6, 58), (21, 43))),
        grip((6, 58), (13, 51), 6),
        P(DIRON, grain=3, hi=1.4, lo=0.6).line([(18, 46), (22, 42)], width=7),
        rivets([(19, 44)]),
        P(DIRON, grain=2, hi=1.4).circle(5, 59, 3),
        chain, spikes, ball,
    ]
    img = sprite(parts, size=M)
    for (x, y) in holes:  # see-through chain links
        img.putpixel((x, y), (0, 0, 0, 0))
    return img


# ---------------------------------------------------------------- firearms

def flintlock():
    barrel = P(STEEL, grain=2, hi=1.3, lo=0.6).rect(20, 20, 57, 25)
    barrel.tint(lambda x, y, c: mul(c, 1.25) if y == 21 else (mul(c, 0.8) if y == 24 else c))   # octagonal facets
    stock = P(WOOD, grain=4).poly([(17, 24), (50, 24), (50, 28), (27, 30), (23, 36), (19, 50), (8, 52), (6, 48), (12, 30)])
    stock.tint(lambda x, y, c: mul(c, 0.84) if (x * 2 + y * 3) % 11 == 0 else c)
    return sprite([
        stock,
        P(WOOD_D, grain=3).poly([(8, 52), (19, 50), (20, 46), (9, 48)]),
        barrel,
        P(DIRON, grain=2, hi=1.4).rect(55, 19, 58, 26),                       # muzzle band
        P(GOLD, grain=3, hi=1.35, lo=0.6).rect(44, 24, 46, 28),              # barrel band
        P((90, 60, 36), grain=2).line([(30, 27), (50, 27)], width=1),        # ramrod
        P(GOLD, grain=3, hi=1.35, lo=0.6).poly([(5, 47), (9, 47), (9, 53), (5, 52)]),   # butt cap
        P(DIRON, grain=3, hi=1.3, lo=0.6).rect(19, 22, 30, 28),              # lock plate
        P(STEEL_D, grain=2, hi=1.4, lo=0.6).poly([(16, 12), (21, 11), (23, 15), (22, 21), (19, 22), (18, 17)]),   # cock
        P((54, 54, 60), grain=2).rect(17, 10, 22, 12),                       # flint in its jaws
        P(STEEL, grain=2, hi=1.3).poly([(25, 15), (28, 15), (28, 21), (25, 21)]),       # frizzen
        P(GOLD, grain=3, hi=1.3, lo=0.6).line([(24, 30), (27, 37), (34, 37), (36, 30)], width=2),   # trigger guard
        P(DIRON, grain=2).line([(30, 30), (30, 34)], width=2),
        rivets([(22, 24), (27, 24)]),
    ], size=M)


def musket():
    a, b = (20, 41), (61, 2)
    barrel = P(STEEL, grain=2, hi=1.3, lo=0.6).line([a, b], width=4)
    d = side_of(a, b)
    barrel.tint(lambda x, y, c: mul(c, 1.25) if d(x, y) < -0.8 else c)
    return sprite([
        P(WOOD, grain=4).poly([(0, 54), (8, 62), (25, 47), (22, 40), (13, 42)]),          # butt stock
        P(WOOD_D, grain=3).poly([(0, 54), (4, 58), (13, 50), (9, 47)]),
        P(GOLD, grain=3, hi=1.35, lo=0.6).line([(0, 54), (6, 60)], width=3),               # butt plate
        P(WOOD, grain=4).line([(19, 46), (49, 16)], width=6).tint(wood_grain((19, 46), (49, 16))),   # fore-end
        barrel,
        P((96, 64, 38), grain=2).line([(25, 44), (50, 19)], width=1),                     # ramrod
        P(GOLD, grain=3, hi=1.35, lo=0.6).line([(28, 32), (33, 37)], width=2),
        P(GOLD, grain=3, hi=1.35, lo=0.6).line([(38, 22), (43, 27)], width=2),
        P(GOLD, grain=3, hi=1.35, lo=0.6).line([(46, 14), (51, 19)], width=2),
        P(DIRON, grain=2, hi=1.4).line([(59, 3), (61, 5)], width=3),                      # muzzle
        P(DIRON, grain=3, hi=1.3, lo=0.6).poly([(18, 37), (26, 37), (26, 42), (18, 42)]), # lock plate
        P(STEEL_D, grain=2, hi=1.4, lo=0.6).poly([(17, 30), (21, 30), (22, 36), (19, 37)]),   # cock
        P((54, 54, 60), grain=2).rect(17, 28, 21, 30),
        P(GOLD, grain=3, hi=1.3, lo=0.6).line([(23, 46), (26, 50), (30, 48)], width=2),   # trigger guard
        rivets([(20, 39), (24, 39)]),
    ], size=M)


def bullet():
    parts = []
    for cx, cy in ((19, 41), (43, 43), (31, 22)):
        b = P(LEAD, grain=2, hi=1.4, lo=0.6).circle(cx, cy, 8.5)
        b.tint(lambda x, y, c, cx=cx, cy=cy: mix(c, (230, 234, 240), 0.8) if (x - cx + 3.5) ** 2 + (y - cy + 3.5) ** 2 < 5
               else (mul(c, 1.15) if (x - cx + 2) ** 2 + (y - cy + 2) ** 2 < 22 else c))
        b.tint(lambda x, y, c, cx=cx, cy=cy: mul(c, 0.7) if (x - cx) ** 2 + (y - cy) ** 2 > 48 and x + y > cx + cy else c)
        parts.append(b)
    return sprite(parts, size=M)


def iron_shield_texture():
    """64x64: front 24x44 at (0,0), back 24x44 at (26,0), rim strip (52,0) 4x44, top/bottom (0,46) 24x4, handle (56,0)."""
    from PIL import Image
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    front = P(IRON, grain=3, hi=1.2, lo=0.75).rect(0, 0, 23, 43)
    ring = lambda x, y: min(x, 23 - x, y, 43 - y)
    front.tint(lambda x, y, c: mul(c, 0.66) if ring(x, y) == 0 else
               (mul(c, 1.25) if ring(x, y) == 1 else (mul(c, 1.08) if ring(x, y) == 2 else c)))
    # the cross, with a gold trim
    in_cross = lambda x, y: (9 <= x <= 14 and 4 <= y <= 39) or (4 <= x <= 19 and 14 <= y <= 19)
    near_cross = lambda x, y: (8 <= x <= 15 and 3 <= y <= 40) or (3 <= x <= 20 and 13 <= y <= 20)
    front.tint(lambda x, y, c: mix(c, (164, 34, 32), 0.85) if in_cross(x, y) else (mix(c, GOLD, 0.8) if near_cross(x, y) else c))
    front.render(img)
    for (x, y) in ((3, 3), (20, 3), (3, 40), (20, 40), (3, 21), (20, 21), (11, 3), (12, 40)):
        img.putpixel((x, y), BRIGHT + (255,))
        img.putpixel((x + 1, y + 1), mul(IRON, 0.6) + (255,))
    # the boss
    for y in range(13, 21):
        for x in range(8, 16):
            dx, dy = x - 11.5, y - 16.5
            if dx * dx + dy * dy <= 13:
                f = 1.35 - 0.12 * (dx + dy)
                img.putpixel((x, y), mul(STEEL, max(0.55, min(1.4, f))) + (255,))
    back = P(WOOD, grain=5).rect(26, 0, 49, 43)
    back.tint(lambda x, y, c: mul(c, 0.72) if (x - 26) % 6 == 0 else c)
    back.tint(lambda x, y, c: IRON if x in (26, 27, 48, 49) or y in (0, 1, 42, 43) else c)
    back.render(img)
    P(DIRON, grain=3).rect(52, 0, 55, 43).render(img)
    P(DIRON, grain=3).rect(0, 46, 23, 49).render(img)
    P(LEATHER, grain=3).rect(56, 0, 63, 15).tint(wrap()).render(img)
    return img


ALL = {"longsword": longsword, "greatsword": greatsword, "battle_axe": battle_axe, "halberd": halberd, "glaive": glaive,
       "flail": flail, "flintlock": flintlock, "musket": musket, "bullet": bullet}


def write():
    for name, fn in ALL.items():
        fn().save(os.path.join(ITEM, name + ".png"))
    iron_shield_texture().save(os.path.join(ITEM, "iron_shield.png"))


if __name__ == "__main__":
    write()
    print("64x64 weapons written")
