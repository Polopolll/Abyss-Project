# Changelog

## 0.3.0
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
