"""Clawline Pick (3D): a climbing pick. The head curves down on both sides like two claws of dark steel with
red-hot tips, a grappling line is wound round the haft and a hook hangs from it."""
import sys, math
from model3d import *

DSTEEL = [(14, 14, 18), (30, 30, 36), (54, 54, 62), (86, 86, 96), (130, 130, 142), (184, 184, 196), (236, 236, 244)]
HOT = [(90, 10, 10), (180, 30, 20), (250, 80, 40), (255, 170, 100), (255, 236, 210)]
WOOD = [(30, 20, 14), (50, 34, 24), (74, 52, 36), (100, 72, 50)]
ROPE = [(90, 70, 46), (130, 104, 72), (170, 144, 104), (206, 184, 144)]

m = Model('clawline_pick')
m.region('steel', (0, 0, 8, 8), cracked(DSTEEL, HOT, density=0.012, base=0.4))
m.region('edge', (8, 0, 12, 4), sheen(DSTEEL, 0.45, 0.98))
m.region('hot', (12, 0, 16, 4), flow(HOT, speed=2, base=0.55))
m.region('wood', (8, 4, 12, 8), wrap(WOOD))
m.region('rope', (12, 4, 16, 8), wrap(ROPE))
m.region('iron', (0, 8, 8, 12), edge(IRON, 0.55))
R = m.rot

# pommel and haft
m.box((C - 1.1, -7.4, C - 1.1), (C + 1.1, -6.2, C + 1.1), 'iron')
m.box((C - 0.5, -8.4, C - 0.5), (C + 0.5, -7.4, C + 0.5), 'hot', glow=10)
m.box((C - 0.8, -6.2, C - 0.8), (C + 0.8, 21.0, C + 0.8), 'wood')
# grappling line wound round the haft
for i in range(9):
    yy = 5.6 + i * 0.75
    m.box((C - 0.98, yy, C - 0.98), (C + 0.98, yy + 0.5, C + 0.98), 'rope', rot=R('y', 22.5 if i % 2 else -22.5, (C, yy, C)))
# the hook hanging off the line
m.box((C + 0.9, 8.2, C - 0.15), (C + 1.2, 11.0, C + 0.15), 'rope')
m.box((C + 0.8, 6.6, C - 0.25), (C + 1.3, 8.2, C + 0.25), 'iron')
m.box((C + 1.3, 6.6, C - 0.25), (C + 2.5, 7.1, C + 0.25), 'iron')
m.box((C + 2.0, 7.1, C - 0.25), (C + 2.5, 8.0, C + 0.25), 'edge')
for yy in (-4.0, 0.0, 4.0):
    m.box((C - 1.0, yy - 0.3, C - 1.0), (C + 1.0, yy + 0.3, C + 1.0), 'iron')

# head: a central block, then each claw sweeps out and curls down in three pieces
HY = 21.6
m.box((C - 1.8, HY - 1.6, C - 1.3), (C + 1.8, HY + 1.6, C + 1.3), 'steel')
m.box((C - 2.0, HY - 2.0, C - 1.5), (C + 2.0, HY - 1.4, C + 1.5), 'iron')
for side in (-1, 1):
    x, y = C + side * 1.6, HY
    for ang, ln, w in ((0, 4.0, 1.3), (22.5, 3.6, 1.1), (45, 3.0, 0.85)):
        a = -ang * side
        x0, x1 = (x, x + side * ln) if side > 0 else (x + side * ln, x)
        m.box((x0, y - w, C - 0.9 * w), (x1, y + w, C + 0.9 * w), 'steel', rot=R('z', a, (x, y, C)) if ang else None)
        m.box((x0, y + w, C - 0.25), (x1, y + w + 0.45, C + 0.25), 'edge', rot=R('z', a, (x, y, C)) if ang else None)
        r = math.radians(ang)
        x, y = x + side * math.cos(r) * ln, y - math.sin(r) * ln
    # red-hot claw tip
    m.box((x - 0.55, y - 1.8, C - 0.55), (x + 0.55, y + 0.4, C + 0.55), 'hot', rot=R('z', -45 * side, (x, y, C)), glow=15)

if __name__ == '__main__':
    print(m.write(sys.argv[1], sys.argv[2], grip_y=0.0, tp_scale=0.62, fp_scale=0.47), 'elements')
