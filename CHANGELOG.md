# Changelog

## Repository
- The project is now public at https://github.com/Polopolll/Abyss-Project under the MIT License (code and all assets). Every released jar is kept in [`releases/`](releases/).
- Added [`pictures/`](pictures/) (screenshots of every weapon, the domain and the icons, plus 0.2 images) and [`references/`](references/) (credits for the mods and games Abyss drew on, and what each one inspired).

## 0.3.2 (in review)
**Crash fix:** the effects renderer created new vertex buffers every frame, and that native memory was never freed, so after ~15 minutes the game ran out of memory and crashed. It now reuses one set; memory stays flat (tested: 56 moves back to back, no growth).

**Sounds:** the synthesized sounds are gone. Every Arsenal and domain sound is now built from Minecraft's own recordings, pitched and layered for the Abyss: sweeps, tridents, the warden's sonic boom and charge, amethyst, illusioner mirrors, end portal, respawn anchor.

**Weapon models rebuilt:** the constructs were see-through (faces drew over each other in the wrong order) and crude. They're now solid, faceted crystal with lit faces:
- Abyssal Edge: a slim, slightly curved blade with a clipped point, swept guard, gem and banded grip.
- Event Cleaver: a broad blade with ricasso shoulders and a chisel point, a dark spine with a set gem, winged guard.
- Singularity Maul: an octagonal drum head banded in dark crystal, end spikes and a top spike.
- Horizon Reaper: a thick-backed crescent sweeping out and down to a point, with a back spike.
- Rift Lance: a leaf head with a midrib, hooked wings and a butt spike.
- Aegis & Shard: a broad shard blade, and a hex shield with a bevelled rim and a gem boss.
- Hands of the Abyss: cuffs, armoured backs with a gem, and knuckle spikes.
- In first person the blades now show their face instead of their edge, and the shield sits in view.
- **You actually hold them now** (Better Combat style). First person draws your real arms: the right fist closes on the grip, and on two-handed weapons (Event Cleaver, Singularity Maul, Horizon Reaper, Rift Lance) your left hand holds the grip or shaft through every swing. The Aegis is strapped to your left forearm, and the gauntlets are worn on your hands. The scythe rests diagonally with its crescent sweeping out to the left.
- Third person has a ready stance per weapon, and for two-handers the left hand is placed on the shaft by inverse kinematics every frame, during moves too.

