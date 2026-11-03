"""Abyssal Maw (3D): a sword from the deep. Teeth line both edges like an open jaw, glowing spots on dark teal steel,
and an angler's lure arches off the guard with a bright orb hanging over the blade."""
import sys, math
from model3d import *

DEEP = [(4, 14, 18), (8, 28, 36), (14, 48, 60), (24, 76, 90), (44, 112, 126), (90, 160, 170), (170, 220, 224)]
LURE = [(120, 90, 10), (220, 170, 30), (255, 220, 90), (255, 248, 200), (255, 255, 255)]
BIO = [(10, 80, 90), (30, 170, 180), (90, 240, 240), (200, 255, 255)]


def deep(px, r, f, n):
    """Dark teal steel with bioluminescent spots that pulse."""
    x0, y0, x1, y1 = r
    for y in range(y0, y1):
        for x in range(x0, x1):
            lx, ly = x - x0, y - y0
            c = ramp(DEEP, 0.32 + 0.12 * NZ[y][x] + 0.05 * math.sin(ly * 0.3))
            spot = NZ2[y][x] > 0.95 and (lx + ly) % 3 == 0
            if spot:
                tw = 0.5 + 0.5 * math.sin(f / n * 2 * math.pi + NZ[y][x] * 9)
                c = ramp(BIO, 0.3 + 0.6 * tw)
            px[x, y] = c + (255,)


m = Model('abyssal_maw')
m.region('steel', (0, 0, 8, 8), deep)
m.region('edge', (8, 0, 12, 4), sheen(DEEP, 0.4, 0.95))
m.region('lure', (12, 0, 16, 4), flow(LURE, speed=1, base=0.6))
m.region('tooth', (8, 4, 12, 8), sheen(BONE, 0.4, 0.98))
m.region('grip', (12, 4, 16, 8), wrap([(4, 10, 14), (10, 24, 30), (18, 40, 50), (30, 60, 72)]))
m.region('bio', (0, 8, 4, 12), flow(BIO, speed=2, base=0.5))
m.region('dark', (4, 8, 8, 12), plate(DEEP, 0.2, rivets=False))
R = m.rot

# pommel
m.box((C - 1.1, -7.0, C - 1.1), (C + 1.1, -5.6, C + 1.1), 'dark')
m.box((C - 0.6, -8.4, C - 0.6), (C + 0.6, -7.0, C + 0.6), 'bio', rot=R('y', 45, (C, -7.7, C)), glow=13)
# grip
m.box((C - 0.85, -5.6, C - 0.85), (C + 0.85, 5.2, C + 0.85), 'grip')
# guard: a jaw hinge, fin-like
m.box((C - 3.6, 5.2, C - 1.2), (C + 3.6, 6.8, C + 1.2), 'dark')
for dx in (-1, 1):
    m.box((C + dx * 3.6 - 0.6, 4.4, C - 0.5), (C + dx * 3.6 + 0.6, 7.6, C + 0.5), 'steel', rot=R('z', 22.5 * dx, (C + dx * 3.6, 6, C)))
m.box((C - 0.7, 5.4, C - 1.3), (C + 0.7, 6.6, C + 1.3), 'bio', glow=15)

# blade, a little wider toward the tip, with teeth on both edges
B0, B1, TIP = 6.8, 26.0, 31.0
for i in range(int(B1 - B0)):
    y0 = B0 + i
    W = 1.9 + 0.5 * (i / (B1 - B0))
    m.box((C - W, y0, C - 0.55), (C + W, y0 + 1, C + 0.55), 'steel', faces=('north', 'south', 'east', 'west'),
          uv=m.surface('steel', C - W, C + W, y0, y0 + 1, C - 2.4, C + 2.4, B0, B1))
    if i % 2 == 0 and i > 0:
        for dx in (-1, 1):
            x = C + dx * (W + 0.4)
            m.box((x - 0.45, y0 + 0.1, C - 0.3), (x + 0.45, y0 + 1.0, C + 0.3), 'tooth', rot=R('z', -22.5 * dx, (x, y0 + 0.5, C)))
steps = 5
for i in range(steps):
    y0 = B1 + i * (TIP - B1) / steps
    y1 = B1 + (i + 1) * (TIP - B1) / steps
    w = 2.4 * (1 - (i + 0.5) / steps) + 0.15
    m.box((C - w, y0, C - 0.5), (C + w, y1, C + 0.5), 'steel', faces=('north', 'south', 'east', 'west', 'up'))
# a glowing line down the middle
m.box((C - 0.3, B0 + 0.5, C - 0.6), (C + 0.3, B1, C + 0.6), 'bio', glow=12)

# the lure: a stalk arching off the guard over the blade, a bright orb hanging from it
x, y = C - 3.4, 6.6
for ang, ln in ((22.5, 7.0), (0, 6.0), (-22.5, 3.0)):
    m.box((x - 0.3, y, C - 0.3), (x + 0.3, y + ln, C + 0.3), 'dark', rot=R('z', ang, (x, y, C)))
    a = math.radians(ang)
    x, y = x - math.sin(a) * ln, y + math.cos(a) * ln
m.box((x - 0.15, y - 1.6, C - 0.15), (x + 0.15, y, C + 0.15), 'dark')
m.ball((x, y - 2.6, C), 1.1, 'lure', glow=15)

if __name__ == '__main__':
    print(m.write(sys.argv[1], sys.argv[2], grip_y=0.0, tp_scale=0.64, fp_scale=0.48), 'elements')
