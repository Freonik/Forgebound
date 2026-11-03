"""Stasis Hourglass (3D): gold end plates with blue time gems, four twisted pillars, two bulbs of real (translucent)
glass, golden sand piled in both halves with a glowing stream falling through the neck, runes of time floating
around it. Held like any flat item (it lives in the off hand)."""
import sys, math
from model3d import *

GLASS = [(150, 200, 230), (190, 225, 245), (230, 245, 255), (255, 255, 255)]
SAND = [(140, 96, 30), (196, 146, 54), (232, 190, 92), (252, 226, 150), (255, 246, 210)]
TIME = [(30, 90, 200), (60, 150, 240), (140, 210, 255), (230, 250, 255)]
WOODH = [(26, 14, 10), (46, 28, 18), (70, 44, 28), (96, 64, 42)]


def glass(px, r, f, n):
    """Pale glass: mostly see-through, a brighter streak and rim."""
    x0, y0, x1, y1 = r
    for y in range(y0, y1):
        for x in range(x0, x1):
            lx = x - x0
            streak = abs(lx - 4) < 1.5
            a = 150 if streak else (95 if lx in (0, x1 - x0 - 1) else 60)
            px[x, y] = ramp(GLASS, 0.9 if streak else 0.45) + (a,)


m = Model('stasis_hourglass')
m.region('gold', (0, 0, 4, 4), sheen(GOLD, 0.35, 0.98))
m.region('gplate', (4, 0, 12, 4), plate(GOLD, 0.5))
m.region('time', (12, 0, 16, 4), flow(TIME, speed=2, base=0.55))
m.region('sand', (0, 4, 8, 8), plate(SAND, 0.55, rivets=False))
m.region('stream', (8, 4, 12, 8), flow(SAND, speed=6, base=0.65))
m.region('glass', (12, 4, 16, 8), glass)
m.region('wood', (0, 8, 4, 12), wrap(WOODH))
R = m.rot

Y0, Y1 = 0.5, 15.5       # outer plates
MID = 8.0

# end plates: gold, a blue time gem set in each
for y0, y1 in ((Y0, Y0 + 1.2), (Y1 - 1.2, Y1)):
    m.box((C - 4.6, y0, C - 4.6), (C + 4.6, y1, C + 4.6), 'gplate')
    m.box((C - 4.0, y0 - 0.3 if y0 < 5 else y1, C - 4.0), (C + 4.0, y0 if y0 < 5 else y1 + 0.3, C + 4.0), 'gold')
for yy in (Y0 - 0.35, Y1 + 0.05):
    m.box((C - 0.8, yy, C - 0.8), (C + 0.8, yy + 0.3, C + 0.8), 'time', glow=15)
for dx in (-1, 1):
    m.box((C + dx * 4.6 - 0.2, Y0 + 0.2, C - 0.8), (C + dx * 4.6 + 0.2, Y0 + 1.0, C + 0.8), 'time', glow=15)
    m.box((C + dx * 4.6 - 0.2, Y1 - 1.0, C - 0.8), (C + dx * 4.6 + 0.2, Y1 - 0.2, C + 0.8), 'time', glow=15)

# four twisted pillars at the corners (alternating 45-degree segments)
for dx in (-1, 1):
    for dz in (-1, 1):
        x, z = C + dx * 3.6, C + dz * 3.6
        for k in range(6):
            y0 = Y0 + 1.2 + k * (Y1 - Y0 - 2.4) / 6
            y1 = y0 + (Y1 - Y0 - 2.4) / 6
            m.box((x - 0.45, y0, z - 0.45), (x + 0.45, y1, z + 0.45), 'wood' if k % 2 else 'gold',
                  rot=R('y', 45 if k % 2 else 0, (x, y0, z)))


def bulb(lower):
    """One glass bulb: rings narrowing toward the neck."""
    rows = 6
    for i in range(rows):
        t = (i + 0.5) / rows                       # 0 at the plate, 1 at the neck
        r = 2.9 * (1 - t ** 1.6) + 0.45
        h = (MID - 0.3 - (Y0 + 1.2)) / rows
        y0 = (Y0 + 1.2 + i * h) if lower else (Y1 - 1.2 - (i + 1) * h)
        m.box((C - r, y0, C - r), (C + r, y0 + h, C + r), 'glass', faces=('north', 'south', 'east', 'west'))
    return


bulb(True)
bulb(False)
m.box((C - 0.5, MID - 0.3, C - 0.5), (C + 0.5, MID + 0.3, C + 0.5), 'gold')          # the neck band

# sand: a pile in the bottom, what is left in the top, the stream falling through the neck
m.box((C - 2.6, Y0 + 1.2, C - 2.6), (C + 2.6, Y0 + 2.4, C + 2.6), 'sand')
m.box((C - 1.8, Y0 + 2.4, C - 1.8), (C + 1.8, Y0 + 3.3, C + 1.8), 'sand')
m.box((C - 0.9, Y0 + 3.3, C - 0.9), (C + 0.9, Y0 + 3.9, C + 0.9), 'sand')
m.box((C - 1.4, MID + 1.0, C - 1.4), (C + 1.4, MID + 2.0, C + 1.4), 'sand')
m.box((C - 2.2, MID + 2.0, C - 2.2), (C + 2.2, MID + 3.0, C + 2.2), 'sand')
m.box((C - 0.18, Y0 + 3.6, C - 0.18), (C + 0.18, MID + 1.2, C + 0.18), 'stream', glow=13)

# runes of time floating around it (a tilted ring of glyph chips)
for k in range(8):
    a = 2 * math.pi * k / 8
    x, z = C + math.cos(a) * 6.4, C + math.sin(a) * 6.4
    yy = MID + 1.6 * math.sin(a * 1.0)
    m.box((x - 0.35, yy - 0.5, z - 0.35), (x + 0.35, yy + 0.5, z + 0.35), 'time', rot=R('y', 45, (x, yy, z)), glow=15)

GENERATED = {   # vanilla item/generated hand poses (the clock is a flat item)
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
}

if __name__ == '__main__':
    m.write_texture(sys.argv[1])
    m.write_model(sys.argv[2], 0, display=m.sprite_display(GENERATED, 0, gui_tilt=(20, -30)))
    print(len(m.elements), 'elements')
