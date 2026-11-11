# ClickPortals

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

**ClickPortals** is a warp utility plugin for **Paper** and **Folia** servers that provides **Aetheryte** warp points managed through Paper's native **Dialog UI** and client-side **PacketEvents** visual entities.

## Features

- **Folia & Paper Compatible**: Built from the ground up using `RegionScheduler`, `EntityScheduler`, and `teleportAsync` (`folia-supported: true`).
- **Clean World Persistence**: Aetheryte entities use non-ticking `Marker` entities and World `PersistentDataContainer` (`clickportals:aetherytes`) backed by an $O(1)$ in-memory cache (`AetheryteRegistry`).
- **Client-Side Visuals & Interaction**: Uses [PacketEvents](https://github.com/retrooper/packetevents) to render client-only `BLOCK_DISPLAY`, `TEXT_DISPLAY` holograms, and `INTERACTION` hitboxes—leaving zero orphaned display entities if the plugin is removed.
- **Paper Dialog UI**: Native Dialog screens for naming new Aetherytes, selecting warp destinations (with cost and availability indicators), and confirming removal.
- **Type-Safe Configuration**: Powered by [SpongePowered Configurate 4](https://github.com/SpongePowered/Configurate) (`configurate-yaml`) with comment preservation.
- **Per-Player Localization (i18n)**: MiniMessage YAML translation files (`lang/en_US.yml`, `lang/ja_JP.yml`) that automatically adapt chat messages, Dialog UIs, and client-side holograms to each player's client language.
- **Aetheryte Variations**: Define multiple Aetheryte types (`aetheryte/<type>.yml`) with custom creation items (`item_model` & `custom_model_data`), crafting recipes, `BLOCK_DISPLAY` visual block models, configurable interaction hitboxes, ring-shaped surrounding landing spread (`min-radius` / `max-radius`), placement clearance checks, per-player placement limits, and placement cooldowns.
- **Granular Permissions**: Fine-grained permission nodes for warp usage, placement, removal, warp cost / limit / cooldown bypass, and commands (`/cp give`, `/cp reload`), plus reserved bypass nodes for future placement costs.

## Documentation

- [Permissions & Commands Reference](docs/permissions.md)
- [Localization & Message Formatting (i18n)](docs/i18n.md)
- [Aetheryte Variations Reference](docs/variations.md)

## Requirements

- **Java**: 25+
- **Server**: Paper or Folia `26.3`+
- **Dependencies**:
  - [PacketEvents](https://modrinth.com/plugin/packetevents) (optional, recommended for client-side Aetheryte visuals and interaction)

## Building

```sh
./gradlew build
```

The shaded plugin jar is generated at `build/libs/ClickPortals-<version>.jar`.

## License

This project is licensed under the **GNU General Public License v3.0 or later (`GPL-3.0-or-later`)** — see the [LICENSE](LICENSE) file for details.
