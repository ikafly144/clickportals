# ClickPortals Wiki

Welcome to the official documentation for **ClickPortals** — a **Paper** and **Folia** warp portal plugin providing **Aetherytes**, native **Dialog UI** menus, client-side **PacketEvents** visuals, and **BetterModel** 3D animation support.

## Features at a Glance

- **Folia & Paper Safe**: Uses `RegionScheduler`, `EntityScheduler`, and `teleportAsync` (`folia-supported: true`).
- **Zero-Entity World Footprint**: Client-side visuals rendered with PacketEvents (`BLOCK_DISPLAY`, `TEXT_DISPLAY`, `INTERACTION`) and backed by non-ticking Marker / World PDC storage.
- **Customizable Variations (`aetheryte/*.yml`)**: Custom creation items, recipes, block or BetterModel 3D models, hitbox bounds, clearance checks, landing rings, placement/warp costs, and casting times.
- **Hub-Based Networks & Access Control**: Choose between `normal` Aetherytes (which can also serve as Network Hubs) and child `type: network` Aetherytes linked bidirectionally to their Hub within the same world, with `PUBLIC`, `INVITE_ONLY`, and `OWNER_ONLY` visibility plus invitee management.
- **Formula Cost Engine**: Charge Vault money, XP points, XP levels, or food using expressions (`$block_distance`, `$pet_count`, `$owned_count`, `$dimension_cost`, etc.) with mandatory/optional rules and per-visibility `visibility-cost` overrides.
- **Leashed Pet Warping**: Teleport entities leashed to the warping player along with them, with configurable caps and `$pet_count` surcharges.
- **Sound Effects & Per-Player i18n**: Global and per-variation sound overrides (`ambient` loops + event cues) and automatic client locale translation (`en_US`, `ja_JP`).

## Documentation Index

| Page | Description |
|---|---|
| [Installation & Setup](Installation-and-Setup) | Requirements, dependencies, first-run directory structure, troubleshooting & FAQ |
| [Usage & Commands](Usage-and-Commands) | Player workflow, Dialog menus, `/cp give`, `/cp reload`, and `/cp aetheryte` commands |
| [Permissions](Permissions) | All permission nodes, default values, bypasses, and `clickportals.admin` |
| [Configuration Guide](Configuration-Guide) | Detailed `config.yml` reference (`language`, `hologram`, `limits`, `warp`, `dimensions`, `update-checker`, `sounds`) |
| [Aetheryte Variations & Networks](Aetheryte-Variations-and-Networks) | Custom `aetheryte/<type>.yml` guide, world networks, BetterModel hooks, and formula syntax |
