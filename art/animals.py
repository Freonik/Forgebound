"""Scorched Lands animal variants: the vanilla textures recoloured (same UV) into burnt, ashen versions.

    python3 art/animals.py <pack>/assets/forgebound/textures/entity [preview.png]

Bases: the vanilla 1.21.11 textures in /home/claude/ref/vanilla/animals (temperate pig / cow / chicken, wolf).
"""
import colorsys
import os
import sys
from PIL import Image

OUT = sys.argv[1]
PREVIEW = sys.argv[2] if len(sys.argv) > 2 else None
BASE = '/home/claude/ref/vanilla/animals'

ASH = [(18, 16, 16), (34, 31, 30), (54, 50, 48), (78, 73, 70), (106, 100, 96), (138, 131, 126), (170, 163, 156)]
EMBER = [(120, 30, 6), (200, 70, 10), (255, 130, 30), (255, 200, 90)]


def ramp(colors, t):
    t = max(0.0, min(0.999, t)) * (len(colors) - 1)
    i = int(t)
    f = t - i
    a, b = colors[i], colors[min(i + 1, len(colors) - 1)]
    return tuple(int(a[k] + (b[k] - a[k]) * f) for k in range(3))


def recolor(name, base, dark=0.0, embers=0.0, keep_red=True, light=1.0):
    im = Image.open(f'{BASE}/{base}.png').convert('RGBA')
    out = Image.new('RGBA', im.size)
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = im.getpixel((x, y))
            if a == 0:
                continue
            h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
            if keep_red and s > 0.45 and (h < 0.08 or h > 0.9) and 0.2 < l < 0.6:
                c = ramp(EMBER, 0.35 + l * 0.6)                       # comb, wattle, inner ears: glowing ember
            elif s > 0.5 and 0.1 < h < 0.2 and l > 0.4:
                c = ramp(EMBER, 0.7)                                  # beaks, hooves' highlights: hot metal
            else:
                t = (l * light - dark)
                c = ramp(ASH, t)
                # cracks of embers in the darker parts of the hide
                if embers and l < 0.62 and ((x * 7 + y * 13) % 29 == 0 or (x * 11 + y * 5) % 37 == 0) and (x + y) % 3:
                    c = ramp(EMBER, 0.45 + 0.4 * ((x * y) % 5) / 5)
            out.putpixel((x, y), c + (a,))
    return out


os.makedirs(f'{OUT}/pig', exist_ok=True)
os.makedirs(f'{OUT}/cow', exist_ok=True)
os.makedirs(f'{OUT}/chicken', exist_ok=True)
os.makedirs(f'{OUT}/wolf', exist_ok=True)
made = {
    'pig/charred_pig': recolor('charred_pig', 'temperate_pig', dark=0.35, embers=1, keep_red=False),
    'cow/ashen_cow': recolor('ashen_cow', 'temperate_cow', dark=0.0, embers=1, light=1.05, keep_red=False),
    'chicken/cinder_chicken': recolor('cinder_chicken', 'temperate_chicken', dark=0.25, embers=0),
    'wolf/cinder_wolf': recolor('cinder_wolf', 'wolf', dark=0.08, embers=1),
    'wolf/cinder_wolf_tame': recolor('cinder_wolf_tame', 'wolf_tame', dark=0.08, embers=1),
    'wolf/cinder_wolf_angry': recolor('cinder_wolf_angry', 'wolf_angry', dark=0.08, embers=1),
}
for path, im in made.items():
    im.save(f'{OUT}/{path}.png')
if PREVIEW:
    tiles = [Image.open(f'{BASE}/temperate_pig.png').convert('RGBA'), made['pig/charred_pig'],
             Image.open(f'{BASE}/temperate_cow.png').convert('RGBA'), made['cow/ashen_cow'],
             Image.open(f'{BASE}/temperate_chicken.png').convert('RGBA'), made['chicken/cinder_chicken'],
             Image.open(f'{BASE}/wolf.png').convert('RGBA'), made['wolf/cinder_wolf']]
    S = 6
    W = sum(t.width * S + 16 for t in tiles)
    H = max(t.height for t in tiles) * S
    sheet = Image.new('RGBA', (W, H), (40, 40, 52, 255))
    x = 0
    for t in tiles:
        big = t.resize((t.width * S, t.height * S), Image.NEAREST)
        sheet.alpha_composite(big, (x, 0))
        x += t.width * S + 16
    sheet.save(PREVIEW)
print('animals done')
