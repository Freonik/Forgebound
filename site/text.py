"""Texts of the wiki: (Czech, English). HTML is allowed. Item data comes from config.yml, not from here."""

PAGES = {
    'index': ('Domů', 'Home'),
    'forge': ('Kovárna a hvězdy', 'Forge & stars'),
    'weapons': ('Zbraně', 'Weapons'),
    'relics': ('Relikvie a sety', 'Relics & sets'),
    'materials': ('Legendární materiály', 'Legendary materials'),
    'world': ('Svět', 'The world'),
    'events': ('Eventy', 'Events'),
    'quests': ('Questy a ekonomika', 'Quests & economy'),
    'pvp': ('PvP a týmy', 'PvP & teams'),
    'commands': ('Příkazy', 'Commands'),
}

TRIGGERS = {
    'RIGHT_CLICK': ('Pravé tlačítko', 'Right-click'),
    'SHIFT_RIGHT_CLICK': ('Shift + pravé tlačítko', 'Shift + right-click'),
    'SWAP': ('F', 'F'),
    'SHIFT_SWAP': ('Shift + F', 'Shift + F'),
}

SLOTS = {
    'HEAD': ('helma', 'helmet'), 'CHEST': ('hrudník', 'chestplate'), 'LEGS': ('kalhoty', 'leggings'),
    'FEET': ('boty', 'boots'), 'OFFHAND': ('druhá ruka', 'off-hand'),
}

QUEST_TYPES = {
    'KILL': ('zabít', 'kill'), 'KILL_PLAYER': ('zabít hráče', 'kill players'), 'MINE': ('vytěžit', 'mine'),
    'FISH': ('rybařit', 'fish'), 'TRAVEL': ('cestovat', 'travel'), 'DELIVER': ('přinést', 'bring'),
}

SETS = {
    'heartsteel': {
        'cs': ('Heartsteel', 'Nabitý úder do <b>hráče</b> dá navíc 3 + 5 % tvého max. HP a <b>natrvalo</b> ti zvedne max. HP '
                             '(až o +10 srdcí, každý kus si pamatuje svůj díl). Jednou za 30 s na hráče, moby se nepočítají.'),
        'en': ('Heartsteel', 'A charged hit on a <b>player</b> deals 3 + 5 % of your max HP extra and <b>permanently</b> grows '
                             'your max HP (up to +10 hearts; every piece stores its share). Once per player every 30 s, mobs don\'t count.'),
    },
    'thorn': {
        'cs': ('Thorn', 'Vrací 2 + 25 % poškození zblízka (proti šípům polovinu) a útočník dostane <b>Grievous Wounds</b> – 3 s se nemůže léčit.'),
        'en': ('Thorn', 'Reflects 2 + 25 % of melee damage (half against arrows); attackers get <b>Grievous Wounds</b> – no healing for 3 s.'),
    },
    'thick_hide': {
        'cs': ('Thick Hide', '40 % poškození, které dostaneš, se změní na <b>šedé zdraví</b>, které během ~3 s mizí. '
                             '<b>Dvojitý Shift</b> ho setřese, takže o něj nepřijdeš (cooldown 20 s).'),
        'en': ('Thick Hide', '40 % of the damage you take becomes <b>grey health</b> that drains over ~3 s. '
                             '<b>Double-tap Shift</b> to shrug it off so you never lose it (20 s cooldown).'),
    },
    'pale_seraph': {
        'cs': ('Pale Seraph', 'Dva efekty, každý kus přidává k oběma. <b>Duše</b>: nabité údery plní Soul (max 99), drž Shift 1,5 s na místě '
                              'a 33 Soul se změní na 3 srdce. <b>Křídla</b>: když bys umřel, vrátíš se s 50 % HP a 3 s ochrany; poloviční pád.'),
        'en': ('Pale Seraph', 'Two effects, every piece adds to both. <b>Soul</b>: charged hits fill your Soul (max 99); hold Shift and stand still '
                              'for 1.5 s to turn 33 Soul into 3 hearts. <b>Wings</b>: when you would die you come back with 50 % HP and 3 s of '
                              'protection; half fall damage.'),
    },
}