**The look (from Andy's references):**
- The domain sky is a nebula: billowing violet, magenta, blue and crimson clouds with lit edges and dark dust lanes, star clusters, and a lensed black hole (Gargantua-style) hanging in it.
- Black holes everywhere: stars in moves, the gathering before a nuke, the domain's birth and Collapse are now real black holes, a spinning swirl (cyan-white arcs into an oval shadow) or the lensed kind (a hot disk across the shadow, its far side bent into a ring).
- Spores are tiny turning black holes.
- Big flashes are softer (no more white-outs). The welcome message shows your actual key bindings.
- First-person clean-up: nothing smears across your view any more. Glows, beams, motes and shells that pass right by your eye fade out instead of filling the screen. Ground shockwaves stay rings instead of a disc under your feet.
- Liberations: stars and black holes sit far enough out to be seen whole. The Rift Lance's giant spear hangs over your right shoulder and its beam slants down onto your target. The Aegis shield lands in front of you, not on top of you.

## 0.3.1
Scope cut down to what matters most: the wheel, the movesets, and Domain Expansion.

**The wheel works**
- Holding X opens it, and it stays open while you hold. In 0.3.0, key-repeat made it close itself after about a second.
- Point and let go to select. Drag a medallion onto another to swap their places (saved). Press 1-9 to pick a slot directly.
- The details panel sits beside the wheel instead of under it, where it ran into the hotbar.

**Movesets, rebuilt the Chronoweave / Wuthering Waves way**
- One clean slash per hit: a crescent of light (violet glow, body, white core) that sweeps across in about 2 ticks with a white-hot head, holds a beat, and dissolves tail-first. A thin seam of the abyss is left behind and closes.
- Slashes are laid out from your eyes and full look direction, so in first person they cross the screen around the crosshair wherever you aim. They always face the camera, so they never go edge-on, and they fade out right at your eye instead of flooding the screen.
- Each move has one bold main cut, and its secondary cuts are faint hairlines, so nothing piles up.
- Thrusts shoot a spear of light along your aim with a ring where it stops.
- Rend storms are quick successive slashes ending in one bright burst.
- Black stars have a hard black core and an accretion ring (no more pale discs).
- Giant constructs are made of light, with a sigil where they come through (no flat void panels).
- The Aegis barrier is a wall of glowing hex cells.
- Impacts are a tight white star with spikes and a ring. Shockwave, landing and burst flashes are smaller.
- **Sound for everything:** a new set of 21 sounds (made with `tools/make_sounds.py`): three swing takes, a heavy swing, the abyss "tear", three hit takes, a heavy hit, construct summon, crystal shatter, a Liberation drop, dodge, perfect dodge, charge, domain open, spears, pulse, collapse, and form open. Each is timed to its visual beat: on spawn, on each storm cut, and on each burst or landing.

**Domain Expansion**
- Fixed the misaligned sky: it was a 100-block cube that crossed the camera's far plane at short render distances and broke into flat panes. It's now a small cube drawn without depth, so the galaxy fills the whole sky cleanly.
- Clouds are hidden inside an open domain and come back afterwards.
- New open, pulse, collapse and spears sounds; the spears ring out at each target as they erupt.

## 0.3.0
**Fixes from the screenshot review**
- Constructs are mounted on the arm bone (third person) and have their own first-person view model; the first-person swing never lifts a weapon over the camera.
- Cuts are drawn after clouds (no white blocks through them), face the camera so they never vanish edge-on, and fade when they pass through your eyes; void spheres and self-centred spikes no longer fill the screen.
- Holding X reads the key binding's state as well as the raw key, so the wheel opens reliably.

**Controls and forms (Ronova-style)**
- Skills are now forms: R opens one, and while it's open LMB / RMB / Mouse 4 / Mouse 5 are its four sub-actions, with hints by the crosshair that change with context (Shift, airborne, counter ready, in the Realm). R again closes it.
- New keys: X tap = next skill, hold X = wheel, X + 1–8 = that slot, X + scroll = flip; R use/open/close; G bodies screen; H manual (unbound). The old V / C / Left Alt / Z / Shift+Z / B / combat-mode keys are gone.
- Wheel: drag a medallion onto another to swap their places; the order is saved.
- New HUD: selected-skill medallion with its neighbours, output bar, Arsenal weapon medallion with resonance gauge and chain pips, Spore payload ring, Realm banner.
- The Domain becomes your open form while it stands: Sure-Hit Spears, Pressure Pulse, Collapse, Dismiss.

**Abyssal Arsenal (replaces combat mode)**
- Seven forged weapons (Abyssal Edge, Event Cleaver, Singularity Maul, Horizon Reaper, Rift Lance, Aegis & Shard, Hands of the Abyss), switched with scroll. Each has its own basic chain, heavy, plunge, counter, skill and liberation. The old combo, Abyssal Beam and Gravity Fold now live on the gauntlets.
- LMB tap chains, hold for the heavy, LMB in the air for the plunge; RMB dodges, and a perfect dodge opens a 2.5 s counter window; Mouse 4 skill (costs output); Mouse 5 liberation at 100 resonance. One press is buffered during a move.
- Aegis & Shard: hold to guard against frontal hits; the damage absorbed powers Retribution.
- Moves are data, played on one timeline on server and clients. Constructs sit on the arm bone and have a first-person view model; rends show the galaxy behind them, then sever. Hit-stop, damage numbers (`damageNumbers`), perfect-dodge slow-motion.

**The Realm**
- Authority form, RMB: step into the Abyss at the same coordinates. Fly, heal, and see other players' souls through walls. Hold LMB to peek into the reflected world, RMB to leave through a two-way rift, Mouse 5 to choose which world you exit into.

**Spores**
- Spore Form: RMB hold paints a selection over spores and infected creatures; Mouse 4 unleashes the payload on all of them at once, or with nothing selected on everything of yours in front of you. Shift+LMB seeker spore, Shift+RMB plant.

**Visuals**
- The domain's galaxy sky is world-anchored and overlaid on the sky instead of following the view.
- New effects: domain sure-hit spears, domain collapse, Beelzebub inhale, dodge, perfect dodge, weapon forging, Realm gate, peek eye, form sigils and auras, guard, souls through walls.
- Effects retimed with a build-up, impact and linger. The nuke gathers for 22 ticks; inside-out detonations build up for 18 ticks.
- Domain infection now has eight stages (adds hairline veins, bleeding and engulfing). Plants and liquids go at stage 3, leaves at stage 6.

**Other**
- Settings screen (Visuals and World rules tabs), from the Mods menu Config button or the manual. World rules are read-only on a server.
- The manual: chapters for every skill and weapon, with looping top-down previews of each move.
- `/abyss demo move|form|fx` for testing and recording.
- Cooldowns are still off by default (`cooldownsEnabled`).

## 0.2.1 (2026-11-18)
- Domain corruption reworked: blocks keep their own texture and the abyss spreads over them as an overlay in five stages (veins → cracks → patches → spreading → nearly swallowed), 4 patterns × 4 rotations per face so nothing repeats. Dark galaxy veins with thin violet rims; uncovered parts darken as the stage rises.
- Plants and liquids wither at stage 2, leaves at stage 4; veins creep into neighbouring blocks.
- When the domain ends the infection recedes stage by stage and withered plants and water grow back. `domainScarsPermanent=true` instead turns fully infected blocks into Abyssal Rot (now with random rotations).

## 0.2.0 (2026-11-18)
- Chronoweave-style radial wheel (with icons) and a compact HUD by the hotbar (selected-ability medallion, every ability with cooldown sweeps, output bar, bodies).
- One-handed controls: V wheel, R cast, C spores, Left Alt lens, X combat mode, Z/Shift+Z bodies, B main, G bodies screen.
- One Domain slot: pick Open or Closed while charging (scroll / right-click).
- Combat mode: 5-hit abyssal combo, Abyssal Beam (hold), Rift Lunge (sprint), Gravity Fold (falling), with player poses.
- Open Domain overhaul: galaxy sky and wall, stars, nebula, rising debris, expansion front, pressure pulses, ground rots into Abyssal Rot (heals afterwards), items dissolve, FOV and vignette pressure.
- Vessel: stronger body, immunities, Sovereignty (can't be killed; mixin-enforced).
- No cooldowns by default (`cooldownsEnabled`); Abyssal Upgrade (creative only) for infinite output.
- Spore Lens: payload ring around the crosshair, right-click fires.
- Self-test: 22 checks.

## 0.1.0 (2026-11-18)
First build.
- Abyss user (Abyssal Core, one per world), infinite reserve with a per-second output cap, mastery.
- Spores: 360° spread, plant and tag modes, latch or pass-through, corrosion, multiplication and jumping between hosts, Spore Lens with 8 payloads.
- Domain Expansion: Open (charged, spreads at the expansion rate) and Closed (copies the area into an Abyss arena), crushing weight, sure hit.
- Percentage Negation (damage and harmful effects, paid in output), soul resistance.
- Beelzebub (devour, stat and immunity share), Brood puppets, Mimic Volley, Transport to the Abyss.
- Two-way rifts to any dimension; Rift screen; rift-step.
- Triangle Combat Power clash.
- Main body and clones: Descend, swap between bodies, orders (Hold/Follow/Attack/Mirror/Patrol), death falls back to the main body; bodies survive restarts.
- Combat Power for every entity from every mod, datapack overrides, API, Angel Eyes HUD.
- The Abyss dimension with a galaxy sky; crystalline void body for the user and clones (galaxy shader, purple seams), corrosion cracks on every creature.
- `/abyss selftest` (14 checks, all passing on a dedicated server with a real client).
