import json, os, shutil, subprocess, sys
P = '/home/claude/pack/assets'
CC = f'{P}/customcraft'
MC = f'{P}/minecraft/items'
ART = '/home/claude/art'

def wjson(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(data, f, indent=2)

def run(script, *args):
    subprocess.run([sys.executable, f'{ART}/{script}', *args], check=True)

# clean old textures (including animation metadata)
for d in ['textures/item', 'textures/block', 'textures/font', 'textures/entity/equipment/humanoid',
          'textures/entity/equipment/humanoid_leggings', 'models/item', 'equipment']:
    full = f'{CC}/{d}'
    if os.path.isdir(full):
        for n in os.listdir(full):
            p = f'{full}/{n}'
            if os.path.isfile(p):
                os.remove(p)
os.makedirs(f'{CC}/textures/entity/equipment/humanoid', exist_ok=True)
os.makedirs(f'{CC}/textures/font', exist_ok=True)
os.makedirs(f'{CC}/models/item', exist_ok=True)
os.makedirs(f'{CC}/textures/item/gui', exist_ok=True)

# weapons
run('echo_blade.py', f'{CC}/textures/item/echo_blade.png')
run('graviton_hammer.py', f'{CC}/textures/item/graviton_hammer.png')
run('hex_bow.py', f'{CC}/textures/item')
run('soulbinder.py', f'{CC}/textures/item/soulbinder.png')
# relics (icons + worn armor), gui, menu
run('relics.py', f'{CC}/textures/item', f'{CC}/textures/entity/equipment/humanoid')
# relic sets: the other Heartsteel / Thorn pieces, their leggings layers, helmet and boot details
run('sets.py', f'{CC}/textures/item', f'{CC}/textures/entity/equipment')
# Butcher's Cleaver, Abyssal Maw, Thick Hide (+ worn leggings) and their materials
run('tank.py', f'{CC}/textures/item', f'{CC}/textures/entity/equipment')
# Clawline Pick, Silk Needle, Soul Vessel Mask (+ worn), Stitchguard and their materials
run('hk.py', f'{CC}/textures/item', f'{CC}/textures/entity/equipment')
run('sets2.py', f'{CC}/textures/item', f'{CC}/textures/entity/equipment')
# worn Thick Hide / Thorn Plate: netherite's shading, recoloured (replaces the flat layers from tank/sets/sets2)
run('hide_worn.py', f'{CC}/textures/entity/equipment')
run('thorn_worn.py', f'{CC}/textures/entity/equipment')
run('seraph_worn.py', f'{CC}/textures/entity/equipment')
run('heart_worn.py', f'{CC}/textures/entity/equipment')
# Knightfall, Rimebreaker, Pinning Repeater, Valkyrie's Lance, Trident of the Great Sea + materials
run('arsenal.py', f'{CC}/textures/item')
# The Last Act (rifle on the crossbow) + Gilded Mask Shard
run('lastact.py', f'{CC}/textures/item')
# mythic event weapons: Worldsplitter, Bloodfang, Titan's Echo
run('mythics.py', f'{CC}/textures/item')
# Totem of the Forge
run('forge_totem.py', f'{CC}/textures/item')
for n in os.listdir(f'{CC}/textures/item/gui'):
    os.remove(f'{CC}/textures/item/gui/{n}')
run('gui.py', f'{CC}/textures/item/gui')
run('menu.py', f'{CC}/textures/font')

# ---- models
for n in ['echo_blade', 'graviton_hammer', 'soulbinder', 'butcher_cleaver', 'abyssal_maw', 'clawline_pick', 'silk_needle',
          'knightfall', 'rimebreaker', 'rimebreaker_thrown', 'worldsplitter', 'bloodfang', 'titans_echo']:
    wjson(f'{CC}/models/item/{n}.json', {"parent": "minecraft:item/handheld", "textures": {"layer0": f"customcraft:item/{n}"}})
for n in ['hex_bow', 'hex_bow_pulling_0', 'hex_bow_pulling_1', 'hex_bow_pulling_2']:
    wjson(f'{CC}/models/item/{n}.json', {"parent": "minecraft:item/bow", "textures": {"layer0": f"customcraft:item/{n}"}})
for n in ['stasis_hourglass', 'angels_wings', 'thorn_plate', 'heartsteel_core', 'thick_hide', 'soul_vessel_mask',
          'heartsteel_helm', 'heartsteel_greaves', 'heartsteel_treads', 'thorn_crown', 'thorn_greaves', 'thorn_treads',
          'hide_cowl', 'hide_mantle', 'hide_stompers', 'seraph_mantle', 'seraph_greaves']:
    wjson(f'{CC}/models/item/{n}.json', {"parent": "minecraft:item/generated", "textures": {"layer0": f"customcraft:item/{n}"}})
wjson(f'{CC}/models/item/hydra_edge.json', {"parent": "minecraft:item/handheld", "textures": {"layer0": "customcraft:item/hydra_edge"}})
for n in ['angels_wings']:
    wjson(f'{CC}/equipment/{n}.json', {"layers": {"humanoid": [{"texture": f"customcraft:{n}"}]}})
# set armor: helmet / chestplate / boots use the humanoid layer, leggings their own layer
for n in ['thorn_plate', 'heartsteel', 'thick_hide', 'pale_seraph']:
    wjson(f'{CC}/equipment/{n}.json', {"layers": {"humanoid": [{"texture": f"customcraft:{n}"}],
                                                  "humanoid_leggings": [{"texture": f"customcraft:{n}"}]}})
wjson(f'{CC}/equipment/soul_mask.json', {"layers": {"humanoid": [{"texture": "customcraft:soul_mask"}]}})   # old mask items
# Stitchguard: a real 3D shield (same shape and hand poses as the vanilla shield), normal + blocking
def shield_model(vanilla_model):
    display = json.load(open(f'/home/claude/ref/vanilla/{vanilla_model}'))['display']
    plate = {"north": [0, 0, 6, 11], "south": [0, 0, 6, 11], "east": [6.5, 0, 7, 11], "west": [6.5, 0, 7, 11],
             "up": [6.5, 0, 7, 3], "down": [6.5, 0, 7, 3]}
    return {"gui_light": "front", "textures": {"particle": "customcraft:item/stitchguard", "s": "customcraft:item/stitchguard"},
            "elements": [
                {"from": [-6, -11, 1], "to": [6, 11, 2], "faces": {k: {"uv": v, "texture": "#s"} for k, v in plate.items()}},
                {"from": [-1, -3, -5], "to": [1, 3, 1],
                 "faces": {k: {"uv": [8, 0, 9, 3], "texture": "#s"} for k in ["north", "south", "east", "west", "up", "down"]}}],
            "display": display}
wjson(f'{CC}/models/item/stitchguard.json', shield_model('m_shield.json'))
wjson(f'{CC}/models/item/stitchguard_blocking.json', shield_model('m_shield_blocking.json'))
# names of Forgebound's enchantments (the server sends English fallbacks, the pack adds translations)
LANG = {
    'en_us': {"enchantment.forgebound.forge_touch": "Forge Touch", "enchantment.forgebound.bounty_hunter": "Bounty Hunter", "enchantment.forgebound.lifesteal": "Lifesteal", "enchantment.forgebound.bleed": "Bleed", "enchantment.forgebound.execute": "Execute", "enchantment.forgebound.cripple": "Cripple", "enchantment.forgebound.wisdom": "Wisdom", "enchantment.forgebound.overload": "Overload", "enchantment.forgebound.dodge": "Dodge", "enchantment.forgebound.implants": "Implants", "enchantment.forgebound.springs": "Springs", "enchantment.forgebound.warding": "Warding", "enchantment.forgebound.grounded": "Grounded", "enchantment.forgebound.cleanse": "Cleanse", "enchantment.forgebound.telepathy": "Telepathy", "enchantment.forgebound.vein_miner": "Vein Miner"},
    'cs_cz': {"enchantment.forgebound.forge_touch": "Kovářský dotek", "enchantment.forgebound.bounty_hunter": "Lovec odměn", "enchantment.forgebound.lifesteal": "Vysávání života", "enchantment.forgebound.bleed": "Krvácení", "enchantment.forgebound.execute": "Poprava", "enchantment.forgebound.cripple": "Ochromení", "enchantment.forgebound.wisdom": "Moudrost", "enchantment.forgebound.overload": "Přetížení", "enchantment.forgebound.dodge": "Úhyb", "enchantment.forgebound.implants": "Implantáty", "enchantment.forgebound.springs": "Pružiny", "enchantment.forgebound.warding": "Štít proti legendám", "enchantment.forgebound.grounded": "Ukotvení", "enchantment.forgebound.cleanse": "Očista", "enchantment.forgebound.telepathy": "Telepatie", "enchantment.forgebound.vein_miner": "Těžba žil"},
}
for code, entries in LANG.items():
    os.makedirs(f'{CC}/lang', exist_ok=True)
    with open(f'{CC}/lang/{code}.json', 'w', encoding='utf-8') as f:
        json.dump(entries, f, indent=2, ensure_ascii=False)

# menu font: background glyphs
wjson(f'{CC}/font/menu.json', {"providers": [
    {"type": "space", "advances": {"\uf808": -8}},
    {"type": "bitmap", "file": "customcraft:font/forge_weapons.png", "ascent": 13, "height": 256, "chars": ["\ue000"]},
    {"type": "bitmap", "file": "customcraft:font/forge_relics.png", "ascent": 13, "height": 256, "chars": ["\ue001"]},
    {"type": "bitmap", "file": "customcraft:font/shop.png", "ascent": 13, "height": 256, "chars": ["\ue002"]},
    {"type": "bitmap", "file": "customcraft:font/shop_market.png", "ascent": 13, "height": 256, "chars": ["\ue003"]},
    {"type": "bitmap", "file": "customcraft:font/bounty_exchange.png", "ascent": 13, "height": 256, "chars": ["\ue004"]},
] + [{"type": "bitmap", "file": f"customcraft:font/picker_{r}.png", "ascent": 13, "height": 256, "chars": [chr(0xe004 + r)]}
     for r in range(1, 7)]})
gui_display = {"gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.125, 1.125, 1]}}
for n in ['clear', 'select', 'cancel', 'proceed_on', 'proceed_off']:
    wjson(f'{CC}/models/gui/{n}.json', {"parent": "minecraft:item/generated", "textures": {"layer0": f"customcraft:item/gui/{n}"}, "display": gui_display})
for n in ['arrow_left', 'arrow_right']:
    wjson(f'{CC}/models/gui/{n}.json', {"parent": "minecraft:item/generated", "textures": {"layer0": f"customcraft:item/gui/{n}"}})

# ---- item definitions
def dispatch(entries, fallback):
    return {"model": {"type": "minecraft:range_dispatch", "property": "minecraft:custom_model_data",
                      "entries": [{"threshold": t, "model": m} for t, m in entries], "fallback": fallback}}
def model(m):
    return {"type": "minecraft:model", "model": m}

wjson(f'{MC}/netherite_sword.json', dispatch([(1001, model("customcraft:item/echo_blade")),
                                              (1007, model("customcraft:item/abyssal_maw")),
                                              (1009, model("customcraft:item/silk_needle")),
                                              (1102, model("customcraft:item/bloodfang")),
                                              (1103, model("customcraft:item/titans_echo"))], model("minecraft:item/netherite_sword")))
wjson(f'{MC}/netherite_pickaxe.json', dispatch([(1008, model("customcraft:item/clawline_pick"))], model("minecraft:item/netherite_pickaxe")))
wjson(f'{MC}/netherite_axe.json', dispatch([(1006, model("customcraft:item/butcher_cleaver")),
                                            (1011, model("customcraft:item/rimebreaker")),
                                            (1014, model("customcraft:item/rimebreaker_thrown")),
                                            (1101, model("customcraft:item/worldsplitter"))], model("minecraft:item/netherite_axe")))
# Totem of the Forge
wjson(f'{CC}/models/item/forge_totem.json', {"parent": "minecraft:item/generated", "textures": {"layer0": "customcraft:item/forge_totem"}})
wjson(f'{MC}/totem_of_undying.json', dispatch([(1601, model("customcraft:item/forge_totem"))], model("minecraft:item/totem_of_undying")))
# Knightfall (mace)
wjson(f'{MC}/mace.json', dispatch([(1010, model("customcraft:item/knightfall"))],
                                  json.load(open('/home/claude/ref/vanilla/i_mace.json'))['model']))
# Pinning Repeater: same states as the vanilla crossbow. Vanilla crossbow textures point to the top-left
# (stock in the bottom-right corner) and the crossbow model's hand rotation expects that, so ours are mirrored.
from PIL import Image as _XImg
CROSSBOWS = ['pinning_repeater', 'the_last_act']
STATES = ['', '_pulling_0', '_pulling_1', '_pulling_2', '_arrow', '_firework']
for _n in [b + st for b in CROSSBOWS for st in STATES]:
    _f = f'{CC}/textures/item/{_n}.png'
    if os.path.exists(_f):
        _XImg.open(_f).transpose(_XImg.FLIP_LEFT_RIGHT).save(_f)
# Pinning Repeater: same states as the vanilla crossbow
# The Last Act is a rifle: held upright with the barrel pointing forward, not lying flat like a crossbow
RIFLE_HOLD = {
    "thirdperson_righthand": {"rotation": [0, -90, 33], "translation": [0, 3.0, 1.0], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, 90, -33], "translation": [0, 3.0, 1.0], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, -70, 33], "translation": [1.13, 2.6, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 70, -33], "translation": [1.13, 2.6, 1.13], "scale": [0.68, 0.68, 0.68]},
}
for n in [b + st for b in CROSSBOWS for st in STATES]:
    m = {"parent": "minecraft:item/crossbow", "textures": {"layer0": f"customcraft:item/{n}"}}
    if n.startswith('the_last_act'):
        m["display"] = RIFLE_HOLD
    wjson(f'{CC}/models/item/{n}.json', m)
