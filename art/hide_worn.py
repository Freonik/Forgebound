"""Thick Hide worn layers (humanoid + humanoid_leggings), drawn per face: overlapping hide scales with real shading,
a pale fur collar / hood rim / boot cuffs, leather straps with teal studs and stitched seams - like the item icons.
Runs after tank.py and sets2.py (it replaces their Thick Hide layers)."""
import sys, os
sys.path.insert(0, '/home/claude/art')
from px import ramp
from PIL import Image

EQ = sys.argv[1]                      # textures/entity/equipment
PREVIEW = sys.argv[2] if len(sys.argv) > 2 else None
V = '/home/claude/ref/vanilla'

HIDE = [(30, 28, 18), (48, 44, 29), (68, 62, 41), (90, 83, 55), (114, 106, 72), (140, 131, 92), (168, 158, 114)]
FUR = [(70, 58, 40), (98, 84, 60), (128, 112, 82), (156, 140, 106), (182, 168, 132)]
BELT = [(60, 38, 22), (88, 58, 34), (118, 80, 48)]
TEAL = [(20, 96, 92), (44, 160, 148), (130, 230, 210)]
STITCH = (196, 172, 120)

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


LIGHT = {'top': 1.0, 'front': 0.0, 'left': -0.7, 'right': -0.7, 'back': -0.5, 'bottom': -1.6}


def hide(face, lx, ly, row_h=4, shift=0):
    """Overlapping scales: each row lit at the top, shadowed at the bottom, rows offset like bricks."""
    row = (ly + shift) // row_h
    inrow = (ly + shift) % row_h
    col_off = 2 if row % 2 else 0
    shade = 3.4 + LIGHT.get(face, 0)
    if inrow == 0:
        shade += 0.8                                  # lit top edge of the scale
    elif inrow == row_h - 1:
        shade -= 1.2                                  # shadow under it
    if (lx + col_off) % 4 == 3 and inrow != 0:
        shade -= 0.9                                  # gap between two scales in a row
    if (lx * 7 + ly * 3) % 11 == 0:
        shade += 0.5                                  # a little life in the leather
    return HIDE[max(0, min(len(HIDE) - 1, int(round(shade))))]


def fur(face, lx, ly, depth):
    """Shaggy pale fur: lighter on top, uneven lower edge."""
    shade = 2.6 + LIGHT.get(face, 0) * 0.8 - depth * 0.6 + ((lx * 5 + ly) % 3 == 0) * 0.6
    return FUR[max(0, min(len(FUR) - 1, int(round(shade))))]


def strap(face, ly0, ly, studs_at, lx):
    if ly == ly0:
        return BELT[2 if face == 'top' else 1]
    if ly == ly0 + 1:
        if lx in studs_at and face in ('front', 'back'):
            return TEAL[1]
        return BELT[0]
    return None


def grad(keys, n=9):
    """n colours along the key colours."""
    out = []
    for i in range(n):
        t = i / (n - 1) * (len(keys) - 1)
        a = keys[int(t)]; b = keys[min(len(keys) - 1, int(t) + 1)]; f = t - int(t)
        out.append(tuple(int(a[k] + (b[k] - a[k]) * f) for k in range(3)))
    return out


# netherite's layers are shaded in 9 grey steps - we keep that shading (the 3D look) and only swap the material
LEVELS = [9, 17, 32, 42, 53, 62, 73, 88, 110]
RAMPS = {
    'hide': grad([(16, 12, 8), (36, 28, 17), (60, 48, 29), (86, 70, 42), (114, 94, 58), (146, 124, 80)]),
    'fur': grad([(40, 32, 22), (78, 66, 48), (122, 108, 80), (166, 152, 118), (204, 192, 160)]),
    'belt': grad([(30, 18, 10), (64, 40, 22), (98, 64, 36), (134, 92, 54)]),
    'teal': grad([(10, 50, 48), (24, 104, 98), (48, 168, 154), (140, 236, 214)]),
    'stitch': grad([(90, 74, 48), (150, 128, 88), (210, 188, 140)]),
}


def level(px):
    lum = 0.3 * px[0] + 0.59 * px[1] + 0.11 * px[2]
    return min(range(len(LEVELS)), key=lambda i: abs(LEVELS[i] - lum))


def armor_style(part, face, lx, ly, w, h):
    if part == 'head':
        if face == 'front' and ly == 1 and lx in (3, 4):
            return 'teal', 2                           # gem on the brow
        if face != 'top' and ly >= 6:
            return 'fur', 1                            # fur where the hood meets the shoulders
        if face in ('left', 'right') and lx == 4 and 1 <= ly <= 5 and ly % 2:
            return 'stitch', 1
        return 'hide', 0
    if part == 'body':
        if face == 'top' or ly <= 1 or (ly == 2 and lx % 3 != 1):
            return 'fur', 1 if ly == 0 else 0          # collar with a ragged edge
        if ly in (9, 10):
            if face == 'front' and ly == 9 and lx in (3, 4):
                return 'teal', 1 if lx == 3 else 2     # buckle
            return 'belt', 1 if ly == 9 else 0
        if face in ('left', 'right') and lx in (1, 2) and ly % 2 == 0:
            return 'stitch', 0                          # side lacing
        return 'hide', 0
    if part == 'arm':
        if face == 'top' or ly == 0:
            return 'fur', 1                             # fur on the shoulder
        if ly in (9, 10):
            if face == 'front' and ly == 9 and lx == 1:
                return 'teal', 1
            return 'belt', 0                            # bracer strap
        return 'hide', 0
    if part == 'leg':
        if ly in (6, 7) and face not in ('top', 'bottom'):
            return 'fur', 1 if ly == 6 else 0          # fur cuff on the boots
        if ly == 9 and face not in ('top', 'bottom'):
            if face == 'front' and lx == 1:
                return 'teal', 1
            return 'belt', 0
        return 'hide', 0
    return 'hide', 0


def legs_style(part, face, lx, ly, w, h):
    if part == 'body' and ly in (7, 8) and face not in ('top', 'bottom'):
        if face == 'front' and ly == 7 and lx in (3, 4):
            return 'teal', 1 if lx == 3 else 2
        return 'belt', 1 if ly == 7 else 0
    if part == 'leg' and face in ('left', 'right') and lx == w // 2 and ly % 2 == 0 and ly > 1:
        return 'stitch', 0
    return 'hide', 0


def paint(base, style):
    out = Image.new('RGBA', base.size, (0, 0, 0, 0))
    for y in range(base.size[1]):
        for x in range(base.size[0]):
            if base.getpixel((x, y))[3] == 0:
                continue
            f = face_of(x, y)
            if not f:
                continue
            mat, lift = style(f[0], f[1], f[2], f[3], f[4], f[5])
            lv = min(8, level(base.getpixel((x, y))) + lift)
            out.putpixel((x, y), RAMPS[mat][lv] + (255,))
    return out


armor = paint(Image.open(f'{V}/eq_netherite.png').convert('RGBA'), armor_style)
legs = paint(Image.open(f'{V}/eq_netherite_leggings.png').convert('RGBA'), legs_style)
os.makedirs(f'{EQ}/humanoid', exist_ok=True)
os.makedirs(f'{EQ}/humanoid_leggings', exist_ok=True)
armor.save(f'{EQ}/humanoid/thick_hide.png')
legs.save(f'{EQ}/humanoid_leggings/thick_hide.png')
print('thick hide layers written')
