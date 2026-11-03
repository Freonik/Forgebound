"""Mythic event weapons: Worldsplitter (axe), Bloodfang (sword), Titan's Echo (greatsword). Animated, 32x32."""
import sys, math
sys.path.insert(0, '/home/claude/art')
from px import *

OUT = sys.argv[1]

VOID = [(10, 4, 20), (24, 8, 44), (44, 16, 76), (70, 30, 116), (106, 50, 160), (150, 86, 206), (200, 140, 240), (246, 214, 255)]
AMETH = [(60, 20, 100), (120, 50, 190), (200, 110, 255), (250, 210, 255)]
OBSID = [(8, 6, 14), (20, 14, 32), (36, 26, 54), (58, 44, 84)]
BLOOD = [(36, 2, 8), (70, 4, 14), (112, 10, 22), (158, 20, 32), (204, 40, 46), (240, 96, 90), (255, 190, 170)]
BONE = [(110, 98, 80), (160, 148, 124), (206, 196, 172), (240, 234, 214)]
DARK = [(20, 10, 12), (40, 22, 24), (64, 38, 38)]
BRONZE = [(52, 30, 10), (92, 56, 20), (138, 90, 36), (186, 132, 58), (226, 180, 96), (252, 228, 160)]
STONE = [(22, 20, 22), (36, 33, 34), (52, 48, 48), (70, 64, 62), (92, 84, 80)]
MAGMA = [(120, 20, 0), (200, 60, 0), (255, 130, 20), (255, 210, 90), (255, 250, 210)]


def U(fn, OX=3.0, OY=29.0):
    def f(x, y):
        u, v = diag(x, y, OX, OY)
        return fn(u, v)
    return f


def lit(v, w):
    t = (w - v) / (2 * w) if w > 0 else 0.5
    return max(0.0, min(1.0, t))


# ============================================================ Worldsplitter: a void double-bit axe with a living crack
def worldsplitter(f, n):
    c = Canvas()
    pulse = 0.5 + 0.5 * math.sin(2 * math.pi * f / n)
    shine = (f / n) * 30 - 6
    HU = 23.0

    def handle(u, v):
        if 1.6 <= u <= HU + 4.6 and abs(v) <= 1.0:
            if int(u) in (5, 9, 13, 17):
                return ramp(AMETH, 0.3 + 0.6 * lit(v, 1.0))
            return ramp(OBSID, 0.1 + 0.85 * lit(v, 1.0))
        if HU + 4.6 < u < HU + 7.0 and abs(v) <= 1.1 - (u - HU - 4.6) * 0.45:
            return ramp(AMETH, 0.45 + 0.5 * pulse)          # glowing spike on top
        if math.hypot(u - 0.9, v) <= 1.7:
            return ramp(AMETH, 0.4 + 0.5 * pulse)           # pommel gem
        return None
    c.fill(U(handle), 'handle')

    def head(u, v):
        du = u - HU
        side = abs(v)
        if side < 1.2:
            return None
        depth = side - 1.0
        R = 9.6
        dd = math.hypot(du * 0.95, depth - 0.2)
        if dd > R or depth > 9.4:
            return None
        if math.hypot(du + 7.4, depth - 2.4) < 4.0 or math.hypot(du - 7.6, depth - 2.4) < 3.8:
            return None                                        # carved beards on both bits
        if R - dd < 1.1:                                       # cutting edge with a sliding glint
            g = max(0, 1 - abs((du + depth) - shine + 10) / 2.4)
            return ramp(VOID, min(0.99, 0.78 + 0.21 * g))
        # the crack: a jagged glowing line through the middle of each bit
        k = du + 1.6 * math.sin(depth * 1.7) + (0.6 if v > 0 else -0.6)
        if abs(k) < 0.55 and depth > 1.2:
            return ramp(AMETH, 0.55 + 0.45 * pulse)
        if abs(k) < 1.1 and depth > 1.2:
            return ramp(VOID, 0.45 + 0.25 * pulse)
        if R - dd < 1.9:
            return ramp(VOID, 0.5)                             # bevel
        t = 0.12 + 0.025 * depth + (0.08 if v < 0 else 0)
        if (int(du * 2.3) * 7 + int(depth * 2.1) * 13) % 23 == 0:
            t = 0.75                                           # a few stars in the void
        return ramp(VOID, min(0.99, t))
    c.fill(U(head), 'head')
    c.outline((6, 2, 12, 255))
    # sparkles drifting off the crack
    for i in range(2):
        a = (f / n + i * 0.5) % 1.0
        x, y = int(17 + 6 * a), int(9 - 5 * a + i * 6)
        if (x, y) not in c.px and 0 <= x < 32 and 0 <= y < 32 and a < 0.8:
            c.set(x, y, ramp(AMETH, 0.9 - a), 'spark')
    return c.image()


