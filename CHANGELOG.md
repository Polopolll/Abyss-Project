# Changelog

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
