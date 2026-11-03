"""Hex Bow (3D): a recurve of dark hexed wood. Bone grip plates, green hex runes glowing along the limbs, a bowstring
of green light. Four models share one atlas: hex_bow (rest) and hex_bow_pulling_0..2 (string drawn back further,
a hexed arrow on the string). Built upright (string vertical, limbs bulging to -x) and turned onto the vanilla bow's
diagonal by the display transform, so it is held exactly like a vanilla bow."""
import sys, math
from model3d import *

DWOOD = [(14, 8, 10), (28, 16, 18), (44, 28, 28), (64, 42, 40), (88, 60, 54)]
HEXG = [(10, 70, 30), (30, 150, 60), (80, 230, 110), (180, 255, 200), (240, 255, 245)]
BONEP = [(110, 100, 84), (156, 146, 124), (200, 192, 170), (236, 230, 212)]

base = Model('hex_bow')
base.region('wood', (0, 0, 8, 8), cracked(DWOOD, HEXG, density=0.018, base=0.45))
base.region('bone', (8, 0, 12, 4), sheen(BONEP, 0.3, 0.9))
base.region('hex', (12, 0, 16, 4), flow(HEXG, speed=3, base=0.55))
base.region('grip', (8, 4, 12, 8), wrap([(20, 10, 14), (40, 22, 26), (64, 38, 40), (90, 58, 56)]))
base.region('string', (12, 4, 16, 8), flow([(120, 220, 160), (180, 250, 210), (230, 255, 240), (255, 255, 255)], speed=4, base=0.6))
base.region('shaft', (0, 8, 8, 12), sheen(DWOOD, 0.35, 0.8))
base.region('steel', (8, 8, 12, 12), sheen(STEEL, 0.4, 0.98))
R = base.rot

GRIP_X = C - 3.2     # the belly of the bow at the grip
TIP_Y = 11.0         # limb tips at 8 ± this


def limb(m, direction):
    """Three pieces from the grip to a tip, bending toward +x. direction = 1 (upper limb) or -1 (lower)."""
    x, y = GRIP_X, C + direction * 2.6
    pieces = ((0, 3.2, 1.0), (22.5, 3.6, 0.85), (45, 3.6, 0.7))
    for ang, ln, w in pieces:
        a = -ang * direction
        y0, y1 = (y, y + ln) if direction > 0 else (y - ln, y)
        rot = R('z', a, (x, y, C)) if ang else None
        m.box((x - w, y0, C - 0.75), (x + w, y1, C + 0.75), 'wood', rot=rot)
        # a glowing hex rune on the outside of each piece
        ry = (y0 + y1) / 2
        m.box((x - w - 0.12, ry - 0.35, C - 0.35), (x - w + 0.2, ry + 0.35, C + 0.35), 'hex', rot=rot, glow=15)
        r = math.radians(ang)
        x, y = x + math.sin(r) * ln, y + direction * math.cos(r) * ln
    # a curled tip with a steel nock
    m.box((x - 0.5, y - 0.5, C - 0.55), (x + 0.9, y + 0.5, C + 0.55), 'steel')
    return x + 0.5, y


def build(m, pull):
    """The bow, with the string drawn back `pull` units (0 = at rest, no arrow)."""
    m.box((GRIP_X - 1.2, C - 2.6, C - 0.95), (GRIP_X + 1.2, C + 2.6, C + 0.95), 'grip')
    for dy in (-1, 1):
        m.box((GRIP_X - 1.35, C + dy * 2.6 - 0.5, C - 1.05), (GRIP_X + 1.35, C + dy * 2.6 + 0.5, C + 1.05), 'bone')
    m.box((GRIP_X - 1.5, C - 0.4, C - 1.15), (GRIP_X - 1.1, C + 0.4, C + 1.15), 'hex', glow=15)
    top = limb(m, 1)
    bot = limb(m, -1)
    nock = (top[0] + pull, C, C)
    m.dots((top[0], top[1], C), nock, 'string', size=0.13, step=0.3, glow=15)
    m.dots((bot[0], bot[1], C), nock, 'string', size=0.13, step=0.3, glow=15)
    if pull > 0:
        # a hexed arrow on the string, pointing out past the belly (-x)
        x1 = nock[0]
        x0 = GRIP_X - 6.0
        m.box((x0, C - 0.22, C - 0.22), (x1, C + 0.22, C + 0.22), 'shaft')
        for dz in (-1, 1):
            m.box((x1 - 2.6, C - 0.06, C + dz * 0.2), (x1 - 0.6, C + 0.06, C + dz * 0.75), 'hex', glow=10)
            m.box((x1 - 2.6, C + dz * 0.2, C - 0.06), (x1 - 0.6, C + dz * 0.75, C + 0.06), 'hex', glow=10)
        m.box((x0 - 1.6, C - 0.5, C - 0.5), (x0, C + 0.5, C + 0.5), 'steel', rot=R('x', 45, (x0 - 0.8, C, C)))
        m.box((x0 - 2.4, C - 0.25, C - 0.25), (x0 - 1.4, C + 0.25, C + 0.25), 'hex', glow=15)


VANILLA_BOW = {
    "thirdperson_righthand": {"rotation": [-80, 260, -40], "translation": [-1, -2, 2.5], "scale": [0.9, 0.9, 0.9]},
    "thirdperson_lefthand": {"rotation": [-80, -280, 40], "translation": [-1, -2, 2.5], "scale": [0.9, 0.9, 0.9]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
}
THETA = -45          # upright string -> the vanilla sprite's bottom-left to top-right diagonal

states = [(base, 0)] + [(base.variant(f'hex_bow_pulling_{i}'), p) for i, p in enumerate((2.6, 4.6, 6.6))]
for m, pull in states:
    build(m, pull)

if __name__ == '__main__':
    base.write_texture(sys.argv[1])
    d = base.sprite_display(VANILLA_BOW, THETA)
    for m, _ in states:
        m.write_model(sys.argv[2], 0, display=d)
    print(len(base.elements), 'elements')
