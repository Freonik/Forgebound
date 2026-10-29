"""Sealed Tomes (common / rare / legendary) and the rune book a tome turns into (32x32, animated).

A thick leather book seen slightly from the side: spine on the left, page edges on the right and bottom,
metal corner caps, two chains crossing the cover and a glowing seal where they meet.
"""
import sys, math, os
sys.path.insert(0, '/home/claude/art')
from px import *

OUT = sys.argv[1]
PREVIEW = sys.argv[2] if len(sys.argv) > 2 else None

PAGE = [(150, 132, 100), (196, 178, 140), (232, 220, 188), (250, 244, 222)]
IRON = [(22, 22, 28), (44, 44, 52), (78, 78, 90), (124, 124, 138), (182, 182, 196), (230, 230, 240)]
GOLD = [(84, 48, 10), (138, 88, 22), (192, 136, 40), (236, 186, 72), (255, 228, 140), (255, 250, 214)]

THEMES = {
    'common': dict(cover=[(12, 34, 18), (22, 58, 30), (36, 88, 46), (56, 120, 64), (84, 156, 90)],
                   metal=IRON, glow=[(10, 70, 30), (30, 150, 70), (90, 230, 120), (190, 255, 200), (245, 255, 245)]),
    'rare': dict(cover=[(10, 18, 44), (18, 32, 78), (30, 54, 118), (48, 82, 160), (76, 118, 200)],
                 metal=IRON, glow=[(10, 40, 90), (30, 100, 200), (80, 180, 255), (180, 230, 255), (245, 252, 255)]),
    'legendary': dict(cover=[(30, 6, 8), (58, 12, 14), (92, 20, 20), (130, 34, 28), (170, 56, 40)],
                      metal=GOLD, glow=[(120, 50, 0), (220, 120, 10), (255, 190, 50), (255, 235, 150), (255, 255, 235)]),
    'rune': dict(cover=[(14, 8, 24), (28, 14, 46), (46, 24, 74), (70, 38, 108), (100, 58, 146)],
                 metal=IRON, glow=[(60, 10, 110), (130, 40, 210), (190, 110, 255), (230, 190, 255), (252, 245, 255)]),
}

# the book's outline (cover), x0..x1 / y0..y1, spine on the left, pages on the right/bottom
X0, X1, Y0, Y1 = 6, 24, 4, 26
RUNES = [  # tiny 3x3 glyphs scattered on the cover
    ["#.#", ".#.", "#.#"], ["##.", ".#.", ".##"], [".#.", "###", ".#."], ["#..", "##.", "#.#"], [".##", "#..", "##."],
]


