"""Shared toolkit for the 3D weapon models (block elements + a painted 64x64 atlas, 4 px per model unit).

A weapon script builds a Model: atlas regions (each with a painter), boxes, then write(). Weapons are modelled
upright along +Y around the centre line x = z = 8, with the middle of the grip at `grip_y`; write() works out the
hand transforms so the grip sits exactly where a vanilla sword's grip sits (same blade direction), fits the
inventory icon, item frame and dropped item automatically, and checks Minecraft's limits.
"""
import json, math, random
from PIL import Image

N = 64
C = 8.0


# ------------------------------------------------------------------ colour helpers

def clamp(v, a=0, b=255):
    return max(a, min(b, int(round(v))))


def mix(c1, c2, t):
    return tuple(clamp(c1[i] + (c2[i] - c1[i]) * t) for i in range(3))


def ramp(cols, t):
    t = max(0.0, min(0.999, t))
    x = t * (len(cols) - 1)
    i = int(x)
    return mix(cols[i], cols[i + 1], x - i)


def _noise(seed):
    rnd = random.Random(seed)
    return [[rnd.random() for _ in range(N)] for _ in range(N)]


NZ = _noise(7)
NZ2 = _noise(11)

# shared palettes
IRON = [(8, 8, 11), (20, 20, 25), (34, 34, 41), (52, 52, 61), (78, 78, 90), (116, 116, 130), (170, 172, 186), (228, 230, 240)]
STEEL = [(18, 20, 28), (40, 44, 58), (70, 76, 94), (110, 118, 138), (160, 168, 188), (210, 216, 232), (248, 250, 255)]
LEATHER = [(16, 10, 8), (34, 22, 16), (56, 36, 24), (80, 54, 36)]
GOLD = [(60, 34, 8), (110, 70, 18), (168, 116, 34), (218, 168, 62), (250, 214, 116), (255, 244, 200)]
BRONZE = [(40, 22, 8), (80, 48, 18), (128, 84, 34), (178, 126, 56), (220, 172, 92), (250, 222, 160)]
STONE = [(14, 12, 14), (30, 27, 30), (48, 44, 46), (68, 63, 64), (92, 86, 84)]
SOUL = [(4, 30, 22), (10, 80, 52), (30, 150, 96), (80, 220, 150), (170, 255, 210), (240, 255, 248)]
EMBER = [(40, 4, 8), (110, 14, 24), (190, 36, 40), (250, 96, 70), (255, 176, 130), (255, 236, 210)]
MAGMA = [(60, 8, 0), (140, 30, 0), (220, 80, 8), (255, 150, 30), (255, 214, 110), (255, 250, 220)]
VOID = [(8, 3, 18), (22, 8, 42), (44, 16, 76), (76, 32, 124), (120, 60, 180), (176, 110, 236), (232, 196, 255)]
BLOOD = [(30, 2, 6), (66, 4, 12), (110, 10, 22), (160, 20, 32), (210, 44, 50), (245, 110, 100), (255, 190, 170)]
BONE = [(96, 86, 70), (140, 128, 106), (186, 176, 150), (226, 218, 196), (250, 246, 232)]
FROST = [(10, 26, 52), (20, 50, 92), (36, 84, 140), (64, 128, 188), (112, 180, 230), (180, 226, 252), (238, 252, 255)]


# ------------------------------------------------------------------ painters: fn(px, (x0, y0, x1, y1), frame, frames)

def plate(pal, base=0.3, rivets=True, seed_shift=0):
    """Hammered metal plate with a darker rim, scratches and rivets."""
    def paint(px, r, f, n):
        x0, y0, x1, y1 = r
        w, h = x1 - x0, y1 - y0
        for y in range(y0, y1):
            for x in range(x0, x1):
                lx, ly = x - x0, y - y0
                t = base + 0.10 * NZ[(y + seed_shift) % N][x] + 0.05 * math.sin(lx * 0.7 + ly * 0.3)
                rim = min(lx, ly, w - 1 - lx, h - 1 - ly)
                if rim == 0:
                    t = base * 0.4
                elif rim == 1:
                    t += 0.1 if (lx < 2 or ly < 2) else -0.05
                if (lx * 3 + ly * 7) % 29 == 0 and rim > 2:
                    t += 0.14
                if rivets and w >= 16 and lx % 10 == 4 and ly % 10 == 4:
                    t = base + 0.34
                px[x, y] = ramp(pal, t) + (255,)
    return paint


