# Aetheryte Variations & Networks

Every `.yml` file placed in `plugins/ClickPortals/aetheryte/<id>.yml` defines an Aetheryte variation (the filename without `.yml` becomes the lowercase variation ID; `default.yml` is the built-in default variation).

---

## Variation Structure Overview

### Identity & Type
```yaml
display-name: "<aqua>Aetheryte</aqua>"
type: "normal" # "normal" (standard) or "network" (world-scoped player networks)
```

### Creation Item & Crafting Recipe (`item`, `recipe`)
```yaml
item:
  material: COMPASS
  name: "" # Empty = localized default name
  lore: [] # Empty = localized default lore
  item-model: "" # Optional custom minecraft:item_model key (e.g. myserver:aetheryte)
  custom-model-data:
    floats: []
    flags: []
    strings: []
    colors: [] # RGB integers, e.g. 0x55FFFF
  glint: true

recipe:
  enabled: true
  shape:
    - SSS
    - SAS
    - SSS
  ingredients:
    S: CHISELED_STONE_BRICKS
    A: AMETHYST_SHARD
```

### Visual Model, Hitbox & Teleport Landing (`model`, `hitbox`, `teleport`)
```yaml
model:
  block: minecraft:lodestone
  scale: [1.0, 1.0, 1.0]
  translation: [0.0, 0.0, 0.0]
  name-height: 1.35

hitbox:
  width: 1.0
  height: 1.0

teleport:
  min-radius: 1 # 1 .. max-radius
  max-radius: 1 # 1 .. 8 blocks
  face-aetheryte: true
```
- **Landing Ring**: Arriving players search a ring between `min-radius` and `max-radius` blocks around the Aetheryte for a safe standing position so they never spawn inside the crystal/model.

### Placement Clearance, Limits & Visibility Costs (`placement`, `limit`)
```yaml
placement:
  clearance:
    radius: 0 # 0 = origin column only (max 8)
    height: 2 # 1..16 blocks tall
  cost: []
  min-distance: -1.0 # -1 inherits global, 0 disables
  visibility-cost:
    public: null      # null inherits placement.cost, [] = free
    invite-only: null
    owner-only: null

limit:
  max-per-player: -1  # -1 = unlimited
  cooldown-ticks: -1  # -1 inherits global limits.placement-cooldown-ticks, 0 disables
```
- When changing an Aetheryte's visibility in its management menu, ClickPortals charges the target visibility's full `placement.visibility-cost` (or base `placement.cost` if `null`). No partial refunds are issued.

### Per-Variation Warp Overrides (`warp`)
All fields below override `config.yml` when warping **from** this variation (or using `null` / `-1` to inherit global settings):
```yaml
warp:
  distance:
    min: -1.0
    max: -1.0
    cross-dimension: null
  cost: null # null inherits global warp.cost, [] = free
  cast:
    ticks: -1 # -1 inherits global, 0 = instant warp
    player-model: null
    cast-animation: null
    arrive-animation: null
    immobilize: null
    cancel:
      on-move: null
      on-damage: null
      on-interact: null
  visibility-cost:
    public: null
    invite-only: null
    owner-only: null
  pets:
    enabled: null       # null inherits config.yml
    max-entities: null  # null or -1 inherits config.yml
    cost: null          # null inherits config.yml, [] = free
```

---

## Hub & Child World-Scoped Networks (`type: "network"`)

ClickPortals implements an **FFXIV-style Hub + Child Network** model:
- **Hub Aetherytes (`type: "normal"`)**: Any placed `normal` Aetheryte whose variation allows `network.allow-hub: true` (the default) can host one or more world-scoped networks (`AetheryteNetwork`). Network creation and management (rename, visibility, invites, deletion) are accessed from the Hub Aetheryte's **Operations -> Manage Networks** dialog.
- **Child Aetherytes (`type: "network"`)**: Placing a `type: "network"` Aetheryte attaches it to an existing joinable Hub network in the same world. Placement fails if no joinable Hub network exists in that world.
- **Bidirectional Routing**:
  - `normal` <-> `normal` (across reachable dimensions)
  - Hub `normal` <-> any child Aetheryte in a network hosted by that Hub
  - Child <-> child within the **same network ID** (only accessible while its Hub still exists in that world)
