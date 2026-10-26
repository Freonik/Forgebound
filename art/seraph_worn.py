"""Pale Seraph worn layers (helm, Seraph Mantle, Angel's Wings boots + Seraph Greaves): netherite's 9-step shading
recoloured to white porcelain with gold trim, pale soul-blue gems and white feathers - like the item icons.
Runs after sets2.py; the old mask / wings items share the same full look."""
import sys, os
from PIL import Image

EQ = sys.argv[1]
V = '/home/claude/ref/vanilla'

FACES = []


def box(part, u, v, w, h, d):
    FACES.extend([
        (part, 'top', u + d, v, w, d), (part, 'bottom', u + d + w, v, w, d),
        (part, 'right', u, v + d, d, h), (part, 'front', u + d, v + d, w, h),
        (part, 'left', u + d + w, v + d, d, h), (part, 'back', u + 2 * d + w, v + d, w, h)])


box('head', 0, 0, 8, 8, 8)
box('body', 16, 16, 8, 12, 4)
box('arm', 40, 16, 4, 12, 4)
box('leg', 0, 16, 4, 12, 4)


def face_of(x, y):
    for part, face, fx, fy, fw, fh in FACES:
        if fx <= x < fx + fw and fy <= y < fy + fh:
            return part, face, x - fx, y - fy, fw, fh
    return None


def grad(keys, n=9):
    """n colours along the key colours."""
    out = []
    for i in range(n):
        t = i / (n - 1) * (len(keys) - 1)
        a = keys[int(t)]; b = keys[min(len(keys) - 1, int(t) + 1)]; f = t - int(t)
        out.append(tuple(int(a[k] + (b[k] - a[k]) * f) for k in range(3)))
    return out


LEVELS = [9, 17, 32, 42, 53, 62, 73, 88, 110]
RAMPS = {
    'porc': grad([(40, 44, 60), (82, 88, 110), (126, 132, 156), (168, 174, 196), (204, 208, 224), (236, 238, 246)]),
    'gold': grad([(74, 44, 12), (138, 90, 24), (196, 140, 40), (236, 188, 74), (255, 228, 140)]),
    'gem': grad([(20, 60, 120), (40, 110, 200), (90, 170, 240), (170, 220, 255), (230, 248, 255)]),
    'feather': grad([(120, 126, 146), (184, 190, 206), (226, 230, 240), (255, 255, 255)]),
    'visor': grad([(8, 10, 18), (20, 26, 40), (40, 50, 70)]),
}


def level(px):
    lum = 0.3 * px[0] + 0.59 * px[1] + 0.11 * px[2]
    return min(range(len(LEVELS)), key=lambda i: abs(LEVELS[i] - lum))


def armor_style(part, face, lx, ly, w, h):
    if part == 'head':
        if face == 'top':
            return ('gold', 2) if lx in (3, 4) else ('porc', 1)        # gold crest over the helm
        if face == 'front':
            if ly == 3 and 1 <= lx <= 6:
                return 'gem', 3 if lx in (2, 5) else 2                  # glowing visor band
            if lx in (3, 4) and ly >= 4:
                return 'visor', 0 if lx == 3 else 1                     # breathing slit
            if ly == 2 or (ly == 4 and lx in (1, 6)):
                return 'gold', 2                                        # gold brow trim
            if ly == 0 and lx in (3, 4):
                return 'gem', 4 if lx == 3 else 3                       # gem on the crest
            return 'porc', 1
        if ly == 7:
            return 'gold', 1                                            # gold rim round the neck
        if face in ('left', 'right') and ly == 3:
            return 'gold', 1
        return 'porc', 1
    if part == 'body':
        if face in ('top', 'bottom'):
            return 'gold', 1
        if face == 'front':
            if ly == 0:
                return 'gold', 3                                        # gold collar
            if lx in (3, 4):
                if ly in (4, 5):
                    return 'gem', 4 if lx == 3 else 3                   # soul gem
                return 'gold', 2 if lx == 3 else 1                      # gold line down the middle
            if lx in (1, 6) and ly % 2 == 1 and 2 <= ly <= 9:
                return 'gold', 3                                        # rivets
            if ly == 11:
                return 'gold', 1
            return 'porc', 0
        if face == 'back':
            dx = min(lx, w - 1 - lx)
            if lx in (3, 4) and 1 <= ly <= 10:
                return 'gold', 1 if lx == 3 else 0                      # gold spine between the wings
            if 1 <= ly <= 9 and (ly - 1) // 3 <= dx <= 2:
                return 'feather', 4 if (dx + ly) % 3 else 0             # folded wings, tips at the shoulders
            if ly in (0, 11):
                return 'gold', 1
            return 'porc', -1                                           # darker so the wings stand out
        return 'porc', 1
    if part == 'arm':
        if face == 'top' or ly <= 2:
            return 'feather', 4 if (lx + ly) % 2 else 1                 # feather pauldrons
        if ly == 3:
            return 'gold', 2
        if ly == 9:
            return 'gold', 1
        return 'porc', 1
    if part == 'leg':
        if face in ('top', 'bottom'):
            return 'porc', 1
        if ly == 6:
            return 'gold', 2                                            # gold cuff
        if ly == 11:
            return 'gold', 0                                            # gilded sole
        if face in ('left', 'right') and 7 <= ly <= 9 and lx >= 1:
            return 'feather', 4 if (lx + ly) % 2 else 1                 # little wings on the ankles
        return 'porc', 1
    return 'porc', 1


def legs_style(part, face, lx, ly, w, h):
    if part == 'body':
        if ly in (7, 8) and face not in ('top', 'bottom'):
            if face == 'front' and lx in (3, 4):
                return 'gem', 4 if ly == 7 else 2                       # gem on the belt
            return 'gold', 3 if ly == 7 else 1
        return 'porc', 1
    if face == 'right' and 1 <= ly <= 6:
        return 'feather', 4 if (lx + ly) % 3 else 1                     # feather tassets on the outer side
    if face == 'front' and ly in (5, 6):
        return 'gold', 3 if ly == 5 else 1                              # gold knee band
    if face == 'front' and lx == 0 and ly % 2 == 0:
        return 'gold', 2                                                # rivets down the leg
    return 'porc', 1


def paint(base, style):
    out = Image.new('RGBA', base.size, (0, 0, 0, 0))
    for y in range(base.size[1]):
        for x in range(base.size[0]):
            f = face_of(x, y)
            if not f or (x >= 32 and y < 16):
                continue                                   # no hat layer
            px = base.getpixel((x, y))
            if px[3] == 0:
                if f[0] != 'head':
                    continue
                px = (53, 53, 53, 255)                     # the helm is closed: fill netherite's eye holes
            mat, lift = style(f[0], f[1], f[2], f[3], f[4], f[5])
            lv = max(0, min(8, level(px) + lift))
            out.putpixel((x, y), RAMPS[mat][lv] + (255,))
    return out


armor = paint(Image.open(f'{V}/eq_netherite.png').convert('RGBA'), armor_style)
legs = paint(Image.open(f'{V}/eq_netherite_leggings.png').convert('RGBA'), legs_style)
for n in ('pale_seraph', 'soul_mask', 'angels_wings'):
    armor.save(f'{EQ}/humanoid/{n}.png')
legs.save(f'{EQ}/humanoid_leggings/pale_seraph.png')
print('pale seraph layers written')
