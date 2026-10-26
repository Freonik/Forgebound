"""Thorn Plate worn layers (humanoid + humanoid_leggings): netherite's 9-step shading recoloured to dark bark,
with green thorny vines, pale bone spikes (crown, shoulders, spine, boot cuffs). Runs after sets.py."""
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
    'bark': grad([(8, 10, 6), (16, 20, 11), (26, 32, 17), (38, 46, 24), (54, 62, 33), (74, 82, 46)]),
    'vine': grad([(14, 40, 12), (30, 76, 24), (54, 116, 38), (88, 158, 58), (128, 196, 86)]),
    'spike': grad([(64, 66, 36), (120, 126, 70), (170, 180, 110), (214, 222, 150), (246, 250, 206)]),
}


def level(px):
    lum = 0.3 * px[0] + 0.59 * px[1] + 0.11 * px[2]
    return min(range(len(LEVELS)), key=lambda i: abs(LEVELS[i] - lum))


def vine_path(face, lx, ly, w):
    """A vine winding diagonally around a limb: continuous across the four sides."""
    off = {'front': 0, 'left': 4, 'back': 8, 'right': 12}.get(face)
    if off is None:
        return False
    return (lx + off + ly) % 16 in (0, 8)


def armor_style(part, face, lx, ly, w, h):
    if part == 'head':
        if face == 'top' and (lx in (0, w - 1) or ly in (0, h - 1)) and (lx + ly) % 2 == 0:
            return 'spike', 4                          # crown of bone spikes round the top
        if face in ('front', 'back', 'left', 'right'):
            if ly == 0 and lx % 2 == 0:
                return 'spike', 3                      # spike tips over the brow
            if ly == 2:
                return 'vine', 1                       # thorny vine band
            if ly == 1 and lx % 3 == 1:
                return 'spike', 3
            if ly == 3:
                return 'vine', -1
        return 'bark', 0
    if part == 'body':
        if face == 'front' and 1 <= ly <= 9 and (lx == ly - 1 or lx == w - ly):
            return ('spike', 4) if ly in (3, 6) else ('vine', -1)   # crossed vines with thorns
        if face == 'back' and lx in (3, 4):
            if ly % 3 == 1 and lx == (3 if (ly // 3) % 2 else 4):
                return 'spike', 4                      # spine of thorns
            return 'vine', -1
        if face in ('left', 'right') and vine_path('front', lx, ly, w):
            return 'vine', 1
        return 'bark', 0
    if part == 'arm':
        if face == 'top':
            return ('spike', 4) if (lx + ly) % 2 == 0 else ('spike', 1)
        if ly == 0 and lx % 2 == 1:
            return 'spike', 3                          # spikes over the shoulder edge
        if vine_path(face, lx, ly, w) and ly > 1:
            return ('spike', 4) if ly % 5 == 2 else ('vine', 1)
        return 'bark', 0
    if part == 'leg':
        if face not in ('top', 'bottom') and ly == 6 and lx % 2 == 0:
            return 'spike', 4                          # spiky boot cuff
        if face not in ('top', 'bottom') and ly == 7:
            return 'vine', 1
        return 'bark', 0
    return 'bark', 0


def legs_style(part, face, lx, ly, w, h):
    if part == 'body' and face not in ('top', 'bottom') and ly in (7, 8):
        if ly == 7 and lx % 3 == 1:
            return 'spike', 3
        return 'vine', 1 if ly == 7 else -1           # vine belt
    if part == 'leg' and vine_path(face, lx, ly, w) and ly > 0:
        return ('spike', 4) if ly % 5 == 3 else ('vine', 1)
    return 'bark', 0


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
            lv = max(0, min(8, level(base.getpixel((x, y))) + lift))
            out.putpixel((x, y), RAMPS[mat][lv] + (255,))
    return out


armor = paint(Image.open(f'{V}/eq_netherite.png').convert('RGBA'), armor_style)
legs = paint(Image.open(f'{V}/eq_netherite_leggings.png').convert('RGBA'), legs_style)
armor.save(f'{EQ}/humanoid/thorn_plate.png')
legs.save(f'{EQ}/humanoid_leggings/thorn_plate.png')
print('thorn plate layers written')
