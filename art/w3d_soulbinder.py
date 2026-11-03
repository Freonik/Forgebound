"""Soulbinder (3D): a reaper's scythe. A long dark haft with silver bands, a ghostly blade curving off the top,
soul fire licking along it and lost souls drifting around."""
import sys, math
from model3d import *

GHOST = [(10, 40, 60), (20, 80, 110), (50, 140, 170), (110, 200, 220), (190, 240, 250), (245, 255, 255)]
SOULC = [(10, 60, 80), (30, 140, 170), (90, 220, 240), (190, 250, 255), (250, 255, 255)]
SHADE = [(6, 6, 10), (14, 14, 22), (26, 26, 38), (40, 40, 56)]

m = Model('soulbinder')
m.region('blade', (0, 0, 8, 8), cracked(GHOST, SOULC, density=0.02, base=0.5))
m.region('edge', (8, 0, 12, 4), sheen(GHOST, 0.55, 0.99))
m.region('soul', (12, 0, 16, 4), flow(SOULC, speed=3, base=0.5))
m.region('haft', (8, 4, 12, 8), wrap(SHADE))
m.region('silver', (12, 4, 16, 8), sheen(STEEL, 0.4, 0.95))
m.region('flame', (0, 8, 8, 16), flame(SOULC))
m.region('dark', (8, 8, 12, 12), plate(SHADE, 0.4, rivets=False))
R = m.rot
m.turn = False      # built with the blade on -x already, like the vanilla hoe

# long haft, silver bands, a soul gem at the bottom
m.box((C - 0.75, -9.0, C - 0.75), (C + 0.75, 26.0, C + 0.75), 'haft')
for yy in (-8.4, -3.0, 4.0, 12.0, 20.0):
    m.box((C - 0.95, yy - 0.3, C - 0.95), (C + 0.95, yy + 0.3, C + 0.95), 'silver')
m.box((C - 0.6, -11.0, C - 0.6), (C + 0.6, -9.0, C + 0.6), 'soul', rot=R('y', 45, (C, -10, C)), glow=13)
# a grip spur halfway down the haft
m.box((C + 0.6, 6.4, C - 0.4), (C + 2.6, 7.2, C + 0.4), 'dark')

# head collar
m.box((C - 1.3, 24.4, C - 1.3), (C + 1.3, 26.6, C + 1.3), 'dark')
m.box((C - 0.6, 26.6, C - 0.6), (C + 0.6, 28.4, C + 0.6), 'silver', rot=R('y', 45, (C, 27.5, C)))


def segment(px, py, length, w_in, w_out, angle):
    """A straight piece of the scythe blade, built along -x from its pivot then turned by `angle` (about z).
    The cutting edge is on the lower side. Returns the end point."""
    rot = R('z', angle, (px, py, C)) if angle else None
    x0, x1 = px - length, px + 0.3
    m.box((x0, py - w_out, C - 0.45), (x1, py + w_in, C + 0.45), 'blade', rot=rot,
          uv=m.surface('blade', x0, x1, py - w_out, py + w_in, x0, x1, py - 2.5, py + 2.5))
    m.box((x0, py - w_out - 0.6, C - 0.22), (x1, py - w_out, C + 0.22), 'edge', rot=rot, glow=8)
    a = math.radians(angle)
    return px - math.cos(a) * length, py - math.sin(a) * length


# the blade sweeps out from the collar and curves down
x, y = segment(C, 25.4, 8.0, 1.2, 2.2, 0)
x, y = segment(x, y, 6.0, 1.0, 1.7, 22.5)
x, y = segment(x, y, 5.0, 0.7, 1.1, 45)
m.box((x - 1.0, y - 1.4, C - 0.25), (x + 0.4, y + 0.2, C + 0.25), 'edge', rot=R('z', 45, (x, y, C)), glow=8)
# soul fire licking along the top of the blade
m.plane((C - 10.5, 26.2, 0), (C - 2.0, 31.6, 0), 'flame', origin=(C - 6.2, 26.2, C))
# lost souls drifting round the blade
for sx, sy, sz in ((C - 4.0, 19.6, C + 2.2), (C + 3.0, 28.4, C - 1.8), (C - 15.0, 20.0, C - 1.4)):
    m.box((sx - 0.45, sy - 0.45, sz - 0.45), (sx + 0.45, sy + 0.45, sz + 0.45), 'soul', glow=15)
    m.box((sx - 0.2, sy - 1.3, sz - 0.2), (sx + 0.2, sy - 0.45, sz + 0.2), 'soul', glow=15)

if __name__ == '__main__':
    print(m.write(sys.argv[1], sys.argv[2], grip_y=0.0, tp_scale=0.6, fp_scale=0.45), 'elements')