def crossbow_states(P):
    return {"type": "minecraft:select", "property": "minecraft:charge_type",
            "cases": [{"when": "arrow", "model": model(P + "_arrow")}, {"when": "rocket", "model": model(P + "_firework")}],
            "fallback": {"type": "minecraft:condition", "property": "minecraft:using_item",
                         "on_false": model(P),
                         "on_true": {"type": "minecraft:range_dispatch", "property": "minecraft:crossbow/pull",
                                     "entries": [{"threshold": 0.58, "model": model(P + "_pulling_1")},
                                                 {"threshold": 1.0, "model": model(P + "_pulling_2")}],
                                     "fallback": model(P + "_pulling_0")}}}
wjson(f'{MC}/crossbow.json', dispatch([(1012, crossbow_states('customcraft:item/pinning_repeater')),
                                       (1016, crossbow_states('customcraft:item/the_last_act'))],
                                      json.load(open('/home/claude/ref/vanilla/i_crossbow.json'))['model']))
# Valkyrie's Lance + Trident: flat icon in menus / on the ground, a long held model like the vanilla spear in hand
def long_weapon(name):
    wjson(f'{CC}/models/item/{name}.json', {"parent": "minecraft:item/generated", "textures": {"layer0": f"customcraft:item/{name}"}})
    wjson(f'{CC}/models/item/{name}_in_hand.json', {"parent": "minecraft:item/spear_in_hand",
                                                    "textures": {"layer0": f"customcraft:item/{name}_in_hand"}})
    return {"type": "minecraft:select", "property": "minecraft:display_context",
            "cases": [{"when": ["gui", "ground", "fixed", "on_shelf"], "model": model(f"customcraft:item/{name}")}],
            "fallback": model(f"customcraft:item/{name}_in_hand")}
