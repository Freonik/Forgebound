"""Warden of the Pit: a real 3D model (block elements) instead of a flat sprite.

A black iron war-maul: a caged soul-fire core inside a slatted head, a crushing face on one end and a hooked beak
on the other, horns on top, soul flames rising out of it, chains hanging off the head and shards of blackstone
floating around it. Texture: a 64x64 atlas (4 px per model unit) with animated soul fire.

    python3 warden3d.py <textures/item dir> <models/item dir>
"""
import sys, math, json, random
from PIL import Image

N = 64              # atlas size
FRAMES = 8
TEX = 'customcraft:item/warden_of_the_pit'


def clamp(v, a=0, b=255):
    return max(a, min(b, int(round(v))))


def mix(c1, c2, t):
    return tuple(clamp(c1[i] + (c2[i] - c1[i]) * t) for i in range(3))


def ramp(cols, t):
    t = max(0.0, min(0.999, t))
    x = t * (len(cols) - 1)
    i = int(x)
    return mix(cols[i], cols[i + 1], x - i)


IRON = [(8, 8, 11), (20, 20, 25), (34, 34, 41), (52, 52, 61), (78, 78, 90), (116, 116, 130), (170, 172, 186), (228, 230, 240)]
SOUL = [(4, 30, 22), (10, 80, 52), (30, 150, 96), (80, 220, 150), (170, 255, 210), (240, 255, 248)]
LEATHER = [(16, 10, 8), (34, 22, 16), (56, 36, 24), (80, 54, 36)]
STONE = [(10, 9, 12), (22, 20, 26), (36, 33, 40), (54, 50, 58)]

# atlas regions in UV units (0..16), 1 unit = 4 px
R = {
    'iron':   (0, 0, 8, 8),       # black iron plate
    'edge':   (8, 0, 12, 4),      # bright worn steel (edges, caps, rings)
    'soul':   (12, 0, 16, 4),     # soul fire (glows)
    'grip':   (8, 4, 12, 8),      # leather wrap
    'stone':  (12, 4, 16, 8),     # blackstone shard with green veins
    'rune':   (0, 8, 4, 16),      # iron slat with a glowing seam
    'flame':  (4, 8, 12, 16),     # translucent soul flames (rising)
    'dark':   (12, 8, 16, 12),    # near-black inside
    'spike':  (12, 12, 16, 16),   # dark polished spike steel
}


def noise(seed):
    rnd = random.Random(seed)
    return [[rnd.random() for _ in range(N)] for _ in range(N)]


NZ = noise(7)
NZ2 = noise(11)


def region(name):
    u0, v0, u1, v1 = R[name]
    return int(u0 * 4), int(v0 * 4), int(u1 * 4), int(v1 * 4)


