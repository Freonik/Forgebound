"""Heartsteel worn layers: netherite's 9-step shading in dark crimson steel, a gold-rimmed heart on the chest,
gold-set heart gems on the helm, belt and boots, and glowing red cracks running out from every gem - like the icons."""
import sys, os, random
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
    'steel': grad([(10, 7, 9), (22, 15, 19), (36, 25, 31), (54, 39, 46), (78, 58, 66), (108, 84, 92)]),
    'glow': grad([(40, 6, 10), (70, 12, 18), (100, 20, 26), (130, 30, 34)]),      # steel lit by a crack
    'core': grad([(90, 0, 12), (150, 6, 22), (200, 20, 34), (240, 50, 50), (255, 150, 120)]),
    'gold': grad([(70, 40, 8), (130, 86, 18), (190, 140, 40), (238, 196, 92), (255, 238, 170)]),
}

HEART = [            # 8 x 7, R gold rim, H highlight, B bright, M red, D shadow, C glowing core
    ".RR..RR.",
    "RHBRRBMR",
    "RBBMMMDR",
    "RBMCCMDR",
    ".RMMMDR.",
    "..RMDR..",
    "...RR...",
]
HEART_MAT = {'R': ('gold', 6), 'H': ('core', 8), 'B': ('core', 6), 'M': ('core', 4), 'D': ('core', 2), 'C': ('core', 7)}
GEM = ["RBR", "BCM", "RMR"]            # small gold-set gem, 3 x 3
GEM_MAT = {'R': ('gold', 6), 'B': ('core', 7), 'C': ('core', 8), 'M': ('core', 4)}


def cracks(seed, w, h, starts, steps=7):
    """Glowing cracks: short jagged walks running away from the gems."""
    rnd = random.Random(seed)
    pts = set()
    for (x, y, dx, dy) in starts:
        for _ in range(steps):
            if not (0 <= x < w and 0 <= y < h):
                break
            pts.add((x, y))
            if rnd.random() < 0.45:
                x += rnd.choice((-1, 1)) if dx == 0 else dx
            else:
                y += dy if dy else rnd.choice((-1, 1))
                x += dx if rnd.random() < 0.3 else 0
    return pts


CR = {
    ('head', 'front'): cracks(1, 8, 8, [(2, 1, -1, 0), (5, 1, 1, 0)], 3),
    ('head', 'back'): cracks(2, 8, 8, [(3, 7, 0, -1), (5, 7, 1, -1)], 4),
    ('head', 'left'): cracks(3, 8, 8, [(0, 2, 1, 1)], 4),
    ('head', 'right'): cracks(4, 8, 8, [(7, 2, -1, 1)], 4),
    ('body', 'front'): cracks(5, 8, 12, [(3, 8, -1, 1), (4, 8, 1, 1), (1, 2, -1, -1), (6, 2, 1, -1)], 3),
    ('body', 'back'): cracks(6, 8, 12, [(3, 1, 0, 1), (4, 5, 1, 1), (2, 6, -1, 1)], 4),
    ('arm', 'front'): cracks(7, 4, 12, [(1, 4, 0, 1)], 4),
    ('arm', 'right'): cracks(8, 4, 12, [(2, 2, 0, 1)], 5),
    ('arm', 'back'): cracks(9, 4, 12, [(2, 4, 0, 1)], 4),
    ('leg', 'front'): cracks(10, 4, 12, [(1, 10, 0, -1), (2, 10, 1, -1)], 3),
    ('leg', 'right'): cracks(11, 4, 12, [(1, 7, 0, 1)], 3),
}
CR_LEGS = {
    ('body', 'front'): cracks(12, 8, 12, [(2, 9, -1, 1), (5, 9, 1, 1)], 3),
    ('leg', 'front'): cracks(13, 4, 12, [(1, 0, 0, 1), (2, 3, 1, 1)], 6),
    ('leg', 'right'): cracks(14, 4, 12, [(2, 0, 0, 1)], 6),
    ('leg', 'back'): cracks(15, 4, 12, [(1, 2, 0, 1)], 5),
}


def crack_or_steel(table, part, face, lx, ly):
    pts = table.get((part, face), ())
    if (lx, ly) in pts:
        return 'core', 3 + (lx + ly) % 2, True         # the crack glows the same in light and shadow
    if (lx, ly - 1) in pts and (lx, ly + 1) not in pts:
        return 'glow', 1, True                         # red light bleeding onto the steel under it
    return 'steel', 0


MAP_MAT = {'G': ('gold', 6, True), 'g': ('gold', 4, True), 'o': ('gold', 2, True), 'H': ('core', 8, True),
           'C': ('core', 7, True), 'R': ('core', 5, True), 'M': ('core', 4, True), 'r': ('core', 3, True),
           'v': ('glow', 2, True)}
