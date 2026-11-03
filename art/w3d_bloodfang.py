"""Bloodfang (3D): a curved fang of a blade. Dark crimson steel bending back toward the tip, a serrated spine of
teeth, a glowing blood channel, a bone grip and a guard of two fangs; blood drips hang off the teeth."""
import sys, math
from model3d import *

m = Model('bloodfang')
m.region('blade', (0, 0, 8, 8), cracked(BLOOD, [(120, 0, 10), (220, 30, 40), (255, 120, 110), (255, 220, 210)],
                                        density=0.02, base=0.32))
m.region('edge', (8, 0, 12, 4), sheen([(90, 70, 72), (160, 140, 142), (230, 218, 216), (255, 250, 248)], 0.3, 0.95))
m.region('blood', (12, 0, 16, 4), flow([(80, 0, 8), (150, 6, 20), (220, 30, 40), (255, 90, 80), (255, 180, 160)], speed=2))
m.region('bone', (8, 4, 12, 8), sheen(BONE, 0.3, 0.85))
m.region('wrap', (12, 4, 16, 8), wrap([(30, 4, 8), (60, 10, 16), (96, 18, 26), (130, 30, 36)]))
m.region('dark', (0, 8, 8, 12), plate(BLOOD, 0.14, rivets=False))
R = m.rot

# pommel: a fang
m.box((C - 0.9, -7.2, C - 0.9), (C + 0.9, -5.6, C + 0.9), 'bone')
m.box((C - 0.5, -9.4, C - 0.5), (C + 0.5, -7.2, C + 0.5), 'bone', rot=R('z', 22.5, (C, -7.2, C)))
m.box((C - 0.25, -8.0, C - 0.25), (C + 0.25, -7.4, C + 0.25), 'blood', glow=15)

# grip: bone, three thin crimson bands
m.box((C - 0.85, -5.6, C - 0.85), (C + 0.85, 5.6, C + 0.85), 'bone')
for yy in (-3.6, 0.0, 3.6):
    m.box((C - 1.0, yy - 0.35, C - 1.0), (C + 1.0, yy + 0.35, C + 1.0), 'wrap')

# guard: two fangs curling forward
m.box((C - 2.6, 5.6, C - 1.3), (C + 2.6, 7.2, C + 1.3), 'dark')
m.box((C - 0.8, 5.8, C - 1.42), (C + 0.8, 7.0, C + 1.42), 'blood', glow=15)
for dx in (-1, 1):
    ex = C + dx * 2.6
    m.box((ex - 0.6, 5.0, C - 0.7), (ex + 0.6, 8.6, C + 0.7), 'bone', rot=R('z', 22.5 * dx, (ex, 7.0, C)))
    m.box((ex + dx * 0.6 - 0.35, 8.2, C - 0.4), (ex + dx * 0.6 + 0.35, 10.0, C + 0.4), 'bone',
          rot=R('z', 45 * dx, (ex + dx * 0.6, 8.2, C)))


def segment(px, py, length, w_back, w_edge, angle):
    """One straight piece of the curved blade, built upright from its pivot (px, py) and turned by `angle`:
    body, the bright cutting edge (+x), a dark spine with teeth (-x), the glowing blood channel.
    Returns where the piece ends (the next pivot)."""
    rot = R('z', angle, (px, py, C)) if angle else None
    y0, y1 = py - 0.5, py + length
    m.box((px - w_back, y0, C - 0.6), (px + w_edge, y1, C + 0.6), 'blade', rot=rot, faces=('north', 'south', 'up', 'down'),
          uv=m.surface('blade', px - w_back, px + w_edge, y0, y1, px - 3, px + 3, y0 - 1, y1 + 1))
    m.box((px + w_edge, y0, C - 0.28), (px + w_edge + 0.8, y1, C + 0.28), 'edge', rot=rot)
    m.box((px - w_back - 0.5, y0, C - 0.5), (px - w_back, y1, C + 0.5), 'dark', rot=rot)
    m.box((px - 0.35, y0 + 0.5, C - 0.66), (px + 0.35, y1 - 0.3, C + 0.66), 'blood', rot=rot, glow=15)
    k = y0 + 0.8
    while k < y1 - 0.9:                          # serrated spine: teeth leaning toward the tip
        m.box((px - w_back - 1.5, k, C - 0.32), (px - w_back - 0.4, k + 0.9, C + 0.32), 'edge', rot=rot)
        k += 1.7
    a = math.radians(angle)
    return px - math.sin(a) * length, py + math.cos(a) * length


# the blade bends back toward the tip: straight, then 22.5 and 45 degrees, each piece starting where the last ended
x, y = segment(C, 7.2, 10.0, 2.2, 2.4, 0)
x, y = segment(x, y, 7.0, 2.0, 2.1, 22.5)
x, y = segment(x, y, 5.6, 1.5, 1.6, 45)
# the tip
m.box((x - 1.3, y - 0.6, C - 0.45), (x + 1.4, y + 2.4, C + 0.45), 'edge', rot=R('z', 45, (x, y, C)))
m.box((x - 0.6, y + 2.4, C - 0.3), (x + 0.7, y + 3.6, C + 0.3), 'edge', rot=R('z', 45, (x, y, C)))

# blood drips hanging off the teeth
for x, y0, ln in [(C - 3.6, 9.4, 1.8), (C - 3.6, 14.4, 1.2)]:
    m.box((x - 0.18, y0 - ln, C - 0.18), (x + 0.18, y0, C + 0.18), 'blood', glow=12)
    m.box((x - 0.3, y0 - ln - 0.5, C - 0.3), (x + 0.3, y0 - ln, C + 0.3), 'blood', glow=12)

if __name__ == '__main__':
    print(m.write(sys.argv[1], sys.argv[2], grip_y=0.0, tp_scale=0.6, fp_scale=0.45), 'elements')
