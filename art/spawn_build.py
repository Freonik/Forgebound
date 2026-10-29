"""The spawn citadel for /forge spawnbuild: a round forge plaza (~70x70) made for the season ceremony.

    python3 art/spawn_build.py src/main/resources/spawn_build.txt.gz [preview.png]

Coordinates: x = east, z = south, y = up; (0, 0, 0) is the floor block under the admin's feet (the ceremony center).
Output (gzip text): "P <index> <blockdata>" palette lines, "B x y z index" blocks, "S x y z line|line|line|line" sign texts.
The plugin clears the air above the plaza and fills the ground under it itself.
"""
import gzip
import math
import random
import sys

R_WALL_IN, R_WALL_OUT = 31, 34
B = {}
SIGNS = {}
rnd = random.Random(7)


def put(x, y, z, block):
    B[(x, y, z)] = block


def r_of(x, z):
    return math.hypot(x, z)


# ---------------------------------------------------------------- floor
for x in range(-R_WALL_OUT, R_WALL_OUT + 1):
    for z in range(-R_WALL_OUT, R_WALL_OUT + 1):
        r = r_of(x, z)
        if r > R_WALL_OUT + 0.5:
            continue
        a = (math.degrees(math.atan2(z, x)) + 360) % 360
        if r <= 0.5:
            b = 'minecraft:gilded_blackstone'
        elif r <= 2.5:
            b = 'minecraft:chiseled_polished_blackstone'
        elif r <= 11.5:
            # dark ceremony circle: the glowing sigil is drawn on top of it
            b = 'minecraft:cracked_polished_blackstone_bricks' if rnd.random() < 0.12 else 'minecraft:polished_blackstone_bricks'
            if 6.5 < r <= 7.5:
                b = 'minecraft:polished_blackstone'
        elif r <= 12.5:
            b = 'minecraft:gilded_blackstone' if int(a) % 30 < 4 else 'minecraft:chiseled_polished_blackstone'
        elif r <= R_WALL_IN + 0.5:
            spoke = any(abs(((a - s) + 180) % 360 - 180) < 2.2 * 20 / max(r, 1) for s in range(0, 360, 45))
            if spoke:
                b = 'minecraft:polished_deepslate'
            elif (x % 7 == 0 and z % 7 == 0):
                b = 'minecraft:shroomlight'            # light in the floor: no mobs at spawn
            else:
                b = 'minecraft:cracked_deepslate_tiles' if rnd.random() < 0.08 else 'minecraft:deepslate_tiles'
            if 19.5 < r <= 20.5:
                b = 'minecraft:polished_blackstone_bricks'
        else:
            b = 'minecraft:deepslate_bricks'
        put(x, 0, z, b)

# center: the spot the admin stands on, a low rim around it
for x in range(-3, 4):
    for z in range(-3, 4):
        if 2.5 < r_of(x, z) <= 3.5:
            put(x, 1, z, 'minecraft:polished_blackstone_slab[type=bottom]')

# ---------------------------------------------------------------- pillars with braziers
PILLAR_R, PILLAR_H = 21, 22
for i in range(8):
    ang = math.radians(22.5 + 45 * i)
    cx, cz = round(PILLAR_R * math.cos(ang)), round(PILLAR_R * math.sin(ang))
    # plinth 5x5, 3 high
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            for y in range(1, 4):
                edge = abs(dx) == 2 or abs(dz) == 2
                if y == 3 and edge:
                    put(cx + dx, y, cz + dz, 'minecraft:polished_blackstone_brick_slab[type=bottom]')
                elif y < 3 or not edge:
                    put(cx + dx, y, cz + dz, 'minecraft:polished_blackstone_bricks' if edge else 'minecraft:deepslate_bricks')
    # shaft 3x3 with bands
    for y in range(4, PILLAR_H):
        for dx in range(-1, 2):
            for dz in range(-1, 2):
                if (y - 4) % 6 == 5:
                    blk = 'minecraft:chiseled_deepslate'
                elif dx == 0 and dz == 0:
                    blk = 'minecraft:deepslate_bricks'
                elif abs(dx) == 1 and abs(dz) == 1:
                    blk = 'minecraft:polished_basalt[axis=y]'
                else:
                    blk = 'minecraft:polished_deepslate'
                put(cx + dx, y, cz + dz, blk)
    # capital 5x5 and the brazier
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            put(cx + dx, PILLAR_H, cz + dz, 'minecraft:polished_blackstone_bricks')
            if abs(dx) == 2 or abs(dz) == 2:
                put(cx + dx, PILLAR_H + 1, cz + dz, 'minecraft:blackstone_wall')
            else:
                put(cx + dx, PILLAR_H + 1, cz + dz, 'minecraft:magma_block')
    put(cx, PILLAR_H + 2, cz, 'minecraft:campfire[lit=true]')
    for dx, dz in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        put(cx + dx, PILLAR_H + 2, cz + dz, 'minecraft:fire')
    # chains with lanterns hanging from the capital corners
    for dx, dz in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        for y in range(PILLAR_H - 5, PILLAR_H):
            put(cx + dx, y, cz + dz, 'minecraft:iron_chain[axis=y]')
        put(cx + dx, PILLAR_H - 6, cz + dz, 'minecraft:lantern[hanging=true]')

