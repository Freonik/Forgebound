"""Butcher Cleaver (3D): a huge, heavy cleaver. A thick slab of stained steel with a hole in the corner and a meat
hook hanging through it, a bright honed edge with blood on it, a bone handle bound with iron rivets."""
import sys, math
from model3d import *

STAIN = [(22, 20, 24), (48, 44, 50), (84, 80, 88), (124, 120, 128), (164, 160, 168), (206, 204, 210), (236, 236, 240)]
GORE = [(50, 0, 8), (100, 6, 18), (150, 14, 26), (200, 30, 40), (240, 80, 80)]
PLAGUE = [(30, 10, 40), (60, 24, 80), (100, 50, 130), (150, 90, 180)]


def stained(px, r, f, n):
    """Steel with blood splashes and a few plague-violet stains."""
    x0, y0, x1, y1 = r
    pulse = 0.5 + 0.5 * math.sin(2 * math.pi * f / n)
    for y in range(y0, y1):
        for x in range(x0, x1):
            lx, ly = x - x0, y - y0
            t = 0.45 + 0.12 * NZ[y][x] + 0.08 * math.sin(lx * 0.25 + ly * 0.1)
            c = ramp(STAIN, t)
            # dried blood: soft round splats with drips running down
            blood = 0.0
            for (bx, by, br) in ((22, 10, 5.5), (9, 23, 4.0), (26, 25, 2.6)):
                d = math.hypot(lx - bx, ly - by) + 1.4 * (NZ2[y][x] - 0.5) * 2
                blood = max(blood, 1 - d / br)
                if abs(lx - bx - 1) < 0.8 and by < ly < by + br * 2.2 and NZ[y0][x] > 0.3:
                    blood = max(blood, 0.5)
            if blood > 0.25:
                c = ramp(GORE, 0.2 + 0.35 * blood + 0.1 * NZ[y][x])
            elif blood > 0.05:
                c = mix(c, ramp(GORE, 0.25), 0.35)
            if math.hypot(lx - 6, ly - 8) + 2 * math.sin(lx + ly) < 3.5:
                c = ramp(PLAGUE, 0.4 + 0.3 * pulse)
            px[x, y] = c + (255,)


m = Model('butcher_cleaver')
m.region('blade', (0, 0, 8, 8), stained)
m.region('edge', (8, 0, 12, 4), sheen(STAIN, 0.5, 0.99))
m.region('gore', (12, 0, 16, 4), flow(GORE, speed=1, base=0.45))
m.region('bone', (8, 4, 12, 8), sheen(BONE, 0.3, 0.85))
m.region('iron', (12, 4, 16, 8), edge(IRON, 0.55))
m.region('spine', (0, 8, 8, 12), plate(STAIN, 0.3, rivets=True))
R = m.rot

# handle: bone, iron rivets, a knob
m.box((C - 0.9, -6.4, C - 0.9), (C + 0.9, 6.0, C + 0.9), 'bone')
for yy in (-4.0, 0.0, 4.0):
    for dz in (-1, 1):
        m.box((C - 0.3, yy - 0.3, C + dz * 0.95 - 0.12), (C + 0.3, yy + 0.3, C + dz * 0.95 + 0.12), 'iron')
m.box((C - 1.2, -7.6, C - 1.2), (C + 1.2, -6.4, C + 1.2), 'bone', rot=R('y', 45, (C, -7, C)))
m.box((C - 1.1, 5.6, C - 1.1), (C + 1.1, 7.0, C + 1.1), 'iron')

# the slab: a big rectangle off to one side of the handle line (+x), thick at the spine, a honed edge at +x
X0, X1 = C - 1.4, C + 9.4
Y0, Y1 = 6.6, 24.0
HOLE = (X0 + 2.4, Y1 - 2.6)          # centre of the hole near the top back corner
hr = 1.1
rows = int((Y1 - Y0) / 1.0)
for i in range(rows):
    y0 = Y0 + i * (Y1 - Y0) / rows
    y1 = y0 + (Y1 - Y0) / rows
    xe = X1 - 0.9 + 0.5 * math.sin((y0 - Y0) / (Y1 - Y0) * math.pi)    # the edge bellies out a little
    spans = [(X0, xe)]
    if abs((y0 + y1) / 2 - HOLE[1]) < hr:
        spans = [(X0, HOLE[0] - hr), (HOLE[0] + hr, xe)]
    for a, b in spans:
        m.box((a, y0, C - 0.7), (b, y1, C + 0.7), 'blade', faces=('north', 'south', 'up', 'down', 'west'),
              uv=m.surface('blade', a, b, y0, y1, X0, X1, Y0, Y1))
    m.box((xe, y0, C - 0.3), (xe + 1.0, y1, C + 0.3), 'edge')
# a thick spine along the top and the back
m.box((X0 - 0.2, Y1, C - 0.9), (X1 + 0.2, Y1 + 0.8, C + 0.9), 'spine')
m.box((X0 - 0.8, Y0, C - 0.9), (X0, Y1 + 0.8, C + 0.9), 'spine')
# blood running down the edge
for yy, ln in ((18.0, 2.4), (11.0, 3.2)):
    m.box((X1 + 0.05, yy - ln, C - 0.34), (X1 + 0.45, yy, C + 0.34), 'gore', glow=8)

# the meat hook hanging through the hole on a ring
hx, hy = HOLE
m.ring((hx, hy, C), 1.5, 0.45, 'iron', axis='z', angle=0) if False else None
for zz in (-1, 1):                                   # the ring through the hole: two side bars + top/bottom
    m.box((hx - 0.3, hy - 1.6, C + zz * 1.6 - 0.3), (hx + 0.3, hy + 1.6, C + zz * 1.6 + 0.3), 'iron')
m.box((hx - 0.3, hy + 1.2, C - 1.9), (hx + 0.3, hy + 1.7, C + 1.9), 'iron')
m.box((hx - 0.3, hy - 1.7, C - 1.9), (hx + 0.3, hy - 1.2, C + 1.9), 'iron')
# the hook itself hangs off the front of the ring, curling back up at the bottom
m.box((hx - 0.3, hy - 6.2, C + 1.5), (hx + 0.3, hy - 1.6, C + 2.1), 'iron')
m.box((hx - 0.3, hy - 6.8, C + 1.5), (hx + 0.3, hy - 6.2, C + 4.0), 'iron')
m.box((hx - 0.3, hy - 6.8, C + 3.4), (hx + 0.3, hy - 4.6, C + 4.0), 'iron')
m.box((hx - 0.22, hy - 4.8, C + 3.45), (hx + 0.22, hy - 4.0, C + 3.95), 'edge')

if __name__ == '__main__':
    print(m.write(sys.argv[1], sys.argv[2], grip_y=0.0, tp_scale=0.66, fp_scale=0.5), 'elements')