- **World Scoped & Shared Access**: Networks belong to a single world. Visibility (`PUBLIC`, `INVITE_ONLY`, `OWNER_ONLY`) and invitees are configured per network on its Hub and apply across all children in that network.
- **Hub Removal Guard & Admin Cascade**:
  - Regular players cannot remove a Hub Aetheryte while any child Aetherytes exist in its hosted networks.
  - Admins (`clickportals.admin`) receive a cascade warning and can remove the Hub along with all hosted networks and child Aetherytes in one action (no child item refunds), or manage networks via `/cp aetheryte network`.
  - Removing a Hub that only has empty networks automatically cleans up those empty networks.

### `network` Configuration Section
```yaml
network:
  allow-hub: true # For type: normal variations: allow hosting Aetheryte networks (default true)
  limit:
    max-per-player: -1  # Max networks of this variation per player (-1 = unlimited)
    max-aetheryte: -1   # Max child Aetherytes inside a single network (-1 = unlimited)
    max-distance: -1.0  # Max Euclidean distance from Hub / existing members when joining (-1.0 disables)
  cost:
    create: [] # Cost entries charged when creating a network from this Hub
    join: []   # Extra cost entries charged when placing a child Aetheryte into an existing network
```

---

## BetterModel Integration (`bettermodel`) & Sounds (`sounds`)

```yaml
bettermodel:
  enabled: false
  model-id: "" # Model ID registered in BetterModel
  animations:
    idle: "idle"
    on-interact: ""
    on-warp-depart: ""
    on-warp-arrive: ""

sounds:
  # Any event key or ambient field set to null inherits config.yml
  place:
    sound: null
    volume: null
    pitch: null
  ambient:
    enabled: null
    sound: null
    volume: null
    pitch: null
    range: null
    interval-ticks: null
```
If BetterModel is enabled for a variation and `model-id` resolves in BetterModel, PacketEvents suppresses the `BLOCK_DISPLAY` fallback so BetterModel renders cleanly; otherwise, ClickPortals gracefully falls back to `model.block`.

---

## Cost & Formula Engine

All cost lists (`cost`, `visibility-cost`, `pets.cost`, `network.cost`) accept entries of this form:

```yaml
- type: vault # "vault", "exp-points", "exp-levels", or "food"
  amount: "max(10, $block_distance * 0.25 + $pet_count * 50)"
  required: true # true = mandatory, false = pay up to available balance
```

- **Operators**: `+`, `-`, `*`, `/`, `%`, `^`, comparisons (`==`, `!=`, `<`, `<=`, `>`, `>=`), logic (`&&`, `||`, `!`), ternary (`cond ? a : b`).
- **Functions**: `min(...)`, `max(...)`, `clamp(v, lo, hi)`, `abs(x)`, `floor(x)`, `ceil(x)`, `round(x)`, `sqrt(x)`, `log(x)`, `log10(x)`, `pow(base, exp)`.
- **Percentage Shorthand**: `"10%"` evaluates as 10% of the entry's resource (`$balance`, `$exp_points`, `$exp_level`, or `$food`).
- **Variables**:
  - **Warp / Pets**: `$block_distance`, `$horizontal_distance`, `$chunk_distance`, `$dy`, `$cross_dimension` (`1.0` or `0.0`), `$hops`, `$dimension_cost`, `$pet_count`
  - **Placement**: `$owned_count`, `$variation_count`
  - **Player**: `$balance`, `$exp_points`, `$exp_level`, `$food`
- **All-or-Nothing Rollback**: Multi-cost transactions are atomic; if any required part fails mid-payment, already deducted resources are refunded immediately (or queued if offline).
