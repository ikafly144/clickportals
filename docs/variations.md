# Aetheryte Variations Reference

ClickPortals allows server administrators to define multiple Aetheryte variations under `plugins/ClickPortals/aetheryte/<variation>.yml`. On first startup, `default.yml` is generated automatically.

Each file's stem name (matching `[a-z0-9_-]+`, e.g. `default`, `grand`, `city_shard`) becomes its variation identifier.

## Full Configuration Example (`aetheryte/<type>.yml`)

```yaml
# Display name of this Aetheryte variation in MiniMessage format
display-name: "<gold>Grand Aetheryte</gold>"

# Creation item settings for this variation (#38)
item:
  # Base Bukkit Material for the creation item (vanilla item components are masked)
  material: ECHO_SHARD
  # Custom item display name in MiniMessage format (leave empty to use localized default from lang/*.yml)
  name: "<!italic><bold><gold>Grand Aetheryte Shard</gold></bold>"
  # Custom item lore lines in MiniMessage format (leave empty to use localized default from lang/*.yml)
  lore:
    - "<!italic><gray>Right-click a block with open clearance</gray>"
    - "<!italic><gray>to erect a Grand Aetheryte.</gray>"
  # Optional resource pack item_model key (1.21.4+ minecraft:item_model component)
  item-model: "myserver:grand_aetheryte_item"
  # Optional minecraft:custom_model_data component values
  custom-model-data:
    floats: []
    flags: []
    strings: []
    colors: []
  # Whether to apply enchantment glint to the creation item
  glint: true

# Crafting recipe settings for this variation (#38)
recipe:
  enabled: true
  shape:
    - " G "
    - "GEG"
    - " G "
  ingredients:
    G: GOLD_BLOCK
    E: ECHO_SHARD

# Client-side visual model settings (BLOCK_DISPLAY)
model:
  # Block state string displayed via BLOCK_DISPLAY (e.g. minecraft:lodestone)
  block: minecraft:lodestone
  # 3D scale vector [x, y, z] applied to the display entity
  scale: [2.0, 3.0, 2.0]
  # 3D translation offset [x, y, z] applied to the display entity
  translation: [0.0, 1.0, 0.0]
  # Vertical offset in blocks above the Aetheryte origin for the floating name hologram
  name-height: 3.35

# Client-side INTERACTION entity hitbox dimensions in blocks (#40)
hitbox:
  width: 2.0
  height: 3.0

# Teleport destination spread and orientation settings (#41)
teleport:
  # Minimum inner horizontal distance in blocks from the Aetheryte center (0 = no inner exclusion beyond hitbox, max 8)
  min-radius: 1
  # Maximum outer horizontal radius in blocks around the Aetheryte to search for a safe landing spot (0 = center/top only, max 8)
  max-radius: 3
  # Whether to orient the arriving player to face toward the Aetheryte when landing in the surrounding ring
  face-aetheryte: true

# Placement validation and surrounding clearance requirements (#42)
placement:
  clearance:
    # Horizontal Chebyshev radius in blocks around the origin that must be replaceable/empty (0 = origin column only, max 8)
    radius: 1
    # Vertical height in blocks from the origin upward that must be replaceable/empty (1-16, default 2)
    height: 3

# Per-player placement limit and cooldown for this variation (#43)
limit:
  # Maximum number of Aetherytes of this variation a single player may own (-1 for unlimited)
  max-per-player: 2
  # Placement cooldown in ticks for this variation (-1 to inherit global config.yml limits.placement-cooldown-ticks, 0 to disable)
  cooldown-ticks: -1
```

## Key Behaviors

- **Backward Compatibility (`#37`)**: Existing Aetherytes placed in v0.2 worlds without a stored variation tag automatically resolve to `default`. If a custom variation file is deleted later, any placed Aetherytes of that variation safely fall back to `default` (logging a warning once).
- **Ring-Shaped Landing Spread (`#41`)**: When `teleport.max-radius` is `1`–`8`, ClickPortals searches all columns within the Euclidean ring between `max(teleport.min-radius, hitbox.width / 2)` and `teleport.max-radius` for a non-hazardous solid floor with 2 passable blocks above, picks one at random to spread arriving players, and optionally turns the player to face the Aetheryte (`face-aetheryte: true`). If no spot inside the ring is available, it falls back to checking the center origin column.
- **Surrounding Space Clearance (`#42`)**: Before opening the creation dialog—and again on the target region thread before consuming the item—ClickPortals checks that all blocks in the `(2 * radius + 1) x height x (2 * radius + 1)` volume around the placement origin are replaceable (air, short grass, snow layer, etc., excluding water/lava).
- **Per-Player Placement Limits & Cooldown (`#43`)**: Placement counts are checked against both the global limit in `config.yml` (`limits.max-aetherytes-per-player`) and the variation-specific `limit.max-per-player` across all currently loaded worlds. In addition, a placement cooldown (configured globally via `limits.placement-cooldown-ticks` or overridden per variation via `limit.cooldown-ticks`) is tracked in the player's `PersistentDataContainer` (`clickportals:last_place_tick`) and rendered client-side via `DataComponentTypes.USE_COOLDOWN` and PacketEvents (`WrapperPlayServerSetCooldown`). Players with `clickportals.bypass.limit` bypass both placement limits and cooldowns.
