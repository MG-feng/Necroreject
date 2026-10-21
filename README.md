# Necroreject

> Fungal corruption spreads through the battlefield.

A Mindustry v160.7 mod written in Kotlin. Adds a faction of fungal/necrotic mech and leg units that spread corrosive puddles and auras across the map.

## Units

| Name (EN) | Name (CN) | Role |
|-----------|-----------|------|
| Necroflux | 液烂 | Light mech – spits phlegm and leaves puddles |
| Corrocrust | 蚀痂 | Support mech – fungal aura heals allies / corrodes enemies |
| Ruptgout | 崩疽 | Heavy quadruped – lobs arc phlegm shells, bursts on death |
| Skysunder | 溃穹 | Artillery octopod – massive long-range phlegm shells, spawns Necrofluxes |
| Rendcrown | 裂冕 | Epic-tier crawler – corrosion field, pustules, mass death spawns |

## Status effects

- **Fungal Corrosion (菌蚀)** – DoT + slow applied by puddles, auras and phlegm hits.
- **Rooted (扎根)** – short immobilization after being hit by Skysunder shells.

## Building

Requires JDK 17+.

```sh
./gradlew jar          # builds build/libs/necrorejectDesktop.jar (desktop mod jar)
./gradlew deploy       # builds combined Desktop + Android jar (requires Android SDK)
```

Drop the resulting jar into your Mindustry `mods/` folder.

## Project layout

```
scripts/                  Kotlin sources (main plugin, systems, bullets, registry)
  necroreject.kt          Mod entry point
  NecroBootstrap.kt       Installs systems in the correct order
  systems/                PuddleSystem, CorrosionSystem, NecroUtil, NecroFx
  bullet/                 Custom BulletTypes (PhlegmBullet, ArcPhlegm, AuraBullet, ...)
  units/                  NecroRegistry – unit scanning, aura/pustule ticking, spawns
content/units/            HJSON unit definitions
bundles/                  Localization (en, zh-CN, zh-TW)
sprites/                 Unit and effect sprites (to be added)
sounds/                  Custom sounds (to be added)
```
