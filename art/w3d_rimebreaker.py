"""Rimebreaker (3D): a frost axe. A crescent bit of blue ice with a glowing rune, a cluster of ice crystals on the
back, icicles hanging off the beard, frost mist rising, a dark haft with silver bands.
Also writes rimebreaker_thrown: the same haft with the head gone (shown in the hand while the axe is flying)."""
import sys, math
from model3d import *

ICE = [(16, 40, 74), (30, 70, 120), (54, 110, 170), (90, 156, 214), (150, 204, 240), (210, 238, 255), (248, 254, 255)]
RUNE = [(40, 120, 200), (90, 190, 255), (170, 236, 255), (240, 255, 255)]
WOOD = [(24, 16, 12), (42, 28, 20), (62, 42, 30), (84, 58, 40)]

m = Model('rimebreaker')
m.region('ice', (0, 0, 8, 8), cracked(ICE, RUNE, density=0.02, base=0.5))
m.region('frost', (8, 0, 12, 4), sheen(ICE, 0.55, 0.99))
m.region('rune', (12, 0, 16, 4), flow(RUNE, speed=2, base=0.5))
m.region('wood', (8, 4, 12, 8), wrap(WOOD))
m.region('silver', (12, 4, 16, 8), sheen(STEEL, 0.4, 0.95))
m.region('crystal', (0, 8, 4, 16), sheen(ICE, 0.45, 0.98))
m.region('mist', (4, 8, 12, 16), flame([(120, 170, 220), (170, 210, 245), (210, 236, 255), (240, 250, 255), (255, 255, 255)]))
R = m.rot


def haft(mm):
    # pommel: an ice shard in a silver cap
    mm.box((C - 1.1, -7.0, C - 1.1), (C + 1.1, -6.0, C + 1.1), 'silver')
    mm.box((C - 0.6, -9.0, C - 0.6), (C + 0.6, -7.0, C + 0.6), 'crystal', rot=R('y', 45, (C, -8, C)), glow=8)
    # haft with silver bands
    mm.box((C - 0.8, -6.0, C - 0.8), (C + 0.8, 18.0, C + 0.8), 'wood')
    for yy in (-4.0, 0.0, 4.0, 8.5, 13.0):
        mm.box((C - 1.0, yy - 0.3, C - 1.0), (C + 1.0, yy + 0.3, C + 1.0), 'silver')


haft(m)
HY0, HY1 = 15.5, 29.5
MY = (HY0 + HY1) / 2
# socket
m.box((C - 1.5, HY0 + 2.0, C - 1.3), (C + 1.5, HY1 - 2.0, C + 1.3), 'silver')
# the ice bit (+x): flared, a convex edge, bright frost rim
for i in range(int(HY1 - HY0)):
    y0 = HY0 + i
    t = (y0 + 0.5 - MY) / ((HY1 - HY0) / 2)
    if abs(t) > 1:
        continue
    x0 = C + 1.5 + 5.5 * abs(t) ** 1.6
    x1 = C + 11.6 + 1.6 * (1 - t * t) + (1.6 * t * t if t < 0 else 0)
    m.box((x0, y0, C - 0.8), (x1 - 1.0, y0 + 1, C + 0.8), 'ice', faces=('north', 'south', 'up', 'down'),
          uv=m.surface('ice', x0, x1 - 1.0, y0, y0 + 1, C + 1.5, C + 13.5, HY0, HY1))
    m.box((x1 - 1.0, y0, C - 0.35), (x1, y0 + 1, C + 0.35), 'frost', glow=6)
# the rune: a glowing snowflake on the bit
RX, RY = C + 7.0, MY + 0.5
for (dx, dy) in ((0, 1), (1, 0), (0.7, 0.7), (-0.7, 0.7)):
    for k in range(-4, 5):
        x, y = RX + dx * k * 0.5, RY + dy * k * 0.5
        m.box((x - 0.22, y - 0.22, C - 0.86), (x + 0.22, y + 0.22, C + 0.86), 'rune', glow=15)
# icicles hanging off the beard
for x, ln in ((C + 9.0, 2.6), (C + 10.6, 1.8), (C + 7.6, 1.4)):
    top = HY0 + 1.0
    m.box((x - 0.35, top - ln, C - 0.35), (x + 0.35, top, C + 0.35), 'crystal', rot=R('y', 45, (x, top, C)), glow=4)
# back: a cluster of ice crystals
for (bx, by, ang, ln, w) in ((C - 1.5, MY + 1.0, 45, 5.0, 0.9), (C - 1.5, MY - 1.4, 22.5, 4.0, 0.75),
                             (C - 1.5, MY + 3.4, 45, 3.2, 0.6)):
    m.box((bx - w, by - w, C - w), (bx + w, by + ln, C + w), 'crystal', rot=R('z', ang, (bx, by, C)), glow=6)
# top spike
m.box((C - 0.7, HY1 - 2.0, C - 0.7), (C + 0.7, HY1 + 1.6, C + 0.7), 'crystal', rot=R('y', 45, (C, HY1, C)), glow=6)
# frost mist rising off the edge, and snowflakes
m.plane((C + 9.4, MY - 4.0, 0), (C + 15.4, MY + 4.0, 0), 'mist', glow=10, origin=(C + 12.4, MY - 4.0, C))
for x, yy, z in ((C + 14.0, MY + 5.0, C + 1.2), (C + 3.6, HY1 + 1.4, C - 1.6), (C + 15.0, MY - 3.0, C - 1.0)):
    m.box((x - 0.3, yy - 0.3, z - 0.3), (x + 0.3, yy + 0.3, z + 0.3), 'frost', rot=R('z', 45, (x, yy, z)), glow=12)

# the thrown look: just the haft, the socket torn off, frost where the head was
t = m.variant('rimebreaker_thrown')
haft(t)
t.box((C - 1.0, 18.0, C - 1.0), (C + 1.0, 18.8, C + 1.0), 'silver')
t.box((C - 0.55, 18.8, C - 0.55), (C + 0.55, 20.2, C + 0.55), 'rune', rot=R('y', 45, (C, 19.5, C)), glow=15)

if __name__ == '__main__':
    m.write_texture(sys.argv[1])
    d = m.write_model(sys.argv[2], grip_y=0.0, tp_scale=0.62, fp_scale=0.46)
    t.write_model(sys.argv[2], 0, display=d)      # held exactly like the full axe
    print(len(m.elements), 'elements')