COMMANDS = [
    ('Shift + pravé tlačítko na crafting table', ('otevře kovárnu', 'opens the forge')),
    ('/legends', ('všechny legendy na serveru a tvoje statistiky s nimi', 'every legend on the server and your stats with them')),
    ('/bounty', ('tvoje bounty, nejhledanější hráči a směnárna tokenů', 'your bounty, the most wanted players and the token exchange')),
    ('/shop', ('obchod za Bounty Tokeny', 'the Bounty Token shop')),
    ('/pay <hráč> <počet>', ('pošle tokeny jinému hráči', 'send tokens to another player')),
    ('/quests', ('tvoje denní a týdenní questy', 'your daily and weekly quests')),
    ('/events', ('co se právě děje', 'what\'s happening right now')),
    ('/top [kategorie]', ('žebříčky', 'leaderboards')),
    ('/codex', ('Forge Codex – kniha se vším ve hře', 'the Forge Codex – the in-game guide')),
    ('/hud', ('schová / ukáže sidebar', 'hide / show the sidebar')),
    ('/team', ('menu týmu (plugin Forgebound Teams)', 'the team menu (Forgebound Teams plugin)')),
    ('/tc, !zpráva · /ac, @zpráva', ('týmový chat · chat týmu a spojenců', 'team chat · team + allies chat')),
]

