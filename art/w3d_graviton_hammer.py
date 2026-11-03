"""Graviton Hammer (3D): an octagonal war-hammer. Silver striking faces, a violet gravity core glowing through the
drum, gold bands, a ring and chunks of rock caught in orbit around the head."""
import sys, math
from model3d import *

SILVER = [(40, 42, 54), (80, 84, 100), (130, 134, 152), (180, 184, 200), (225, 228, 240), (252, 252, 255)]
NIGHT = [(10, 6, 22), (22, 14, 44), (38, 24, 72), (58, 38, 104), (84, 58, 140)]
GRAV = [(40, 10, 90), (90, 40, 180), (150, 100, 240), (210, 170, 255), (250, 236, 255)]

m = Model('graviton_hammer')
m.region('drum', (0, 0, 8, 8), cracked(NIGHT, GRAV, density=0.02, base=0.45))
m.region('silver', (8, 0, 12, 4), sheen(SILVER, 0.3, 0.95))
m.region('core', (12, 0, 16, 4), flow(GRAV, speed=3, base=0.5))
m.region('grip', (8, 4, 12, 8), wrap([(10, 6, 16), (24, 16, 34), (40, 28, 56), (60, 44, 80)]))
m.region('gold', (12, 4, 16, 8), sheen(GOLD, 0.35, 0.95))
m.region('haft', (0, 8, 8, 12), plate(NIGHT, 0.35, rivets=False))
m.region('rock', (8, 8, 12, 12), veined(STONE, GRAV, 0.1))
R = m.rot

# pommel
m.box((C - 1.2, -7.6, C - 1.2), (C + 1.2, -6.0, C + 1.2), 'gold')
m.box((C - 0.6, -8.8, C - 0.6), (C + 0.6, -7.6, C + 0.6), 'core', rot=R('y', 45, (C, -8.2, C)), glow=12)

# haft with gold bands and a wrapped grip
m.box((C - 0.8, -6.0, C - 0.8), (C + 0.8, 18.0, C + 0.8), 'haft')
m.box((C - 1.0, -5.0, C - 1.0), (C + 1.0, 4.6, C + 1.0), 'grip')
for yy in (-5.2, 4.8, 9.0, 13.0):
    m.box((C - 1.2, yy - 0.3, C - 1.2), (C + 1.2, yy + 0.3, C + 1.2), 'gold')

# head: an octagonal drum along x (a box and the same box turned 45 about x), silver faces, the core showing
HY = 21.5
L = 7.5      # half length of the drum
r = 3.6
m.box((C - L, HY - r, C - r), (C + L, HY + r, C + r), 'drum', side='drum', ends='drum')
m.box((C - L, HY - r, C - r), (C + L, HY + r, C + r), 'drum', rot=R('x', 45, (C, HY, C)))
for dx in (-1, 1):
    a, b = (C - L - 1.0, C - L) if dx < 0 else (C + L, C + L + 1.0)
    m.box((a, HY - r + 0.4, C - r + 0.4), (b, HY + r - 0.4, C + r - 0.4), 'silver')
    m.box((a, HY - r + 0.4, C - r + 0.4), (b, HY + r - 0.4, C + r - 0.4), 'silver', rot=R('x', 45, (C, HY, C)))
    m.box((a - 0.3 if dx < 0 else b, HY - 1.4, C - 1.4), (a if dx < 0 else b + 0.3, HY + 1.4, C + 1.4), 'core', glow=15)
    # gold bands around the drum
    bx = C + dx * (L - 1.4)
    m.box((bx - 0.5, HY - r - 0.25, C - r - 0.25), (bx + 0.5, HY + r + 0.25, C + r + 0.25), 'gold')
# the gravity core: a glowing window round the middle of the drum
m.box((C - 2.2, HY - r - 0.12, C - 1.4), (C + 2.2, HY + r + 0.12, C + 1.4), 'core', glow=15)
m.box((C - 2.2, HY - 1.4, C - r - 0.12), (C + 2.2, HY + 1.4, C + r + 0.12), 'core', glow=15)
# socket
m.box((C - 1.4, HY - r - 1.6, C - 1.4), (C + 1.4, HY - r, C + 1.4), 'gold')

# a ring in orbit (tilted) and rocks caught by the field
m.ring((C, HY, C), 6.4, 0.35, 'core', glow=15, axis='z', angle=22.5)
for x, yy, z, s, ax, ang in [(C - 6.0, HY + 6.4, C + 2.0, 1.2, 'x', 45), (C + 6.6, HY - 5.6, C - 1.8, 1.0, 'y', 45),
                             (C + 3.0, HY + 7.6, C - 2.6, 0.8, 'z', 22.5), (C - 4.4, HY - 6.6, C + 2.8, 0.9, 'x', -22.5)]:
    m.box((x - s / 2, yy - s / 2, z - s / 2), (x + s / 2, yy + s / 2, z + s / 2), 'rock', rot=R(ax, ang, (x, yy, z)), glow=6)

if __name__ == '__main__':
    print(m.write(sys.argv[1], sys.argv[2], grip_y=0.0, tp_scale=0.6, fp_scale=0.45), 'elements')