# ---------------------------------------------------------------- outer wall, gate towers
WALL_H = 8
GATE_HALF = 3


def in_gate(x, z):
    return (abs(x) <= GATE_HALF and abs(z) > R_WALL_IN - 2) or (abs(z) <= GATE_HALF and abs(x) > R_WALL_IN - 2)


for x in range(-R_WALL_OUT, R_WALL_OUT + 1):
    for z in range(-R_WALL_OUT, R_WALL_OUT + 1):
        r = r_of(x, z)
        if not (R_WALL_IN + 0.5 < r <= R_WALL_OUT + 0.5):
            continue
        gate = in_gate(x, z)
        for y in range(1, WALL_H + 1):
            if gate and y <= 6:
                continue
            inner = r <= R_WALL_IN + 1.5
            if y == WALL_H:
                blk = 'minecraft:polished_blackstone_bricks'
            elif y == 1:
                blk = 'minecraft:polished_blackstone'
            else:
                blk = 'minecraft:deepslate_bricks' if not (rnd.random() < 0.1) else 'minecraft:cracked_deepslate_bricks'
            if gate and y == 7:
                blk = 'minecraft:chiseled_polished_blackstone'
            put(x, y, z, blk)
        # crenels on the outer edge
        if r > R_WALL_OUT - 0.6 and (x + z) % 2 == 0:
            put(x, WALL_H + 1, z, 'minecraft:deepslate_brick_wall')
        # lanterns on the inner face
        if R_WALL_IN + 0.5 < r <= R_WALL_IN + 1.5 and (x * 3 + z) % 9 == 0 and not gate:
            put(x, WALL_H + 1, z, 'minecraft:lantern')

# gate towers: 5x5, 14 high, both sides of each gate
TOWER_H = 14
for gx, gz in ((0, -1), (0, 1), (1, 0), (-1, 0)):
    for side in (-1, 1):
        if gx == 0:
            cx, cz = side * (GATE_HALF + 3), gz * (R_WALL_IN + 2)
        else:
            cx, cz = gx * (R_WALL_IN + 2), side * (GATE_HALF + 3)
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                edge = abs(dx) == 2 or abs(dz) == 2
                for y in range(1, TOWER_H + 1):
                    if not edge and y > 1:
                        continue
                    blk = 'minecraft:polished_blackstone_bricks' if (abs(dx) == 2 and abs(dz) == 2) else 'minecraft:deepslate_bricks'
                    if y % 5 == 0:
                        blk = 'minecraft:chiseled_deepslate'
                    put(cx + dx, y, cz + dz, blk)
                put(cx + dx, TOWER_H + 1, cz + dz, 'minecraft:polished_blackstone_bricks')
                if edge and (dx + dz) % 2 == 0:
                    put(cx + dx, TOWER_H + 2, cz + dz, 'minecraft:polished_blackstone_brick_wall')
        for dx in range(-1, 2):
            for dz in range(-1, 2):
                put(cx + dx, TOWER_H + 2, cz + dz, 'minecraft:magma_block')
        put(cx, TOWER_H + 3, cz, 'minecraft:campfire[lit=true]')
        # arrow slits
        for y in (6, 10):
            for dx, dz in ((0, -2), (0, 2), (-2, 0), (2, 0)):
                put(cx + dx, y, cz + dz, 'minecraft:iron_bars')
    # chains and lanterns hanging in the gateway
    for k in (-2, 2):
        x, z = (k, gz * (R_WALL_IN + 2)) if gx == 0 else (gx * (R_WALL_IN + 2), k)
        put(x, 6, z, 'minecraft:iron_chain[axis=y]')
        put(x, 5, z, 'minecraft:lantern[hanging=true]')