# ============================================================ Bloodfang: a curved fang-blade with a serrated back
def bloodfang(f, n):
    c = Canvas()
    shine = (f / n) * 40 - 6
    drip = (f % n) / n
    G = 9.0                                                    # guard position along the axis

    def hilt(u, v):
        if 1.5 <= u < G - 1.2 and abs(v) <= 0.95:
            if int(u * 1.5) % 2 == 0:
                return ramp(DARK, 0.3 + 0.6 * lit(v, 0.95))     # wrapped grip
            return ramp(BONE, 0.2 + 0.6 * lit(v, 0.95))
        if math.hypot(u - 1.0, v) <= 1.6:
            return ramp(BLOOD, 0.55)                           # pommel stone
        if G - 1.2 <= u <= G + 0.4:
            # bone guard curving towards the edge like two teeth
            if abs(v) <= 3.6 - abs(u - G + 0.4) * 0.2:
                return ramp(BONE, 0.3 + 0.6 * lit(v, 3.6))
        return None
    c.fill(U(hilt), 'hilt')

    def blade(u, v):
        t = u - G
        if t < 0 or t > 24:
            return None
        bend = 0.0065 * t * t                                  # the blade curves back like a fang
        w = 2.7 * (1 - (t / 24.5) ** 1.6) + 0.3
        cv = v + bend
        if cv < -w or cv > w * 0.9:
            return None
        # serrated back (v < 0 side)
        if cv < -w + 0.9 and 3 < t < 19 and int(t * 1.1) % 3 == 0:
            return None
        if cv > w * 0.9 - 0.8:                                 # bright cutting edge + glint
            g = max(0, 1 - abs(t - shine) / 2.2)
            return ramp(BLOOD, min(0.99, 0.72 + 0.27 * g))
        if abs(cv + 0.2) < 0.35 and t < 21:
            return ramp(BLOOD, 0.18)                           # dark blood groove
        return ramp(BLOOD, 0.3 + 0.3 * lit(cv, w))
    c.fill(U(blade), 'blade')
    c.outline((14, 0, 4, 255))
    # a drop of blood running down the blade
    u = 30 - 18 * drip
    x = int(round(3.0 + u * S2 - 0.5 + 1))
    y = int(round(29.0 - u * S2 - 0.5 + 1))
    if (x, y) in c.px and c.kind.get((x, y)) == 'blade':
        c.set(x, y, ramp(BLOOD, 0.62), 'blade')
    return c.image()


# ============================================================ Titan's Echo: a broad bronze greatsword with a molten core
def titans_echo(f, n):
    c = Canvas()
    pulse = 0.5 + 0.5 * math.sin(2 * math.pi * f / n)
    flow = (f / n) * 6.0
    G = 8.5

    def hilt(u, v):
        if 1.4 <= u < G - 1.0 and abs(v) <= 1.0:
            if int(u * 1.2) % 3 == 0:
                return ramp(BRONZE, 0.5 + 0.4 * lit(v, 1.0))
            return ramp(STONE, 0.2 + 0.6 * lit(v, 1.0))
        if math.hypot(u - 0.9, v) <= 1.8:
            return ramp(MAGMA, 0.45 + 0.5 * pulse)             # molten pommel
        if G - 1.0 <= u <= G + 0.8 and abs(v) <= 4.2:
            if abs(v) > 3.2 and u < G:
                return None
            return ramp(BRONZE, 0.25 + 0.65 * lit(v, 4.2))     # heavy crossguard
        return None
    c.fill(U(hilt), 'hilt')

    def blade(u, v):
        t = u - G - 0.8
        if t < 0:
            return None
        L = 27.0
        w = 2.9 if t < L - 4 else 2.9 * (L - t) / 4
        if t > L or abs(v) > w:
            return None
        if abs(v) > w - 0.75:                                  # bronze edges
            return ramp(BRONZE, 0.55 + 0.4 * lit(v, w))
        if abs(v) < 0.75 and t < L - 3:                        # the molten core, flowing up the blade
            k = ((t * 0.5 - flow) % 3.0) / 3.0
            return ramp(MAGMA, 0.25 + 0.5 * k + 0.2 * pulse)
        crack = (int(t * 0.9) + int(abs(v) * 2)) % 7 == 0
        return ramp(STONE, (0.3 if not crack else 0.05) + 0.6 * lit(v, w))
    c.fill(U(blade), 'blade')
    c.outline((16, 8, 2, 255))
    return c.image()


