"""Hand-drawn 32x32 item icons that replace the earlier muddy renders: siege engines, flour and venison.

Run on its own (python3 tools/gen_icons.py); it only writes the files it draws.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from gen_hd import Part, sprite, mul, ITEM, GOLD, IRON, DIRON, WOOD, WOOD_D, LEATHER, LEATHER_D, PAPER  # noqa: E402

PLANK = (164, 120, 72)
LOG = (118, 84, 52)
ROPE = (196, 168, 112)
BLACK_IRON = (60, 62, 70)
CANVAS = (226, 214, 184)


def wheel(cx, cy, r, color=WOOD_D, hub=IRON):
    """A spoked cart wheel: rim, four spokes and an iron hub."""
    rim = Part(color, grain=3).where(lambda x, y: r - 1.3 <= ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5 <= r + 0.4)
    spokes = Part(mul(color, 1.15), grain=2, hi=1, lo=1)
    spokes.line([(cx - r + 1, cy), (cx + r - 1, cy)]).line([(cx, cy - r + 1), (cx, cy + r - 1)])
    if r >= 4:
        d = (r - 1) * 0.7
        spokes.line([(cx - d, cy - d), (cx + d, cy + d)]).line([(cx - d, cy + d), (cx + d, cy - d)])
    h = Part(hub, grain=2, hi=1.4).circle(cx, cy, 1.0)
    return [spokes, rim, h]


def battering_ram():
    # a roofed wheeled frame with an iron-capped log slung underneath
    roof = Part((96, 70, 44), grain=4).poly([(3, 13), (16, 4), (29, 13), (27, 15), (16, 8), (5, 15)])
    roof.tint(lambda x, y, c: mul(c, 0.8) if (x + y) % 4 == 0 else c)        # shingles
    posts = Part(WOOD, grain=4).rect(6, 13, 7, 23).rect(25, 13, 26, 23)
    beam = Part(WOOD_D, grain=3).rect(5, 22, 27, 23)
    chains = Part(IRON, grain=1, hi=1, lo=1).pixels([(12, y) for y in range(10, 15)] + [(20, y) for y in range(10, 15)])
    log = Part(LOG, grain=5).rect(2, 15, 26, 18)
    log.tint(lambda x, y, c: mul(c, 0.85) if x % 5 == 0 else c)
    cap = Part(DIRON, grain=2, hi=1.4).poly([(26, 14), (30, 15), (30, 18), (26, 19)])
    bands = Part(IRON, grain=1).rect(9, 15, 9, 18).rect(18, 15, 18, 18)
    return sprite([roof, posts, chains, log, bands, cap, beam] + wheel(9, 26, 3.6) + wheel(23, 26, 3.6))


def cannon():
    barrel = Part(BLACK_IRON, grain=3, hi=1.45, lo=0.65).line([(6, 21), (26, 9)], width=6)
    bands = Part((90, 92, 102), grain=1, hi=1.3).line([(10, 16), (13, 21)], width=2).line([(21, 10), (24, 14)], width=2)
    muzzle = Part((30, 30, 34), grain=0, hi=1, lo=1).circle(27, 8.5, 1.6)
    cascabel = Part(BLACK_IRON, grain=2, hi=1.4).circle(4, 22.5, 1.6)
    carriage = Part(WOOD, grain=4).poly([(3, 23), (20, 17), (22, 21), (6, 27), (3, 27)])
    trail = Part(WOOD_D, grain=3).rect(2, 26, 9, 28)
    return sprite([carriage, trail, barrel, bands, muzzle, cascabel] + wheel(17, 23, 5.4))


def trebuchet():
    # A-frame uprights on a base, a long throwing arm with a counterweight box and a sling
    base = Part(WOOD_D, grain=3).rect(2, 27, 29, 29)
    legs = Part(WOOD, grain=4).line([(6, 27), (15, 11)], width=2).line([(24, 27), (16, 11)], width=2)
    brace = Part(WOOD_D, grain=3).line([(10, 21), (21, 21)], width=1)
    arm = Part(PLANK, grain=4).line([(4, 4), (22, 17)], width=2)
    pivot = Part(IRON, grain=1, hi=1.4).circle(15.5, 11.5, 1.3)
    box = Part((104, 76, 48), grain=4).rect(20, 17, 26, 23)
    box.tint(lambda x, y, c: mul(c, 0.75) if y in (19, 21) else c)
    stone = Part((130, 130, 128), grain=6).circle(23, 16, 1.5)
    sling = Part(ROPE, grain=2, hi=1, lo=1).line([(4, 4), (3, 9), (5, 12)])
    shot = Part((120, 118, 112), grain=6, hi=1.3).circle(5, 13, 1.6)
    return sprite([legs, brace, base, arm, sling, shot, box, stone, pivot])


def supply_cart():
    # a canvas-covered wagon on two wheels, shafts out front
    shafts = Part(WOOD_D, grain=3).line([(22, 21), (30, 18)], width=1).line([(22, 22), (30, 20)], width=1)
    bed = Part(PLANK, grain=4).rect(3, 17, 23, 22)
    bed.tint(lambda x, y, c: mul(c, 0.78) if y == 19 or x in (8, 15) else c)
    cover = Part(CANVAS, grain=4).where(lambda x, y: 3 <= x <= 23 and 6 <= y <= 17 and
                                       ((x - 13) / 11) ** 2 + ((y - 17) / 11) ** 2 <= 1)
    cover.tint(lambda x, y, c: mul(c, 0.84) if x in (8, 13, 18) else c)       # hoops
    rope = Part(ROPE, grain=2, hi=1, lo=1).rect(3, 16, 23, 16)
    sacks = Part((200, 176, 120), grain=4).circle(26, 15, 2.3)
    return sprite([shafts, cover, rope, bed, sacks] + wheel(8, 24, 4.6) + wheel(19, 24, 4.6))


def cannonball():
    ball = Part(BLACK_IRON, grain=3, hi=1.6, lo=0.6).circle(15.5, 16.5, 8)
    ball.tint(lambda x, y, c: mul(c, 1.6) if (x - 12) ** 2 + (y - 13) ** 2 < 5 else c)
    return sprite([ball])


def flour():
    # a plump cloth sack, tied at the neck with string, the top folded over in a little ruff
    body = Part(PAPER, grain=4, hi=1.08, lo=0.8).where(
        lambda x, y: ((x - 15.5) / 10.5) ** 2 + ((y - 20) / 9) ** 2 <= 1 and y <= 28)
    body.tint(lambda x, y, c: mul(c, 0.9) if (x - 10) % 7 == 0 and y > 14 else c)   # creases
    neck = Part(mul(PAPER, 0.95), grain=3).poly([(12, 12), (19, 12), (18, 9), (13, 9)])
    ruff = Part(PAPER, grain=4).poly([(9, 4), (13, 7), (16, 3), (19, 7), (23, 4), (20, 10), (11, 10)])
    string = Part((150, 104, 60), grain=2, hi=1.2).rect(11, 10, 20, 11)
    knot = Part((150, 104, 60), grain=2).pixels([(21, 12), (22, 13), (20, 13)])
    stamp = Part((176, 140, 70), grain=1, hi=1, lo=1).pixels(
        [(15, 18), (16, 18), (14, 19), (17, 19), (15, 20), (16, 20), (15, 21), (16, 21), (14, 22), (17, 22), (15, 23), (16, 23)])
    dust = Part((248, 246, 240), grain=0, hi=1, lo=1).pixels([(15, 2), (17, 1), (13, 2)])
    return sprite([body, neck, ruff, string, knot, stamp, dust])


def venison(cooked=False):
    """A T-bone style cut: a round slab of meat with a fat cap and a bone through it."""
    meat_c = (190, 56, 54) if not cooked else (136, 74, 40)
    fat_c = (240, 222, 206) if not cooked else (214, 166, 100)
    fat = Part(fat_c, grain=3).where(lambda x, y: ((x - 16) / 13) ** 2 + ((y - 17) / 10) ** 2 <= 1)
    meat = Part(meat_c, grain=6).where(lambda x, y: ((x - 16.5) / 11.3) ** 2 + ((y - 17.5) / 8.4) ** 2 <= 1)
    # marbling / sear lines
    if cooked:
        meat.tint(lambda x, y, c: mul(c, 0.7) if (x + y) % 6 == 0 else c)
    else:
        meat.tint(lambda x, y, c: mul(c, 1.25) if (x * 3 + y * 5) % 17 == 0 else c)
    bone = Part((242, 234, 216), grain=2, hi=1.15).line([(10, 11), (16, 18), (14, 25)], width=2)
    marrow = Part((200, 150, 120), grain=1, hi=1, lo=1).pixels([(16, 18)])
    return sprite([fat, meat, bone, marrow])


def main():
    icons = {"battering_ram": battering_ram(), "cannon": cannon(), "trebuchet": trebuchet(), "supply_cart": supply_cart(),
             "cannonball": cannonball(), "flour": flour(), "venison": venison(False), "cooked_venison": venison(True)}
    only = set(sys.argv[1:])
    for name, im in icons.items():
        if only and name not in only:
            continue
        im.save(os.path.join(ITEM, name + ".png"))
        print("wrote", name)


if __name__ == "__main__":
    main()
