# Configuration Guide (`config.yml`)

ClickPortals stores global settings in `plugins/ClickPortals/config.yml`. Comments and formatting are preserved by Configurate 4.

---

## `language`
Controls localization behavior using translation files in `plugins/ClickPortals/lang/<locale>.yml` (`en_US` and `ja_JP` bundled).

```yaml
language:
  default-locale: en_US
  use-client-locale: true
```

- **`default-locale`**: Fallback locale used for console messages, creation items, and unsupported player client locales.
- **`use-client-locale`**: When `true`, chat messages, Dialog UIs, and PacketEvents holograms automatically render in each player's Minecraft client locale.

---

## `hologram`
Configures PacketEvents client-side displays (`BLOCK_DISPLAY`, `TEXT_DISPLAY`) and `INTERACTION` hitboxes.

```yaml
hologram:
  enabled: true
  view-distance: 32.0
  delayed-refresh-fallback: false
```

- **`enabled`**: Enables or disables client-side visuals and hitboxes.
- **`view-distance`**: Radius in blocks within which client-side holograms and models are shown (minimum `4.0`).
- **`delayed-refresh-fallback`**: Opt-in fallback scheduling extra visual syncs at 5 and 20 ticks after join/respawn/world change/warp (keep `false` unless packet ordering requires it).

---

## `limits`
Global per-player limits and placement defaults. Set any limit to `-1` for unlimited.

```yaml
limits:
  max-aetherytes-per-player: -1
  placement-cooldown-ticks: 100
  min-distance-between-aetherytes: 0.0
  max-favorites-per-player: -1
  max-networks-per-player: -1
```

- **`max-aetherytes-per-player`**: Total Aetherytes a player may own across all variations.
- **`placement-cooldown-ticks`**: Cooldown in ticks after placing an Aetheryte (`20` ticks = 1 second, `0` disables). Can be overridden per variation.
- **`min-distance-between-aetherytes`**: Minimum block distance to existing Aetherytes in the same world (`0.0` disables). Can be overridden per variation.
- **`max-favorites-per-player`**: Maximum favorited Aetherytes stored per player (`0` disables favorites, `-1` unlimited).
- **`max-networks-per-player`**: Total Aetheryte networks a player may own across all `type: network` variations (`-1` unlimited).

---

## `warp`
Global warping behavior, distance boundaries, pricing, cast channeling, visibility costs, and leashed pet warping.

```yaml
warp:
  distance:
    min: 0.0
    max: -1.0
    cross-dimension: true
  cost: []
  show-optional-costs: true
  cast:
    ticks: 0
    player-model: "steve"
    cast-animation: ""
    arrive-animation: ""
    immobilize: true
    cancel:
      on-move: true
      on-damage: true
      on-interact: true
  visibility-cost:
    public: null
    invite-only: null
    owner-only: null
  pets:
    enabled: true
    max-entities: 5
    cost: []
```

