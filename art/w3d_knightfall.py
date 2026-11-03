"""Knightfall (3D): a knight's morning star. A steel ball bristling with spikes on every side, a ruby core burning in
it, a gold crown collar, a red and gold haft and a ruby pommel."""
import sys, math
from model3d import *

RUBY = [(60, 0, 10), (130, 10, 30), (200, 30, 50), (255, 90, 90), (255, 190, 180)]
NAVY = [(14, 16, 28), (30, 34, 54), (56, 62, 90), (96, 104, 136), (150, 158, 186), (210, 216, 236), (250, 252, 255)]
CRIMSON = [(30, 4, 8), (70, 10, 18), (120, 20, 30), (170, 36, 44)]

m = Model('knightfall')
m.region('steel', (0, 0, 8, 8), plate(NAVY, 0.42))
m.region('spike', (8, 0, 12, 4), sheen(NAVY, 0.4, 0.98))
m.region('ruby', (12, 0, 16, 4), flow(RUBY, speed=2, base=0.5))
m.region('red', (8, 4, 12, 8), wrap(CRIMSON))
m.region('gold', (12, 4, 16, 8), sheen(GOLD, 0.35, 0.95))
m.region('gplate', (0, 8, 8, 12), plate(GOLD, 0.45))
R = m.rot

# pommel: a ruby set in gold
m.box((C - 1.3, -7.2, C - 1.3), (C + 1.3, -5.6, C + 1.3), 'gplate')
m.box((C - 0.8, -8.6, C - 0.8), (C + 0.8, -7.0, C + 0.8), 'ruby', rot=R('y', 45, (C, -7.8, C)), glow=12)
m.box((C - 0.35, -9.6, C - 0.35), (C + 0.35, -8.6, C + 0.35), 'gold', rot=R('y', 45, (C, -9, C)))

# haft: red wrap with gold spirals and bands
m.box((C - 0.85, -5.6, C - 0.85), (C + 0.85, 17.0, C + 0.85), 'red')
for i in range(8):
    yy = -4.6 + i * 2.6
    m.box((C - 1.0, yy, C - 1.0), (C + 1.0, yy + 0.5, C + 1.0), 'gold', rot=R('y', 45 if i % 2 else 0, (C, yy, C)))
m.box((C - 1.25, 16.4, C - 1.25), (C + 1.25, 17.4, C + 1.25), 'gplate')

# crown collar under the head: gold points
m.box((C - 2.0, 17.2, C - 2.0), (C + 2.0, 18.4, C + 2.0), 'gplate')
for k in range(4):
    a = k * 90
    dx, dz = math.cos(math.radians(a)) * 1.7, math.sin(math.radians(a)) * 1.7
    m.box((C + dx - 0.35, 18.2, C + dz - 0.35), (C + dx + 0.35, 19.6, C + dz + 0.35), 'gold', rot=R('y', 45, (C + dx, 19, C + dz)))

# the ball
HY = 23.0
r = 4.6
m.ball((C, HY, C), r, 'steel')
# ruby core showing through a cross-shaped band on four sides
m.box((C - 1.3, HY - 1.3, C - r * 1.16), (C + 1.3, HY + 1.3, C + r * 1.16), 'ruby', glow=15)
m.box((C - r * 1.16, HY - 1.3, C - 1.3), (C + r * 1.16, HY + 1.3, C + 1.3), 'ruby', glow=15)
# gold rims round the gems
for (fx, fz) in ((0, 1), (0, -1), (1, 0), (-1, 0)):
    cx, cz = C + fx * r * 1.15, C + fz * r * 1.15
    if fx == 0:
        m.box((cx - 1.7, HY - 1.7, cz - 0.2), (cx + 1.7, HY - 1.3, cz + 0.2), 'gold')
        m.box((cx - 1.7, HY + 1.3, cz - 0.2), (cx + 1.7, HY + 1.7, cz + 0.2), 'gold')
    else:
        m.box((cx - 0.2, HY - 1.7, cz - 1.7), (cx + 0.2, HY - 1.3, cz + 1.7), 'gold')
        m.box((cx - 0.2, HY + 1.3, cz - 1.7), (cx + 0.2, HY + 1.7, cz + 1.7), 'gold')


def spike(x, y, z, axis, ang, length=3.2, w=1.0):
    """A spike sticking straight out along +y from (x, y, z), turned about `axis`."""
    m.box((x - w, y, z - w), (x + w, y + length * 0.55, z + w), 'spike', rot=R(axis, ang, (x, y, z)))
    m.box((x - w * 0.5, y + length * 0.55, z - w * 0.5), (x + w * 0.5, y + length, z + w * 0.5), 'spike',
          rot=R(axis, ang, (x, y, z)))


# spikes: top, the four sides, and the eight diagonals of the upper/lower half
spike(C, HY + r * 0.9, C, 'y', 45, 4.2)
for ang in (45, -45):
    spike(C, HY, C, 'x', ang, r + 4.2)      # out the front/back diagonals
    spike(C, HY, C, 'z', ang, r + 4.2)      # out the side diagonals
# straight sideways spikes: build them along y and turn 90 = two 45 turns are not allowed, so use boxes along x / z
for dx in (-1, 1):
    x0 = C + dx * (r * 1.05)
    m.box((min(x0, x0 + dx * 2.0), HY - 1.0, C - 1.0), (max(x0, x0 + dx * 2.0), HY + 1.0, C + 1.0), 'spike')
    m.box((min(x0 + dx * 2.0, x0 + dx * 3.8), HY - 0.5, C - 0.5), (max(x0 + dx * 2.0, x0 + dx * 3.8), HY + 0.5, C + 0.5), 'spike')
for dz in (-1, 1):
    z0 = C + dz * (r * 1.05)
    m.box((C - 0.8, HY + 2.4 - 0.8, min(z0, z0 + dz * 2.6)), (C + 0.8, HY + 2.4 + 0.8, max(z0, z0 + dz * 2.6)), 'spike')
    m.box((C - 0.8, HY - 2.4 - 0.8, min(z0, z0 + dz * 2.6)), (C + 0.8, HY - 2.4 + 0.8, max(z0, z0 + dz * 2.6)), 'spike')

if __name__ == '__main__':
    print(m.write(sys.argv[1], sys.argv[2], grip_y=0.0, tp_scale=0.6, fp_scale=0.45), 'elements')
