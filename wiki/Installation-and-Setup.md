# Installation & Setup

## Requirements

- **Java**: Java **25** or newer
- **Server Software**: **Paper** `26.3`+ or **Folia** `26.3`+
- **Required Plugins**:
  - **PacketEvents** (used for client-side display entities, holograms, hitboxes, cast immobilization, and cooldown overlays)
  - **BetterModel** (used for 3D animated Aetheryte models and warp cast player animations)
- **Optional Plugins**:
  - **Vault** + any Vault-compatible economy plugin (only needed if you configure `type: vault` costs)

---

## Installation Steps

1. Download the `ClickPortals-<version>.jar` release from Modrinth or GitHub Releases (or build with `./gradlew shadowJar`).
2. Download **PacketEvents** and **BetterModel** (and **Vault** if using currency costs).
3. Copy all jars into your server's `plugins/` folder.
4. Start the server once so ClickPortals generates its default files.

---

## Generated Directory Layout

After first startup, `plugins/ClickPortals/` contains:

```text
plugins/ClickPortals/
├── config.yml              # Global plugin configuration
├── aetheryte/
│   └── default.yml         # Built-in default Aetheryte variation (add <type>.yml here)
└── lang/
    ├── en_US.yml           # English MiniMessage translations
    └── ja_JP.yml           # Japanese MiniMessage translations
```

---

## Reloading & Backups

- Use `/cp reload` (alias `/clickportals reload`) to reload `config.yml`, `aetheryte/*.yml`, `lang/*.yml`, recipes, holograms, and models at runtime.
- **Note on `/cp reload`**: Live reloads never modify or overwrite your configuration files on disk. If a YAML syntax error is present during `/cp reload`, the previous valid active configuration remains running in memory and an error is reported to the sender/console.
- During startup schema synchronization, if any file is upgraded with newly introduced config keys, ClickPortals creates a backup before applying schema additions.

---

## Troubleshooting & FAQ

### Aetheryte models or holograms do not appear
- Ensure **PacketEvents** is installed, up-to-date, and loaded before ClickPortals.
- Check `hologram.enabled: true` and increase `hologram.view-distance` (minimum `4.0`) if needed.
- If a BetterModel ID is missing or `bettermodel.enabled: false`, ClickPortals falls back cleanly to rendering the variation's `model.block` via `BLOCK_DISPLAY`.

### `vault` costs are skipped
- Vault integration is a soft dependency. If Vault or a currency provider plugin is missing, `vault` cost entries are ignored without blocking placement or warping. Check startup logs to confirm Vault detected an economy provider.

### Players cannot place in a dimension or are denied warping across dimensions
- Check `dimensions.worlds.<world>.allow-place` and `dimensions.worlds.<world>.allow-use` in `config.yml`.
- Cross-dimension warps require a connecting path in `dimensions.routes` (players with `clickportals.bypass.dimension` bypass dimension allow-flags, while `dimensions.routes` defines world connectivity and origin pricing).

### Commands don't appear in tab completion
- Regular players do not have `clickportals.command.*` permissions (`op` by default), so `/clickportals` and `/cp` stay hidden unless granted one of `clickportals.command.give`, `clickportals.command.reload`, or `clickportals.command.aetheryte`.
