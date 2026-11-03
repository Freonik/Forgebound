"""Great Sea Trident (3D): a deep-blue shaft crusted with barnacles and coral, a gold crown fitting, three barbed
prongs of prismarine, a glowing pearl in the middle and sea spray swirling up. Writes great_sea_trident and
great_sea_trident_in_hand (held like a vanilla spear)."""
import sys, math
from model3d import *

SEA = [(6, 16, 40), (12, 32, 70), (20, 54, 110), (34, 84, 150), (60, 120, 190), (110, 170, 225)]
PRISM = [(20, 70, 70), (40, 120, 110), (70, 170, 150), (120, 215, 190), (190, 245, 230), (240, 255, 250)]
PEARL = [(150, 170, 200), (200, 215, 240), (235, 245, 255), (255, 255, 255)]
CORAL = [(90, 20, 40), (160, 50, 70), (220, 90, 100), (250, 150, 150)]
SPRAY = [(30, 90, 160), (60, 140, 210), (130, 200, 240), (210, 240, 255), (250, 255, 255)]

m = Model('great_sea_trident')
m.region('shaft', (0, 0, 8, 4), sheen(SEA, 0.3, 0.85))
m.region('gold', (8, 0, 12, 4), sheen(GOLD, 0.35, 0.98))
m.region('pearl', (12, 0, 16, 4), flow(PEARL, speed=1, base=0.6))
m.region('prism', (0, 4, 8, 8), cracked(PRISM, PEARL, density=0.03, base=0.5))
m.region('barn', (8, 4, 12, 8), veined([(70, 70, 64), (110, 106, 96), (150, 146, 134), (190, 186, 172)], CORAL, 0.15))
m.region('coral', (12, 4, 16, 8), sheen(CORAL, 0.3, 0.9))
m.region('spray', (0, 8, 8, 16), flame(SPRAY))
m.region('gplate', (8, 8, 16, 12), plate(GOLD, 0.5))
R = m.rot

# butt: a gold cap with a pearl
m.box((C - 1.0, -14.0, C - 1.0), (C + 1.0, -12.8, C + 1.0), 'gplate')
m.box((C - 0.6, -15.2, C - 0.6), (C + 0.6, -14.0, C + 0.6), 'pearl', glow=12)
# shaft with barnacles and a sprig of coral
m.box((C - 0.75, -12.8, C - 0.75), (C + 0.75, 20.0, C + 0.75), 'shaft')
for (yy, dx, dz, s) in ((-9.0, 1, 0, 0.6), (-6.4, 0, -1, 0.5), (10.0, -1, 0, 0.55), (13.4, 0, 1, 0.65), (15.2, 1, 0, 0.45)):
    x, z = C + dx * 0.85, C + dz * 0.85
    m.box((x - s, yy - s, z - s), (x + s, yy + s, z + s), 'barn')
m.box((C + 0.7, 11.0, C - 0.2), (C + 2.6, 11.5, C + 0.2), 'coral', rot=R('z', 45, (C + 0.7, 11.0, C)))
m.box((C + 1.6, 12.0, C - 0.2), (C + 2.0, 13.6, C + 0.2), 'coral')
for yy in (-2.0, 2.0):
    m.box((C - 0.95, yy - 0.3, C - 0.95), (C + 0.95, yy + 0.3, C + 0.95), 'gold')

# crown fitting: a gold collar with points, the pearl set in front
m.box((C - 2.0, 19.6, C - 1.4), (C + 2.0, 21.4, C + 1.4), 'gplate')
m.box((C - 4.6, 21.0, C - 0.9), (C + 4.6, 22.4, C + 0.9), 'gplate')
for dx in (-1, 0, 1):
    m.box((C + dx * 2.4 - 0.3, 18.8, C - 1.5), (C + dx * 2.4 + 0.3, 19.8, C + 1.5), 'gold')
m.ball((C, 20.6, C - 1.6), 0.9, 'pearl', glow=15)

# three prongs with barbs
for dx in (-1, 0, 1):
    x = C + dx * 3.8
    top = 31.4 if dx == 0 else 29.4
    m.box((x - 0.7, 22.4, C - 0.6), (x + 0.7, top - 2.0, C + 0.6), 'prism')
    m.box((x - 0.45, top - 2.0, C - 0.45), (x + 0.45, top, C + 0.45), 'prism', rot=R('y', 45, (x, top - 1, C)))
    # barb hooking back down
    bx = x + (0.6 if dx >= 0 else -0.6)
    m.box((bx - 0.25, top - 4.4, C - 0.3), (bx + 0.25, top - 2.4, C + 0.3), 'prism',
          rot=R('z', -22.5 if dx >= 0 else 22.5, (bx, top - 2.4, C)))
# the outer prongs bend in toward the tips
# sea spray swirling up between the prongs
m.plane((C - 4.0, 21.8, 0), (C + 4.0, 29.8, 0), 'spray', glow=10, origin=(C, 21.8, C))

inhand = m.variant('great_sea_trident_in_hand')
inhand.elements = m.elements

if __name__ == '__main__':
    m.write_texture(sys.argv[1])
    d = m.write_model(sys.argv[2], grip_y=0.0, tp_scale=0.55, fp_scale=0.45)
    d.update(m.spear_display(-4.0, 0.72, 0.6))
    inhand.write_model(sys.argv[2], 0, display=d)
    print(len(m.elements), 'elements')
