"""Builds the Forgebound wiki (GitHub Pages) from config.yml and the resource pack textures.

    python3 site/build.py _site

Everything about items (names, abilities, costs, drops, quests, shop...) is read from the config, so the wiki stays in
sync with every release. The texts around them are in site/text.py (Czech + English).
"""
import html
import json
import os
import re
import shutil
import sys

import yaml

sys.path.insert(0, os.path.dirname(__file__))
from text import T, PAGES, TRIGGERS, SLOTS, SETS, COMMANDS, QUEST_TYPES  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, '_site'))
TEX = os.path.join(ROOT, 'resourcepack/assets/customcraft/textures/item')
CFG = yaml.safe_load(open(os.path.join(ROOT, 'src/main/resources/config.yml'), encoding='utf-8'))
POM = open(os.path.join(ROOT, 'pom.xml'), encoding='utf-8').read()
VERSION = re.search(r'<artifactId>Forgebound</artifactId>\s*<version>([^<]+)</version>', POM).group(1)
LANGS = ['cs', 'en']
MIRRORED = {'pinning_repeater', 'the_last_act'}   # crossbow textures are stored mirrored for the hold
DOCS_ICON = {'stitchguard'}
HIDDEN_SETS = {'thorn', 'thick_hide'}               # not shown on the wiki (yet)
ARMOR_ROWS = [0, 3]                                 # rows of docs/armor.png shown on the wiki (Heartsteel, Pale Seraph)                         # the pack texture is a shield (model UV), the README icon is the item

COLORS = {'0': '#000', '1': '#0000aa', '2': '#00aa00', '3': '#00aaaa', '4': '#aa0000', '5': '#aa00aa', '6': '#ffaa00',
          '7': '#aaaaaa', '8': '#555', '9': '#5555ff', 'a': '#55ff55', 'b': '#55ffff', 'c': '#ff5555', 'd': '#ff55ff',
          'e': '#ffff55', 'f': '#fff'}


def t(key, lang):
    v = T[key]
    return v[0] if lang == 'cs' else v[1]


def esc(s):
    return html.escape(str(s), quote=True)


def mc(text):
    """Minecraft &-codes -> HTML spans (colors, bold; obfuscated text is dropped)."""
    out, color, bold, obf = [], None, False, False
    parts = re.split(r'(&[0-9a-fk-or])', str(text))
    for p in parts:
        if re.fullmatch(r'&[0-9a-fk-or]', p):
            c = p[1]
            if c in COLORS:
                color, bold, obf = COLORS[c], False, False
            elif c == 'l':
                bold = True
            elif c == 'k':
                obf = True
            elif c == 'r':
                color, bold, obf = None, False, False
            continue
        if not p or obf:
            continue
        style = (f'color:{color};' if color else '') + ('font-weight:700;' if bold else '')
        out.append(f'<span style="{style}">{esc(p)}</span>' if style else esc(p))
    return ''.join(out)


def plain(text):
    return re.sub(r'&[0-9a-fk-or]', '', str(text)).replace('|', '').strip()


def lines(d):
    if d is None:
        return []
    return [d] if isinstance(d, str) else list(d)


def nice(enum):
    return str(enum).replace('minecraft:', '').replace('_', ' ').title()


# ================================================================ images

USED = set()


