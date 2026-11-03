"""Chainbreaker (3D): a black iron war-axe. A chain spirals up the haft, the huge single bit is split by ember-red
cracks, a broken shackle hangs off the back with a glowing snapped end, embers drift off the edge."""
import sys, math
from model3d import *

m = Model('chainbreaker')
m.region('iron', (0, 0, 8, 8), cracked(IRON, EMBER, density=0.025, base=0.3))
m.region('edge', (8, 0, 12, 4), edge(IRON, 0.62))
m.region('ember', (12, 0, 16, 4), flow(EMBER, speed=2, base=0.5))
m.region('grip', (8, 4, 12, 8), wrap(LEATHER))
m.region('blade', (12, 4, 16, 8), sheen(IRON, 0.45, 0.92))
m.region('dark', (0, 8, 4, 12), plate(IRON, 0.18, rivets=False))
m.region('chain', (4, 8, 8, 12), edge(IRON, 0.5))
m.region('flame', (8, 8, 16, 16), flame(EMBER))
m.region('plate', (0, 12, 8, 16), plate(IRON, 0.3))
R = m.rot

# --- pommel: a heavy square shackle around the end of the haft, a short broken chain under it
m.box((C - 1.9, -10.2, C - 1.9), (C + 1.9, -9.4, C + 1.9), 'edge')
for dx, dz in ((-1, 0), (1, 0)):
    m.box((C + dx * 1.6 - 0.35, -9.4, C - 1.9), (C + dx * 1.6 + 0.35, -6.6, C + 1.9), 'chain')
m.box((C - 1.9, -6.6, C - 1.9), (C + 1.9, -6.0, C + 1.9), 'edge')
m.box((C - 0.7, -9.2, C - 0.7), (C + 0.7, -6.8, C + 0.7), 'ember', glow=12)
y = -10.2
for i in range(3):
    m.box((C - 0.5, y - 1.2, C - 0.16), (C + 0.5, y, C + 0.16), 'chain', rot=R('y', 45 if i % 2 == 0 else -45, (C, y, C)))
    y -= 0.95
m.box((C - 0.25, y - 0.3, C - 0.25), (C + 0.25, y + 0.2, C + 0.25), 'ember', glow=15)

# --- haft, leather grip, rings
m.box((C - 0.8, -6.0, C - 0.8), (C + 0.8, 18.5, C + 0.8), 'dark')
m.box((C - 1.0, -5.0, C - 1.0), (C + 1.0, 5.0, C + 1.0), 'grip')
for yy in (-5.3, 5.3, 16.0):
    m.box((C - 1.25, yy - 0.3, C - 1.25), (C + 1.25, yy + 0.3, C + 1.25), 'edge')

# --- a chain spiralling up the haft (links walk round the four sides)
sides = [(1.05, 0), (0, 1.05), (-1.05, 0), (0, -1.05)]
for i in range(11):
    dx, dz = sides[i % 4]
    yy = 6.2 + i * 0.9
    ang = 45 if i % 2 == 0 else -45
    cx, cz = C + dx, C + dz
    m.box((cx - 0.5, yy, cz - 0.17), (cx + 0.5, yy + 1.2, cz + 0.17), 'chain', rot=R('y', ang, (cx, yy, cz)))

# --- head
HY0, HY1 = 16.5, 30.5
MY = (HY0 + HY1) / 2
# socket around the haft
m.box((C - 1.6, HY0 + 1.0, C - 1.3), (C + 1.6, HY1 - 2.5, C + 1.3), 'plate', side='plate')
m.box((C - 1.8, HY0 + 1.0, C - 1.5), (C + 1.8, HY0 + 1.8, C + 1.5), 'edge')
m.box((C - 1.8, HY1 - 3.3, C - 1.5), (C + 1.8, HY1 - 2.5, C + 1.5), 'edge')
# the bit: a crescent built from 1-unit rows, iron body + bright edge + an ember groove
Z0, Z1 = C - 0.75, C + 0.75
for i in range(int(HY1 - HY0)):
    y0 = HY0 + i
    t = (y0 + 0.5 - MY) / ((HY1 - HY0) / 2)
    if abs(t) > 1:
        continue
    flare = abs(t) ** 1.6
    x0 = C + 1.6 + 6.5 * flare                    # narrow neck at the socket, flaring out to the edge
    x1 = C + 12.6 + 1.6 * (1 - t * t) + (1.4 * t * t if t < 0 else 0)   # convex edge, the beard hooks down
    thin = 0.25 * max(0.0, abs(t) - 0.6)
    m.box((x0, y0, Z0 + thin), (x1 - 1.1, y0 + 1, Z1 - thin), 'iron', faces=('north', 'south', 'up', 'down'),
          uv=m.surface('iron', x0, x1 - 1.1, y0, y0 + 1, C + 1.6, C + 14.6, HY0, HY1))
    m.box((x1 - 1.1, y0, C - 0.35), (x1, y0 + 1, C + 0.35), 'blade')