### Subsections
- **`distance`**: Minimum (`min`) and maximum (`max`, `-1.0` = unlimited) warp distance in blocks, and whether distance limits apply to `cross-dimension` warps.
- **`cost`**: Base list of cost entries (see [Cost Engine](Aetheryte-Variations-and-Networks#cost--formula-engine)).
- **`show-optional-costs`**: Whether entries with `required: false` appear in the destination menu and tooltips.
- **`cast`**:
  - `ticks`: Channeling duration in ticks (`<= 0` for instant warp).
  - `player-model` / `cast-animation` / `arrive-animation`: BetterModel player limb model and animation names during/after casting.
  - `immobilize`: Freezes player movement via PacketEvents during channeling while allowing camera rotation.
  - `cancel`: Triggers that abort casting (`on-move`, `on-damage`, `on-interact`).
- **`visibility-cost`**: Per-destination visibility cost lists (`public`, `invite-only`, `owner-only`):
  - `null` (omitted/null): Falls back to the base `warp.cost`.
  - `[]` (explicit empty list): Warping to that visibility tier has zero base warp cost.
  - `[...]` (non-empty list): Replaces the base warp cost with that list of cost entries.
- **`pets`**:
  - `enabled`: Teleport entities leashed (`Leashable`) to the warping player.
  - `max-entities`: Maximum leashed entities permitted per warp (`-1` unlimited, `0` disables carrying pets). Exceeding this denies warping with `error.too-many-pets`.
  - `cost`: Extra cost entries evaluated when warping with leashed entities (`$pet_count` holds the number of leashed entities; only charged when `pets.enabled` is true).

---

## `dimensions`
Cross-dimension routing, pricing, custom display names, and per-world placement/use toggles.

```yaml
dimensions:
  default-cost:
    - type: vault
      amount: "$amount"
      required: true
    - type: exp-levels
      amount: "3"
      required: false
  amounts:
    world: 0.0
    world_nether: 30.0
    world_the_end: 100.0
  worlds:
    world:
      display-name: ""
      allow-place: true
      allow-use: true
    world_nether:
      display-name: ""
      allow-place: true
      allow-use: true
    world_the_end:
      display-name: ""
      allow-place: true
      allow-use: true
  routes:
    - between: [world, world_nether]
      origin:
        world: { type: scale, ratio: 8.0 }
        world_nether: { type: scale, ratio: 0.125 }
    - between: [world, world_the_end]
      origin:
        world: { type: spawn }
        world_the_end: { type: fixed, x: 0.0, z: 0.0 }
      cost-override:
        world_the_end:
          amount: "$default + 300"
```

- **`worlds.<world>.display-name`**: Optional MiniMessage world display name (falls back to world name when empty).
- **`allow-place` / `allow-use`**: Per-world toggles (bypassable with `clickportals.bypass.dimension`).
- **`routes`**: Undirected world edges. ClickPortals computes shortest paths via BFS (`$hops`). Origins can use `{ type: scale, ratio: ... }`, `{ type: spawn }`, or `{ type: fixed, x: ..., z: ... }` to convert coordinates when evaluating cross-dimension distances.

---

## `update-checker`
Automatic release checking against the **Modrinth API** using ClickPortals' built-in project ID (`clickportals`).

```yaml
update-checker:
  enabled: true
  include-prerelease: false
  notify-on-join: true
  check-interval-minutes: 360
```

- **`enabled`**: Toggles periodic and startup update checks.
- **`include-prerelease`**: Whether `alpha` and `beta` Modrinth versions trigger update notifications.
- **`notify-on-join`**: Notifies joining players with `clickportals.notify.update` when a compatible update is available (includes clickable Modrinth link).
- **`check-interval-minutes`**: Periodic check interval in minutes (`0` checks only at startup / `/cp reload`).

---

## `sounds`
Global sound effects (`<namespace>:<key>`, `volume`, `pitch`) and periodic nearby ambient sound loop.

```yaml
sounds:
  place:
    sound: "minecraft:block.beacon.activate"
    volume: 1.0
    pitch: 1.2
  destroy:
    sound: "minecraft:block.beacon.deactivate"
    volume: 1.0
    pitch: 0.9
  interact:
    sound: "minecraft:block.amethyst_block.chime"
    volume: 1.0
    pitch: 1.0
  warp-cast-start:
    sound: "minecraft:block.portal.trigger"
    volume: 0.5
    pitch: 1.5
  warp-cast-cancel:
    sound: "minecraft:block.note_block.bass"
    volume: 0.8
    pitch: 0.7
  warp-depart:
    sound: "minecraft:entity.enderman.teleport"
    volume: 0.9
    pitch: 1.1
  warp-arrive:
    sound: "minecraft:entity.enderman.teleport"
    volume: 1.0
    pitch: 1.2
  error:
    sound: "minecraft:entity.villager.no"
    volume: 0.8
    pitch: 1.0
  ambient:
    enabled: true
    sound: "minecraft:block.beacon.ambient"
    volume: 0.6
    pitch: 1.2
    range: 12.0
    interval-ticks: 80
```

Set `sound: ""` or `volume: 0.0` to silence a specific event sound, or `ambient.enabled: false` to disable ambient sounds (`ambient.range` is clamped between `0.0` and `64.0` blocks). Every field can also be overridden selectively per variation in `aetheryte/<type>.yml`.