spear_vanilla = json.load(open('/home/claude/ref/vanilla/i_netherite_spear.json'))
spear_def = dispatch([(1013, long_weapon('valkyries_lance'))], spear_vanilla['model'])
spear_def['swap_animation_scale'] = spear_vanilla.get('swap_animation_scale', 1.0)
wjson(f'{MC}/netherite_spear.json', spear_def)
wjson(f'{MC}/trident.json', dispatch([(1015, long_weapon('great_sea_trident'))],
                                     json.load(open('/home/claude/ref/vanilla/i_trident.json'))['model']))
wjson(f'{MC}/diamond_axe.json', dispatch([(1002, model("customcraft:item/graviton_hammer"))], model("minecraft:item/diamond_axe")))
wjson(f'{MC}/netherite_hoe.json', dispatch([(1004, model("customcraft:item/soulbinder"))], model("minecraft:item/netherite_hoe")))

def bow_model(prefix):
    return {"type": "minecraft:condition", "property": "minecraft:using_item",
            "on_false": model(f"{prefix}bow"),
            "on_true": {"type": "minecraft:range_dispatch", "property": "minecraft:use_duration", "scale": 0.05,
                        "entries": [{"threshold": 0.65, "model": model(f"{prefix}bow_pulling_1")},
                                    {"threshold": 0.9, "model": model(f"{prefix}bow_pulling_2")}],
                        "fallback": model(f"{prefix}bow_pulling_0")}}
