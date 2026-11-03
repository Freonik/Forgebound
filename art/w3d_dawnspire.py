"""Dawnspire (3D): a staff of light. A white-marble shaft banded with gold, a glowing prism crystal held in a gold
cradle, a sun halo of rays behind it, light rising off it like flame, prism shards floating around."""
import sys, math
from model3d import *

MARBLE = [(170, 164, 150), (206, 200, 186), (232, 228, 216), (248, 246, 238), (255, 255, 252)]
PRISM = [(255, 150, 200), (255, 200, 230), (220, 240, 255), (255, 250, 210), (255, 255, 255)]
SUN = [(200, 130, 20), (250, 190, 50), (255, 226, 120), (255, 248, 210), (255, 255, 255)]


def marble(px, r, f, n):
    """White marble with faint gold veins."""
    x0, y0, x1, y1 = r
    for y in range(y0, y1):
        for x in range(x0, x1):
            lx, ly = x - x0, y - y0
            c = ramp(MARBLE, 0.55 + 0.25 * NZ[y][x] * NZ2[y][x])
            if abs(math.sin(lx * 0.35 + math.sin(ly * 0.5) * 2.0)) < 0.05:
                c = ramp(GOLD, 0.6)
            px[x, y] = c + (255,)


m = Model('dawnspire')
m.region('marble', (0, 0, 8, 8), marble)
m.region('gold', (8, 0, 12, 4), sheen(GOLD, 0.35, 0.98))
m.region('prism', (12, 0, 16, 4), flow(PRISM, speed=2, base=0.55))
m.region('sun', (8, 4, 12, 8), flow(SUN, speed=3, base=0.6))
m.region('gplate', (12, 4, 16, 8), plate(GOLD, 0.5))
m.region('light', (0, 8, 8, 16), flame(SUN))
m.region('shard', (8, 8, 12, 12), sheen(PRISM, 0.4, 0.98))
R = m.rot
m.turn = False      # symmetric: keep the halo behind the prism

# pommel: a small sun gem in gold
m.box((C - 1.2, -9.6, C - 1.2), (C + 1.2, -8.4, C + 1.2), 'gplate')
m.box((C - 0.8, -11.0, C - 0.8), (C + 0.8, -9.6, C + 0.8), 'sun', rot=R('y', 45, (C, -10.3, C)), glow=14)
# shaft: marble with gold bands, a wrapped grip of gold thread
m.box((C - 0.75, -8.4, C - 0.75), (C + 0.75, 19.0, C + 0.75), 'marble')
for yy in (-8.0, -5.0, 5.0, 9.0, 13.0, 16.5):
    m.box((C - 0.95, yy - 0.3, C - 0.95), (C + 0.95, yy + 0.3, C + 0.95), 'gold')
m.box((C - 0.9, -4.6, C - 0.9), (C + 0.9, 4.6, C + 0.9), 'sun')                 # a grip wound with gold thread
for yy in (-4.6, -1.6, 1.4, 4.4):
    m.box((C - 1.05, yy, C - 1.05), (C + 1.05, yy + 0.3, C + 1.05), 'gold')

# head: a gold cradle of two curved arms holding the prism
HY = 24.0
m.box((C - 1.4, 18.6, C - 1.4), (C + 1.4, 20.2, C + 1.4), 'gplate')
for side in (-1, 1):
    x, y = C + side * 1.0, 19.6
    for ang, ln in ((22.5, 3.4), (0, 2.6), (-22.5, 2.8), (-45, 1.8)):
        a = -ang * side
        m.box((x - 0.45, y, C - 0.45), (x + 0.45, y + ln, C + 0.45), 'gold', rot=R('z', a, (x, y, C)) if ang else None)
        r = math.radians(ang)
        x, y = x + side * math.sin(r) * ln, y + math.cos(r) * ln
# the prism: a crystal turned two ways, glowing
m.box((C - 1.5, HY - 2.6, C - 1.5), (C + 1.5, HY + 2.6, C + 1.5), 'prism', rot=R('y', 45, (C, HY, C)), glow=15)
m.box((C - 1.0, HY - 3.6, C - 1.0), (C + 1.0, HY + 3.6, C + 1.0), 'shard', rot=R('y', 45, (C, HY, C)), glow=12)
m.box((C - 0.5, HY + 3.6, C - 0.5), (C + 0.5, HY + 4.8, C + 0.5), 'shard', rot=R('y', 45, (C, HY, C)), glow=12)

# sun halo behind the prism: a ring and rays (in the x-y plane, just behind)
Z = C - 1.9
for k in range(8):
    ang = k * 45
    cx = C + math.cos(math.radians(ang + 90)) * 4.6
    cy = HY + math.sin(math.radians(ang + 90)) * 4.6
    m.box((cx - 0.3, cy - 1.4, Z - 0.15), (cx + 0.3, cy + 1.4, Z + 0.15), 'sun',
          rot=R('z', ((ang + 45) % 90) - 45 if ang % 90 else 0, (cx, cy, Z)) if ang % 90 else None, glow=15)
for (a, b) in (((C - 3.4, HY + 3.2), (C + 3.4, HY + 3.6)), ((C - 3.4, HY - 3.6), (C + 3.4, HY - 3.2))):
    m.box((a[0], a[1], Z - 0.12), (b[0], b[1], Z + 0.12), 'gold')
for x0, x1 in ((C - 3.6, C - 3.2), (C + 3.2, C + 3.6)):
    m.box((x0, HY - 3.4, Z - 0.12), (x1, HY + 3.4, Z + 0.12), 'gold')

# light rising off the head, prism shards drifting round it
m.plane((C - 3.5, HY + 1.0, 0), (C + 3.5, HY + 7.6, 0), 'light', origin=(C, HY + 1.0, C))
for x, yy, z, s, ax, ang in [(C - 6.4, HY + 2.4, C + 1.4, 0.7, 'y', 45), (C + 6.2, HY - 1.6, C - 1.6, 0.6, 'x', 45),
                             (C + 4.4, HY + 5.6, C + 2.0, 0.5, 'z', 45), (C - 4.8, HY - 4.4, C - 1.8, 0.55, 'y', 45)]:
    m.box((x - s / 2, yy - s, z - s / 2), (x + s / 2, yy + s, z + s / 2), 'shard', rot=R(ax, ang, (x, yy, z)), glow=12)

if __name__ == '__main__':
    print(m.write(sys.argv[1], sys.argv[2], grip_y=0.0, tp_scale=0.6, fp_scale=0.45), 'elements')
