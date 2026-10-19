# GIF Screenshots

A client-side Fabric mod that records a short animated GIF of your gameplay with a single keypress. Recording length, FPS and size are configured through [Mod Menu](https://modrinth.com/mod/modmenu).

## Features

- Dedicated keybind, **Record GIF Screenshot** (F9 by default, rebindable in Controls).
- Mod Menu settings screen:
  - Recording time: 1-30 seconds
  - FPS: 1-30
  - Width: 160-1920 px (height follows the window aspect ratio)
- Encoding runs on a background thread; if it falls behind, frames are dropped instead of stalling the game.
- GIFs are saved to `screenshots/gif_<date>_<time>.gif`. Settings live in `config/gif-screenshots.json`.

Defaults: 5 seconds, 15 FPS, 640 px wide. Long, large or high-FPS recordings produce big files, and GIFs are limited to 256 colours per frame.

## Requirements

- Fabric Loader and [Fabric API](https://modrinth.com/mod/fabric-api)
- [Mod Menu](https://modrinth.com/mod/modmenu) (optional, for the settings screen)
- Java 25

## Supported versions

Each Minecraft version lives on its own branch; `main` is the latest.

| Branch | Minecraft |
|--------|-----------|
| `main` | 26.3 |
| `26.2` | 26.2 |
| `26.1` | 26.1 - 26.1.2 |

## Building

```
./gradlew build
```

The jar is written to `build/libs/`. Check out the branch for the Minecraft version you want first.

## Releasing

Releases are published to Modrinth with [Minotaur](https://github.com/modrinth/minotaur) by a GitHub Actions workflow that runs on tags named `v<mod_version>+<minecraft_version>` (for example `v1.0.0+26.3`). The tag must match `gradle.properties` on the tagged branch, and the `MODRINTH_KEY` repository secret must be set.

## License

[MIT](LICENSE)
