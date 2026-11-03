"""Pinning Repeater (3D): a dark steel repeating crossbow. A wooden butt, a box magazine of pinning bolts on top of
the stock, swept-back steel limbs and a steel cable for a string. Six models share one atlas: rest, pulling_0..2
(string drawn back toward the latch), _arrow (a bolt loaded) and _firework (a rocket loaded). Built upright (stock
along +y, limbs at the top) and turned onto the vanilla crossbow's diagonal by the display transform."""
import sys, math
from model3d import *

GUN = [(12, 12, 16), (26, 26, 32), (44, 44, 52), (68, 68, 78), (100, 100, 112), (150, 150, 162), (210, 210, 220)]
WOODC = [(30, 18, 10), (54, 34, 20), (82, 54, 32), (112, 78, 48)]
RED = [(90, 10, 10), (170, 30, 24), (230, 70, 50), (255, 150, 120)]
PIN = [(120, 30, 30), (200, 60, 50), (255, 120, 100), (255, 210, 190)]

base = Model('pinning_repeater')
base.region('steel', (0, 0, 8, 8), plate(GUN, 0.38))
base.region('edge', (8, 0, 12, 4), sheen(GUN, 0.45, 0.98))
base.region('pin', (12, 0, 16, 4), flow(PIN, speed=2, base=0.5))
base.region('wood', (8, 4, 12, 8), wrap(WOODC))
base.region('cable', (12, 4, 16, 8), sheen(STEEL, 0.7, 0.99))
base.region('dark', (0, 8, 8, 12), plate(GUN, 0.2, rivets=False))
base.region('rocket', (8, 8, 12, 12), wrap(RED))
base.region('paper', (12, 8, 16, 12), sheen([(200, 190, 170), (230, 222, 204), (250, 246, 236)], 0.3, 0.9))
R = base.rot

Y_BUTT, Y_FRONT = -3.0, 18.5
PROD_Y = 15.6
LATCH_Y = 6.0


def limbs(m):
    """Swept-back steel limbs from the front of the stock; returns the two tips."""
    tips = []
    for side in (-1, 1):
        x, y = C + side * 1.2, PROD_Y
        for ang, ln, w in ((0, 3.0, 0.8), (22.5, 3.0, 0.65), (45, 2.4, 0.5)):
            a = -ang * side            # turning the outward piece so it sweeps back (-y)
            x0, x1 = (x, x + side * ln) if side > 0 else (x - ln, x)
            rot = R('z', a, (x, y, C)) if ang else None
            m.box((x0, y - w, C - 0.6), (x1, y + w, C + 0.6), 'steel', rot=rot)
            m.box((x0, y + w, C - 0.25), (x1, y + w + 0.3, C + 0.25), 'edge', rot=rot)
            r = math.radians(ang)
            x, y = x + side * math.cos(r) * ln, y - math.sin(r) * ln
        m.box((x - 0.45, y - 0.45, C - 0.45), (x + 0.45, y + 0.45, C + 0.45), 'edge')
        tips.append((x, y))
    return tips


def build(m, string_y, load=None):
    # butt and stock
    m.box((C - 1.4, Y_BUTT, C - 1.2), (C + 1.4, 3.0, C + 1.2), 'wood')
    m.box((C - 1.55, Y_BUTT - 0.5, C - 1.35), (C + 1.55, Y_BUTT, C + 1.35), 'dark')
    m.box((C - 1.0, 3.0, C - 1.0), (C + 1.0, Y_FRONT, C + 1.0), 'steel')
    m.box((C - 0.35, 3.0, C + 1.0), (C + 0.35, Y_FRONT - 1.0, C + 1.25), 'dark')            # the bolt groove rail
    # trigger and guard under the stock
    m.box((C - 0.25, 3.6, C - 2.4), (C + 0.25, 5.0, C - 1.0), 'edge')
    m.box((C - 0.3, 2.8, C - 2.8), (C + 0.3, 5.8, C - 2.4), 'dark')
    # box magazine of bolts on top (+z side faces up when held)
    m.box((C - 1.2, 8.0, C + 1.0), (C + 1.2, 13.0, C + 3.2), 'steel')
    for k in range(3):
        m.box((C - 0.9 + k * 0.7, 13.0, C + 1.6), (C - 0.5 + k * 0.7, 14.4, C + 2.6), 'pin', glow=10)
    # front: a muzzle cap and the prod mount
    m.box((C - 1.3, PROD_Y - 1.2, C - 1.3), (C + 1.3, PROD_Y + 1.2, C + 1.3), 'dark')
    m.box((C - 0.6, Y_FRONT, C - 0.6), (C + 0.6, Y_FRONT + 0.8, C + 0.6), 'edge')
    tips = limbs(m)
    for (tx, ty) in tips:
        m.dots((tx, ty, C), (C, string_y, C + 1.15), 'cable', size=0.17, step=0.3, glow=6)
    if load == 'arrow':
        m.box((C - 0.2, LATCH_Y, C + 1.05), (C + 0.2, Y_FRONT + 2.4, C + 1.45), 'cable')
        m.box((C - 0.5, Y_FRONT + 2.4, C + 0.75), (C + 0.5, Y_FRONT + 3.6, C + 1.75), 'pin', glow=15)
        for dx in (-1, 1):
            m.box((C + dx * 0.2, LATCH_Y, C + 1.2), (C + dx * 0.75, LATCH_Y + 2.0, C + 1.3), 'pin')
    elif load == 'rocket':
        m.box((C - 0.7, LATCH_Y + 1.0, C + 1.1), (C + 0.7, Y_FRONT, C + 2.5), 'rocket')
        m.box((C - 0.5, Y_FRONT, C + 1.3), (C + 0.5, Y_FRONT + 1.4, C + 2.3), 'paper', rot=R('y', 45, (C, Y_FRONT, C + 1.8)))
        m.box((C - 0.1, LATCH_Y, C + 1.7), (C + 0.1, LATCH_Y + 1.0, C + 1.9), 'paper')


VANILLA_XBOW = {
    "thirdperson_righthand": {"rotation": [-90, 0, -60], "translation": [2, 0.1, -3], "scale": [0.9, 0.9, 0.9]},
    "thirdperson_lefthand": {"rotation": [-90, 0, 30], "translation": [2, 0.1, -3], "scale": [0.9, 0.9, 0.9]},
    "firstperson_righthand": {"rotation": [-90, 0, -55], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [-90, 0, 35], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
}
THETA = 45           # upright stock -> the vanilla sprite's diagonal (front top-left, butt bottom-right)

states = [(base, PROD_Y - 1.3, None)]
for i, sy in enumerate((PROD_Y - 4.0, PROD_Y - 6.5, LATCH_Y + 0.5)):
    states.append((base.variant(f'pinning_repeater_pulling_{i}'), sy, None))
states.append((base.variant('pinning_repeater_arrow'), LATCH_Y, 'arrow'))
states.append((base.variant('pinning_repeater_firework'), LATCH_Y, 'rocket'))
for m, sy, load in states:
    build(m, sy, load)

if __name__ == '__main__':
    base.write_texture(sys.argv[1])
    d = base.sprite_display(VANILLA_XBOW, THETA)
    for m, _, _ in states:
        m.write_model(sys.argv[2], 0, display=d)
    print(len(base.elements), 'elements')
