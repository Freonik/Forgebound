"""Valkyrie's Lance (3D): an ivory lance wound with gold. A long leaf-shaped head with a core of light, a pair of
white wings spreading from the socket, a halo above them. Writes valkyries_lance (inventory, ground, frame, thrown)
and valkyries_lance_in_hand (held like a vanilla spear)."""
import sys, math
from model3d import *

IVORY = [(130, 120, 100), (180, 170, 146), (220, 212, 190), (244, 238, 222), (255, 252, 244)]
LIGHT = [(200, 150, 40), (255, 210, 90), (255, 240, 170), (255, 252, 230), (255, 255, 255)]
FEATHER = [(150, 150, 160), (196, 196, 206), (230, 230, 238), (250, 250, 255)]


def feathers(px, r, f, n):
    """White feathers: a quill line with barbs, darker tips."""
    x0, y0, x1, y1 = r
    for y in range(y0, y1):
        for x in range(x0, x1):
            lx, ly = x - x0, y - y0
            t = 0.65 + 0.2 * math.sin(lx * 0.9 + ly * 1.7) * 0.5 + 0.1 * NZ[y][x]
            if lx % 8 in (0, 1):
                t = 0.95
            if ly > (y1 - y0) - 4:
                t -= 0.3
            px[x, y] = ramp(FEATHER, t) + (255,)


m = Model('valkyries_lance')
m.region('ivory', (0, 0, 8, 4), sheen(IVORY, 0.35, 0.95))
m.region('gold', (8, 0, 12, 4), sheen(GOLD, 0.35, 0.98))
m.region('light', (12, 0, 16, 4), flow(LIGHT, speed=2, base=0.6))
m.region('steel', (0, 4, 8, 8), sheen(STEEL, 0.45, 0.99))
m.region('feather', (8, 4, 16, 12), feathers)
m.region('gplate', (0, 8, 8, 12), plate(GOLD, 0.5))
R = m.rot

# butt: a gold cap and spike
m.box((C - 1.0, -14.0, C - 1.0), (C + 1.0, -12.6, C + 1.0), 'gplate')
m.box((C - 0.5, -15.8, C - 0.5), (C + 0.5, -14.0, C + 0.5), 'gold', rot=R('y', 45, (C, -15, C)))
# shaft: ivory with a gold spiral
m.box((C - 0.7, -12.6, C - 0.7), (C + 0.7, 18.0, C + 0.7), 'ivory')
for i in range(15):
    yy = -11.6 + i * 2.0
    m.box((C - 0.85, yy, C - 0.85), (C + 0.85, yy + 0.45, C + 0.85), 'gold', rot=R('y', 45 if i % 2 else 0, (C, yy, C)))
# grip guard (a vamplate disc)
m.box((C - 2.0, 3.2, C - 2.0), (C + 2.0, 3.8, C + 2.0), 'gplate')
m.box((C - 2.0, 3.2, C - 2.0), (C + 2.0, 3.8, C + 2.0), 'gplate', rot=R('y', 45, (C, 3.5, C)))

# socket and wings
m.box((C - 1.2, 17.0, C - 1.2), (C + 1.2, 19.6, C + 1.2), 'gplate')
for dx in (-1, 1):
    # three feathered tiers per wing, swept up and out
    for k, (ln, hgt, ang) in enumerate(((8.0, 2.2, 22.5), (6.6, 1.8, 45), (4.8, 1.5, 45))):
        x0 = C + dx * 1.0
        y0 = 17.6 + k * 1.3
        a, b = (x0, x0 + dx * ln) if dx > 0 else (x0 + dx * ln, x0)
        m.box((a, y0, C - 0.2), (b, y0 + hgt, C + 0.2), 'feather', rot=R('z', ang * dx, (x0, y0, C)))
m.ring((C, 21.0, C), 2.6, 0.3, 'light', glow=15, axis='y', angle=45)

# head: a long leaf of steel with a core of light and gold at the base
H0, H1 = 19.6, 31.6
for i in range(12):
    y0 = H0 + i * (H1 - H0) / 12
    y1 = y0 + (H1 - H0) / 12
    t = (i + 0.5) / 12
    w = 0.6 + 2.3 * math.sin(math.pi * min(1, t * 1.15)) ** 0.8 if t < 0.87 else 0.6 * (1 - t) / 0.13 + 0.15
    m.box((C - w, y0, C - 0.45), (C + w, y1, C + 0.45), 'steel', faces=('north', 'south', 'east', 'west', 'up'))
m.box((C - 0.35, H0 + 1.0, C - 0.52), (C + 0.35, H1 - 3.0, C + 0.52), 'light', glow=15)
m.box((C - 1.0, H0 - 0.2, C - 0.6), (C + 1.0, H0 + 1.0, C + 0.6), 'gold')

inhand = m.variant('valkyries_lance_in_hand')
inhand.elements = m.elements

if __name__ == '__main__':
    m.write_texture(sys.argv[1])
    d = m.write_model(sys.argv[2], grip_y=0.0, tp_scale=0.55, fp_scale=0.45)
    d.update(m.spear_display(-4.0, 0.72, 0.6))
    inhand.write_model(sys.argv[2], 0, display=d)
    print(len(m.elements), 'elements')
