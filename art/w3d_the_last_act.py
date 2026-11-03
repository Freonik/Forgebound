"""The Last Act (3D): a showman's rifle of ivory and gold. A carved ivory stock, a gold receiver with a ruby, a long
dark barrel with gold bands and a front sight. While it is being charged a golden glow climbs the barrel; loaded, the
chamber burns gold (an arrow) or red (a rocket). Six models share one atlas. Built upright (barrel along +y) and
turned onto the sprite's diagonal by the display transform; held like before (barrel forward, not flat)."""
import sys, math
from model3d import *

IVORYR = [(140, 128, 104), (190, 178, 152), (226, 216, 192), (248, 242, 226), (255, 252, 244)]
BARREL = [(10, 10, 14), (22, 22, 30), (38, 38, 50), (60, 60, 76), (96, 96, 116)]
RUBYR = [(80, 0, 14), (160, 20, 36), (230, 50, 60), (255, 140, 140)]
CHARGE = [(140, 90, 10), (230, 170, 40), (255, 220, 110), (255, 248, 210), (255, 255, 255)]
BLAZE = [(120, 20, 0), (220, 70, 10), (255, 140, 40), (255, 220, 140)]

base = Model('the_last_act')
base.region('ivory', (0, 0, 8, 8), plate(IVORYR, 0.55, rivets=False))
base.region('gold', (8, 0, 12, 4), sheen(GOLD, 0.35, 0.98))
base.region('ruby', (12, 0, 16, 4), flow(RUBYR, speed=1, base=0.55))
base.region('barrel', (8, 4, 12, 8), sheen(BARREL, 0.3, 0.9))
base.region('charge', (12, 4, 16, 8), flow(CHARGE, speed=3, base=0.6))
base.region('gplate', (0, 8, 8, 12), plate(GOLD, 0.5))
base.region('blaze', (8, 8, 12, 12), flow(BLAZE, speed=3, base=0.6))
R = base.rot


def build(m, charge=0.0, load=None):
    # stock: a carved ivory butt tapering to the wrist, gold butt plate
    m.box((C - 1.3, -6.0, C - 2.4), (C + 1.3, -1.0, C + 1.6), 'ivory')
    m.box((C - 1.1, -1.0, C - 1.6), (C + 1.1, 3.0, C + 1.2), 'ivory')
    m.box((C - 1.45, -6.6, C - 2.55), (C + 1.45, -6.0, C + 1.75), 'gplate')
    m.box((C - 0.15, -4.6, C - 2.5), (C + 0.15, -2.0, C - 2.38), 'gold')            # inlay line
    # receiver: gold with a ruby, the trigger and guard under it
    m.box((C - 1.2, 3.0, C - 1.3), (C + 1.2, 7.4, C + 1.3), 'gplate')
    m.ball((C, 5.2, C + 1.3), 0.7, 'ruby', glow=12)
    m.box((C - 0.25, 3.6, C - 2.6), (C + 0.25, 4.6, C - 1.3), 'gold')
    m.box((C - 0.3, 2.8, C - 3.0), (C + 0.3, 5.4, C - 2.6), 'gold')
    # fore-end of ivory under the barrel
    m.box((C - 0.9, 7.4, C - 1.4), (C + 0.9, 14.0, C + 0.0), 'ivory')
    # barrel with gold bands and a front sight
    m.box((C - 0.6, 7.4, C - 0.6), (C + 0.6, 26.0, C + 0.6), 'barrel')
    for yy in (9.0, 13.6, 18.0, 22.4):
        m.box((C - 0.8, yy, C - 0.8), (C + 0.8, yy + 0.5, C + 0.8), 'gold')
    m.box((C - 0.8, 25.6, C - 0.8), (C + 0.8, 26.6, C + 0.8), 'gplate')
    m.box((C - 0.15, 24.6, C + 0.6), (C + 0.15, 25.6, C + 1.2), 'gold')
    m.box((C - 0.15, 6.4, C + 1.3), (C + 0.15, 7.4, C + 1.8), 'gold')                # rear sight
    # a gold scope on top (+z), a red lens at each end
    m.box((C - 0.55, 9.0, C + 1.1), (C + 0.55, 17.0, C + 2.2), 'barrel')
    for yy in (9.0, 16.4):
        m.box((C - 0.75, yy, C + 0.9), (C + 0.75, yy + 0.6, C + 2.4), 'gplate')
    m.box((C - 0.4, 17.0, C + 1.25), (C + 0.4, 17.25, C + 2.05), 'ruby', glow=15)
    m.box((C - 0.4, 8.75, C + 1.25), (C + 0.4, 9.0, C + 2.05), 'ruby', glow=8)
    for yy in (10.6, 14.6):
        m.box((C - 0.25, yy, C + 0.6), (C + 0.25, yy + 0.6, C + 1.1), 'gold')
    # the charge climbing the barrel
    if charge > 0:
        top = 7.6 + charge * 18.0
        m.box((C - 0.66, 7.6, C - 0.66), (C + 0.66, top, C + 0.66), 'charge', glow=15)
    if load == 'arrow':
        m.box((C - 0.7, 7.6, C - 0.7), (C + 0.7, 26.2, C + 0.7), 'charge', glow=15)
        m.ring((C, 26.8, C), 1.3, 0.3, 'charge', glow=15)
    elif load == 'rocket':
        m.box((C - 0.7, 7.6, C - 0.7), (C + 0.7, 26.2, C + 0.7), 'blaze', glow=15)
        m.ring((C, 26.8, C), 1.3, 0.3, 'blaze', glow=15)


RIFLE_HOLD = {
    "thirdperson_righthand": {"rotation": [0, -90, 33], "translation": [0, 3.0, 1.0], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, 90, -33], "translation": [0, 3.0, 1.0], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, -70, 33], "translation": [1.13, 2.6, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 70, -33], "translation": [1.13, 2.6, 1.13], "scale": [0.68, 0.68, 0.68]},
}
THETA = 45           # upright barrel -> the sprite's diagonal (muzzle top-left, butt bottom-right)

states = [(base, 0.0, None)]
for i, c in enumerate((0.33, 0.66, 1.0)):
    states.append((base.variant(f'the_last_act_pulling_{i}'), c, None))
states.append((base.variant('the_last_act_arrow'), 0.0, 'arrow'))
states.append((base.variant('the_last_act_firework'), 0.0, 'rocket'))
for m, c, load in states:
    build(m, c, load)

if __name__ == '__main__':
    base.write_texture(sys.argv[1])
    d = base.sprite_display(RIFLE_HOLD, THETA)
    for m, _, _ in states:
        m.write_model(sys.argv[2], 0, display=d)
    print(len(base.elements), 'elements')