def book(theme, f, n):
    t = THEMES[theme]
    c = Canvas()
    pulse = 0.5 + 0.5 * math.sin(2 * math.pi * f / n)
    sealed = theme != 'rune'
    cx, cy = (X0 + X1) / 2 + 1, (Y0 + Y1) / 2

    def px(x, y):
        # page block: right edge and bottom edge, slightly offset (thickness)
        if X1 < x <= X1 + 2 and Y0 + 2 <= y <= Y1 + 1:
            return ramp(PAGE, 0.35 + 0.5 * ((y + x) % 2)) if x == X1 + 1 else ramp(PAGE, 0.15)
        if Y1 < y <= Y1 + 2 and X0 + 3 <= x <= X1 + 1:
            return ramp(PAGE, 0.45 + 0.4 * (x % 2)) if y == Y1 + 1 else ramp(PAGE, 0.1)
        if not (X0 <= x <= X1 and Y0 <= y <= Y1):
            return None
        # spine
        if x <= X0 + 2:
            if y in (Y0 + 3, Y0 + 4, Y1 - 4, Y1 - 3):
                return ramp(t['metal'], 0.55 if y % 2 else 0.35)            # spine bands
            return ramp(t['cover'], 0.12 + 0.08 * (x - X0))
        # metal corner caps
        corner = (x >= X1 - 3 and (y <= Y0 + 3 or y >= Y1 - 3)) and (abs(x - X1) + min(abs(y - Y0), abs(y - Y1)) <= 4)
        if corner:
            return ramp(t['metal'], 0.85 if (x == X1 or y in (Y0, Y1)) else 0.5)
        # cover: lit from the top-left, a raised frame
        frame = x in (X0 + 4, X1 - 1) or y in (Y0 + 2, Y1 - 2)
        inside = X0 + 4 <= x <= X1 - 1 and Y0 + 2 <= y <= Y1 - 2
        light = 0.65 - 0.35 * ((x - X0) + (y - Y0)) / ((X1 - X0) + (Y1 - Y0))
        if frame and inside:
            return ramp(t['metal'], 0.42 + 0.2 * light)
        col = ramp(t['cover'], 0.2 + 0.6 * light + (0.06 if (x * 7 + y * 3) % 5 == 0 else 0))
        return col

    c.fill(px)

    # glowing runes on the cover (they pulse; the rune book is covered in them)
    spots = [(X0 + 6, Y0 + 4), (X1 - 6, Y0 + 4), (X0 + 6, Y1 - 6), (X1 - 6, Y1 - 6)] if sealed else \
            [(X0 + 6, Y0 + 4), (X1 - 6, Y0 + 4), (X0 + 6, Y1 - 6), (X1 - 6, Y1 - 6), (X0 + 6, int(cy) - 1), (X1 - 6, int(cy) - 1)]
    for i, (sx, sy) in enumerate(spots):
        glyph = RUNES[i % len(RUNES)]
        phase = 0.5 + 0.5 * math.sin(2 * math.pi * (f / n + i / len(spots)))
        for gy, row in enumerate(glyph):
            for gx, ch in enumerate(row):
                if ch == '#':
                    c.set(sx + gx - 1, sy + gy, ramp(t['glow'], 0.25 + 0.55 * phase))

    if sealed:
        # two chains crossing the cover
        for k in range(-11, 12):
            for sgn in (1, -1):
                x = int(round(cx + k * 0.82))
                y = int(round(cy + sgn * k * 1.0))
                if X0 + 3 <= x <= X1 + 1 and Y0 - 1 <= y <= Y1 + 1:
                    link = (k % 2 == 0)
                    c.set(x, y, ramp(IRON, 0.8 if link else 0.45))
                    c.set(x + 1, y, ramp(IRON, 0.25))
        # the seal where they meet: a metal ring with a glowing gem
        for y in range(int(cy) - 4, int(cy) + 5):
            for x in range(int(cx) - 4, int(cx) + 5):
                d = math.hypot(x + 0.5 - cx - 0.5, y + 0.5 - cy - 0.5)
                if d <= 2.2:
                    c.set(x, y, ramp(t['glow'], 0.98 - 0.5 * d / 2.2 + 0.0 * pulse - 0.15 * (1 - pulse)))
                elif d <= 3.6:
                    c.set(x, y, ramp(t['metal'], 0.75 if (x + y) % 3 else 0.4))
        # a highlight on the gem
        c.set(int(cx) - 1, int(cy) - 1, ramp(t['glow'], 0.99))
        if theme == 'legendary':
            # sparks drifting up around the book
            for i in range(4):
                a = 2 * math.pi * (f / n + i / 4)
                sx = int(cx + 12 * math.cos(a))
                sy = int(Y0 + 2 + (f * 2 + i * 7) % 22)
                if 0 <= sx < 32 and (sx < X0 - 1 or sx > X1 + 3):
                    c.set(sx, sy, ramp(GOLD, 0.95))
    else:
        # the rune book: a glowing circle of runes in the middle
        for a in range(0, 360, 15):
            r = 4.5
            x = int(round(cx + r * math.cos(math.radians(a))))
            y = int(round(cy + r * math.sin(math.radians(a))))
            on = ((a // 15) + f) % 3 != 0
            c.set(x, y, ramp(t['glow'], 0.85 if on else 0.4))
        for x in range(int(cx) - 1, int(cx) + 2):
            for y in range(int(cy) - 1, int(cy) + 2):
                c.set(x, y, ramp(t['glow'], 0.6 + 0.39 * pulse if (x, y) == (int(cx), int(cy)) else 0.5 + 0.3 * pulse))

    c.outline((8, 6, 10, 255))
    return c.image()


os.makedirs(OUT, exist_ok=True)
N = 12
previews = []
for theme in ('common', 'rare', 'legendary', 'rune'):
    frames = [book(theme, f, N) for f in range(N)]
    name = 'rune_book' if theme == 'rune' else f'sealed_tome_{theme}'
    save(frames, f'{OUT}/{name}.png', frametime=3, interpolate=True)
    previews.append(frames)
if PREVIEW:
    preview([p[0] for p in previews], PREVIEW, scale=10)
    big = []
    from PIL import Image
    for f in range(N):
        row = Image.new('RGBA', (4 * 32 + 3 * 4, 32), (0, 0, 0, 0))
        for i, p in enumerate(previews):
            row.paste(p[f], (i * 36, 0))
        big.append(row)
    gif(big, PREVIEW.replace('.png', '.gif'), scale=8, ms=150)
print('tomes done')
