"""Echo Blade (3D): a teal crystal-steel longsword. A window through the blade shows living sculk glowing inside,
sculk tendrils curl off the crossguard, two halos of echo float around the blade."""
import sys, math
from model3d import *

TEAL = [(6, 22, 26), (12, 44, 52), (22, 74, 84), (40, 112, 124), (70, 160, 170), (130, 214, 220), (210, 250, 250)]
SCULK = [(2, 20, 28), (6, 50, 66), (10, 100, 120), (40, 190, 210), (140, 245, 250), (230, 255, 255)]
DARKS = [(6, 10, 14), (12, 20, 28), (20, 32, 42), (32, 48, 60)]

m = Model('echo_blade')
m.region('steel', (0, 0, 8, 8), cracked(TEAL, SCULK, density=0.015, base=0.42))
m.region('edge', (8, 0, 12, 4), sheen(TEAL, 0.45, 0.98))
m.region('sculk', (12, 0, 16, 4), flow(SCULK, speed=2, base=0.45))
m.region('grip', (8, 4, 12, 8), wrap(DARKS))
m.region('dark', (12, 4, 16, 8), plate(DARKS, 0.4, rivets=False))
m.region('guard', (0, 8, 8, 12), plate(TEAL, 0.3))
m.region('echo', (8, 8, 12, 12), flow(SCULK, speed=4, base=0.6))
R = m.rot

# pommel: a sculk gem in a dark cage
m.box((C - 1.0, -7.4, C - 1.0), (C + 1.0, -5.6, C + 1.0), 'sculk', rot=R('y', 45, (C, -6.5, C)), glow=13)
m.box((C - 1.3, -6.0, C - 1.3), (C + 1.3, -5.4, C + 1.3), 'guard')
m.box((C - 0.5, -8.6, C - 0.5), (C + 0.5, -7.2, C + 0.5), 'edge', rot=R('y', 45, (C, -8, C)))

# grip
m.box((C - 0.8, -5.4, C - 0.8), (C + 0.8, 5.4, C + 0.8), 'grip')
for yy in (-2.6, 0.0, 2.6):
    m.box((C - 0.95, yy - 0.2, C - 0.95), (C + 0.95, yy + 0.2, C + 0.95), 'edge')

# crossguard with sculk tendrils curling up off its ends
m.box((C - 4.6, 5.4, C - 1.1), (C + 4.6, 7.0, C + 1.1), 'guard')
m.box((C - 1.4, 5.0, C - 1.3), (C + 1.4, 7.6, C + 1.3), 'dark')
m.box((C - 0.7, 5.6, C - 1.4), (C + 0.7, 7.2, C + 1.4), 'sculk', glow=15)
for dx in (-1, 1):
    ex = C + dx * 4.6
    m.box((ex - 0.45, 5.6, C - 0.45), (ex + 0.45, 9.0, C + 0.45), 'dark', rot=R('z', -22.5 * dx, (ex, 5.6, C)))
    m.box((ex + dx * 1.2 - 0.3, 8.4, C - 0.3), (ex + dx * 1.2 + 0.3, 10.4, C + 0.3), 'sculk',
          rot=R('z', 22.5 * dx, (ex + dx * 1.2, 8.4, C)), glow=15)
    m.box((ex + dx * 0.4 - 0.25, 9.8, C - 0.25), (ex + dx * 0.4 + 0.25, 11.0, C + 0.25), 'sculk', glow=15)

# blade: two rails of steel around a window of sculk, a bright edge, a tapering tip
B0, B1, TIP = 7.0, 26.0, 31.4
W = 2.0
for dx in (-1, 1):
    a, b = (C - W, C - 0.7) if dx < 0 else (C + 0.7, C + W)
    m.box((a, B0, C - 0.5), (b, B1, C + 0.5), 'steel', uv=m.surface('steel', a, b, B0, B1, C - W, C + W, B0, B1))
    e0, e1 = (C - W - 0.7, C - W) if dx < 0 else (C + W, C + W + 0.7)
    m.box((e0, B0, C - 0.25), (e1, B1, C + 0.25), 'edge')
m.box((C - 0.7, B0, C - 0.2), (C + 0.7, B1, C + 0.2), 'sculk', glow=15)
for yy in (B0, B1 - 1.0):
    m.box((C - 0.7, yy, C - 0.5), (C + 0.7, yy + 1.0, C + 0.5), 'steel')
steps = 6
for i in range(steps):
    y0 = B1 + i * (TIP - B1) / steps
    y1 = B1 + (i + 1) * (TIP - B1) / steps
    w = W * (1 - (i + 0.5) / steps) + 0.15
    m.box((C - w, y0, C - 0.45), (C + w, y1, C + 0.45), 'steel', faces=('north', 'south', 'up'))
    m.box((C - w - 0.55, y0, C - 0.22), (C - w, y1, C + 0.22), 'edge', faces=('north', 'south', 'west', 'up'))
    m.box((C + w, y0, C - 0.22), (C + w + 0.55, y1, C + 0.22), 'edge', faces=('north', 'south', 'east', 'up'))

# echo halos floating round the blade, and sparks of sculk
m.ring((C, 13.0, C), 3.2, 0.32, 'echo', glow=15, axis='y', angle=22.5)
m.ring((C, 20.5, C), 2.6, 0.28, 'echo', glow=15, axis='y', angle=-22.5)
for x, yy, z, s in [(C - 3.6, 16.6, C + 1.6, 0.35), (C + 3.4, 23.6, C - 1.4, 0.3), (C + 2.8, 10.0, C + 2.2, 0.3)]:
    m.box((x - s, yy - s, z - s), (x + s, yy + s, z + s), 'echo', rot=R('z', 45, (x, yy, z)), glow=15)

if __name__ == '__main__':
    print(m.write(sys.argv[1], sys.argv[2], grip_y=0.0, tp_scale=0.66, fp_scale=0.5), 'elements')