def paint(frame):
    im = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    px = im.load()
    pulse = 0.5 + 0.5 * math.sin(2 * math.pi * frame / FRAMES)

    # black iron: hammered plate, darker rim, scratches, a few rivets
    x0, y0, x1, y1 = region('iron')
    for y in range(y0, y1):
        for x in range(x0, x1):
            lx, ly = x - x0, y - y0
            w = x1 - x0
            t = 0.28 + 0.10 * NZ[y][x] + 0.06 * math.sin(lx * 0.7 + ly * 0.3)
            rim = min(lx, ly, w - 1 - lx, w - 1 - ly)
            if rim == 0:
                t = 0.12
            elif rim == 1:
                t += 0.12 if (lx < 2 or ly < 2) else -0.06
            if (lx * 3 + ly * 7) % 29 == 0 and rim > 2:
                t += 0.16                                   # scratches
            if lx % 10 == 4 and ly % 10 == 4:
                t = 0.62                                    # rivet
            elif lx % 10 == 5 and ly % 10 == 5:
                t = 0.15                                    # rivet shadow
            px[x, y] = ramp(IRON, t) + (255,)

    x0, y0, x1, y1 = region('edge')
    for y in range(y0, y1):
        for x in range(x0, x1):
            t = 0.55 + 0.18 * NZ2[y][x] - 0.02 * (y - y0)
            if (x - x0) in (0, 15) or (y - y0) in (0, 15):
                t = 0.35
            px[x, y] = ramp(IRON, t) + (255,)

    x0, y0, x1, y1 = region('spike')
    for y in range(y0, y1):
        for x in range(x0, x1):
            lx = x - x0
            t = 0.2 + 0.4 * max(0, 1 - abs(lx - 6) / 9) + 0.06 * NZ2[y][x]
            px[x, y] = ramp(IRON, t) + (255,)

    # soul fire: flowing bands, brighter with the pulse
    x0, y0, x1, y1 = region('soul')
    for y in range(y0, y1):
        for x in range(x0, x1):
            lx, ly = x - x0, y - y0
            flow = math.sin((ly + frame * 2) * 0.55 + math.sin(lx * 0.6) * 1.6)
            t = 0.45 + 0.25 * flow + 0.25 * pulse + 0.1 * NZ[y][x]
            px[x, y] = ramp(SOUL, t) + (255,)

    # leather wrap: diagonal bands
    x0, y0, x1, y1 = region('grip')
    for y in range(y0, y1):
        for x in range(x0, x1):
            k = (x - x0 + y - y0) % 6
            t = 0.75 if k == 0 else (0.15 if k == 5 else 0.45 + 0.15 * NZ[y][x])
            px[x, y] = ramp(LEATHER, t) + (255,)

    # rune slats: dark iron, a glowing seam down the middle with small glyph notches
    x0, y0, x1, y1 = region('rune')
    w = x1 - x0
    for y in range(y0, y1):
        for x in range(x0, x1):
            lx, ly = x - x0, y - y0
            t = 0.22 + 0.08 * NZ[y][x] + (0.14 if lx in (1, 2) else 0) - (0.1 if lx >= w - 2 else 0)
            c = ramp(IRON, t)
            if lx in (0, w - 1):
                c = ramp(IRON, 0.08)
            mid = abs(lx - (w - 1) / 2)
            if mid < 1.0 and ly % 9 not in (0, 1):
                c = ramp(SOUL, 0.3 + 0.4 * pulse + 0.15 * math.sin(ly * 0.8 + frame))
            elif mid < 3.0 and ly % 9 == 4:
                c = ramp(SOUL, 0.25 + 0.3 * pulse)          # glyph notch
            px[x, y] = c + (255,)

    # soul flames: three translucent tongues licking upward, animated
    x0, y0, x1, y1 = region('flame')
    W, H = x1 - x0, y1 - y0
    for y in range(y0, y1):
        for x in range(x0, x1):
            lx, ly = x - x0, y - y0
            up = 1 - ly / (H - 1)                           # 0 at the bottom, 1 at the top
            dens = 0.0
            for k in range(3):
                ph = frame / FRAMES * 2 * math.pi + k * 2.1
                top = 0.62 + 0.3 * (0.5 + 0.5 * math.sin(ph)) - (0.12 if k != 1 else 0)
                if up >= top:
                    continue
                h = up / top
                cx = W / 2 + (k - 1) * W * 0.2 + 2.6 * math.sin(ly * 0.33 + ph) * h
                half = (W * (0.2 if k == 1 else 0.15)) * (1 - h) ** 0.65 + 0.6
                dens = max(dens, 1 - abs(lx - cx) / half)
            if dens <= 0:
                continue
            heat = dens * (1 - up * 0.6)
            a = clamp(250 * min(1, dens * 1.8) * (1 - up ** 3) * (0.7 + 0.3 * NZ[y][x]))
            if a < 25:
                continue
            px[x, y] = ramp(SOUL, 0.2 + 0.8 * heat) + (a,)

    # blackstone shard: dark stone, green veins
    x0, y0, x1, y1 = region('stone')
    for y in range(y0, y1):
        for x in range(x0, x1):
            lx, ly = x - x0, y - y0
            t = 0.3 + 0.5 * NZ2[y][x] * NZ[y][x]
            c = ramp(STONE, t)
            vein = abs(math.sin(lx * 0.5 + math.sin(ly * 0.7) * 2.2))
            if vein < 0.12:
                c = ramp(SOUL, 0.3 + 0.4 * pulse)
            px[x, y] = c + (255,)

    x0, y0, x1, y1 = region('dark')
    for y in range(y0, y1):
        for x in range(x0, x1):
            px[x, y] = ramp(IRON, 0.05 + 0.06 * NZ[y][x]) + (255,)
    return im