wjson(f'{MC}/bow.json', dispatch([(1003, bow_model("customcraft:item/hex_"))], bow_model("minecraft:item/")))

for color in ['gray', 'blue', 'orange', 'purple']:
    p = f'{MC}/{color}_shulker_box.json'
    if os.path.exists(p):
        os.remove(p)
# relics: keep the vanilla definition (clock time, armor trims) as the fallback
V = '/home/claude/ref/vanilla'
# legendary materials
run('materials.py', f'{CC}/textures/item')
MATERIALS = [('echo_shard', 1301, 'resonant_shard'), ('heavy_core', 1302, 'graviton_core'),
             ('string', 1303, 'hexed_thread'), ('blaze_powder', 1304, 'soul_ember'),
             ('turtle_scute', 1305, 'hydra_scale'), ('glowstone_dust', 1306, 'chrono_sand'),
             ('feather', 1307, 'seraph_feather'), ('sweet_berries', 1308, 'thornheart_seed'),
             ('heart_of_the_sea', 1309, 'crimson_heart'), ('bone', 1310, 'ravenous_marrow'),
             ('glow_ink_sac', 1311, 'abyssal_lure'), ('leather', 1312, 'behemoth_hide'),
             ('quartz', 1313, 'pale_fragment'), ('armadillo_scute', 1314, 'carapace_shard'),
             ('iron_nugget', 1315, 'deepsteel_claw'), ('string', 1316, 'weavers_silk'),
             ('iron_ingot', 1317, 'fallen_crest'), ('snowball', 1318, 'rime_core'), ('flint', 1319, 'hunters_bolt'),
             ('feather', 1320, 'valkyrie_plume'), ('nautilus_shell', 1321, 'sea_kings_doubloon'),
             ('quartz', 1322, 'gilded_mask_shard')]
