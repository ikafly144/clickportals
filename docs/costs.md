# ClickPortals Cost and Economy Guide

ClickPortals v0.4 introduces an extensible and composable cost engine supporting server economies (via Vault), vanilla experience points/levels, and hunger, along with distance limits and inter-dimensional routes (#7, #8, #9, #14, #15, #16, #26).

---

## 1. Supported Cost Types

| Type | YAML Identifier | Target Resource | Percentage Variable |
|---|---|---|---|
| **Vault Economy** | `vault` | Server economy currency | `$balance` |
| **Experience Points** | `exp-points` | Total experience points | `$exp_points` |
| **Experience Levels** | `exp-levels` | Experience levels | `$exp_level` |
| **Food / Hunger** | `food` | Hunger points (0..20) | `$food` |

### Soft-Depend Vault Integration (#16)
- Vault is supported as an optional dependency (`soft-depend`).
- When Vault or an active economy plugin is not installed, all `vault`-type cost entries are automatically skipped without blocking operations, and currency costs remain disabled.

### Mandatory vs Optional Costs (#7)
Each cost entry can be marked as mandatory (`required: true`, default) or optional (`required: false`):
- **`required: true`**: The player must possess the full assessed amount; otherwise, the transaction is rejected and no resources are deducted.
- **`required: false`**: The player pays whatever amount they currently have up to the requested cost, and the operation still succeeds even if they have 0.
- **`warp.show-optional-costs`**: Servers can set `show-optional-costs: false` in `config.yml` to hide optional costs from warp destination menus and tooltips while still collecting them silently.

### All-or-Nothing Guarantee (#7)
When multiple cost types are combined, every required component must be satisfied. If any part of the payment fails during execution, all previously deducted resources are immediately rolled back and refunded.

---

## 2. Amount Expression Syntax (#7, #8)

Costs are specified as mathematical formulas compiled at startup:
```yaml
amount: "$block_distance * 0.5 + $chunk_distance * 2 + 10"
```

### Supported Syntax
- **Literals**: Numbers (`10`, `3.5`, `0.125`)
- **Variables**: Prefixed with `$` (e.g. `$block_distance`, `$balance`)
- **Operators**:
  - Arithmetic: `+`, `-`, `*`, `/`, `%` (modulo), `^` (power)
  - Comparison: `==`, `!=`, `<`, `<=`, `>`, `>=`
  - Logical: `&&`, `||`, `!`
  - Ternary conditional: `condition ? thenExpr : elseExpr`
- **Functions**:
  - `min(a, b, ...)`
  - `max(a, b, ...)`
  - `clamp(value, min, max)`
  - `abs(x)`
  - `floor(x)`, `ceil(x)`, `round(x)`
  - `sqrt(x)`
  - `log(x)`, `log10(x)`
  - `pow(base, exponent)`
- **Percentage Shorthand**: Specifying `"10%"` automatically scales by 10% of the player's relevant resource (e.g. `$balance * 0.10`).

### Context Variables
- **Warp Variables**:
  - `$block_distance`: 3D Euclidean block distance between Aetherytes (or target origin).
  - `$horizontal_distance`: 2D horizontal block distance ($\sqrt{dx^2 + dz^2}$).
  - `$chunk_distance`: Horizontal distance in chunks ($horizontal / 16$).
  - `$dy`: Vertical distance difference ($|y_1 - y_2|$).
  - `$cross_dimension`: `1.0` if traversing dimensions, `0.0` if in the same world.
  - `$hops`: Number of dimension hops traversed.
  - `$dimension_cost`: Base dimension traversal amount evaluated from route definitions.
- **Placement Variables**:
  - `$owned_count`: Number of Aetherytes currently owned by the player across all variations.
  - `$variation_count`: Number of Aetherytes of this variation currently owned by the player.
- **Player Variables**:
  - `$balance`: Current Vault balance.
  - `$exp_points`: Current total experience points.
  - `$exp_level`: Current experience level.
  - `$food`: Current food level (0..20).

---

## 3. Placement Costs (#15)

Placement costs can be configured per variation in `<dataDir>/aetheryte/<type>.yml`:

```yaml
placement:
  clearance:
    radius: 0
    height: 2
  cost:
    - type: vault
      amount: "500"
      required: true
    - type: exp-levels
      amount: "2"
      required: false
```

- When a player attempts to place an Aetheryte, the cost is checked before the creation item is consumed.
- If placement fails later on the region thread (e.g. due to obstruction or placement limits), both the creation item and charged costs are automatically refunded.
- Players with permission `clickportals.bypass.cost.place` (default `op`) are exempt from placement costs.

---

## 4. Warp Costs and Distance Limits (#14, #26)

Global warp rules are configured in `config.yml`:

```yaml
warp:
  distance:
    min: 10.0   # Minimum distance in blocks required to warp (0 for disabled)
    max: 2000.0 # Maximum distance in blocks allowed to warp (-1 for unlimited)
  cost:
    - type: vault
      amount: "max(10, $block_distance * 0.25)"
      required: true
  show-optional-costs: true
```

Variations can override warp distance and cost in `<dataDir>/aetheryte/<type>.yml`:
```yaml
warp:
  distance:
    min: -1.0   # -1 to inherit global min
    max: 500.0  # Cap this variation to 500 blocks
  cost:         # null to inherit global cost; [] for free
    - type: vault
      amount: "10"
```

- Players with permission `clickportals.bypass.cost.warp` (default `op`) can warp for free.

---

## 5. Cross-Dimension Routes (#9)

Cross-dimension travel is configured in `config.yml` under `dimensions`:

```yaml
dimensions:
  # Default cost specifications applied to cross-dimension hops
  default-cost:
    - type: vault
      amount: "$amount"
      required: true
    - type: exp-levels
      amount: "3"
      required: false

  # Base dimension pricing per world
  amounts:
    world: 0.0
    world_nether: 30.0
    world_the_end: 100.0

  # Inter-dimensional routes and coordinate origins
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

### Route Behavior:
1. **Shortest Path**: Breadth-first search resolves the shortest hop sequence between worlds (e.g. Nether $\to$ Overworld $\to$ End).
2. **Dimension Pricing**:
   - For an edge $A \leftrightarrow B$, the base amount is $amount(A) + amount(B)$.
   - If `cost-override` is defined, `$default` evaluates to the base amount, allowing custom overrides.
3. **Origin Transformation**:
   - `scale`: Scales horizontal coordinates by `ratio` (e.g. Nether $1:8$ conversion).
   - `spawn`: Uses the destination world spawn point.
   - `fixed`: Uses fixed $X, Z$ coordinates.
   Distance variables (`$block_distance`, etc.) are calculated from the resolved origin to the target Aetheryte.
4. **Isolated Worlds**: Worlds without a route connecting them cannot warp to each other and are cleanly rejected in the destination menu.
