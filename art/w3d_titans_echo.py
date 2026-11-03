"""Titan's Echo (3D): a colossal stone greatsword with bronze edges. A molten core runs up the blade, a massive bronze
crossguard with a lava gem, a caged molten pommel, broken rock fragments float around the blade."""
import sys, math
from model3d import *

m = Model("titans_echo")
m.region('stone', (0, 0, 8, 8), cracked(STONE, MAGMA, density=0.03, base=0.38))
m.region('bronze', (8, 0, 12, 4), sheen(BRONZE, 0.35, 0.95))
m.region('magma', (12, 0, 16, 4), flow(MAGMA, speed=3, base=0.5))
m.region('grip', (8, 4, 12, 8), wrap(LEATHER))
m.region('rock', (12, 4, 16, 8), veined(STONE, MAGMA, 0.1))
m.region('plate', (0, 8, 8, 12), plate(BRONZE, 0.42))
m.region('dark', (8, 8, 12, 12), plate(STONE, 0.22, rivets=False))
m.region('flame', (0, 12, 8, 16), flame(MAGMA))
R = m.rot

# pommel: molten core in a bronze cage
m.box((C - 1.1, -8.6, C - 1.1), (C + 1.1, -6.2, C + 1.1), 'magma', glow=14)
for dx in (-1, 1):
    for dz in (-1, 1):
        m.box((C + dx * 1.3 - 0.3, -9.0, C + dz * 1.3 - 0.3), (C + dx * 1.3 + 0.3, -5.8, C + dz * 1.3 + 0.3), 'bronze')
m.box((C - 1.6, -9.6, C - 1.6), (C + 1.6, -9.0, C + 1.6), 'plate')
m.box((C - 1.6, -5.8, C - 1.6), (C + 1.6, -5.2, C + 1.6), 'plate')
m.box((C - 0.6, -11.0, C - 0.6), (C + 0.6, -9.6, C + 0.6), 'bronze', rot=R('y', 45, (C, -10, C)))

# grip
m.box((C - 0.9, -5.2, C - 0.9), (C + 0.9, 6.0, C + 0.9), 'grip')
for yy in (-2.0, 1.5, 4.5):
    m.box((C - 1.1, yy - 0.25, C - 1.1), (C + 1.1, yy + 0.25, C + 1.1), 'bronze')

# crossguard: a heavy bronze bar, ends sweeping up, a lava gem in the middle
m.box((C - 6.0, 6.0, C - 1.5), (C + 6.0, 8.0, C + 1.5), 'plate')
m.box((C - 2.2, 5.4, C - 1.8), (C + 2.2, 8.6, C + 1.8), 'plate')
m.box((C - 1.0, 6.2, C - 1.95), (C + 1.0, 8.2, C + 1.95), 'magma', glow=15)
for dx in (-1, 1):
    ex = C + dx * 6.0
    m.box((ex - 0.9, 6.0, C - 1.2), (ex + 0.9, 10.2, C + 1.2), 'plate', rot=R('z', -22.5 * dx, (ex, 6.0, C)))
    m.box((ex + dx * 1.0 - 0.5, 9.4, C - 0.7), (ex + dx * 1.0 + 0.5, 11.8, C + 0.7), 'bronze',
          rot=R('z', -45 * dx, (ex + dx * 1.0, 9.4, C)))

# blade: stone body + bronze edges + the molten core; the tip narrows in steps
B0, B1, TIP = 8.0, 25.0, 31.6
W = 2.7
m.box((C - W, B0, C - 0.6), (C + W, B1, C + 0.6), 'stone', faces=('north', 'south'),
      uv=m.surface('stone', C - W, C + W, B0, B1, C - W, C + W, B0, B1 + 2))
for dx in (-1, 1):
    a, b = (C - W - 0.8, C - W) if dx < 0 else (C + W, C + W + 0.8)
    m.box((a, B0, C - 0.32), (b, B1, C + 0.32), 'bronze')
m.box((C - 0.55, B0 + 0.6, C - 0.68), (C + 0.55, B1 - 0.4, C + 0.68), 'magma', glow=15)
steps = 7
for i in range(steps):
    y0 = B1 + i * (TIP - B1) / steps
    y1 = B1 + (i + 1) * (TIP - B1) / steps
    w = W * (1 - (i + 0.5) / steps) + 0.2
    m.box((C - w, y0, C - 0.55), (C + w, y1, C + 0.55), 'stone', faces=('north', 'south', 'up'),
          uv=m.surface('stone', C - w, C + w, y0, y1, C - W, C + W, B0, B1 + 2))
    m.box((C - w - 0.6, y0, C - 0.28), (C - w, y1, C + 0.28), 'bronze', faces=('north', 'south', 'west', 'up'))
    m.box((C + w, y0, C - 0.28), (C + w + 0.6, y1, C + 0.28), 'bronze', faces=('north', 'south', 'east', 'up'))
m.box((C - 0.4, B1 - 0.4, C - 0.66), (C + 0.4, B1 + 3.6, C + 0.66), 'magma', glow=15)
# fuller ridges flanking the molten core
for dx in (-1, 1):
    m.box((C + dx * 1.3 - 0.3, B0 + 1.5, C - 0.66), (C + dx * 1.3 + 0.3, B1 - 1.5, C + 0.66), 'dark')

# floating rock fragments orbiting the blade, and heat rising off the guard
for x, yy, z, s, ax, ang in [(C - 5.6, 14.0, C + 2.2, 1.5, 'x', 45), (C + 5.4, 18.5, C - 2.0, 1.2, 'z', 22.5),
                             (C - 4.8, 23.0, C - 2.4, 1.0, 'y', 45), (C + 4.6, 27.6, C + 1.8, 0.9, 'x', -22.5),
                             (C + 6.4, 11.4, C + 1.4, 0.8, 'z', 45)]:
    m.box((x - s / 2, yy - s / 2, z - s / 2), (x + s / 2, yy + s / 2, z + s / 2), 'rock', rot=R(ax, ang, (x, yy, z)), glow=6)
m.plane((C - 3.0, 8.4, 0), (C + 3.0, 13.4, 0), 'flame', origin=(C, 8.4, C))

if __name__ == '__main__':
    print(m.write(sys.argv[1], sys.argv[2], grip_y=0.5, tp_scale=0.62, fp_scale=0.46), 'elements')