def edge(pal, base=0.6):
    """Bright worn metal (edges, rings, caps)."""
    def paint(px, r, f, n):
        x0, y0, x1, y1 = r
        for y in range(y0, y1):
            for x in range(x0, x1):
                t = base + 0.16 * NZ2[y][x] - 0.015 * (y - y0)
                if x in (x0, x1 - 1) or y in (y0, y1 - 1):
                    t = base - 0.22
                px[x, y] = ramp(pal, t) + (255,)
    return paint


def sheen(pal, lo=0.25, hi=0.75):
    """Polished metal with a bright band across (blades, spikes)."""
    def paint(px, r, f, n):
        x0, y0, x1, y1 = r
        w = x1 - x0
        for y in range(y0, y1):
            for x in range(x0, x1):
                lx = x - x0
                t = lo + (hi - lo) * max(0, 1 - abs(lx - w * 0.4) / (w * 0.6)) + 0.05 * NZ2[y][x]
                px[x, y] = ramp(pal, t) + (255,)
    return paint


def flow(pal, speed=2, base=0.45):
    """Glowing flowing energy (lava, soul fire, void), animated."""
    def paint(px, r, f, n):
        x0, y0, x1, y1 = r
        pulse = 0.5 + 0.5 * math.sin(2 * math.pi * f / n)
        for y in range(y0, y1):
            for x in range(x0, x1):
                lx, ly = x - x0, y - y0
                wave = math.sin((ly + f * speed) * 0.55 + math.sin(lx * 0.6) * 1.6)
                t = base + 0.22 * wave + 0.22 * pulse + 0.1 * NZ[y][x]
                px[x, y] = ramp(pal, t) + (255,)
    return paint


def wrap(pal):
    """Leather / cloth grip wrap: diagonal bands."""
    def paint(px, r, f, n):
        x0, y0, x1, y1 = r
        for y in range(y0, y1):
            for x in range(x0, x1):
                k = (x - x0 + y - y0) % 6
                t = 0.8 if k == 0 else (0.12 if k == 5 else 0.45 + 0.15 * NZ[y][x])
                px[x, y] = ramp(pal, t) + (255,)
    return paint


def seam(pal, glow, every=9):
    """Dark plate with a glowing seam down the middle and glyph notches."""
    def paint(px, r, f, n):
        x0, y0, x1, y1 = r
        w = x1 - x0
        pulse = 0.5 + 0.5 * math.sin(2 * math.pi * f / n)
        for y in range(y0, y1):
            for x in range(x0, x1):
                lx, ly = x - x0, y - y0
                c = ramp(pal, 0.22 + 0.08 * NZ[y][x] + (0.12 if lx in (1, 2) else 0) - (0.08 if lx >= w - 2 else 0))
                if lx in (0, w - 1):
                    c = ramp(pal, 0.08)
                mid = abs(lx - (w - 1) / 2)
                if mid < 1.0 and ly % every not in (0, 1):
                    c = ramp(glow, 0.3 + 0.4 * pulse + 0.15 * math.sin(ly * 0.8 + f))
                elif mid < 3.0 and ly % every == every // 2:
                    c = ramp(glow, 0.25 + 0.3 * pulse)
                px[x, y] = c + (255,)
    return paint


def veined(pal, glow, density=0.12):
    """Stone / crystal with glowing veins."""
    def paint(px, r, f, n):
        x0, y0, x1, y1 = r
        pulse = 0.5 + 0.5 * math.sin(2 * math.pi * f / n)
        for y in range(y0, y1):
            for x in range(x0, x1):
                lx, ly = x - x0, y - y0
                c = ramp(pal, 0.3 + 0.5 * NZ2[y][x] * NZ[y][x])
                if abs(math.sin(lx * 0.5 + math.sin(ly * 0.7) * 2.2)) < density:
                    c = ramp(glow, 0.3 + 0.4 * pulse)
                px[x, y] = c + (255,)
    return paint


