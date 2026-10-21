# Necroreject

> Fungal corruption spreads through the battlefield.

Mindustry v160.7 mod by **Moonano-MGFeng**, written in Kotlin.

## Branches

| Branch | Purpose |
|--------|---------|
| [`v0.1.0-dev`](../../tree/v0.1.0-dev) | Active development — full Kotlin source, content, bundles, Gradle build, CI. Build artifacts here. |
| `main` | License + README only (no mod source). Do not merge code into `main`. |

## Download

Pre-built `necrorejectDesktop.jar` is attached to each successful run of the
**Build Mod** workflow on the `v0.1.0-dev` branch (see the Actions tab). Drop the
jar into Mindustry's `mods/` folder.

## Building locally (on `v0.1.0-dev`)

Requires JDK 17+.

```sh
./gradlew jar          # build/libs/necrorejectDesktop.jar (desktop mod jar)
./gradlew deploy       # combined Desktop + Android jar (requires ANDROID_HOME)
```

## License

GNU AGPLv3 — see [LICENSE](LICENSE).
