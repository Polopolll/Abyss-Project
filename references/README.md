# References

Abyss was designed with these mods open side by side for reference. **No code or assets from them are in this repository.** They are only credited here, with notes on what each one inspired.

Most of these mods are **All Rights Reserved**, so their textures, models and screenshots can't be redistributed here (and they are not covered by this repo's MIT license). To see them, follow the links to the authors' own pages.

| Mod | Author(s) | License | Link |
|---|---|---|---|
| Ronova: Ruler of Death | Mian3450 | All Rights Reserved | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/ronova-ruler-of-death) |
| Cursed Fate | ImNotHURAGAN, Gumel | All Rights Reserved | — |
| Photon | KilaBash | GPL-3.0 | [GitHub](https://github.com/Low-Drag-MC/Photon/) |
| Player Animator | KosmX | MIT | — |
| Kleiders Custom Renderer | Kleiders, MCreator | Academic Free License 3.0 | [MCreator](https://mcreator.net/plugin/91185/kleiders-player-renderer-change-players-skin) |
| God Wand | KhangHy | MIT code, ARR assets | — |
| Rick's stuff | ExtraTortik | All Rights Reserved | — |
| The Shooting Star (Demo) | rimuru.dev | All Rights Reserved | [TikTok](https://www.tiktok.com/@rimuru.dev) |

Other inspirations, not mods: **Wuthering Waves** (combat flow), **Jujutsu Kaisen** (domain expansion), and Andy's own earlier mods **Chronoweave** and **Hachimon**.

## What came from where

**Ronova: Ruler of Death** was the main reference for how abilities are organised and controlled.
- An umbrella skill (a *form*) that you open, after which the mouse buttons become its sub-abilities, with hints drawn next to the crosshair. In Abyss: R opens a form; LMB, RMB, Mouse 4 and Mouse 5 are its four actions.
- Skill selection: tap to cycle, hold for a wheel, number keys for slots.
- Round medallion icons with a dial ring and compass spikes. The Abyss medallions were drawn from scratch in the same spirit, in the Abyss palette, with a script (`pictures/0.3/medallion-icons.png`).
- A separate realm at the same coordinates, where you fly, can't die, see souls and peek back into the real world. That became **the Realm** (Authority form, RMB).
- An in-game manual with a chapter per skill. That became the Abyss manual.

**Cursed Fate** was the reference for the domain.
- Gojo's domain: a star field fixed to the world and laid over the real sky, instead of an image glued to the camera. The Abyss galaxy shader is anchored to world directions the same way, and both the domain sky and the cuts use it.
- Sukuna's open domain and the black-hole ring: the sense of pressure and a singularity on the horizon.

**Wuthering Waves** shaped how the Arsenal plays.
- A basic chain of attacks, a held heavy, a plunge from the air, a dodge whose perfect timing opens a counter, a skill, and a liberation fed by a resonance gauge.
- Buffered inputs, so attacks chain cleanly.
- Many different moves per weapon instead of one combo.

**Photon, Player Animator, Kleiders Custom Renderer, God Wand, Rick's stuff and The Shooting Star** were studied as examples of good visual effects in Minecraft: layered glows, trails, emissive cores, shockwave rings, and readable, well-timed impacts. Their general lesson shaped the effect timing in 0.3.0: build-up, then impact, then linger.

**Chronoweave** (Andy's time mod) set the look of the skill wheel and HUD. Its smooth tesseract combos were the bar for how clean the Arsenal had to feel. **Hachimon** set the one-handed control style of the earlier versions.

## How the references were used
Abyss was built only by playing these mods and looking at their resource files: language files and texture sheets, to understand how their skills are organised. None of their code was decompiled. Every texture, model, shader and line of code in Abyss is original.