# hand-drawn faces: '.' = plain shaded steel
MAPS = {
    ('head', 'front'): ["G.oGGo.G",
                        "g.oHCo.g",
                        "oggrRggo",
                        "R......R",
                        "r......r",
                        "v......v",
                        "Gr....rG",
                        "go....og"],
    ('head', 'top'): ["GgGgGgGg",
                      "g..oo..G",
                      "G..oo..g",
                      "g.oRRo.G",
                      "G.oRRo.g",
                      "g..oo..G",
                      "G..oo..g",
                      "gGgGgGgG"],
    ('head', 'back'): ["g..GG..g",
                       "...gg...",
                       ".r.HC.r.",
                       "R.oRro.R",
                       "r..gg..r",
                       "v..oo..v",
                       "..gGGg..",
                       "........"],
    ('head', 'right'): ["G......G",
                        "gggoGogg",
                        "...RHR..",
                        "....r...",
                        "....v...",
                        "........",
                        "........",
                        "........"],
    ('body', 'back'): ["oggGGggo",
                       "...GG...",
                       "..GHCG..",
                       "vGCRRMGv",
                       "rGRRRrGr",
                       ".rGRrGr.",
                       "r..GG..r",
                       "..r..r..",
                       ".r....r.",
                       "r......r",
                       "........",
                        "........"],
}
MAPS[('head', 'left')] = [row[::-1] for row in MAPS[('head', 'right')]]


def from_map(part, face, lx, ly):
    m = MAPS.get((part, face))
    if m is None:
        return None
    ch = m[ly][lx]
    return MAP_MAT.get(ch, ('steel', 0))


ABS = set()     # (part, face, lx, ly) painted at a fixed level, not from netherite's shading


def level(px):
    lum = 0.3 * px[0] + 0.59 * px[1] + 0.11 * px[2]
    return min(range(len(LEVELS)), key=lambda i: abs(LEVELS[i] - lum))


def armor_style(part, face, lx, ly, w, h):
    m = from_map(part, face, lx, ly)
    if m:
        return m
    if part == 'head':
        if face == 'front' and 3 <= lx <= 5 and 0 <= ly <= 2:
            return GEM_MAT[GEM[ly][lx - 3]] + (True,)   # heart gem on the brow
        if face == 'front' and ly == 3:
            return 'gold', 2                           # gold brow band
        return crack_or_steel(CR, part, face, lx, ly)
    if part == 'body':
        if face == 'front' and 1 <= ly <= 7:
            ch = HEART[ly - 1][lx]
            if ch != '.':
                return HEART_MAT[ch] + (True,)
        if face in ('front', 'back') and ly == 0:
            return 'gold', 3 if face == 'front' else 0  # gold collar
        if face == 'back' and lx in (3, 4) and 1 <= ly <= 9 and ly % 2:
            return 'gold', 0 if lx == 3 else -1        # gold clasps down the spine
        return crack_or_steel(CR, part, face, lx, ly)
    if part == 'arm':
        if ly == 3 and face != 'bottom':
            return 'gold', 2                           # gold band under the pauldron
        if face == 'top':
            return 'steel', 1
        return crack_or_steel(CR, part, face, lx, ly)
    if part == 'leg':
        if ly == 6 and face not in ('top', 'bottom'):
            return 'gold', 2                           # gold boot cuff
        if face == 'front' and 8 <= ly <= 9 and 1 <= lx <= 2:
            return ('core', 7, True) if (lx + ly) % 2 else ('core', 4, True)   # small heart gem on each boot
        return crack_or_steel(CR, part, face, lx, ly)
    return 'steel', 0


def legs_style(part, face, lx, ly, w, h):
    if part == 'body' and ly in (7, 8) and face not in ('top', 'bottom'):
        if face == 'front' and 2 <= lx <= 5:
            ch = ["RBMR", "RMDR"][ly - 7][lx - 2]
            return {'R': ('gold', 6, True), 'B': ('core', 7, True), 'M': ('core', 5, True), 'D': ('core', 3, True)}[ch]
        return 'gold', 3 if ly == 7 else 1           # gold belt with a heart buckle
    if part == 'leg' and face == 'front' and ly == 5:
        return 'gold', 1                               # knee rim
    return crack_or_steel(CR_LEGS, part, face, lx, ly)


def paint(base, style):
    out = Image.new('RGBA', base.size, (0, 0, 0, 0))
    for y in range(base.size[1]):
        for x in range(base.size[0]):
            if base.getpixel((x, y))[3] == 0:
                continue
            f = face_of(x, y)
            if not f:
                continue
            r = style(f[0], f[1], f[2], f[3], f[4], f[5])
            mat, lift = r[0], r[1]
            if len(r) > 2:                             # gems are painted as drawn, not shaded by netherite
                lv = lift
            else:
                lv = max(0, min(8, level(base.getpixel((x, y))) + lift))
            out.putpixel((x, y), RAMPS[mat][lv] + (255,))
    return out


armor = paint(Image.open(f'{V}/eq_netherite.png').convert('RGBA'), armor_style)
legs = paint(Image.open(f'{V}/eq_netherite_leggings.png').convert('RGBA'), legs_style)
armor.save(f'{EQ}/humanoid/heartsteel.png')
legs.save(f'{EQ}/humanoid_leggings/heartsteel.png')
print('heartsteel layers written')
