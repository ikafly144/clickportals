# ClickPortals

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

**ClickPortals** is a modern **Aetheryte warp portal plugin** for **Paper** and **Folia** servers. Players craft, place, and organize customizable teleportation hubs using Minecraft's native **Dialog UI**, client-side **PacketEvents** visuals, and optional **BetterModel** 3D animations.

---

## Highlights

- **Paper & Folia Native**: Engineered for region-threaded Folia servers as well as standard Paper servers without main-thread stalls or orphaned entities.
- **Native Dialog UI**: Interactive in-game dialogs for naming new Aetherytes, choosing initial visibility or networks, filtering/sorting warp destinations, managing favorites, and editing settings.
- **Variations & Hub-Based World Networks**: Define custom Aetheryte types (`plugins/ClickPortals/aetheryte/*.yml`) with custom items, crafting recipes, `BLOCK_DISPLAY` / BetterModel visuals, landing rings, casting times, and Hub-hosted world networks (`type: normal` Hubs + `type: network` child Aetherytes).
- **Visibility & Invite Lists**: Control access per Aetheryte or per network with `PUBLIC`, `INVITE_ONLY`, and `OWNER_ONLY` visibility modes, Hub Network Management UI, and invite management.
- **Flexible Cost & Formula Engine**: Charge Vault currency, experience points, experience levels, or hunger using math expressions, optional vs. required entries, and per-visibility cost lists (`visibility-cost`).
- **Cross-Dimension Routes & Leashed Pets**: Configure shortest-path dimension links, coordinate scaling (e.g. Nether `8:1`), per-dimension enable flags, and optional teleportation of leashed entities (`warp.pets`) with `$pet_count` pricing.
- **Audio, Localization & Update Notifications**: Configure event and ambient sounds globally or per variation, adapt messages/holograms automatically to each player's client locale (`en_US`, `ja_JP`), and receive Modrinth update alerts.

---

## Requirements

| Component | Requirement | Notes |
|---|---|---|
| **Java** | **25+** | |
| **Server** | **Paper** or **Folia** `26.3`+ | Full Folia region/entity scheduler support |
| **PacketEvents** | Required | Client-side displays, holograms, hitboxes, cast immobilization |
| **BetterModel** | Required | 3D animated Aetheryte models & player cast animations |
| **Vault** | Optional | Required only if using `type: vault` economy costs |

---

## Quick Start

1. Download **ClickPortals**, **PacketEvents**, and **BetterModel** (plus **Vault** and an economy plugin if desired) and place them in `plugins/`.
2. Start or restart the server to generate `plugins/ClickPortals/config.yml`, `plugins/ClickPortals/aetheryte/default.yml`, and `plugins/ClickPortals/lang/*.yml`.
3. Obtain an Aetheryte item by crafting the default recipe (8 Chiseled Stone Bricks surrounding 1 Amethyst Shard) or running `/cp give <player>`.
4. Right-click a block with the Aetheryte item, enter a name, select visibility, and confirm placement.
5. Right-click a placed Aetheryte to warp to reachable destinations or manage your Aetheryte settings.

---

## Documentation (Wiki)

Browse the documentation in [`wiki/`](wiki/Home.md) or on the [GitHub Wiki](https://github.com/ikafly144/clickportals/wiki):

- [Home](wiki/Home.md) — Overview & documentation index
- [Installation & Setup](wiki/Installation-and-Setup.md) — Requirements, dependencies, file layout, troubleshooting
- [Usage & Commands](wiki/Usage-and-Commands.md) — Player guide and `/cp` / `/cp aetheryte` commands
- [Permissions](wiki/Permissions.md) — Permission nodes, defaults, and `clickportals.admin` inheritance
- [Configuration Guide](wiki/Configuration-Guide.md) — Complete `config.yml` reference
- [Aetheryte Variations & Networks](wiki/Aetheryte-Variations-and-Networks.md) — Custom variations, world networks, BetterModel, and cost formulas

---

## Development & Building

Build the shaded plugin jar and run all checks/unit tests from source:

```sh
./gradlew check shadowJar
```

The output jar is written to `build/libs/ClickPortals-<version>.jar`.

Internal contributor reference notes are kept in [`docs/`](docs/variations.md) ([Variations](docs/variations.md), [Costs](docs/costs.md), [Permissions](docs/permissions.md), [i18n](docs/i18n.md)).

## License

Licensed under the **GNU General Public License v3.0 or later (`GPL-3.0-or-later`)** — see [LICENSE](LICENSE).