def cracked(pal, glow, density=0.09, base=0.3):
    """Metal or stone with glowing cracks (zig-zag), animated pulse."""
    def paint(px, r, f, n):
        x0, y0, x1, y1 = r
        pulse = 0.5 + 0.5 * math.sin(2 * math.pi * f / n)
        for y in range(y0, y1):
            for x in range(x0, x1):
                lx, ly = x - x0, y - y0
                c = ramp(pal, base + 0.12 * NZ[y][x] + 0.04 * math.sin(lx * 0.5 + ly * 0.2))
                k = abs(math.sin(lx * 0.42 + 2.0 * math.sin(ly * 0.33)) * math.cos(ly * 0.21 + lx * 0.07))
                if k < density:
                    c = ramp(glow, 0.45 + 0.4 * pulse)
                elif k < density * 2:
                    c = mix(c, ramp(glow, 0.2), 0.45)
                px[x, y] = c + (255,)
    return paint


def flame(pal, tongues=3):
    """Translucent flames licking upward (map the whole region with stretch=True)."""
    def paint(px, r, f, n):
        x0, y0, x1, y1 = r
        W, H = x1 - x0, y1 - y0
        for y in range(y0, y1):
            for x in range(x0, x1):
                lx, ly = x - x0, y - y0
                up = 1 - ly / (H - 1)
                dens = 0.0
                for k in range(tongues):
                    ph = f / n * 2 * math.pi + k * 2.1
                    top = 0.62 + 0.3 * (0.5 + 0.5 * math.sin(ph)) - (0.12 if k != tongues // 2 else 0)
                    if up >= top:
                        continue
                    hh = up / top
                    cx = W / 2 + (k - (tongues - 1) / 2) * W * 0.2 + 2.6 * math.sin(ly * 0.33 + ph) * hh
                    half = W * (0.2 if k == tongues // 2 else 0.15) * (1 - hh) ** 0.65 + 0.6
                    dens = max(dens, 1 - abs(lx - cx) / half)
                if dens <= 0:
                    continue
                heat = dens * (1 - up * 0.6)
                a = clamp(250 * min(1, dens * 1.8) * (1 - up ** 3) * (0.7 + 0.3 * NZ[y][x]))
                if a >= 25:
                    px[x, y] = ramp(pal, 0.2 + 0.8 * heat) + (a,)
    return paint


def solid(pal, t=0.1):
    def paint(px, r, f, n):
        x0, y0, x1, y1 = r
        for y in range(y0, y1):
            for x in range(x0, x1):
                px[x, y] = ramp(pal, t + 0.06 * NZ[y][x]) + (255,)
    return paint


def starfield(pal, star):
    """Deep void with twinkling stars."""
    def paint(px, r, f, n):
        x0, y0, x1, y1 = r
        for y in range(y0, y1):
            for x in range(x0, x1):
                c = ramp(pal, 0.1 + 0.25 * NZ[y][x] * NZ2[y][x] + 0.08 * math.sin((x + y) * 0.2 + f * 0.8))
                if NZ2[y][x] > 0.975:
                    tw = 0.5 + 0.5 * math.sin(f * 1.3 + NZ[y][x] * 20)
                    c = ramp(star, 0.4 + 0.6 * tw)
                px[x, y] = c + (255,)
    return paint


# ------------------------------------------------------------------ transforms (match the game's convention)

def _R(axis, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    if axis == 'x':
        return [[1, 0, 0], [0, c, -s], [0, s, c]]
    if axis == 'y':
        return [[c, 0, s], [0, 1, 0], [-s, 0, c]]
    return [[c, -s, 0], [s, c, 0], [0, 0, 1]]


def _mm(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def _mv(m, v):
    return [sum(m[i][k] * v[k] for k in range(3)) for i in range(3)]


# vanilla handheld sword: where its grip ends up and where its blade points (blocks), per context
_VANILLA = {
    'thirdperson': ((0.0, -0.009, 0.077), (0.0, 0.985, -0.174), 10),
    'firstperson': ((0.071, 0.002, -0.001), (0.0, 0.94, 0.342), -20),
}


class Model:
    def __init__(self, name, frames=8, frametime=3):
        self.name = name
        self.tex = f'customcraft:item/{name}'
        self.frames = frames
        self.frametime = frametime
        self.regions = {}
        self.painters = {}
        self.elements = []

    # -------- atlas
    def region(self, name, uv, painter):
        self.regions[name] = uv
        self.painters[name] = painter

    def _uv_for(self, name, w, h):
        u0, v0, u1, v1 = self.regions[name]
        return [round(u0, 3), round(v0, 3), round(u0 + min(w, u1 - u0), 3), round(v0 + min(h, v1 - v0), 3)]

    # -------- geometry
    def box(self, fr, to, tex, rot=None, glow=0, faces=None, side=None, stretch=False, ends=None, uv=None):
        """A cuboid. side: region for the 4 side faces, ends: region for up/down. stretch maps the whole region.
        uv: {face: [u0, v0, u1, v1]} explicit UVs (e.g. one big surface built from several boxes)."""
        sx, sy, sz = to[0] - fr[0], to[1] - fr[1], to[2] - fr[2]
        dims = {'north': (sx, sy), 'south': (sx, sy), 'east': (sz, sy), 'west': (sz, sy), 'up': (sx, sz), 'down': (sx, sz)}
        f = {}
        for name, (w, h) in dims.items():
            if faces is not None and name not in faces:
                continue
            reg = tex
            if name in ('up', 'down'):
                reg = ends or tex
            elif side:
                reg = side
            if uv and name in uv:
                fuv = [round(v, 3) for v in uv[name]]
            else:
                fuv = list(self.regions[reg]) if stretch else self._uv_for(reg, max(w, 0.25), max(h, 0.25))
            f[name] = {"uv": fuv, "texture": "#t"}
        e = {"from": [round(v, 3) for v in fr], "to": [round(v, 3) for v in to], "faces": f}
        if rot:
            e["rotation"] = rot
        if glow:
            e["light_emission"] = glow
        self.elements.append(e)
        return e

    def surface(self, reg, x0, x1, y0, y1, bx0, bx1, by0, by1):
        """UVs for the north/south faces of a box that is one piece of a bigger flat surface (bx/by = its bounds)."""
        u0, v0, u1, v1 = self.regions[reg]
        sx, sy = (u1 - u0) / (bx1 - bx0), (v1 - v0) / (by1 - by0)
        a, b = u0 + (x0 - bx0) * sx, u0 + (x1 - bx0) * sx
        c, d = v0 + (by1 - y1) * sy, v0 + (by1 - y0) * sy
        return {'south': [a, c, b, d], 'north': [b, c, a, d]}

    @staticmethod
    def rot(axis, angle, origin):
        return {"angle": angle, "axis": axis, "origin": [round(v, 3) for v in origin]}

    def ball(self, c, r, tex, glow=0):
        """A rough sphere: a cube plus copies turned 45 degrees about each axis."""
        x, y, z = c
        k = r * 0.82
        self.box((x - k, y - k, z - k), (x + k, y + k, z + k), tex, glow=glow)
        for ax in ('x', 'y', 'z'):
            self.box((x - k * 0.98, y - k * 0.98, z - k * 0.98), (x + k * 0.98, y + k * 0.98, z + k * 0.98), tex,
                     rot=self.rot(ax, 45, c), glow=glow)

    def ring(self, c, r, t, tex, glow=0, axis='y', angle=0):
        """A square ring of four bars around the y axis (a halo), optionally turned."""
        x, y, z = c
        rot = self.rot(axis, angle, c) if angle else None
        self.box((x - r, y - t / 2, z - r), (x + r, y + t / 2, z - r + t), tex, rot=rot, glow=glow)
        self.box((x - r, y - t / 2, z + r - t), (x + r, y + t / 2, z + r), tex, rot=rot, glow=glow)
        self.box((x - r, y - t / 2, z - r + t), (x - r + t, y + t / 2, z + r - t), tex, rot=rot, glow=glow)
        self.box((x + r - t, y - t / 2, z - r + t), (x + r, y + t / 2, z + r - t), tex, rot=rot, glow=glow)

    def plane(self, fr, to, tex, angle=45, glow=15, origin=None):
        """A pair of crossed translucent planes (flames, glows) standing on the y axis."""
        cx = (fr[0] + to[0]) / 2
        o = origin or (cx, fr[1], C)
        for a in (angle, -angle):
            self.box((fr[0], fr[1], C), (to[0], to[1], C), tex, rot=self.rot('y', a, o), faces=('north', 'south'),
                     glow=glow, stretch=True)

    # -------- output
    def _points(self):
        pts = []
        for e in self.elements:
            fr, to = e['from'], e['to']
            for cx in (fr[0], to[0]):
                for cy in (fr[1], to[1]):
                    for cz in (fr[2], to[2]):
                        p = [cx, cy, cz]
                        if 'rotation' in e:
                            o = e['rotation']['origin']
                            q = _mv(_R(e['rotation']['axis'], e['rotation']['angle']), [p[i] - o[i] for i in range(3)])
                            p = [q[i] + o[i] for i in range(3)]
                        pts.append([v / 16 - 0.5 for v in p])
        return pts

    def _fit(self, rot, fill):
        """Scale + translation so the model fills `fill` of the slot, centred (game convention: y mirrored)."""
        R = _mm(_mm(_R('x', rot[0]), _R('y', -rot[1])), _R('z', rot[2]))
        q = [_mv(R, p) for p in self._points()]
        lo = [min(p[i] for p in q) for i in range(2)]
        hi = [max(p[i] for p in q) for i in range(2)]
        size = max(hi[0] - lo[0], hi[1] - lo[1])
        s = fill / size
        cx, cy = (lo[0] + hi[0]) / 2 * s, (lo[1] + hi[1]) / 2 * s
        return {"rotation": list(rot), "translation": [round(-cx * 16, 2), round(-cy * 16, 2), 0], "scale": [round(s, 3)] * 3}

    def display(self, grip_y, tp_scale, fp_scale, gui_rot=(20, -35, -42)):
        d = {}
        for ctx, s in (('thirdperson', tp_scale), ('firstperson', fp_scale)):
            target, axis, rz = _VANILLA[ctx]
            off = s * (0.5 - grip_y / 16)
            t = [round((target[i] + off * axis[i]) * 16, 2) for i in range(3)]
            d[f'{ctx}_righthand'] = {"rotation": [0, -90, rz], "translation": t, "scale": [s] * 3}
            d[f'{ctx}_lefthand'] = {"rotation": [0, 90, -rz], "translation": t, "scale": [s] * 3}
        d['gui'] = self._fit(gui_rot, 0.94)
        d['fixed'] = self._fit((0, 180, -45), 0.9)
        g = self._fit((0, 0, -45), 0.55)
        g['translation'][1] = round(g['translation'][1] + 2.5, 2)
        d['ground'] = g
        d['head'] = {"rotation": [0, 0, 0], "translation": [0, 10, 0], "scale": [0.6, 0.6, 0.6]}
        return d

    def check(self):
        for e in self.elements:                     # Minecraft refuses models outside -16..32 or odd angles
            for v in e["from"] + e["to"]:
                assert -16 <= v <= 32, (self.name, e)
            if "rotation" in e:
                assert e["rotation"]["angle"] in (-45, -22.5, 0, 22.5, 45), (self.name, e)

    def write_texture(self, tex_dir):
        sheet = Image.new('RGBA', (N, N * self.frames))
        for f in range(self.frames):
            im = Image.new('RGBA', (N, N), (0, 0, 0, 0))
            px = im.load()
            for name, (u0, v0, u1, v1) in self.regions.items():
                self.painters[name](px, (int(u0 * 4), int(v0 * 4), int(u1 * 4), int(v1 * 4)), f, self.frames)
            sheet.paste(im, (0, f * N))
        sheet.save(f'{tex_dir}/{self.name}.png')
        with open(f'{tex_dir}/{self.name}.png.mcmeta', 'w') as fh:
            json.dump({"animation": {"frametime": self.frametime, "interpolate": True}}, fh)

    def write_model(self, model_dir, grip_y, tp_scale=0.6, fp_scale=0.45, gui_rot=(20, -35, -42), display=None):
        self.check()
        model = {"credit": "Forgebound", "texture_size": [N, N],
                 "textures": {"t": self.tex, "particle": self.tex}, "elements": self.elements,
                 "display": display or self.display(grip_y, tp_scale, fp_scale, gui_rot)}
        with open(f'{model_dir}/{self.name}.json', 'w') as fh:
            json.dump(model, fh, indent=1)
        return model["display"]

    def write(self, tex_dir, model_dir, grip_y, tp_scale=0.6, fp_scale=0.45, gui_rot=(20, -35, -42)):
        self.check()
        self.write_texture(tex_dir)
        self.write_model(model_dir, grip_y, tp_scale, fp_scale, gui_rot)
        return len(self.elements)

    def variant(self, name):
        """Another model drawn from this model's atlas (e.g. the axe with its head thrown away)."""
        v = Model(name, self.frames, self.frametime)
        v.tex = self.tex
        v.regions = self.regions
        v.painters = self.painters
        return v