# ---------------------------------------------------------------- forge stations on the diagonals
for i in range(4):
    ang = math.radians(45 + 90 * i)
    cx, cz = round(26 * math.cos(ang)), round(26 * math.sin(ang))
    ox, oz = (1 if cx > 0 else -1), (1 if cz > 0 else -1)          # outward
    # chimney 3x3 hollow, outside corner
    hx, hz = cx + ox * 2, cz + oz * 2
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            for y in range(1, 17):
                if dx == 0 and dz == 0 and y > 1:
                    continue
                put(hx + dx, y, hz + dz, 'minecraft:bricks' if y > 12 else 'minecraft:deepslate_bricks')
    put(hx, 1, hz, 'minecraft:campfire[lit=true]')                   # smoke rises out of the chimney
    # the hearth in front of it
    put(cx, 1, cz, 'minecraft:blast_furnace[lit=true,facing=' + ('west' if ox > 0 else 'east') + ']')
    put(cx, 2, cz, 'minecraft:blast_furnace[lit=true,facing=' + ('west' if ox > 0 else 'east') + ']')
    put(cx + ox, 1, cz - oz, 'minecraft:lava_cauldron')
    put(cx - ox, 1, cz + oz, 'minecraft:anvil[facing=' + ('north' if oz > 0 else 'south') + ']')
    put(cx - ox * 2, 1, cz, 'minecraft:smithing_table')
    put(cx, 1, cz - oz * 2, 'minecraft:grindstone[face=floor,facing=north]')
    put(cx - ox, 1, cz - oz, 'minecraft:barrel[facing=up]')
    # a small roof of dark oak over the hearth, on chains
    for dx in range(-2, 2):
        for dz in range(-2, 2):
            put(cx - ox * dx, 5, cz - oz * dz, 'minecraft:dark_oak_slab[type=bottom]')
    for dx, dz in ((-2, -2), (1, -2), (-2, 1)):
        for y in range(2, 5):
            put(cx - ox * dx, y, cz - oz * dz, 'minecraft:dark_oak_fence')

# ---------------------------------------------------------------- questmaster stall (east) and notice board (west)
def stall(x0, z0, title):
    # 6 x 5 roofed stall, open towards the plaza (west)
    for x in range(x0, x0 + 6):
        for z in range(z0, z0 + 5):
            put(x, 0, z, 'minecraft:spruce_planks')
            put(x, 5, z, 'minecraft:spruce_slab[type=bottom]' if (x + z) % 2 else 'minecraft:dark_oak_slab[type=bottom]')
    for x, z in ((x0, z0), (x0 + 5, z0), (x0, z0 + 4), (x0 + 5, z0 + 4)):
        for y in range(1, 5):
            put(x, y, z, 'minecraft:stripped_dark_oak_log[axis=y]')
    for z in range(z0, z0 + 5):
        for y in range(1, 5):
            if (x0 + 5, y, z) not in B:
                put(x0 + 5, y, z, 'minecraft:dark_oak_planks')
    for z in range(z0 + 1, z0 + 4):
        put(x0 + 1, 1, z, 'minecraft:spruce_trapdoor[facing=west,half=top,open=false]')   # counter
    put(x0 + 4, 1, z0 + 1, 'minecraft:barrel[facing=up]')
    put(x0 + 4, 1, z0 + 3, 'minecraft:lectern[facing=west]')
    put(x0 + 4, 2, z0 + 1, 'minecraft:lantern')
    put(x0 + 2, 4, z0 + 2, 'minecraft:lantern[hanging=true]')
    put(x0 - 1, 1, z0 + 2, 'minecraft:spruce_sign[rotation=4]')
    SIGNS[(x0 - 1, 1, z0 + 2)] = title


stall(22, -12, ['', '&6&lQUESTMASTER', '&7daily & weekly', '&7quests'])

# notice board: a dark oak wall facing east (the plaza) with signs
bx, bz = -26, -12
for z in range(bz, bz + 7):
    for y in range(1, 6):
        put(bx, y, z, 'minecraft:stripped_dark_oak_log[axis=y]' if z in (bz, bz + 6) else 'minecraft:dark_oak_planks')
    put(bx, 6, z, 'minecraft:dark_oak_slab[type=bottom]')
    put(bx + 1, 6, z, 'minecraft:dark_oak_slab[type=bottom]')
put(bx + 1, 5, bz + 3, 'minecraft:lantern[hanging=true]')
BOARD = [
    ['&6&lFORGEBOUND', '&eSMP', '', '&7Season 1'],
    ['&eLegends exist', '&eonly once.', '&7Forge them,', '&7defend them.'],
    ['&6Wiki', '&ffreonik.github.io', '&f/Forgebound-', '&freleases'],
    ['&eCommands', '&f/quests  /shop', '&f/team  /top', '&f/events'],
    ['&eForge', '&7Shift + right-click', '&7a crafting table', ''],
]
for k, lines in enumerate(BOARD):
    pos = (bx + 1, 3 if k % 2 == 0 else 2, bz + 1 + k)
    put(*pos, 'minecraft:dark_oak_wall_sign[facing=east]')
    SIGNS[pos] = lines

