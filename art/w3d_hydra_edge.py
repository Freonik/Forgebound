"""Hydra Edge (3D): a green-scaled longsword. Its crossguard is three serpent heads with glowing eyes, venom glows
down the fuller and drips off the guard."""
import sys, math
from model3d import *

SCALE = [(8, 30, 14), (16, 56, 26), (28, 90, 40), (48, 130, 58), (90, 180, 90), (160, 230, 150), (220, 255, 210)]
VENOM = [(40, 90, 10), (90, 170, 20), (160, 230, 50), (220, 255, 120), (250, 255, 220)]
DARKG = [(6, 16, 8), (14, 30, 16), (24, 48, 26), (38, 70, 40)]


def scales(px, r, f, n):
    """Overlapping scales."""
    x0, y0, x1, y1 = r
    for y in range(y0, y1):
        for x in range(x0, x1):
            lx, ly = x - x0, y - y0
            row = ly // 4
            sx = (lx + (2 if row % 2 else 0)) % 4
            sy = ly % 4
            d = math.hypot(sx - 1.5, sy - 0.5) / 2.2
            t = 0.55 - 0.3 * d + 0.08 * NZ[y][x]
            if sy == 3:
                t = 0.18
            px[x, y] = ramp(SCALE, t) + (255,)


m = Model('hydra_edge')
m.region('scale', (0, 0, 8, 8), scales)
m.region('edge', (8, 0, 12, 4), sheen(SCALE, 0.45, 0.99))
m.region('venom', (12, 0, 16, 4), flow(VENOM, speed=2, base=0.5))
m.region('grip', (8, 4, 12, 8), wrap(DARKG))
m.region('dark', (12, 4, 16, 8), plate(DARKG, 0.5, rivets=False))
m.region('head', (0, 8, 8, 12), scales)
m.region('fang', (8, 8, 12, 12), sheen(BONE, 0.4, 0.95))
R = m.rot

# pommel: a coiled tail
m.box((C - 1.1, -7.0, C - 1.1), (C + 1.1, -5.6, C + 1.1), 'dark')
m.box((C - 0.7, -8.6, C - 0.7), (C + 0.7, -7.0, C + 0.7), 'scale', rot=R('y', 45, (C, -7.8, C)))
m.box((C - 0.3, -9.6, C - 0.3), (C + 0.3, -8.6, C + 0.3), 'venom', glow=12)
# grip
m.box((C - 0.85, -5.6, C - 0.85), (C + 0.85, 5.0, C + 0.85), 'grip')
for yy in (-3.0, 0.0, 3.0):
    m.box((C - 1.0, yy - 0.25, C - 1.0), (C + 1.0, yy + 0.25, C + 1.0), 'scale')


def serpent(x, y, dirx, up):
    """A serpent head on a neck: dirx = which way it leans (-1 / 0 / 1), up = how long the neck is."""
    ang = -45 * dirx
    m.box((x - 0.9, y, C - 0.9), (x + 0.9, y + up, C + 0.9), 'scale', rot=R('z', ang, (x, y, C)) if dirx else None)
    a = math.radians(ang)
    hx, hy = x - math.sin(a) * up, y + math.cos(a) * up
    # head: a big skull block, a snout pointing outward, glowing eyes, fangs
    m.box((hx - 1.5, hy - 0.4, C - 1.3), (hx + 1.5, hy + 1.8, C + 1.3), 'head')
    m.box((hx - 1.1, hy + 1.8, C - 0.9), (hx + 1.1, hy + 2.3, C + 0.9), 'dark')
    sx = hx + dirx * 1.9 if dirx else hx
    sy = hy + (0.2 if dirx else 2.0)
    if dirx:
        m.box((sx - 1.0, sy - 0.4, C - 0.9), (sx + 1.0, sy + 1.0, C + 0.9), 'head')
    else:
        m.box((sx - 0.9, sy, C - 0.9), (sx + 0.9, sy + 1.4, C + 0.9), 'head')
    for dz in (-1, 1):
        m.box((hx - 0.35, hy + 0.9, C + dz * 1.3 - 0.08), (hx + 0.35, hy + 1.4, C + dz * 1.3 + 0.08), 'venom', glow=15)
        fx = sx + dirx * 0.6
        m.box((fx - 0.15, sy - 1.1, C + dz * 0.45 - 0.15), (fx + 0.15, sy - 0.3, C + dz * 0.45 + 0.15), 'fang')


# crossguard: a bar with three serpent heads rearing off it
m.box((C - 4.0, 5.0, C - 1.0), (C + 4.0, 6.4, C + 1.0), 'dark')
serpent(C - 3.2, 5.8, -1, 3.6)
serpent(C + 3.2, 5.8, 1, 3.6)
# venom dripping off the side heads
for x in (C - 7.4, C + 7.4):
    m.box((x - 0.15, 6.0, C - 0.15), (x + 0.15, 8.2, C + 0.15), 'venom', glow=12)
    m.box((x - 0.28, 5.4, C - 0.28), (x + 0.28, 6.0, C + 0.28), 'venom', glow=12)

# blade
B0, B1, TIP = 8.4, 26.5, 31.5
W = 2.2
m.box((C - W, B0, C - 0.55), (C + W, B1, C + 0.55), 'scale', uv=m.surface('scale', C - W, C + W, B0, B1, C - W, C + W, B0, B1))
for dx in (-1, 1):
    a, b = (C - W - 0.7, C - W) if dx < 0 else (C + W, C + W + 0.7)
    m.box((a, B0, C - 0.26), (b, B1, C + 0.26), 'edge')
m.box((C - 0.4, B0 + 0.4, C - 0.62), (C + 0.4, B1 - 0.2, C + 0.62), 'venom', glow=15)
steps = 6
for i in range(steps):
    y0 = B1 + i * (TIP - B1) / steps
    y1 = B1 + (i + 1) * (TIP - B1) / steps
    w = W * (1 - (i + 0.5) / steps) + 0.15
    m.box((C - w, y0, C - 0.5), (C + w, y1, C + 0.5), 'scale', faces=('north', 'south', 'up'))
    m.box((C - w - 0.6, y0, C - 0.24), (C - w, y1, C + 0.24), 'edge', faces=('north', 'south', 'west', 'up'))
    m.box((C + w, y0, C - 0.24), (C + w + 0.6, y1, C + 0.24), 'edge', faces=('north', 'south', 'east', 'up'))

m.box((C - 1.2, 6.4, C - 1.6), (C + 1.2, 8.0, C - 0.55), 'head')
for dx in (-1, 1):
    m.box((C + dx * 0.6 - 0.3, 7.2, C - 1.66), (C + dx * 0.6 + 0.3, 7.6, C - 1.6), 'venom', glow=15)

if __name__ == '__main__':
    print(m.write(sys.argv[1], sys.argv[2], grip_y=0.0, tp_scale=0.66, fp_scale=0.5), 'elements')
