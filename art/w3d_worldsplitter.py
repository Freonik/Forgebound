"""Worldsplitter (3D): a double-bit axe cut from the void. Starfield bits with amethyst edges and a glowing rift
through each, an obsidian haft with amethyst rings, void crystals and a tear of purple flame around the head."""
import sys, math
from model3d import *

AMETH = [(50, 16, 90), (100, 44, 170), (170, 100, 240), (226, 180, 255), (250, 236, 255)]
OBSID = [(6, 4, 12), (16, 10, 28), (30, 20, 50), (48, 34, 76)]

m = Model('worldsplitter')
m.region('void', (0, 0, 8, 8), starfield(VOID, [(160, 120, 255), (255, 255, 255)]))
m.region('ameth', (8, 0, 12, 4), sheen(AMETH, 0.35, 0.95))
m.region('rift', (12, 0, 16, 4), flow(VOID[3:] + [(255, 240, 255)], speed=3, base=0.55))
m.region('obsid', (8, 4, 12, 8), plate(OBSID, 0.45, rivets=False))
m.region('crystal', (12, 4, 16, 8), sheen(AMETH, 0.3, 0.9))
m.region('plate', (0, 8, 8, 12), plate(OBSID, 0.5))
m.region('flame', (8, 8, 16, 16), flame([(30, 6, 60), (80, 30, 150), (150, 80, 230), (210, 160, 255), (250, 230, 255)]))
m.region('grip', (0, 12, 4, 16), wrap([(14, 8, 24), (30, 18, 46), (50, 30, 72), (76, 50, 104)]))
R = m.rot

# pommel: a void crystal pointing down, amethyst collar
m.box((C - 1.5, -7.0, C - 1.5), (C + 1.5, -6.2, C + 1.5), 'ameth')
m.box((C - 0.9, -9.6, C - 0.9), (C + 0.9, -7.0, C + 0.9), 'crystal', rot=R('y', 45, (C, -8, C)), glow=8)
m.box((C - 0.4, -11.2, C - 0.4), (C + 0.4, -9.6, C + 0.4), 'crystal', rot=R('y', 45, (C, -10, C)), glow=8)

# haft: obsidian, a wrapped grip, amethyst rings
m.box((C - 0.85, -6.2, C - 0.85), (C + 0.85, 26.0, C + 0.85), 'obsid')
m.box((C - 1.05, -5.0, C - 1.05), (C + 1.05, 5.0, C + 1.05), 'grip')
for yy in (-5.2, 5.2, 9.0, 12.5):
    m.box((C - 1.25, yy - 0.3, C - 1.25), (C + 1.25, yy + 0.3, C + 1.25), 'ameth', glow=4 if yy > 6 else 0)

# head: a socket and two mirrored flared bits
HY0, HY1 = 15.0, 29.0
MY = (HY0 + HY1) / 2
m.box((C - 1.8, HY0 + 2.0, C - 1.5), (C + 1.8, HY1 - 2.0, C + 1.5), 'plate')
for yy in (HY0 + 2.0, HY1 - 2.6):
    m.box((C - 2.0, yy, C - 1.7), (C + 2.0, yy + 0.6, C + 1.7), 'ameth')
for side in (-1, 1):
    for i in range(int(HY1 - HY0)):
        y0 = HY0 + i
        t = (y0 + 0.5 - MY) / ((HY1 - HY0) / 2)
        if abs(t) > 1:
            continue
        inner = 1.8 + 5.5 * abs(t) ** 1.7
        outer = 11.4 + 1.4 * (1 - t * t)
        a, b = inner, outer - 1.0
        x0, x1 = (C + a, C + b) if side > 0 else (C - b, C - a)
        m.box((x0, y0, C - 0.7), (x1, y0 + 1, C + 0.7), 'void', faces=('north', 'south', 'up', 'down'),
              uv=m.surface('void', x0, x1, y0, y0 + 1, C - 13, C + 13, HY0, HY1))
        e0, e1 = (C + b, C + outer) if side > 0 else (C - outer, C - b)
        m.box((e0, y0, C - 0.32), (e1, y0 + 1, C + 0.32), 'ameth', glow=6)
    # the rift: a jagged glowing line through each bit
    pts = [(2.6, 4.6), (4.4, 2.2), (4.0, 0.4), (6.4, -1.0), (7.0, -3.2), (9.6, -4.6)]
    for (xa, ya), (xb, yb) in zip(pts, pts[1:]):
        n = max(1, int(math.hypot(xb - xa, yb - ya) / 0.45))
        for k in range(n):
            x = C + side * (xa + (xb - xa) * k / n)
            yy = MY + ya + (yb - ya) * k / n
            m.box((x - 0.24, yy - 0.24, C - 0.76), (x + 0.24, yy + 0.24, C + 0.76), 'rift', glow=15)

# top crystal crown
m.box((C - 0.9, HY1 - 2.0, C - 0.9), (C + 0.9, HY1 + 2.4, C + 0.9), 'crystal', rot=R('y', 45, (C, HY1, C)), glow=10)
m.box((C - 0.4, HY1 + 2.4, C - 0.4), (C + 0.4, HY1 + 3.0, C + 0.4), 'crystal', rot=R('y', 45, (C, HY1, C)), glow=10)
for dx in (-1, 1):
    m.box((C + dx * 1.6 - 0.45, HY1 - 2.2, C - 0.45), (C + dx * 1.6 + 0.45, HY1 + 0.8, C + 0.45), 'crystal',
          rot=R('z', -22.5 * dx, (C + dx * 1.6, HY1 - 2.2, C)), glow=10)

# void crystals drifting round the head, and a tear of purple fire above it
for x, yy, z, s, ax, ang in [(C - 13.8, MY + 5.4, C + 1.6, 1.0, 'x', 45), (C + 13.6, MY - 4.6, C - 1.8, 1.2, 'z', 45),
                             (C - 5.0, HY1 + 2.0, C - 2.4, 0.8, 'y', 45), (C + 4.6, HY0 - 2.6, C + 2.6, 0.9, 'x', -22.5),
                             (C + 9.0, HY1 + 1.0, C + 1.4, 0.7, 'z', 22.5)]:
    m.box((x - s / 2, yy - s, z - s / 2), (x + s / 2, yy + s, z + s / 2), 'crystal', rot=R(ax, ang, (x, yy, z)), glow=12)
m.plane((C - 3.5, HY1 - 1.0, 0), (C + 3.5, HY1 + 2.9, 0), 'flame', origin=(C, HY1, C))

if __name__ == '__main__':
    print(m.write(sys.argv[1], sys.argv[2], grip_y=0.0, tp_scale=0.6, fp_scale=0.45), 'elements')
