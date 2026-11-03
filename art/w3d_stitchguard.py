"""Stitchguard (3D): the vanilla shield's shape and hand poses, made of woven silk. Raised woven bands across the
front, a gold frame, a red-and-white eye glowing in the middle, silver needles sticking out of the rim, stitches,
and the handle behind. Writes stitchguard and stitchguard_blocking (vanilla shield / shield_blocking poses)."""
import sys, math, json
from model3d import *

SILKW = [(120, 96, 64), (160, 132, 92), (196, 170, 126), (226, 204, 162), (246, 232, 200)]
EYE = [(110, 0, 14), (190, 20, 34), (240, 70, 70), (255, 170, 160), (255, 240, 236)]
WEAVE_D = [(60, 44, 28), (90, 68, 44), (120, 94, 62)]


def weave(px, r, f, n):
    """Woven basket pattern."""
    x0, y0, x1, y1 = r
    for y in range(y0, y1):
        for x in range(x0, x1):
            lx, ly = x - x0, y - y0
            over = ((lx // 4) + (ly // 4)) % 2 == 0
            k = (ly % 4) if over else (lx % 4)
            t = 0.35 + 0.18 * math.sin(k / 3 * math.pi) + 0.08 * NZ[y][x]
            if (lx % 4 == 0 and not over) or (ly % 4 == 0 and over):
                t = 0.12
            px[x, y] = ramp(SILKW, t) + (255,)


m = Model('stitchguard')
m.region('weave', (0, 0, 8, 8), weave)
m.region('gold', (8, 0, 12, 4), sheen(GOLD, 0.35, 0.98))
m.region('eye', (12, 0, 16, 4), flow(EYE, speed=2, base=0.55))
m.region('band', (8, 4, 12, 8), sheen(SILKW, 0.4, 0.95))
m.region('needle', (12, 4, 16, 8), sheen(STEEL, 0.45, 0.99))
m.region('dark', (0, 8, 4, 12), wrap(WEAVE_D))
m.region('white', (4, 8, 8, 12), sheen([(200, 196, 190), (236, 232, 226), (255, 255, 252)], 0.4, 0.95))
R = m.rot

# the vanilla shield body: plate (-6..6, -11..11, z 1..2) and handle behind it
m.box((-6, -11, 1), (6, 11, 2), 'weave', uv={'south': [0, 0, 8, 8], 'north': [8, 0, 0, 8]}, side='dark', ends='dark')
m.box((-1, -3, -5), (1, 3, 1), 'dark')
# gold frame round the front
for (a, b) in (((-6.3, 10.4), (6.3, 11.3)), ((-6.3, -11.3), (6.3, -10.4))):
    m.box((a[0], a[1], 0.8), (b[0], b[1], 2.4), 'gold')
for x0, x1 in ((-6.3, -5.5), (5.5, 6.3)):
    m.box((x0, -10.4, 0.8), (x1, 10.4, 2.4), 'gold')
# raised woven bands across the front
for yy in (-8.0, -4.6, 4.6, 8.0):
    m.box((-5.5, yy - 0.7, 2.0), (5.5, yy + 0.7, 2.5), 'band')
# the eye: white rim, red iris, a glowing pupil
m.box((-2.6, -2.6, 2.0), (2.6, 2.6, 2.6), 'white')
m.box((-2.6, -2.6, 2.0), (2.6, 2.6, 2.6), 'white', rot=R('z', 45, (0, 0, 2.3)))
m.box((-1.7, -1.7, 2.6), (1.7, 1.7, 3.0), 'eye', glow=12)
m.box((-0.7, -0.7, 3.0), (0.7, 0.7, 3.3), 'white', glow=15)
# stitches: little crosses along the frame
for yy in (-6.4, -2.0, 2.0, 6.4):
    for x in (-4.6, 4.6):
        m.box((x - 0.5, yy - 0.12, 2.4), (x + 0.5, yy + 0.12, 2.55), 'white', rot=R('z', 45, (x, yy, 2.45)))
        m.box((x - 0.5, yy - 0.12, 2.4), (x + 0.5, yy + 0.12, 2.55), 'white', rot=R('z', -45, (x, yy, 2.45)))
# silver needles sticking out of the rim
for (x, y, ang) in ((-6.0, 9.0, 45), (6.0, 9.0, -45), (-6.0, -9.0, -45), (6.0, -9.0, 45), (0, 11.0, 0)):
    m.box((x - 0.2, y, 1.3), (x + 0.2, y + 3.4, 1.7), 'needle', rot=R('z', ang, (x, y, 1.5)) if ang else None)

if __name__ == '__main__':
    m.write_texture(sys.argv[1])
    for name, src in (('stitchguard', 'm_shield.json'), ('stitchguard_blocking', 'm_shield_blocking.json')):
        v = m.variant(name)
        v.elements = m.elements
        disp = json.load(open(f'/home/claude/ref/vanilla/{src}'))['display']
        v.write_model(sys.argv[2], 0, display=disp)
    print(len(m.elements), 'elements')
