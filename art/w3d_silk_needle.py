"""Silk Needle (3D): a long silver needle-rapier. Silk thread runs through the eye at the guard and trails off in
loops, a cobweb is caught between the guard and the blade, a crimson grip with a spool-shaped pommel."""
import sys, math
from model3d import *

SILK = [(200, 196, 190), (230, 226, 220), (248, 246, 240), (255, 255, 252)]
CRIM = [(40, 4, 10), (80, 10, 22), (130, 20, 36), (180, 40, 52)]


def web(px, r, f, n):
    """A translucent cobweb: radial threads and rings."""
    x0, y0, x1, y1 = r
    W, H = x1 - x0, y1 - y0
    for y in range(y0, y1):
        for x in range(x0, x1):
            lx, ly = x - x0 - W / 2 + 0.5, y - y0 - H / 2 + 0.5
            ang = math.atan2(ly, lx)
            rad = math.hypot(lx, ly)
            if rad > W / 2:
                continue
            spoke = abs(math.sin(ang * 4)) < 0.08 * max(1, 6 / max(rad, 1))
            ringv = abs((rad + 0.6 * math.sin(ang * 8)) % 4.5 - 2.2) < 0.45
            if spoke or ringv:
                a = clamp(200 * (1 - rad / (W / 2)) + 60)
                px[x, y] = ramp(SILK, 0.7) + (a,)


m = Model('silk_needle')
m.region('needle', (0, 0, 8, 4), sheen(STEEL, 0.45, 0.99))
m.region('silk', (8, 0, 12, 4), sheen(SILK, 0.3, 0.95))
m.region('crimson', (12, 0, 16, 4), wrap(CRIM))
m.region('dark', (0, 4, 8, 8), plate(CRIM, 0.4, rivets=False))
m.region('glow', (8, 4, 12, 8), flow([(120, 10, 30), (200, 30, 50), (255, 90, 100), (255, 200, 200)], speed=2))
m.region('web', (0, 8, 8, 16), web)
m.region('silver', (8, 8, 12, 12), edge(STEEL, 0.6))
R = m.rot

# pommel: a spool with silk wound on it
m.box((C - 1.4, -7.4, C - 1.4), (C + 1.4, -6.8, C + 1.4), 'silver')
m.box((C - 1.0, -6.8, C - 1.0), (C + 1.0, -5.4, C + 1.0), 'silk')
m.box((C - 1.4, -5.4, C - 1.4), (C + 1.4, -4.8, C + 1.4), 'silver')
# grip
m.box((C - 0.7, -4.8, C - 0.7), (C + 0.7, 4.0, C + 0.7), 'crimson')
# guard: a swept ring guard (two curved bars) and a cup
m.box((C - 2.2, 4.0, C - 1.0), (C + 2.2, 4.8, C + 1.0), 'silver')
for dx in (-1, 1):
    m.box((C + dx * 2.2 - 0.25, 4.0, C - 0.25), (C + dx * 2.2 + 0.25, 7.0, C + 0.25), 'silver',
          rot=R('z', 22.5 * dx, (C + dx * 2.2, 4.0, C)))
m.box((C - 0.3, -3.6, C + 0.6), (C + 0.3, 4.0, C + 1.0), 'silver')       # knuckle bow
m.box((C - 0.5, 4.8, C - 0.5), (C + 0.5, 6.0, C + 0.5), 'glow', glow=12)

# the needle: long, square, tapering; the eye near the base
N0, N1 = 6.0, 30.0
m.box((C - 0.55, N0, C - 0.55), (C + 0.55, 8.0, C + 0.55), 'needle')
m.box((C - 0.55, 8.0, C - 0.55), (C - 0.2, 10.0, C + 0.55), 'needle')        # the eye: two rails
m.box((C + 0.2, 8.0, C - 0.55), (C + 0.55, 10.0, C + 0.55), 'needle')
m.box((C - 0.55, 10.0, C - 0.55), (C + 0.55, 24.0, C + 0.55), 'needle')
m.box((C - 0.38, 24.0, C - 0.38), (C + 0.38, 28.0, C + 0.38), 'needle')
m.box((C - 0.2, 28.0, C - 0.2), (C + 0.2, N1, C + 0.2), 'needle')
m.box((C - 0.12, N1, C - 0.12), (C + 0.12, N1 + 1.0, C + 0.12), 'silver')
m.box((C - 0.1, 10.5, C - 0.6), (C + 0.1, 23.5, C + 0.6), 'glow', glow=8)

# silk thread through the eye, trailing off in loops
pts = [(C, 9.0, C), (C + 1.2, 8.0, C + 0.6), (C + 2.8, 6.6, C + 1.4), (C + 4.6, 6.0, C + 1.8), (C + 6.0, 6.8, C + 1.6),
       (C + 6.6, 8.6, C + 1.0), (C + 6.0, 10.0, C + 0.4), (C + 4.8, 9.6, C), (C + 4.4, 8.0, C - 0.4), (C + 5.4, 6.0, C - 0.8),
       (C + 7.4, 4.4, C - 1.0), (C + 9.6, 3.8, C - 0.8)]
for (xa, ya, za), (xb, yb, zb) in zip(pts, pts[1:]):
    n = max(1, int(math.dist((xa, ya, za), (xb, yb, zb)) / 0.35))
    for k in range(n):
        x, y, z = xa + (xb - xa) * k / n, ya + (yb - ya) * k / n, za + (zb - za) * k / n
        m.box((x - 0.14, y - 0.14, z - 0.14), (x + 0.14, y + 0.14, z + 0.14), 'silk')
# a cobweb caught between the guard and the needle
m.box((C - 3.6, 4.6, C), (C + 0.2, 10.4, C), 'web', faces=('north', 'south'), stretch=True,
      rot=R('y', 22.5, (C, 4.6, C)))

if __name__ == '__main__':
    print(m.write(sys.argv[1], sys.argv[2], grip_y=0.0, tp_scale=0.66, fp_scale=0.5), 'elements')