# ------------------------------------------------------------------ model

elements = []


def uv_for(name, w, h):
    """A face UV inside the region, sized like the face (1 unit = 4 px), clamped to the region."""
    u0, v0, u1, v1 = R[name]
    w = min(w, u1 - u0)
    h = min(h, v1 - v0)
    return [round(u0, 3), round(v0, 3), round(u0 + w, 3), round(v0 + h, 3)]


def box(fr, to, tex, rot=None, glow=0, faces=None, side=None, stretch=False):
    """A cuboid. tex: region for every face, side: optional region for the 4 side faces.
    stretch: map the whole region onto each face (flames)."""
    fx, fy, fz = fr
    tx, ty, tz = to
    sx, sy, sz = tx - fx, ty - fy, tz - fz
    dims = {'north': (sx, sy), 'south': (sx, sy), 'east': (sz, sy), 'west': (sz, sy), 'up': (sx, sz), 'down': (sx, sz)}
    f = {}
    for name, (w, h) in dims.items():
        if faces is not None and name not in faces:
            continue
        region_name = side if (side and name not in ('up', 'down')) else tex
        uv = list(R[region_name]) if stretch else uv_for(region_name, max(w, 0.25), max(h, 0.25))
        f[name] = {"uv": uv, "texture": "#t"}
    e = {"from": [round(v, 3) for v in fr], "to": [round(v, 3) for v in to], "faces": f}
    if rot:
        e["rotation"] = rot
    if glow:
        e["light_emission"] = glow
    elements.append(e)


def rot(axis, angle, origin):
    return {"angle": angle, "axis": axis, "origin": [round(v, 3) for v in origin]}


C = 8.0  # centre line (x, z)

# --- pommel: a caged soul core with a spike under it
box((C - 1.2, -8.4, C - 1.2), (C + 1.2, -6.0, C + 1.2), 'soul', glow=12)
for dx in (-1, 1):
    for dz in (-1, 1):
        box((C + dx * 1.35 - 0.3, -8.8, C + dz * 1.35 - 0.3), (C + dx * 1.35 + 0.3, -5.6, C + dz * 1.35 + 0.3), 'edge')
box((C - 1.7, -9.2, C - 1.7), (C + 1.7, -8.6, C + 1.7), 'iron', side='edge')
box((C - 0.7, -11.2, C - 0.7), (C + 0.7, -9.2, C + 0.7), 'spike', rot=rot('y', 45, (C, -10, C)))
box((C - 1.7, -6.0, C - 1.7), (C + 1.7, -5.4, C + 1.7), 'iron', side='edge')

# --- haft with a leather grip and steel rings
box((C - 0.8, -5.4, C - 0.8), (C + 0.8, 17.6, C + 0.8), 'iron')
box((C - 1.0, -4.6, C - 1.0), (C + 1.0, 5.0, C + 1.0), 'grip')
for y in (-5.0, 0.0, 5.0, 9.0, 12.5):
    box((C - 1.25, y - 0.3, C - 1.25), (C + 1.25, y + 0.3, C + 1.25), 'edge')
# langets running down from the head
for dz in (-1, 1):
    box((C - 0.9, 11.0, C + dz * 0.95 - 0.2), (C + 0.9, 17.8, C + dz * 0.95 + 0.2), 'edge')

# --- head: soul core inside slatted iron
HY0, HY1 = 17.6, 24.6
MY = (HY0 + HY1) / 2
box((C - 3.6, HY0 + 0.3, C - 2.1), (C + 3.6, HY1 - 0.3, C + 2.1), 'soul', glow=15)
for z0, z1 in ((C - 2.7, C - 2.1), (C + 2.1, C + 2.7)):
    for x0 in (C - 3.9, C - 1.0, C + 1.9):
        box((x0, HY0, z0), (x0 + 2.0, HY1, z1), 'rune')