# ember cracks breaking through the bit (glowing inlays slightly proud of both faces)
def crack_path(pts, step=0.45):
    """small glowing cubes along a jagged polyline, a bit proud of both faces of the bit"""
    for (xa, ya), (xb, yb) in zip(pts, pts[1:]):
        n = max(1, int(math.hypot(xb - xa, yb - ya) / step))
        for k in range(n):
            x, yy = xa + (xb - xa) * k / n, ya + (yb - ya) * k / n
            m.box((x - 0.22, yy - 0.22, Z0 - 0.05), (x + 0.22, yy + 0.22, Z1 + 0.05), 'ember', glow=15)


crack_path([(C + 5.6, MY + 4.4), (C + 5.2, MY + 2.6), (C + 4.2, MY + 1.0), (C + 5.6, MY - 0.6), (C + 6.6, MY - 2.4),
            (C + 8.6, MY - 3.2), (C + 9.4, MY - 5.0)])
crack_path([(C + 5.6, MY - 0.6), (C + 7.6, MY + 0.4), (C + 9.6, MY - 0.2), (C + 11.0, MY + 1.0)])
crack_path([(C + 5.2, MY + 2.6), (C + 7.4, MY + 3.4), (C + 8.6, MY + 5.2)])

# back of the head: a short hammer poll with spikes and the broken shackle hanging off it
m.box((C - 4.6, MY - 1.6, C - 1.2), (C - 1.6, MY + 1.6, C + 1.2), 'plate')
m.box((C - 5.4, MY - 1.2, C - 0.9), (C - 4.6, MY + 1.2, C + 0.9), 'edge')
m.box((C - 7.4, MY + 1.0, C - 0.5), (C - 4.4, MY + 2.0, C + 0.5), 'blade', rot=R('z', -22.5, (C - 4.4, MY + 1.5, C)))
# the shackle: a U of three bars, one arm snapped (glowing)
SX, SY = C - 4.0, MY - 1.6
m.box((SX - 0.3, SY - 3.6, C - 0.3), (SX + 0.3, SY, C + 0.3), 'chain', rot=R('z', 22.5, (SX, SY, C)))
m.box((SX - 2.6, SY - 3.9, C - 0.3), (SX + 0.2, SY - 3.3, C + 0.3), 'chain', rot=R('z', 22.5, (SX, SY, C)))
m.box((SX - 2.6, SY - 3.9, C - 0.3), (SX - 2.0, SY - 2.0, C + 0.3), 'chain', rot=R('z', 22.5, (SX, SY, C)))
m.box((SX - 2.6, SY - 2.2, C - 0.32), (SX - 2.0, SY - 1.7, C + 0.32), 'ember', rot=R('z', 22.5, (SX, SY, C)), glow=15)

# top spike
m.box((C - 0.8, HY1 - 2.5, C - 0.8), (C + 0.8, HY1 + 0.3, C + 0.8), 'blade', rot=R('y', 45, (C, HY1, C)))
m.box((C - 0.35, HY1 + 0.3, C - 0.35), (C + 0.35, HY1 + 1.4, C + 0.35), 'edge', rot=R('y', 45, (C, HY1, C)))

# embers drifting off the edge, and a lick of flame along it
for x, yy, z, s in [(C + 13.2, MY + 4.2, C + 1.4, 0.45), (C + 14.4, MY - 1.0, C - 1.6, 0.35), (C + 12.0, MY - 5.6, C + 1.8, 0.4),
                    (C + 10.6, HY1 + 0.8, C - 1.2, 0.3), (C + 15.0, MY + 1.6, C + 0.6, 0.3)]:
    m.box((x - s, yy - s, z - s), (x + s, yy + s, z + s), 'ember', rot=R('z', 45, (x, yy, z)), glow=15)

if __name__ == '__main__':
    print(m.write(sys.argv[1], sys.argv[2], grip_y=0.0, tp_scale=0.62, fp_scale=0.46), 'elements')