T = {
    'tagline': ('Legendární zbraně, které existují jen jednou. Ukovej je, ubraň je, nenech si je vzít.',
                'Legendary weapons that exist only once. Forge them, defend them, don\'t let anyone take them.'),
    'footer': ('Forgebound je plugin pro Paper 1.21.11. Tahle wiki se generuje z configu, takže čísla sedí s verzí na serveru.',
               'Forgebound is a plugin for Paper 1.21.11. This wiki is generated from the config, so the numbers match the server.'),
    'search': ('Hledat…', 'Search…'),
    'locked-tag': ('Tajné', 'Secret'),
    'locked': ('Schopnosti, staty a recept se odhalí se startem seasony.', 'Abilities, stats and the recipe are revealed when the season starts.'),
    'locked-set': ('Co celý set umí, se dozvíš se startem seasony.', 'What the full set does is revealed when the season starts.'),
    'teaser': ('✦ Sneak peek – všechno ostatní se odhalí se startem Season 1 ✦', '✦ Sneak peek – everything else is revealed when Season 1 starts ✦'),
    'start-title': ('Jak začít', 'Getting started'),
    'start': ('<ol><li><b>Shift + pravé tlačítko</b> na libovolný crafting table otevře <b>kovárnu</b> se všemi recepty.</li>'
              '<li>Každá legenda potřebuje jeden <a href="materials.html">legendární materiál</a> – padá z bossů, vzácných mobů a truhel ve strukturách.</li>'
              '<li>Až máš všechno, spustíš <a href="forge.html">rituál</a>. Celý server se dozví, co se kove, a postupně i kde.</li>'
              '<li>Hotová zbraň spadne na stůl – každá legenda existuje <b>jen jednou</b>, tak ji ubraň.</li>'
              '<li>V <a href="forge.html#ancient">Ancient Forge</a> jí přidáš hvězdy. Smrt ti je zase bere.</li></ol>',
              '<ol><li><b>Shift + right-click</b> any crafting table to open the <b>forge</b> with every recipe.</li>'
              '<li>Every legend needs one <a href="materials.html">legendary material</a> – from bosses, rare mobs and structure chests.</li>'
              '<li>When you have everything, start the <a href="forge.html">ritual</a>. The whole server learns what is being forged, and bit by bit where.</li>'
              '<li>The finished weapon drops on the table – every legend exists <b>only once</b>, so defend it.</li>'
              '<li>An <a href="forge.html#ancient">Ancient Forge</a> gives it stars. Dying takes them away.</li></ol>'),
    'loop-title': ('Jak to do sebe zapadá', 'How it fits together'),
    'loop': ('<div class="flow"><span>bossové a struktury</span>→<span>legendární materiál</span>→<span>rituál v kovárně</span>→'
             '<span>legenda (jen jedna)</span>→<span>hvězdy v Ancient Forge</span>→<span>bounty, PvP, eventy</span></div>',
             '<div class="flow"><span>bosses & structures</span>→<span>legendary material</span>→<span>forge ritual</span>→'
             '<span>a legend (only one)</span>→<span>stars at an Ancient Forge</span>→<span>bounties, PvP, events</span></div>'),
    'f-forge': ('Rituály, jediná kopie, hvězdy', 'Rituals, one of each, stars'),
    'f-weapons': ('Schopnosti, ceny, recepty', 'Abilities, costs, recipes'),
    'f-relics': ('Relikvie a sety brnění', 'Relics and armor sets'),
    'f-materials': ('Kde co padá', 'Where everything drops'),
    'f-world': ('Scorched Lands, meteory, Warden', 'Scorched Lands, meteors, the Warden'),
    'f-events': ('Blood Moon, Rift, velké eventy', 'Blood Moon, Rift, big events'),
    'f-quests': ('Questmaster, bounty, obchod, totem', 'Questmaster, bounties, shop, totem'),
    'f-pvp': ('Combat tag, souboje legend, týmy', 'Combat tag, clashes, teams'),
    'f-commands': ('Všechny příkazy pro hráče', 'Every player command'),

    # forge
    'forge-menu-title': ('Kovárna', 'The forge'),
    'forge-menu': ('<p><b>Shift + pravé tlačítko</b> na crafting table s prázdnou rukou. Vybereš recept a u materiálů vidíš '
                   '<i>potřeba / máš</i>. Když máš všechno, klikni na <b>FORGE</b>.</p>',
                   '<p><b>Shift + right-click</b> a crafting table with an empty hand. Pick a recipe; the materials show '
                   '<i>needed / you have</i>. When you have everything, press <b>FORGE</b>.</p>'),
    'ritual-title': ('Rituál', 'The ritual'),
    'ritual': ('<ul><li>Materiály zmizí a celý server se dozví, <b>co</b> se kove – ale ne kdo.</li>'
               '<li>Boss bar ukazuje zbývající čas a nápovědu, kde to je. Poloha se odhaluje postupně: '
               'nejdřív jen dimenze, v 33 % oblast ±500 bloků, v 66 % ±100 bloků a v 87 % přesné souřadnice.</li>'
               '<li>Předmět se točí nad stolem a stůl nejde rozbít, spálit ani vyhodit do vzduchu.</li>'
               '<li>Na konci udeří blesk a legenda <b>spadne na stůl</b>. Kdo ji sebere, toho je.</li>'
               '<li>Rituál potřebuje aspoň <b>{min} hráče online</b>; když jich ubude, čas se zastaví.</li></ul>',
               '<ul><li>The materials are taken and the whole server learns <b>what</b> is being forged – but not by whom.</li>'
               '<li>A boss bar shows the time left and a hint where it is. The location is revealed step by step: '
               'first only the dimension, at 33 % an area of ±500 blocks, at 66 % ±100 blocks and at 87 % the exact coordinates.</li>'
               '<li>The item spins above the table, and the table can\'t be broken, burned or blown up.</li>'
               '<li>At the end lightning strikes and the legend <b>drops on the table</b>. Whoever picks it up owns it.</li>'
               '<li>A ritual needs at least <b>{min} players online</b>; if fewer are left, the timer pauses.</li></ul>'),
    'unique-title': ('Každá legenda jen jednou', 'One of each'),
    'unique': ('<p>Každá zbraň a relikvie může na serveru existovat <b>jen jednou</b>. Jakmile se její rituál spustí, nikdo jiný ji kovat nemůže. '
               'Když se legenda zničí (láva, oheň, void, zmizí na zemi), dá se ukovat znovu. A když její majitel dlouho nehraje, '
               'legenda se rozpadne a vrátí do kovárny.</p>',
               '<p>Every weapon and relic can exist <b>only once</b> on the server. As soon as its ritual starts, nobody else can forge it. '
               'If the legend is destroyed (lava, fire, the void, despawning), it can be forged again. And if its holder stops playing '
               'for a long time, it crumbles and goes back to the forge.</p>'),
    'stars-title': ('★ Hvězdy', '★ Stars'),
    'stars': ('<p>Každá legendární zbraň má <b>1 až 5 hvězd</b> (po půlkách). Každá hvězda nad ★★★ přidává {pct} % poškození a ubírá stejně '
              'z cooldownů, každá pod ★★★ naopak ubírá. Čerstvě ukovaná zbraň má ★.</p>'
              '<ul><li><b>Smrt bere hvězdy:</b> zabije-li tě hráč, přijdeš o <b>{pvp} ★</b> na každé nesené zbrani, jakákoli jiná smrt bere {other} ★. Pod ★ nespadne nic.</li>'
              '<li>Hvězdy se získávají <b>jen v Ancient Forge</b>.</li></ul>',
              '<p>Every legend weapon has <b>1 to 5 stars</b> (in half steps). Each star above ★★★ adds {pct} % damage and takes the same off the '
              'cooldowns, each one below takes it away. A freshly forged weapon has ★.</p>'
              '<ul><li><b>Dying costs stars:</b> killed by a player you lose <b>{pvp} ★</b> on every weapon you carry, any other death costs {other} ★. Nothing drops below ★.</li>'
              '<li>Stars are earned <b>only at an Ancient Forge</b>.</li></ul>'),
    'ancient-title': ('Ancient Forge', 'Ancient Forges'),
    'ancient': ('<p>Jsou tři, každá silnější: ve <a href="world.html#scorched">Scorched Lands</a> (do ★★), v <b>Netheru</b> (do ★★★) a v <b>Endu</b> '
                '(do ★★★★★) – ta se vynoří z voidu na létajícím ostrově, <b>když poprvé padne drak</b>. Nad každou stoupá sloup soul ohně, '
                'takže je vidět z dálky. Použij kovadlinu se zbraní v ruce: jeden rituál = o hvězdu výš. Zbraň zůstane v kovárně až do konce '
                'a pak tam spadne – kdo tam zrovna stojí, ten si ji vezme. Potom kovárna den chladne.</p>',
                '<p>There are three, each stronger: in the <a href="world.html#scorched">Scorched Lands</a> (up to ★★), in the <b>Nether</b> (up to ★★★) and in '
                'the <b>End</b> (up to ★★★★★) – it rises out of the void on a floating island <b>when the dragon dies for the first time</b>. A pillar of '
                'soul fire rises above each one. Use the anvil holding the weapon: one ritual = one star up. The weapon stays in the forge until '
                'the end and then drops there – whoever is standing there takes it. Afterwards the forge cools down for a day.</p>'),
    'star-col': ('Hvězda', 'Star'), 'time-col': ('Čas', 'Time'), 'tokens-col': ('Tokeny', 'Tokens'), 'items-col': ('Předměty', 'Items'),

    # items
    'weapons-intro': ('Každá zbraň má pasivní efekt, efekt při zásahu a dvě schopnosti na <kbd>F</kbd> a <kbd>Shift + F</kbd>. '
                      'Čísla platí pro ★★★ – hvězdy je mění.',
                      'Every weapon has a passive, an on-hit effect and two abilities on <kbd>F</kbd> and <kbd>Shift + F</kbd>. '
                      'The numbers are for ★★★ – stars change them.'),
    'relics-intro': ('Relikvie fungují jen ve svém slotu. Nosit můžeš jednu na slot: helma, hrudník, kalhoty, boty a druhá ruka.',
                     'Relics only work in their own slot. You can wear one per slot: helmet, chestplate, leggings, boots and off-hand.'),
    'single-relics': ('Samostatné relikvie', 'Single relics'),
    'sets-how-title': ('Jak fungují sety', 'How sets work'),
    'sets-how': ('<p>Každý kus setu je vlastní legenda (vlastní rituál, jedna na server). <b>Každý kus dává 1/4 efektu, všechny čtyři '
                 'dohromady 5/4.</b> Kusy různých setů jde kombinovat, ale celý set je jasně nejsilnější. Action bar ukazuje, kolik kusů máš (2/4, 4/4 ✦).</p>',
                 '<p>Every piece of a set is its own legend (its own ritual, one per server). <b>Each piece gives 1/4 of the effect, all four '
                 'together 5/4.</b> You can mix pieces of different sets, but the full set is clearly the strongest. The action bar shows how many you wear (2/4, 4/4 ✦).</p>'),
    'materials-intro': ('<p>Každá legenda potřebuje jeden legendární materiál. <b>Nic není jisté</b> – nejvyšší šance je 45 %. '
                        'Z mobů padají jen když je zabije <b>hráč</b>. Materiál na zemi svítí, nehoří a nezmizí, a celý server se o nálezu dozví.</p>',
                        '<p>Every legend needs one legendary material. <b>Nothing is guaranteed</b> – the best chance is 45 %. '
                        'Mob drops only count when a <b>player</b> kills the mob. The material glows on the ground, can\'t burn or despawn, and every find is announced.</p>'),
    'forge-cost': ('Cena ve kovárně', 'Forge cost'),
    'where': ('Kde ho najdeš', 'Where to find it'),
    'used-for': ('Na co', 'Used for'),
    'chest': ('Truhly:', 'Chests:'),
    'in-biome': ('v', 'in'),
    'above-y': ('nad Y', 'above Y'),
    'passive': ('Pasivně', 'Passive'),
    'onhit': ('Při zásahu', 'On hit'),
    'charges': ('nabití', 'charges'),
    'unlock': ('odemkne se po {} killech', 'unlocks after {} kills'),
    'kills-tag': ('Roste s killy', 'Grows with kills'),
    'kills-desc': ('Zbraň počítá zabité hráče a postupně odemyká schopnosti a enchanty.', 'The weapon counts player kills and unlocks abilities and enchantments over time.'),

    # world
    'scorched-title': ('🌋 Scorched Lands', '🌋 Scorched Lands'),
    'scorched': ('<p>Vzácný spálený biom: popel ve vzduchu, šedooranžová obloha, struska, blackstone a magma, ze stromů zbyly pahýly. '
                 'Najdeš v něm ruiny – <b>Ruined Forge</b> s velkou truhlou a strážemi, <b>Burnt Workshop</b>, <b>Slag Heap</b>, <b>Watchtower</b> – a v jednom '
                 'z nich i <b>Ancient Forge</b>. Stráže jsou huskové v otlučeném brnění a truhly mají železo, zlato, občas diamanty, '
                 'netherite scrap, Bounty Tokeny a ve velkých i legendární materiál.</p>'
                 '<p>Na serveru jsou vždy aspoň <b>dvě oblasti</b> 500–1700 bloků od středu.</p>',
                 '<p>A rare burnt biome: ash in the air, a grey-orange sky, slag, blackstone and magma, trees burnt down to stumps. '
                 'It has ruins – the <b>Ruined Forge</b> with a big chest and guards, the <b>Burnt Workshop</b>, the <b>Slag Heap</b>, the <b>Watchtower</b> – and '
                 'in one of them an <b>Ancient Forge</b>. The guards are husks in battered armor, and the chests have iron, gold, sometimes diamonds, '
                 'netherite scrap, Bounty Tokens and (big chests) a legendary material.</p>'
                 '<p>The server always has at least <b>two areas</b> 500–1700 blocks from the centre.</p>'),
    'meteor-title': ('☄ Meteory', '☄ Meteors'),
    'meteor': ('<p>Každé 2–4 hodiny dopadne meteor do 2500 bloků od spawnu. Boss bar odpočítává 5 minut a místo se odhaluje postupně. '
               'V kráteru je <b>Meteor Cache</b>: vždy legendární materiál, k tomu diamanty, zlato, XP, Bounty Tokeny a někdy Legend Tracker. '
               'Občas místo hlídá Forge Warden.</p>',
               '<p>Every 2–4 hours a meteor falls within 2500 blocks of spawn. A boss bar counts down five minutes and the site is revealed step by step. '
               'The crater has a <b>Meteor Cache</b>: always a legendary material, plus diamonds, gold, XP, Bounty Tokens and sometimes a Legend Tracker. '
               'Sometimes the Forge Warden guards it.</p>'),
    'warden-title': ('⚒ Forge Warden', '⚒ The Forge Warden'),
    'warden': ('<p><b>Shift + pravé tlačítko na crafting table s Nether Starem</b> probudí Forge Wardena – obra v netheritu s 500 HP. '
               'Dupe, přitahuje řetězy, volá pomocníky, pod 50 % chrlí oheň a pod 25 % zuří. Když padne: 1000 XP, 10 Bounty Tokenů a '
               '<b>Warden\'s Seal</b> pro toho, kdo dal nejvíc poškození – pravým tlačítkem si z něj vybereš <b>jakýkoli</b> legendární materiál.</p>',
               '<p><b>Sneak + right-click a crafting table holding a Nether Star</b> to wake the Forge Warden – a giant in netherite with 500 HP. '
               'It slams, drags people in with chains, calls thralls, breathes fire below 50 % and rages below 25 %. When it falls: 1000 XP, 10 Bounty Tokens '
               'and a <b>Warden\'s Seal</b> for whoever dealt the most damage – right-click it to choose <b>any</b> legendary material.</p>'),

    # events
    'events-intro': ('<code>/events</code> ukáže, co se právě děje. Běžné eventy startují samy, když je online aspoň pár hráčů.',
                     '<code>/events</code> shows what\'s happening right now. Regular events start on their own when a few players are online.'),
    'bloodmoon-title': ('☾ Blood Moon', '☾ Blood Moon'),
    'bloodmoon': ('<p>Někdy v noci vyjde krvavý měsíc. Do rána mají monstra víc života a víc bolí, chodí ve dvojicích, '
                  'objevují se <b>Blood-crazed elity</b> (3× XP a Bounty Tokeny), legendární materiály padají častěji a <b>nejde spát</b>.</p>',
                  '<p>Some nights the Blood Moon rises. Until dawn monsters have more health and hit harder, come in pairs, '
                  '<b>Blood-crazed elites</b> appear (3× XP and Bounty Tokens), legendary materials drop more often and <b>nobody can sleep</b>.</p>'),
    'rift-title': ('✷ Rift', '✷ Rift'),
    'rift': ('<p>Každé 1,5–3 hodiny se u náhodného hráče otevře trhlina. Souřadnice se ohlásí hned, po 3 minutách z ní vyleze <b>5 vln</b> '
             'monster až po Rift Ravagera a Rift Evokera. Odměna se dělí podle toho, kdo kolik udělal; nejlepší dostane i legendární materiál.</p>',
             '<p>Every 1.5–3 hours a Rift opens near a random player. The coordinates are announced right away and after 3 minutes <b>5 waves</b> '
             'of monsters pour out, up to a Rift Ravager and a Rift Evoker. Rewards are split by how much everyone did; the best player also gets a legendary material.</p>'),
    'hunt-title': ('☠ Legend Hunt', '☠ Legend Hunt'),
    'hunt': ('<p>Každé 2–4 hodiny je náhodný držitel legendy <b>hodinu loven</b>: svítí, jeho oblast se každých 5 minut ohlásí a jeho bounty '
             'se počítá dvakrát. Zato se mu schopnosti nabíjejí dvakrát rychleji. Když hodinu přežije, dostane tokeny.</p>',
             '<p>Every 2–4 hours a random legend holder is <b>hunted for an hour</b>: they glow, their area is announced every 5 minutes and their '
             'bounty counts double. In return their abilities recharge twice as fast. Survive the hour and they get tokens.</p>'),
    'small-title': ('Malé eventy', 'Small events'),
    'small': ('<ul><li><b>✦ Happy Hour</b> – dvojnásobné XP, dvojnásobné dropy materiálů, nebo rituály dvakrát rychleji.</li>'
              '<li><b>⚖ Forge Market</b> – speciální nabídky v <code>/shop</code>.</li>'
              '<li><b>✉ Golden Shipment</b> – někde nedaleko spawnu se snese bedna; kdo ji najde první, ten bere.</li></ul>',
              '<ul><li><b>✦ Happy Hour</b> – double XP, double material drops or rituals twice as fast.</li>'
              '<li><b>⚖ Forge Market</b> – special offers in <code>/shop</code>.</li>'
              '<li><b>✉ Golden Shipment</b> – a crate floats down somewhere near spawn; first come, first served.</li></ul>'),
    'big-title': ('⚔ Velké eventy', '⚔ Big events'),
    'big': ('<p>Velké eventy plánuje admin dopředu – server je ohlásí den, hodinu, 10 minut a minutu předem a posledních 10 minut běží odpočet.</p>'
            '<ul><li><b>☠ The Closing World</b> – border se stahuje k jednomu místu. Smrt, odpojení nebo odchod ze světa = konec. Vyhrává poslední, kdo zůstane.</li>'
            '<li><b>⚔ The Hunt</b> – všichni jsou vidět na locator baru. Vyhrává nejvíc zabitých hráčů.</li>'
            '<li><b>☠ The Forge Colossus</b> – obří boss: dupe, hází ohnivé koule, přitahuje a volá kostlivce. Ublížit mu můžou jen hráči. Vyhrává nejvíc poškození.</li></ul>'
            '<p>Vítěz dostane <a href="#mythic">mytickou zbraň</a> na {days} dní, ostatní tokeny, materiály a <a href="quests.html#totem">Totem of the Forge</a>.</p>',
            '<p>Big events are planned ahead by an admin – the server announces them a day, an hour, 10 minutes and a minute before, and the last 10 minutes count down.</p>'
            '<ul><li><b>☠ The Closing World</b> – the border closes in on one spot. Dying, logging out or leaving the world puts you out. The last one standing wins.</li>'
            '<li><b>⚔ The Hunt</b> – everyone shows on the locator bar. The most player kills wins.</li>'
            '<li><b>☠ The Forge Colossus</b> – a giant boss: it slams, throws fire, drags people in and calls skeletons. Only players can hurt it. The most damage wins.</li></ul>'
            '<p>The winner gets a <a href="#mythic">mythic weapon</a> for {days} days; everyone else tokens, materials and a <a href="quests.html#totem">Totem of the Forge</a>.</p>'),
    'mythic-title': ('✦ Mytické zbraně', '✦ Mythic weapons'),
    'mythic': ('Mnohem silnější než legendy – ale po {days} dnech <b>vyprchají</b>, ať jsou kdekoli. Nemají hvězdy, nedají se ukovat a může jich být víc.',
               'Far stronger than legends – but they <b>fade</b> after {days} days, wherever they are. No stars, they can\'t be forged and there can be several.'),

    # quests & economy
    'questmaster-title': ('📜 Questmaster', '📜 The Questmaster'),
    'questmaster': ('<p>Questmaster stojí na spawnu. Každý den má <b>{daily} denní</b> questy a každý týden (od pondělí) <b>{weekly} týdenní</b> – '
                    'pro všechny stejné. Postup se počítá kdekoli (<code>/quests</code>), odměnu si vyzvedneš u něj. Za všechny denní questy v jednom dni '
                    'je bonus <b>{bonus} tokeny</b>. Položené bloky se do těžby nepočítají a každý hráč se v kill questu počítá jen jednou.</p>',
                    '<p>The Questmaster stands at spawn. Every day he has <b>{daily} daily</b> quests and every week (from Monday) <b>{weekly} weekly</b> – '
                    'the same for everybody. Progress counts anywhere (<code>/quests</code>), the reward is claimed at him. All daily quests in one day give '
                    'a bonus of <b>{bonus} tokens</b>. Blocks you placed don\'t count for mining, and every player counts only once for kill quests.</p>'),
    'daily-title': ('Denní questy (náhodně {} z nich)'.replace(' ({} z nich)', ''), 'Daily quests'),
    'weekly-title': ('Týdenní questy', 'Weekly quests'),
    'quest-col': ('Quest', 'Quest'), 'type-col': ('Typ', 'Type'), 'reward-col': ('Odměna', 'Reward'),
    'tokens-word': ('tokenů', 'tokens'), 'material-word': ('legendární materiál', 'legendary material'),
    'bounty-title': ('Bounty', 'Bounties'),
    'bounty': ('<p>Kdo nosí legendu, má na hlavě odměnu: <b>+1 Bounty Token každých 10 minut</b>, až 20. Při 5, 10 a 20 se to dozví celý server. '
               'Kdo tě zabije, vezme si celou tvoji bounty. Stejný hráč za stejnou oběť znovu až po 30 minutách.</p>'
               '<p>V <code>/bounty</code> si za tokeny koupíš <b>Legend Tracker</b> (kompas k nejbližšímu držiteli legendy), náhodný nebo vybraný legendární materiál.</p>',
               '<p>Carrying a legend puts a price on your head: <b>+1 Bounty Token every 10 minutes</b>, up to 20. At 5, 10 and 20 the whole server is told. '
               'Whoever kills you claims your whole bounty. The same killer can claim the same player again only after 30 minutes.</p>'
               '<p>In <code>/bounty</code> tokens buy a <b>Legend Tracker</b> (a compass to the nearest legend holder), a random or a chosen legendary material.</p>'),
    'shop-title': ('🪙 Obchod', '🪙 The shop'),
    'shop': ('Bounty Tokeny jsou měna serveru. <code>/shop</code> má stálou nabídku a týdenní nabídku, která se každých 7 dní mění. Tokeny pošleš přes <code>/pay</code>.',
             'Bounty Tokens are the server\'s currency. <code>/shop</code> has a fixed offer and a weekly offer that changes every 7 days. Send tokens with <code>/pay</code>.'),
    'shop-perm': ('Vždy k dispozici', 'Always available'),
    'shop-rot': ('Týdenní nabídka (náhodně několik z nich)', 'Weekly offer (a few of these at random)'),
    'totem': ('<p>Totem of undying, který funguje <b>odkudkoli z inventáře</b>. Vrátí tě s <b>plným HP</b>, na 3 s jsi nezranitelný, dostaneš absorpci '
              'a ohnivý výbuch odhodí všechny kolem. Získáš ho z velkých eventů, některých týdenních questů a občas v týdenní nabídce obchodu.</p>',
              '<p>A totem of undying that works <b>from anywhere in your inventory</b>. It brings you back at <b>full health</b>, untouchable for 3 s, with '
              'absorption, and a burst of fire throws everyone around you back. From big events, some weekly quests and now and then the weekly shop offer.</p>'),
    'legends-title': ('Legendy a /legends', 'Legends and /legends'),
    'legends': ('<p><code>/legends</code> ukáže všechny legendy na serveru (volné, kované, něčí) a tvoje vlastní statistiky s nimi. '
                'Když držitel legendy {days} dní nehraje, legenda se rozpadne a vrátí do kovárny.</p>',
                '<p><code>/legends</code> shows every legend on the server (free, being forged, owned) and your own stats with them. '
                'If a legend\'s holder doesn\'t play for {days} days, it crumbles and goes back to the forge.</p>'),

    # pvp
    'combat-title': ('⚔ Combat tag', '⚔ Combat tag'),
    'combat': ('<p>Když dáš nebo dostaneš ránu od hráče, jsi <b>{sec} s v boji</b> (červený bar). <b>Odpojení v boji = smrt</b>: itemy spadnou '
               'a kill dostane ten, kdo tě trefil naposled. V boji nejde letět s elytrou.</p>',
               '<p>Hit a player or get hit by one and you are <b>in combat for {sec} s</b> (a red bar). <b>Logging out in combat = death</b>: your items drop '
               'and whoever hit you last gets the kill. No elytra while in combat.</p>'),
    'clash-title': ('Souboj legend', 'Clash of legends'),
    'clash': ('<p>Zabij hráče, který nese legendu, <b>s legendou v ruce</b> a tvoje zbraň dostane <b>+{stars} ★</b> (nejvýš do {max} ★ – poslední hvězdy dává jen End).</p>',
              '<p>Kill a player who carries a legend <b>with a legend in your hand</b> and your weapon gets <b>+{stars} ★</b> (up to {max} ★ – the last stars only come from the End).</p>'),
    'mace-title': ('Mace', 'Maces'),
    'mace': ('<p>Vanilla mace se na serveru craftit nedá.</p>',
             '<p>Vanilla maces can\'t be crafted on this server.</p>'),
    'teams-title': ('Týmy', 'Teams'),
    'teams': ('<p>Týmy přidává plugin <a href="https://github.com/Freonik/Forgebound-Teams">Forgebound Teams</a>. <code>/team</code> otevře menu, kde jde '
              'naklikat všechno: založení týmu (až 3 hráči), pozvánky, tag a barva před jménem, týmový chat (<code>!zpráva</code>), spojenci '
              '(<code>@zpráva</code>), války a žebříček týmů. Spoluhráči a spojenci si neublíží – ani schopnostmi legend.</p>',
              '<p>Teams come from the <a href="https://github.com/Freonik/Forgebound-Teams">Forgebound Teams</a> plugin. <code>/team</code> opens a menu where '
              'everything can be clicked: create a team (up to 3 players), invites, a tag and color in front of the names, team chat (<code>!message</code>), allies '
              '(<code>@message</code>), wars and the team leaderboard. Teammates and allies can\'t hurt each other – not even with legend abilities.</p>'),

    'cmd-col': ('Příkaz', 'Command'), 'what-col': ('Co dělá', 'What it does'),
}
T['daily-title'] = ('Denní questy', 'Daily quests')