# caps
box((C - 4.2, HY1, C - 3.0), (C + 4.2, HY1 + 0.9, C + 3.0), 'iron', side='edge')
box((C - 4.2, HY0 - 0.9, C - 3.0), (C + 4.2, HY0, C + 3.0), 'iron', side='edge')
# side pillars at the corners of the core
for dx in (-1, 1):
    for dz in (-1, 1):
        box((C + dx * 3.75 - 0.45, HY0, C + dz * 2.55 - 0.45), (C + dx * 3.75 + 0.45, HY1, C + dz * 2.55 + 0.45), 'edge')

# crushing face (west end): a heavy block, a bright striking plate and four studs
box((C - 8.4, HY0 + 0.2, C - 2.8), (C - 4.0, HY1 - 0.2, C + 2.8), 'iron')
box((C - 9.2, HY0 + 0.6, C - 2.4), (C - 8.4, HY1 - 0.6, C + 2.4), 'edge')
for dy in (-1, 1):
    for dz in (-1, 1):
        box((C - 10.0, MY + dy * 1.5 - 0.45, C + dz * 1.2 - 0.45),
            (C - 9.2, MY + dy * 1.5 + 0.45, C + dz * 1.2 + 0.45), 'spike')
box((C - 8.0, HY0 + 0.6, C - 3.0), (C - 6.4, HY1 - 0.6, C + 3.0), 'edge')

# hooked beak (east end): three tapering segments bending down
box((C + 4.0, HY0 + 0.8, C - 2.0), (C + 7.0, HY1 - 0.8, C + 2.0), 'iron')
box((C + 6.8, MY - 1.7, C - 1.5), (C + 9.6, MY + 1.7, C + 1.5), 'spike', rot=rot('z', -22.5, (C + 6.8, MY, C)))
box((C + 8.9, MY - 2.9, C - 1.0), (C + 11.4, MY - 0.4, C + 1.0), 'spike', rot=rot('z', -45, (C + 8.9, MY - 1.6, C)))
box((C + 10.2, MY - 4.9, C - 0.5), (C + 11.6, MY - 3.0, C + 0.5), 'edge', rot=rot('z', -45, (C + 10.6, MY - 4.0, C)))

# top spike and horns
box((C - 1.0, HY1 + 0.9, C - 1.0), (C + 1.0, HY1 + 4.2, C + 1.0), 'spike', rot=rot('y', 45, (C, HY1 + 2, C)))
box((C - 0.45, HY1 + 4.2, C - 0.45), (C + 0.45, HY1 + 6.4, C + 0.45), 'spike', rot=rot('y', 45, (C, HY1 + 5, C)))
for dx in (-1, 1):
    bx = C + dx * 3.4
    box((bx - 0.6, HY1 + 0.6, C - 0.6), (bx + 0.6, HY1 + 3.4, C + 0.6), 'edge',
        rot=rot('z', 22.5 * dx * -1, (bx, HY1 + 0.6, C)))
    box((bx + dx * 1.1 - 0.4, HY1 + 2.8, C - 0.4), (bx + dx * 1.1 + 0.4, HY1 + 5.0, C + 0.4), 'spike',
        rot=rot('z', 45 * dx * -1, (bx + dx * 1.1, HY1 + 2.8, C)))

# chains hanging off the head: real links, alternating 45 degrees, ending in a broken shackle
for cx in (C - 3.2, C + 3.2):
    y = HY0 - 0.9
    for i in range(6):
        ang = 45 if i % 2 == 0 else -45
        box((cx - 0.55, y - 1.25, C - 0.18), (cx + 0.55, y, C + 0.18), 'edge', rot=rot('y', ang, (cx, y, C)))
        y -= 1.0
    box((cx - 0.75, y - 0.9, C - 0.25), (cx + 0.75, y - 0.3, C + 0.25), 'spike', rot=rot('z', 22.5, (cx, y, C)))