IRON = [(14, 14, 18), (30, 30, 36), (50, 50, 58), (76, 76, 86), (110, 110, 122), (150, 150, 162), (196, 196, 206)]
EMBER = [(70, 10, 30), (140, 20, 50), (210, 50, 70), (255, 120, 110), (255, 210, 190)]


# ============================================================ Chainbreaker: a dark iron axe, a chain coiled up the haft,
# a broken shackle hanging from the head
def chainbreaker(f, n):
    c = Canvas()
    pulse = 0.5 + 0.5 * math.sin(2 * math.pi * f / n)
    shine = (f / n) * 30 - 6
    HU = 22.0

    def haft(u, v):
        if 1.6 <= u <= HU + 3.0 and abs(v) <= 1.0:
            # a chain coiled around the haft: links alternate along it
            k = (u * 0.9) % 3.0
            if 3.0 <= u <= HU - 1.0 and k < 1.4:
                ring = abs(abs(v) - 0.5) < 0.6 and (k < 0.5 or k > 0.9)
                return ramp(IRON, (0.75 if ring else 0.55) + 0.2 * lit(v, 1.0))
            return ramp(DARK, 0.2 + 0.8 * lit(v, 1.0))
        if math.hypot(u - 0.9, v) <= 1.7:
            return ramp(EMBER, 0.35 + 0.5 * pulse)          # ember pommel
        return None
    c.fill(U(haft), 'haft')

    def head(u, v):
        du = u - HU
        if v > -1.1:                                        # single bit, on one side
            return None
        depth = -v - 1.0
        R = 9.8
        dd = math.hypot(du * 0.9, depth - 0.6)
        if dd > R or depth > 9.6:
            return None
        if math.hypot(du + 7.8, depth - 3.0) < 4.2:
            return None                                     # bearded underside
        if R - dd < 1.1:                                    # edge with a sliding glint
            g = max(0, 1 - abs((du + depth) - shine + 10) / 2.4)
            return ramp(IRON, min(0.99, 0.72 + 0.27 * g))
        # rivets of the shackle band near the haft
        if depth < 2.2 and abs(du) < 3.4:
            if int(du + 4) % 3 == 0 and depth > 0.8:
                return ramp(IRON, 0.85)
            return ramp(IRON, 0.45)
        # ember cracks from the broken chains
        k = du + 1.3 * math.sin(depth * 1.5)
        if abs(k - 1.5) < 0.5 and depth > 2.4:
            return ramp(EMBER, 0.4 + 0.5 * pulse)
        if R - dd < 1.9:
            return ramp(IRON, 0.5)
        return ramp(IRON, 0.34 + 0.035 * depth + (0.08 if du > 0 else 0))
    c.fill(U(head), 'head')
    c.outline((6, 4, 8, 255))
    # the broken shackle hanging off the back of the head (pixel-placed, swings a little)
    sw = 1 if pulse > 0.66 else (0 if pulse > 0.33 else -1)
    for dx, dy in [(0, 0), (1, 0), (2, 0), (3, 1), (3, 2), (3, 3), (2, 4), (1, 4), (0, 4), (-1, 3), (-1, 2), (-1, 1)]:
        x, y = 18 + dx + sw, 14 + dy
        if 0 <= x < 32 and 0 <= y < 32 and c.kind.get((x, y)) != 'head':
            c.set(x, y, ramp(IRON, 0.85 if dy < 2 else 0.65), 'shackle')
    for x, y in [(20 + sw, 19), (21 + sw, 20)]:
        if c.kind.get((x, y)) != 'head':
            c.set(x, y, ramp(EMBER, 0.6 + 0.3 * pulse), 'shackle')        # the snapped, glowing end
    for i in range(2):
        a = (f / n + i * 0.5) % 1.0
        x, y = int(12 + 5 * a), int(6 - 4 * a + i * 5)
        if (x, y) not in c.px and 0 <= x < 32 and 0 <= y < 32 and a < 0.8:
            c.set(x, y, ramp(EMBER, 0.9 - a), 'spark')
    return c.image()


ALL = {'worldsplitter': worldsplitter, 'bloodfang': bloodfang, 'titans_echo': titans_echo, 'chainbreaker': chainbreaker}
if __name__ == '__main__':
    previews = []
    for name, fn in ALL.items():
        frames = [fn(i, 12) for i in range(12)]
        save(frames, f'{OUT}/{name}.png', frametime=3)
        previews.append(frames[0])
    if len(sys.argv) > 2:
        preview(previews, sys.argv[2])
