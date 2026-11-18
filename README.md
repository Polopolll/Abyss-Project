# Abyss

Standalone Forge 1.20.1 (Forge 47.x) mod. You become the vessel of an infinite dimension, the Abyss. It has an infinite reserve, but you can only push out so much per second.

## Getting it
Craft an **Abyssal Core** (crying obsidian ×4, echo shard ×2, sculk catalyst ×2, nether star) and use it. There is one Abyss user per world (config `singleUser`). Admins can use `/abyss awaken <player>`.

## Controls (one hand on WASD; all rebindable)
| Key | |
|---|---|
| **Hold V** | Ability wheel (release to select; tap to keep it open and click) |
| **R** | Cast the selected ability. Domain: hold to invest output, **scroll or right-click to pick Open / Closed**, release to expand. Mash during a Triangle |
| **C** | Spores: tap/hold = 360° spread · **Shift+C** cycles Spread / Plant / Tag |
| **Hold Left Alt** | Spore Lens: click = select spore/host, shift-click = sweep, **scroll = payload**, **right-click = fire** (1–8 also fire directly) |
| **X** | Combat mode: left click = the abyssal combo |
| **Z** / **Shift+Z** | Next / previous body · **B** back to the main body · **G** Bodies screen |

## Combat mode (X)
Left click chains five strikes: **Rift Jab** (a void arm punches out of a tear beside the target), **Fold Slash** (a diagonal crescent of folded space), **Rift Blink** (step through a rift and hit from behind), **Void Lances** (crystal spears erupt under the target and launch it), **Event Horizon** (a singularity crushes, pulls everything in, then bursts). **Hold** to charge the **Abyssal Beam** (pierces, carves soft blocks). **Sprint + click** = **Rift Lunge** (14-block dash through a rift). **Click while falling** = **Gravity Fold** (space folds you down into a crater that drags enemies in). Every hit corrodes, and inside your domain every hit is a sure hit.

## The vessel
+20 HP, +6 attack, +10 armor, +6 toughness, knockback resistance and +15% speed (`vesselStrength`). Immune to falling, drowning, suffocation, freezing, fire and lava, starvation, poison and wither (`vesselImmunities`). **Sovereignty:** the vessel can't die. A lethal blow dissolves the body and it re-forms at half health; another lethal blow within 4 s is held in place. Raw `setHealth(0)`, `die()` and removal by other mods are refused (mixins), and max health can't be lowered. `/kill` and the void still work unless `negateVoidAndKill` is on.

## Items
- **Abyssal Core:** become the vessel.
- **Abyssal Upgrade** (creative only, no recipe): use to toggle **infinite output**. Domains charge to their maximum almost instantly.

## Config highlights
- `cooldownsEnabled` (default **false**: no cooldowns, including clone re-forming) and `cooldownMultiplier`.
- `allowInfiniteOutput`
- `domainDecay`, `domainDecayPerTick`, `domainScarsPermanent` (default false: the world heals), `domainDissolveItems`
- `sovereignty`, `reformSeconds`, `vesselStrength`, `vesselImmunities`, `comboDamage`, `beamCarves`

## Powers
- **Spores:** a 360° burst from the body, nearly instant, then they drift through blocks for 5 minutes. Each spore that meets a creature either **latches on** (weaker creatures are easier to infect) or passes through. Latched spores **corrode** the host, **multiply** inside it, and **jump** to anything it touches. Any spore, or any infested host, can become a payload: **Nuke** (from the inside out in a host, ignoring armor), **Small Domain**, **Beelzebub**, **Transport** to the Abyss, **Disintegrate** (no drops), **Rift**, **Clone Seed**, **Rift-step**. Plant mode places a spore where you aim. Tag mode shoots a seeker spore that always latches.
- **Domain Expansion** (one slot; choose Open or Closed while charging): *Open* pours the abyss into the world. From outside it's a black sphere of galaxy with a white lensing rim and an accretion ring. Inside, the sky becomes galaxy, stars hang in the air, the ground rots into abyss (plants, leaves and liquids vanish, dropped items dissolve), crystal debris rises, and every 2.5 s a pressure pulse slams everything down and inward. The world heals once the domain ends. Hold L to invest output, and the sphere grows at the expansion rate up to your investment. *Closed* copies the area into an arena in the Abyss and takes everyone inside with it until it ends. Inside a domain, the weight of the abyss crushes, pins, drags flyers down and corrodes, scaled by the Combat Power gap. **Every one of your abilities is a sure hit:** invulnerability frames are cleared, and hits land even if another mod cancels them.
- **Percentage Negation:** damage and harmful effects are cancelled while your output lasts. Damage beyond your remaining output leaks through. A lost Triangle breaks it.
- **Soul Resistance:** resists 90% of soul-type damage (`#abyss:soul_attacks`, or any damage type whose name contains "soul").
- **Beelzebub:** devours anything below 1.5× your CP (or under 30% HP). Players are bitten instead: they lose 35% HP and you steal their buffs. You keep a share of what you eat: health, damage, armor, speed, extra output, immunities (fire, drowning, flight, undead), and the projectiles it fires (**Mimic Volley**). **Brood** releases devoured creatures as abyss puppets that fight for you.
- **Rifts:** two-way tears to any coordinates in any dimension the server has, modded ones included. Anything walking in comes out the other side with its momentum.
- **Triangle:** a tug of war of Combat Power. The winner shatters the loser's defences (+50% damage taken, every hit lands, 3 s stun, −25% max HP, buffs stripped). If you lose, you are stunned and your negation goes down.
- **Main body and clones:** *Descend* puts your main body on its throne in the Abyss while you carry on as a clone. Swapping moves *you* (inventory, camera, keys) into another body and leaves a body behind. A clone that dies under you throws you back into the main body. If the main body dies, every clone collapses. Clones run on orders: Hold / Follow / Attack / **Mirror** (repeats your spreads, nukes and Beelzebub on its own target) / Patrol. They spread spores and detonate infested enemies on their own.
- **The Abyss is hostile:** everything it touches corrodes (stacks eat armor and max HP, and stop healing at 5+). The Abyss dimension itself corrodes anyone who doesn't belong there.

## Combat Power (CP)
CP is computed for any living entity from any mod, using:
- **Survival:** health, armor and toughness, immunities, boss status.
- **Offence:** melee damage, ranged and explosive attacks (read from its AI goals), and the biggest hit it has actually landed.
- **Mobility:** speed, flight, teleporting.
- **Powers:** buffs, size, and any modded attribute above its default.

A zombie is about 90. To adjust CP:
- **Datapack:** `data/<ns>/abyss_cp/*.json` with `{"overrides":[{"entity":"iceandfire:fire_dragon","multiply":1.5},{"tag":"forge:bosses","add":500},{"entity":"x:y","set":9000}]}`
- **Command:** `/abyss cp set <targets> <multiplier>`
- **API:** `CombatPower.registerProvider(entity -> multiplier)`

`/abyss cp` inspects whatever you are looking at.

## Admin
`/abyss awaken|revoke|mastery|refill|cast <ability>|cp [targets]|cp set|spores burst|clear|count|clones list|recall|domain end|selftest|config [list|key [value]]`

Build: `gradle build` (Java 17) → `build/libs/abyss-<version>.jar`. See CHANGELOG.md.