# floating blackstone shards around the head
for (x, y, z, s, axis, ang) in [(C - 6.5, HY1 + 3.4, C - 3.6, 1.4, 'x', 45), (C + 6.4, HY1 + 2.6, C + 3.8, 1.1, 'z', 22.5),
                                (C - 5.6, HY0 - 3.2, C + 4.2, 1.2, 'y', 45), (C + 5.4, HY0 - 2.2, C - 4.4, 1.5, 'x', -22.5),
                                (C + 1.6, HY1 + 6.4, C + 2.6, 0.9, 'z', 45)]:
    box((x - s / 2, y - s / 2, z - s / 2), (x + s / 2, y + s / 2, z + s / 2), 'stone', rot=rot(axis, ang, (x, y, z)), glow=6)

# soul flames: crossed translucent planes rising out of the head and the pommel
for a in (45, -45):
    box((C - 4.0, HY1 - 2.0, C), (C + 4.0, HY1 + 6.0, C), 'flame', rot=rot('y', a, (C, HY1, C)),
        faces=('north', 'south'), glow=15, stretch=True)
for x in (C - 6.2, C + 5.6):
    box((x - 1.6, HY1 - 1.0, C), (x + 1.6, HY1 + 2.6, C), 'flame', rot=rot('y', 45, (x, HY1, C)),
        faces=('north', 'south'), glow=15, stretch=True)
box((C - 1.5, -7.6, C), (C + 1.5, -4.2, C), 'flame', rot=rot('y', 45, (C, -6, C)),
    faces=('north', 'south'), glow=15, stretch=True)


def model():
    return {
        "credit": "Forgebound",
        "texture_size": [N, N],
        "textures": {"t": TEX, "particle": TEX},
        "elements": elements,
        "display": {
            # held like the vanilla sword: same grip point and blade direction, just a much bigger weapon
            "thirdperson_righthand": {"rotation": [0, -90, 10], "translation": [0, 4.47, 0.42], "scale": [0.6, 0.6, 0.6]},
            "thirdperson_lefthand": {"rotation": [0, 90, -10], "translation": [0, 4.47, 0.42], "scale": [0.6, 0.6, 0.6]},
            "firstperson_righthand": {"rotation": [0, -90, -20], "translation": [1.13, 3.33, 1.18], "scale": [0.45, 0.45, 0.45]},
            "firstperson_lefthand": {"rotation": [0, 90, 20], "translation": [1.13, 3.33, 1.18], "scale": [0.45, 0.45, 0.45]},
            "gui": {"rotation": [20, -35, -42], "translation": [-1.15, -1.04, 0], "scale": [0.38, 0.38, 0.38]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 1, 0], "scale": [0.4, 0.4, 0.4]},
            "fixed": {"rotation": [0, 180, -45], "translation": [0, -1, 0], "scale": [0.5, 0.5, 0.5]},
            "head": {"rotation": [0, 0, 0], "translation": [0, 10, 0], "scale": [0.6, 0.6, 0.6]},
        },
    }


if __name__ == '__main__':
    for e in elements:                      # Minecraft refuses models outside -16..32
        for v in e["from"] + e["to"]:
            assert -16 <= v <= 32, e
        if "rotation" in e:
            assert e["rotation"]["angle"] in (-45, -22.5, 0, 22.5, 45), e
    tex_dir, model_dir = sys.argv[1], sys.argv[2]
    import model3d
    turner = model3d.Model('warden_of_the_pit')
    turner.elements = elements
    turner.turn_around()                    # edge/beak facing away from the player, like vanilla blades
    sheet = Image.new('RGBA', (N, N * FRAMES))
    for i in range(FRAMES):
        sheet.paste(paint(i), (0, i * N))
    sheet.save(f'{tex_dir}/warden_of_the_pit.png')
    with open(f'{tex_dir}/warden_of_the_pit.png.mcmeta', 'w') as fh:
        json.dump({"animation": {"frametime": 3, "interpolate": True}}, fh)
    with open(f'{model_dir}/warden_of_the_pit.json', 'w') as fh:
        json.dump(model(), fh, indent=1)
    print(len(elements), 'elements')
