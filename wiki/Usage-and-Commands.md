# Usage & Commands

## Player Guide

### 1. Obtaining & Crafting an Aetheryte
- By default, players with crafting enabled can craft the **Default Aetheryte** item using **8 Chiseled Stone Bricks** surrounding **1 Amethyst Shard** (`aetheryte/default.yml`).
- Operators can also grant Aetherytes of any configured variation using `/cp give`.

### 2. Placing an Aetheryte
1. Hold an Aetheryte creation item and right-click against a block.
2. In the **Creation Dialog**:
   - Enter a display name (**1–32 characters**).
   - Select initial **Visibility** (`PUBLIC`, `INVITE_ONLY`, or `OWNER_ONLY`). Placement costs vary if the variation configures `placement.visibility-cost`.
   - For **`type: network`** variations, choose either to create a new world network (naming the network) or join an existing world network you own or are invited to.
3. Confirm to place the Aetheryte. If placement fails on the region thread (e.g., insufficient clearance or limits), both the item and any charged costs are refunded.

### 3. Warping Between Aetherytes
- Right-click any accessible Aetheryte to open the **Warp Destination Dialog**.
- Features inside the dialog:
  - **Search & Filter**: Filter by world or type to search destinations.
  - **Sort Order**: Cycle sort ordering (remembered per player).
  - **Favorites**: Star/unstar favorite Aetherytes up to `limits.max-favorites-per-player`.
  - **Destination Tooltips**: Inspect distance, computed warp cost (including optional components and visibility costs), and bypass indicators.
- If `warp.cast.ticks > 0`, a **cast bar** appears and you are temporarily immobilized until teleportation completes. Depending on `cancel.*` settings, moving, taking damage, or interacting cancels the cast without charging costs.
- **Leashed Entities (`warp.pets`)**: If enabled, animals/mobs currently leashed to you are safely detached, teleported to your arrival spot, and re-leashed upon landing.

### 4. Managing an Aetheryte & Visibility
Owners (and admins with `clickportals.admin` / `clickportals.rename.any` / `clickportals.remove.any`) can open the **Management Menu** from an Aetheryte's dialog:
- **Rename**: Rename the Aetheryte (`1–32` characters).
- **Visibility**: Switch between `PUBLIC`, `INVITE_ONLY`, and `OWNER_ONLY`. Changing visibility charges the target visibility's full `placement.visibility-cost` (or base `placement.cost` when `null`). No refunds are issued for downgrades.
- **Invite List**: Add or remove players when using `INVITE_ONLY` (managed at the network level for `type: network` Aetherytes).
- **Rename Network**: Rename the owning world network (`type: network`).
- **Remove**: Remove the Aetheryte and return its creation item (non-creative owners or `clickportals.remove.any`).
- **Admin Tools**: Operators holding `clickportals.admin` also see dialog actions to force-remove (no refund), transfer ownership, change variation, or resync visuals.

---

## Command Reference

Root command: `/clickportals` (alias `/cp`).

### Item & Plugin Management

| Command | Permission | Description |
|---|---|---|
| `/cp give [targets] [amount] [variation]` | `clickportals.command.give` | Gives `amount` (`1–64`, default `1`) Aetheryte creation items of `variation` (default `default`) to `targets` (defaults to the executing player). Drops overflow naturally on the ground. |
| `/cp reload` | `clickportals.command.reload` | Reloads `config.yml`, `lang/*.yml`, `aetheryte/*.yml`, recipes, models, holograms, sounds, and update checker without modifying config files on disk. |

---

### Administrative Aetheryte Management (`/cp aetheryte`)

Requires permission `clickportals.command.aetheryte` (included in `clickportals.admin`, default `op`). Supports full Brigadier tab completion.

#### List Aetherytes
```text
/cp aetheryte list [world] [owner] [page]
```
- Displays registered Aetherytes sorted by creation time (**10 entries per page**), with clickable UUID copy tooltips.
- Pass `*` for `world` or `owner` as a wildcard filter (e.g., `/cp aetheryte list * Steve 2`).

#### Inspect Aetheryte Details
```text
/cp aetheryte get <id>
```
- `<id>` accepts a full UUID or any unique lowercase UUID prefix (e.g., first 8 characters from `list`).
- Displays UUID, name, variation, world & coordinates, owner, effective visibility, and network ID.

#### Place an Aetheryte Directly
```text
/cp aetheryte place <variation> <world> <x> <y> <z> <name> [owner] [visibility] [networkId]
```
- Places an Aetheryte on `<world>` at block `<x> <y> <z>` via Folia's `RegionScheduler`.
- Bypasses player limits, costs, placement cooldowns, minimum distance, and block clearance checks.
- Quote `<name>` if it contains spaces (e.g., `"Main Hub"`).
- `[owner]`: Player name or UUID (defaults to executing player, or `Server` from console).
- `[visibility]`: `PUBLIC` (default), `INVITE_ONLY`, or `OWNER_ONLY`.
- `[networkId]`: Used when `<variation>` has `type: network`; pass an existing network UUID in the same world to join it, or omit / `new` / `none` to create a new network.

#### Remove an Aetheryte
```text
/cp aetheryte remove <id>
```
- Unregisters and removes the Aetheryte, Marker entity, BetterModel/BlockDisplay model, and hologram **without dropping an item**. Automatically deletes a world network if its last member is removed.

#### Modify an Existing Aetheryte
```text
/cp aetheryte set <id> name <name>
/cp aetheryte set <id> owner <owner>
/cp aetheryte set <id> variation <variation>
/cp aetheryte set <id> location <world> <x> <y> <z>
/cp aetheryte set <id> visibility <PUBLIC|INVITE_ONLY|OWNER_ONLY>
/cp aetheryte set <id> network <networkId|none|new>
```
- **`name`**: Updates display name (`1–32` chars, supports spaces) and refreshes hologram/PDC.
- **`owner`**: Transfers ownership by player name or UUID and updates hologram/PDC.
- **`variation`**: Updates variation and refreshes model, hologram, and PDC. Switching from `network` to `normal` detaches from the old network (deleting it if empty); switching from `normal` to `network` creates a network if not already in one.
- **`location`**: Moves the Aetheryte, Marker entity, model, and hologram cleanly across chunks or worlds. Cross-world relocation persists both source and destination worlds and detaches/recreates world-scoped networks safely.
- **`visibility`**: Updates visibility of the Aetheryte (or its owning network if attached to one) and refreshes holograms without charging player costs.
- **`network`**: Only valid for `type: network` variations; attaches to an existing network UUID in the same world, creates a `new` network, or detaches with `none`.