def sprite(name, size=64, cls=''):
    """An item icon; animated strips play as CSS sprite animations."""
    path = os.path.join(TEX, name + '.png')
    if not os.path.exists(path) or name in DOCS_ICON:
        path = os.path.join(ROOT, 'docs/items', name + '.png')
        if not os.path.exists(path):
            return ''
        USED.add(('docs', name))
        return f'<img class="icon {cls}" src="../img/{name}.png" width="{size}" height="{size}" alt="">'
    USED.add(('tex', name))
    from PIL import Image
    im = Image.open(path)
    frames = max(1, im.height // im.width) if os.path.exists(path + '.mcmeta') else 1
    flip = ' flip' if name in MIRRORED else ''
    if frames == 1:
        return f'<img class="icon{flip} {cls}" src="../img/{name}.png" width="{size}" height="{size}" alt="">'
    ft = 2
    meta = path + '.mcmeta'
    if os.path.exists(meta):
        ft = json.load(open(meta)).get('animation', {}).get('frametime', 2)
    dur = frames * ft / 20
    return (f'<span class="icon anim{flip} {cls}" style="--s:{size}px;--n:{frames};--d:{dur:.2f}s;'
            f'background-image:url(../img/{name}.png)"></span>')


def copy_images():
    os.makedirs(os.path.join(OUT, 'img'), exist_ok=True)
    for kind, name in USED:
        src = os.path.join(TEX if kind == 'tex' else os.path.join(ROOT, 'docs/items'), name + '.png')
        shutil.copy(src, os.path.join(OUT, 'img', name + '.png'))
    from PIL import Image
    armor = Image.open(os.path.join(ROOT, 'docs/armor.png'))
    row = armor.height // 4
    out = Image.new('RGBA', (armor.width, row * len(ARMOR_ROWS)))
    for i, r in enumerate(ARMOR_ROWS):
        out.paste(armor.crop((0, r * row, armor.width, (r + 1) * row)), (0, i * row))
    out.save(os.path.join(OUT, 'img', 'armor.png'))
    for f in ['forge-menu.png', 'banner.png', 'weapons.gif']:
        p = os.path.join(ROOT, 'docs', f)
        if os.path.exists(p):
            shutil.copy(p, os.path.join(OUT, 'img', f))


# ================================================================ data

def craftable(kind, ident):
    return CFG.get('craftable', {}).get(kind, {}).get(ident, True)


def weapons(mythic=False):
    return [(k, v) for k, v in CFG['weapons'].items()
            if bool(v.get('mythic')) == mythic and (mythic or craftable('weapons', k))]


def hidden(sec, ident):
    return sec == 'relics' and CFG['relics'].get(ident, {}).get('relic', {}).get('set') in HIDDEN_SETS


def relics():
    return [(k, v) for k, v in CFG['relics'].items() if craftable('relics', k) and not hidden('relics', k)]


def materials():
    uses = used_for()
    return [(k, v) for k, v in CFG['materials'].items() if craftable('materials', k) and (k in uses or not any(
        str(e['item']) == 'material:' + k for sec in ['weapons', 'relics'] for e in
        [x for v2 in CFG[sec].values() for x in (v2.get('forge') or [])]))]


def item_name(ref):
    ref = str(ref)
    if ':' in ref and not ref.startswith('minecraft:'):
        kind, ident = ref.split(':', 1)
        sec = {'weapon': 'weapons', 'relic': 'relics', 'material': 'materials'}[kind]
        e = CFG[sec].get(ident)
        return plain(e['name']) if e else ident
    return nice(ref)


def chip(ref, amount, lang):
    ref = str(ref)
    if ref.startswith('material:'):
        ident = ref.split(':', 1)[1]
        return (f'<a class="chip legendary" href="materials.html#{ident}">{sprite(ident, 24)}'
                f'<b>{esc(item_name(ref))}</b></a>')
    return f'<span class="chip">{amount}× {esc(nice(ref))}</span>'


def cost_html(entries, lang):
    if not entries:
        return ''
    return '<div class="chips">' + ''.join(chip(e['item'], e.get('amount', 1), lang) for e in entries) + '</div>'


def used_for():
    m = {}
    for sec in ['weapons', 'relics']:
        for k, v in CFG[sec].items():
            if hidden(sec, k) or not craftable(sec, k):
                continue
            for e in v.get('forge', []) or []:
                ref = str(e['item'])
                if ref.startswith('material:'):
                    m.setdefault(ref.split(':', 1)[1], []).append((sec, k, v))
    return m


def drop_text(d, lang):
    chance = d.get('chance', 0)
    chance = (f'{chance:g}' if isinstance(chance, (int, float)) else chance) + ' %'
    if 'mob' in d:
        what = nice(d['mob'])
        extra = []
        for b in d.get('biomes') or ([d['biome']] if d.get('biome') else []):
            extra.append((t('in-biome', lang)) + ' ' + nice(b))
        if d.get('min-y') is not None:
            extra.append(t('above-y', lang) + ' ' + str(d['min-y']))
        return f'{esc(what)}{(" (" + ", ".join(map(esc, extra)) + ")") if extra else ""} <b>{chance}</b>'
    if 'loot-table' in d:
        lt = ' '.join(nice(x) for x in str(d['loot-table']).replace('chests/', '').split('/'))
        return f'{esc(t("chest", lang))} {esc(lt)} <b>{chance}</b>'
    return esc(json.dumps(d))


# ================================================================ cards

def ability_block(a, lang):
    rows = []
    passive = a.get('passive')
    if passive:
        desc = lines(passive.get('description'))
        rows.append(f'<div class="ab passive"><span class="tag">{t("passive", lang)}</span>'
                    f'<p>{" ".join(map(esc, desc))}</p>'
                    + (f'<small>{t("unlock", lang).format(passive["unlock-kills"])}</small>'
                       if passive.get('unlock-kills') else '') + '</div>')
    for hit in a.get('on-hit', []) or []:
        rows.append(f'<div class="ab onhit"><span class="tag">{t("onhit", lang)}</span>'
                    f'<p>{" ".join(map(esc, lines(hit.get("description"))))}</p></div>')
    for key, act in (a.get('active') or {}).items():
        trig = TRIGGERS.get(act.get('trigger', 'SWAP'), ('?', '?'))
        trig = trig[0] if lang == 'cs' else trig[1]
        meta = [f'<kbd>{esc(trig)}</kbd>']
        if act.get('cooldown'):
            meta.append(f'⏱ {act["cooldown"]} s')
        if act.get('charges'):
            meta.append(f'{t("charges", lang)} {act["charges"]}')
        if act.get('unlock-kills'):
            meta.append(t('unlock', lang).format(act['unlock-kills']))
        rows.append(f'<div class="ab active"><span class="tag">{mc(act.get("name", key))}</span>'
                    f'<span class="meta">{" · ".join(meta)}</span>'
                    f'<p>{" ".join(map(esc, lines(act.get("description"))))}</p></div>')
    if a.get('kills', {}).get('enabled'):
        rows.append(f'<div class="ab"><span class="tag">{t("kills-tag", lang)}</span><p>{t("kills-desc", lang)}</p></div>')
    return ''.join(rows)


def weapon_card(k, v, lang, mythic=False):
    stats = []
    if v.get('attack-damage'):
        stats.append(f'⚔ {v["attack-damage"]}')
    if v.get('attack-speed'):
        stats.append(f'⚡ {v["attack-speed"]}')
    base = nice(v.get('material', ''))
    lore = ''.join(f'<p class="lore">{mc(l)}</p>' for l in lines(v.get('lore')))
    cost = '' if mythic else (f'<h4>{t("forge-cost", lang)}</h4>' + cost_html(v.get('forge'), lang))
    return f'''<article class="card item" id="{k}" data-search="{esc(plain(v["name"]).lower())}">
  <header>{sprite(k)}<div><h3>{mc(v["name"])}</h3><div class="sub">{" · ".join([esc(base)] + stats)}</div></div></header>
  {lore}
  <div class="abilities">{ability_block(v.get("abilities") or {}, lang)}</div>
  {cost}
</article>'''


def relic_card(k, v, lang):
    r = v.get('relic', {})
    slot = SLOTS.get(r.get('slot', ''), (r.get('slot', ''), r.get('slot', '')))
    slot = slot[0] if lang == 'cs' else slot[1]
    desc = lines(r.get('description'))
    lore = ''.join(f'<p class="lore">{mc(l)}</p>' for l in lines(v.get('lore')))
    meta = [esc(slot)]
    if r.get('cooldown'):
        meta.append(f'⏱ {r["cooldown"]} s')
    return f'''<article class="card item" id="{k}" data-search="{esc(plain(v["name"]).lower())}">
  <header>{sprite(k)}<div><h3>{mc(v["name"])}</h3><div class="sub">{" · ".join(meta)}</div></div></header>
  {lore}
  {f'<div class="abilities"><div class="ab"><p>{" ".join(map(esc, desc))}</p></div></div>' if desc else ''}
  <h4>{t("forge-cost", lang)}</h4>{cost_html(v.get("forge"), lang)}
</article>'''


def material_card(k, v, lang, uses):
    drops = v.get('drops') or []
    for_ = uses.get(k, [])
    target = ''.join(f'<a class="chip legendary" href="{"weapons" if s == "weapons" else "relics"}.html#{i}">'
                     f'{sprite(i, 24)}<b>{esc(plain(e["name"]))}</b></a>' for s, i, e in for_)
    return f'''<article class="card item small" id="{k}" data-search="{esc(plain(v["name"]).lower())}">
  <header>{sprite(k, 48)}<div><h3>{mc(v["name"])}</h3></div></header>
  <h4>{t("where", lang)}</h4><ul class="drops">{"".join(f"<li>{drop_text(d, lang)}</li>" for d in drops) or "<li>-</li>"}</ul>
  {f'<h4>{t("used-for", lang)}</h4><div class="chips">{target}</div>' if target else ''}
</article>'''


# ================================================================ pages

def layout(lang, slug, title, body):
    other = 'en' if lang == 'cs' else 'cs'
    nav = ''.join(f'<a href="{s}.html" class="{"on" if s == slug else ""}">{p[0] if lang == "cs" else p[1]}</a>'
                  for s, p in PAGES.items())
    return f'''<!doctype html>
<html lang="{lang}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{esc(title)} · Forgebound Wiki</title>
<meta name="description" content="{esc(t("tagline", lang))}">
<link rel="icon" href="../img/forge_totem.png">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link href="https://fonts.googleapis.com/css2?family=Cinzel:wght@600;800&family=Inter:wght@400;600;700&display=swap" rel="stylesheet">
<link rel="stylesheet" href="../style.css">
</head>
<body>
<header class="top">
  <button class="burger" aria-label="Menu" onclick="document.body.classList.toggle('menu')">☰</button>
  <a class="brand" href="index.html">⚒ <span>Forgebound</span> <small>wiki</small></a>
  <div class="spacer"></div>
  <a class="lang" href="../{other}/{slug}.html">{"English" if lang == "cs" else "Čeština"}</a>
</header>
<nav class="side">{nav}<div class="ver">Forgebound {esc(VERSION)}</div></nav>
<main>
<h1>{esc(title)}</h1>
{body}
<footer>{t("footer", lang)}</footer>
</main>
<script src="../app.js"></script>
</body>
</html>'''


def section(title, inner, ident=''):
    return f'<section{(" id=" + chr(34) + ident + chr(34)) if ident else ""}><h2>{title}</h2>{inner}</section>'


def p(text):
    return f'<p>{text}</p>'


def search_box(lang):
    return f'<input class="search" type="search" placeholder="{t("search", lang)}" oninput="filterCards(this.value)">'


def page_index(lang):
    cards = ''.join(f'<a class="feature" href="{slug}.html"><span class="emoji">{emoji}</span>'
                    f'<b>{PAGES[slug][0] if lang == "cs" else PAGES[slug][1]}</b><small>{t("f-" + slug, lang)}</small></a>'
                    for slug, emoji in [('forge', '⚒'), ('weapons', '⚔'), ('relics', '🛡'), ('materials', '💎'),
                                        ('world', '🌋'), ('events', '☾'), ('quests', '📜'), ('pvp', '☠'),
                                        ('commands', '⌨')])
    icons = ''.join(sprite(k, 48) for k, _ in weapons()[:8])
    return f'''<div class="hero">
  <div class="hero-icons">{icons}</div>
  <p class="tag">{t("tagline", lang)}</p>
</div>
{section(t("start-title", lang), t("start", lang))}
<div class="features">{cards}</div>
{section(t("loop-title", lang), t("loop", lang))}'''


def page_forge(lang):
    costs = CFG['stars']['costs']
    rows = ''
    for star, c in costs.items():
        items = ''.join(chip(e['item'], e.get('amount', 1), lang) for e in c.get('items', []))
        rows += (f'<tr><td>{"★" * int(star)}</td><td>{c.get("minutes", "?")} min</td><td>{c.get("tokens", 0)}</td>'
                 f'<td><div class="chips">{items}</div></td></tr>')
    mx = CFG.get('ancient-forge', {}).get('max-stars', {})
    return (section(t("forge-menu-title", lang), t("forge-menu", lang) + '<img class="shot" src="../img/forge-menu.png" alt="">')
            + section(t("ritual-title", lang), t("ritual", lang).format(min=CFG['ritual'].get('min-players', 3)))
            + section(t("unique-title", lang), t("unique", lang))
            + section(t("stars-title", lang), t("stars", lang).format(
                pvp=CFG['stars'].get('lose-on-player-kill', 2), other=CFG['stars'].get('lose-on-other-death', 0.5),
                pct=CFG['stars'].get('percent-per-star', 7.5)), 'stars')
            + section(t("ancient-title", lang), t("ancient", lang) +
                      f'<table><tr><th>{t("star-col", lang)}</th><th>{t("time-col", lang)}</th><th>{t("tokens-col", lang)}</th>'
                      f'<th>{t("items-col", lang)}</th></tr>{rows}</table>', 'ancient'))


def page_weapons(lang):
    cards = ''.join(weapon_card(k, v, lang) for k, v in weapons())
    return p(t("weapons-intro", lang)) + search_box(lang) + f'<div class="grid">{cards}</div>'


def page_relics(lang):
    singles = [(k, v) for k, v in relics() if not v['relic'].get('set')]
    sets = {}
    for k, v in relics():
        if v['relic'].get('set'):
            sets.setdefault(v['relic']['set'], []).append((k, v))
    out = p(t("relics-intro", lang)) + search_box(lang)
    out += section(t("single-relics", lang), f'<div class="grid">{"".join(relic_card(k, v, lang) for k, v in singles)}</div>')
    for sid, pieces in sets.items():
        name, desc = SETS.get(sid, {'cs': (sid, ''), 'en': (sid, '')})[lang]
        out += section(name, p(desc) + f'<div class="grid">{"".join(relic_card(k, v, lang) for k, v in pieces)}</div>', sid)
    out += section(t("sets-how-title", lang), t("sets-how", lang) + '<img class="shot" src="../img/armor.png" alt="">')
    return out


def page_materials(lang):
    uses = used_for()
    cards = ''.join(material_card(k, v, lang, uses) for k, v in materials())
    return t("materials-intro", lang) + search_box(lang) + f'<div class="grid small">{cards}</div>'


def page_world(lang):
    return (section(t("scorched-title", lang), t("scorched", lang), 'scorched')
            + section(t("meteor-title", lang), t("meteor", lang), 'meteors')
            + section(t("warden-title", lang), t("warden", lang), 'warden'))


def page_events(lang):
    big = CFG.get('big-events', {})
    mythics = ''.join(weapon_card(k, v, lang, mythic=True) for k, v in weapons(mythic=True))
    return (p(t("events-intro", lang))
            + section(t("bloodmoon-title", lang), t("bloodmoon", lang))
            + section(t("rift-title", lang), t("rift", lang))
            + section(t("hunt-title", lang), t("hunt", lang))
            + section(t("small-title", lang), t("small", lang))
            + section(t("big-title", lang), t("big", lang).format(days=big.get('mythic-days', 7)), 'big')
            + section(t("mythic-title", lang), p(t("mythic", lang).format(days=big.get('mythic-days', 7)))
                      + f'<div class="grid">{mythics}</div>', 'mythic'))


def quest_rows(pool, lang):
    rows = ''
    for k, q in (pool or {}).items():
        if q.get('enabled', True) is False:
            continue
        rew = []
        if q.get('tokens'):
            rew.append(f'{q["tokens"]} {t("tokens-word", lang)}')
        if q.get('materials'):
            rew.append(f'{q["materials"]}× {t("material-word", lang)}')
        if q.get('totems'):
            rew.append(f'{q["totems"]}× Totem of the Forge')
        if q.get('xp'):
            rew.append(f'{q["xp"]} XP')
        qt = QUEST_TYPES.get(q.get('type', ''), ('', ''))
        rows += f'<tr><td>{esc(q.get("name", k))}</td><td>{esc(qt[0] if lang == "cs" else qt[1])}</td><td>{esc(", ".join(rew))}</td></tr>'
    return f'<table><tr><th>{t("quest-col", lang)}</th><th>{t("type-col", lang)}</th><th>{t("reward-col", lang)}</th></tr>{rows}</table>'


def page_quests(lang):
    q = CFG.get('quests', {})
    shop = CFG.get('economy', {}).get('shop', {})
    perm = ''.join(f'<span class="chip">{o.get("amount", 1)}× {esc(nice(o.get("material", k)))} · <b>{o.get("price")}</b> 🪙</span>'
                   for k, o in (shop.get('permanent') or {}).items() if o.get('enabled', True))
    rot = ''.join(f'<span class="chip">{o.get("amount", 1)}× {esc(nice(o.get("material", k)))} · <b>{o.get("price")}</b> 🪙</span>'
                  for k, o in (shop.get('rotating') or {}).items() if o.get('enabled', True))
    return (section(t("questmaster-title", lang), t("questmaster", lang).format(
                daily=q.get('daily-count', 3), weekly=q.get('weekly-count', 1), bonus=q.get('all-daily-bonus-tokens', 2)))
            + section(t("daily-title", lang), quest_rows(q.get('daily'), lang))
            + section(t("weekly-title", lang), quest_rows(q.get('weekly'), lang))
            + section(f'{sprite("bounty_token", 32)} ' + t("bounty-title", lang), t("bounty", lang), 'bounty')
            + section(t("shop-title", lang), p(t("shop", lang)) + f'<h4>{t("shop-perm", lang)}</h4><div class="chips">{perm}</div>'
                      + f'<h4>{t("shop-rot", lang)}</h4><div class="chips">{rot}</div>', 'shop')
            + section(f'{sprite("forge_totem", 32)} Totem of the Forge', t("totem", lang), 'totem')
            + section(t("legends-title", lang), t("legends", lang).format(days=CFG.get('legends', {}).get('inactive-days', 7))))


def page_pvp(lang):
    c = CFG.get('combat', {})
    d = c.get('duel', {})
    return (section(t("combat-title", lang), t("combat", lang).format(sec=c.get('tag-seconds', 20)), 'combat')
            + section(t("clash-title", lang), t("clash", lang).format(stars=d.get('stars', 0.5), max=d.get('max-stars', 4)), 'clash')
            + section(t("mace-title", lang), t("mace", lang))
            + section(t("teams-title", lang), t("teams", lang), 'teams'))


def page_commands(lang):
    rows = ''.join(f'<tr><td><code>{esc(c)}</code></td><td>{esc(d[0] if lang == "cs" else d[1])}</td></tr>' for c, d in COMMANDS)
    return f'<table><tr><th>{t("cmd-col", lang)}</th><th>{t("what-col", lang)}</th></tr>{rows}</table>'


BUILDERS = {'index': page_index, 'forge': page_forge, 'weapons': page_weapons, 'relics': page_relics,
            'materials': page_materials, 'world': page_world, 'events': page_events, 'quests': page_quests,
            'pvp': page_pvp, 'commands': page_commands}


def main():
    if os.path.exists(OUT):
        shutil.rmtree(OUT)
    os.makedirs(OUT)
    sprite('forge_totem', 16)          # favicon
    for lang in LANGS:
        os.makedirs(os.path.join(OUT, lang))
        for slug, build in BUILDERS.items():
            title = 'Forgebound' if slug == 'index' else (PAGES[slug][0] if lang == 'cs' else PAGES[slug][1])
            with open(os.path.join(OUT, lang, slug + '.html'), 'w', encoding='utf-8') as f:
                f.write(layout(lang, slug, title, build(lang)))
    here = os.path.dirname(os.path.abspath(__file__))
    shutil.copy(os.path.join(here, 'style.css'), OUT)
    shutil.copy(os.path.join(here, 'app.js'), OUT)
    with open(os.path.join(OUT, 'index.html'), 'w', encoding='utf-8') as f:
        f.write('''<!doctype html><meta charset="utf-8"><title>Forgebound Wiki</title>
<script>location.replace((navigator.language||'').toLowerCase().startsWith('cs')||(navigator.language||'').toLowerCase().startsWith('sk')?'cs/index.html':'en/index.html')</script>
<a href="cs/index.html">Čeština</a> · <a href="en/index.html">English</a>''')
    open(os.path.join(OUT, '.nojekyll'), 'w').close()
    copy_images()
    print(f'wiki built: {OUT} ({len(LANGS) * len(BUILDERS)} pages, {len(USED)} icons, Forgebound {VERSION})')


if __name__ == '__main__':
    main()