by_item = {}
for item, cmd, name in MATERIALS:
    wjson(f'{CC}/models/item/{name}.json', {"parent": "minecraft:item/generated", "textures": {"layer0": f"customcraft:item/{name}"}})
    by_item.setdefault(item, []).append((cmd, model(f"customcraft:item/{name}")))
for item, entries in by_item.items():
    vanilla = json.load(open(f'/home/claude/ref/vanilla/i_{item}.json'))['model']
    wjson(f'{MC}/{item}.json', dispatch(sorted(entries, key=lambda e: e[0]), vanilla))
# Armor relics keep showing their trim in the inventory: base icon + vanilla trim overlay per material
TRIMS = ['quartz', 'iron', 'netherite', 'redstone', 'copper', 'gold', 'emerald', 'diamond', 'lapis', 'amethyst', 'resin']
def trimmed(name, piece):
    cases = []
    for mat in TRIMS:
        wjson(f'{CC}/models/item/{name}_{mat}_trim.json', {"parent": "minecraft:item/generated", "textures": {
            "layer0": f"customcraft:item/{name}", "layer1": f"minecraft:trims/items/{piece}_trim_{mat}"}})
        cases.append({"when": f"minecraft:{mat}", "model": model(f"customcraft:item/{name}_{mat}_trim")})
    return {"type": "minecraft:select", "property": "minecraft:trim_material", "cases": cases,
            "fallback": model(f"customcraft:item/{name}")}

for item, entries in [('clock', [(1201, model('customcraft:item/stasis_hourglass'))]),
                      ('netherite_boots', [(1202, model('customcraft:item/angels_wings')),
                                           (1211, model('customcraft:item/heartsteel_treads')),
                                           (1214, model('customcraft:item/thorn_treads')),
                                           (1217, model('customcraft:item/hide_stompers'))]),
                      ('netherite_chestplate', [(1203, model('customcraft:item/thorn_plate')),
                                                (1204, model('customcraft:item/heartsteel_core')),
                                                (1216, model('customcraft:item/hide_mantle')),
                                                (1218, model('customcraft:item/seraph_mantle'))]),
                      ('iron_sword', [(1205, model('customcraft:item/hydra_edge'))]),
                      ('netherite_leggings', [(1206, model('customcraft:item/thick_hide')),
                                              (1210, model('customcraft:item/heartsteel_greaves')),
                                              (1213, model('customcraft:item/thorn_greaves')),
                                              (1219, model('customcraft:item/seraph_greaves'))]),
                      ('netherite_helmet', [(1207, model('customcraft:item/soul_vessel_mask')),
                                            (1209, model('customcraft:item/heartsteel_helm')),
                                            (1212, model('customcraft:item/thorn_crown')),
                                            (1215, model('customcraft:item/hide_cowl'))]),
                      ('shield', [(1208, {"type": "minecraft:condition", "property": "minecraft:using_item",
                                          "on_false": model('customcraft:item/stitchguard'),
                                          "on_true": model('customcraft:item/stitchguard_blocking')})])]:
    vanilla = json.load(open(f'{V}/i_{item}.json'))['model']
    wjson(f'{MC}/{item}.json', dispatch(entries, vanilla))