# ---------------------------------------------------------------- paths of light from the gates to the circle
for t in range(13, R_WALL_IN):
    for gx, gz in ((0, -1), (0, 1), (1, 0), (-1, 0)):
        for w in (-2, 2):
            x, z = (w, gz * t) if gx == 0 else (gx * t, w)
            if t % 4 == 0:
                put(x, 1, z, 'minecraft:polished_blackstone_wall')
                put(x, 2, z, 'minecraft:lantern')


# ---------------------------------------------------------------- write
def write(path):
    palette = {}
    lines = []
    for pos, blk in sorted(B.items(), key=lambda kv: (kv[0][1], kv[0][0], kv[0][2])):
        if blk not in palette:
            palette[blk] = len(palette)
        lines.append(f'B {pos[0]} {pos[1]} {pos[2]} {palette[blk]}')
    out = [f'P {i} {b}' for b, i in palette.items()] + lines
    out += [f'S {p[0]} {p[1]} {p[2]} ' + '|'.join(t) for p, t in SIGNS.items()]
    with gzip.open(path, 'wt', encoding='utf-8') as f:
        f.write('\n'.join(out) + '\n')
    print(f'{len(B)} blocks, {len(palette)} block types, {len(SIGNS)} signs -> {path}')


COLORS = {
    'blackstone': (40, 34, 40), 'gilded': (150, 110, 40), 'deepslate': (70, 70, 76), 'basalt': (88, 88, 92),
    'shroomlight': (240, 150, 70), 'magma': (200, 80, 20), 'fire': (255, 160, 40), 'campfire': (255, 140, 40),
    'chain': (60, 64, 72), 'lantern': (250, 200, 110), 'bricks': (150, 72, 56), 'dark_oak': (66, 44, 24),
    'spruce': (110, 80, 48), 'iron_bars': (150, 150, 150), 'lava': (255, 120, 20), 'anvil': (60, 60, 60),
    'smithing': (60, 50, 50), 'blast': (90, 90, 95), 'grindstone': (130, 130, 130), 'barrel': (120, 86, 50),
    'lectern': (150, 110, 70), 'sign': (80, 56, 32),
}


def color(blk):
    name = blk.split(':')[1].split('[')[0]
    for k in ('gilded', 'shroomlight', 'magma', 'campfire', 'fire', 'lantern', 'chain', 'lava', 'iron_bars',
              'basalt', 'bricks' if name == 'bricks' else '#', 'dark_oak', 'spruce', 'anvil', 'smithing', 'blast',
              'grindstone', 'barrel', 'lectern', 'sign', 'blackstone', 'deepslate'):
        if k in name:
            c = COLORS[k]
            return c if 'cracked' not in name else tuple(int(v * 0.85) for v in c)
    return (120, 120, 120)


def preview(path):
    from PIL import Image, ImageDraw
    S = 7                                           # half-width of a block in pixels
    W, H = 2 * S * 72 + 40, S * 72 + 34 * 2 * S + 60
    img = Image.new('RGB', (W, H), (24, 22, 30))
    d = ImageDraw.Draw(img)
    ox, oy = W // 2, 34 * 2 * S + 30

    def iso(x, y, z):
        return ox + (x - z) * S, oy + (x + z) * S // 2 - y * S

    def shade(c, f):
        return tuple(max(0, min(255, int(v * f))) for v in c)

    for (x, y, z) in sorted(B, key=lambda p: (p[0] + p[2], p[1])):
        c = color(B[(x, y, z)])
        top = [iso(x, y + 1, z), iso(x + 1, y + 1, z), iso(x + 1, y + 1, z + 1), iso(x, y + 1, z + 1)]
        left = [iso(x, y + 1, z + 1), iso(x + 1, y + 1, z + 1), iso(x + 1, y, z + 1), iso(x, y, z + 1)]
        right = [iso(x + 1, y + 1, z), iso(x + 1, y + 1, z + 1), iso(x + 1, y, z + 1), iso(x + 1, y, z)]
        d.polygon(left, fill=shade(c, 0.7))
        d.polygon(right, fill=shade(c, 0.85))
        d.polygon(top, fill=c)
    img = img.crop(img.getbbox() if False else (0, 0, W, H))
    bg = Image.new('RGB', img.size, (24, 22, 30))
    from PIL import ImageChops
    box = ImageChops.difference(img, bg).getbbox()
    if box:
        img = img.crop((box[0] - 20, box[1] - 20, box[2] + 20, box[3] + 20))
    img = img.resize((img.width * 3 // 2, img.height * 3 // 2), Image.LANCZOS)
    img.save(path)
    print('preview', path)


if __name__ == '__main__':
    write(sys.argv[1])
    if len(sys.argv) > 2:
        preview(sys.argv[2])