wjson(f'{MC}/arrow.json', dispatch([(9020, model("customcraft:gui/arrow_left")), (9021, model("customcraft:gui/arrow_right"))],
                                   model("minecraft:item/arrow")))
# Collector's Trophy (advancement reward)
run('trophy.py', f'{CC}/textures/item')
wjson(f'{CC}/models/item/collectors_trophy.json', {"parent": "minecraft:item/generated", "textures": {"layer0": "customcraft:item/collectors_trophy"}})
# season ceremony: a white glyph big enough to cover the whole screen when shown as a title (titles are 4x)
from PIL import Image as _Img
_Img.new('RGBA', (64, 16), (255, 255, 255, 255)).save(f'{CC}/textures/font/flash.png')
wjson(f'{CC}/font/flash.json', {"providers": [
    {"type": "bitmap", "file": "customcraft:font/flash.png", "ascent": 128, "height": 256, "chars": ["\ue100"]}]})

# Recon Spyglass (custom model data 1501): flat icon in the inventory, held like a small tool in the hand
run('recon.py', f'{CC}/textures/item')
wjson(f'{CC}/models/item/recon_spyglass.json', {"parent": "minecraft:item/generated", "textures": {"layer0": "customcraft:item/recon_spyglass"}})
wjson(f'{CC}/models/item/recon_spyglass_in_hand.json', {"parent": "minecraft:item/handheld",
    "textures": {"layer0": "customcraft:item/recon_spyglass"},
    "display": {"thirdperson_righthand": {"rotation": [0, -90, 35], "translation": [0, 2, 0.5], "scale": [0.6, 0.6, 0.6]},
                "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 2.2, 1.13], "scale": [0.55, 0.55, 0.55]}}})
_vanilla_spyglass = json.load(open(f'{V}/i_spyglass.json'))['model']
wjson(f'{MC}/spyglass.json', dispatch([(1501, {"type": "minecraft:select", "property": "minecraft:display_context",
                                               "cases": [{"when": ["gui", "ground", "fixed", "on_shelf"],
                                                          "model": model("customcraft:item/recon_spyglass")}],
                                               "fallback": model("customcraft:item/recon_spyglass_in_hand")})],
                                      _vanilla_spyglass))

# Legend Tracker (custom model data 1502): 32 needle angles, pointing at its lodestone target like a compass
run('tracker.py', f'{CC}/textures/item')
for i in range(32):
    wjson(f'{CC}/models/item/legend_tracker_{i:02d}.json',
          {"parent": "minecraft:item/generated", "textures": {"layer0": f"customcraft:item/legend_tracker_{i:02d}"}})
_vanilla_compass = json.load(open(f'{V}/i_compass.json'))['model']
_tracker_entries = []
for e in _vanilla_compass['on_true']['entries']:
    n = int(e['model']['model'].rsplit('_', 1)[1])                  # vanilla compass_NN, 16 = straight up
    _tracker_entries.append({"threshold": e['threshold'], "model": model(f"customcraft:item/legend_tracker_{(n - 16) % 32:02d}")})
_tracker = {"type": "minecraft:range_dispatch", "property": "minecraft:compass", "target": "lodestone", "scale": 32.0,
            "entries": _tracker_entries}
wjson(f'{MC}/compass.json', dispatch([(1502, _tracker)], _vanilla_compass))

# Bounty Token and Warden's Seal
run('tokens.py', f'{CC}/textures/item')
for name in ['bounty_token', 'wardens_seal']:
    wjson(f'{CC}/models/item/{name}.json', {"parent": "minecraft:item/generated", "textures": {"layer0": f"customcraft:item/{name}"}})
wjson(f'{MC}/knowledge_book.json', dispatch([(1401, model("customcraft:item/collectors_trophy")),
                                             (1402, model("customcraft:item/bounty_token")),
                                             (1403, model("customcraft:item/wardens_seal"))],
                                            json.load(open('/home/claude/ref/vanilla/i_knowledge_book.json'))['model']))
print("pack built")
