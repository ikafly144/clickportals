# Usage & Commands

## Player Guide

### 1. Obtaining & Crafting an Aetheryte
- By default, players with crafting enabled can craft the **Default Aetheryte** item using **8 Chiseled Stone Bricks** surrounding **1 Amethyst Shard** (`aetheryte/default.yml`).
- Operators can also grant Aetherytes of any configured variation using `/cp give`.

### 2. Placing an Aetheryte
1. Hold an Aetheryte creation item and right-click against a block.
2. In the **Creation Dialog**:
   - Enter a display name (**1–32 characters**).
   - For **`type: normal`** variations, select initial **Visibility** (`PUBLIC`, `INVITE_ONLY`, or `OWNER_ONLY`). Placement costs vary if the variation configures `placement.visibility-cost`.
   - For **`type: network`** variations, select an existing joinable Hub network in the current world (you must own or be invited to a network created on a `normal` Hub first; placement fails if no joinable Hub network exists).
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

### 4. Managing an Aetheryte, Hub Networks & Visibility
Owners (and admins with `clickportals.admin` / `clickportals.rename.any` / `clickportals.remove.any`) can open the **Operations Menu** from an Aetheryte's dialog:
- **Rename**: Rename the Aetheryte (`1–32` characters).
- **Visibility**: Switch between `PUBLIC`, `INVITE_ONLY`, and `OWNER_ONLY`. Changing visibility charges the target visibility's full `placement.visibility-cost` (or base `placement.cost` when `null`). No refunds are issued for downgrades.
- **Invite List**: Add or remove players when using `INVITE_ONLY` (managed at the network level for `type: network` Aetherytes).
- **Manage Networks (Hub `normal` Aetherytes)**: When a `normal` Aetheryte variation enables `network.allow-hub: true`, its owner or an admin can open **Manage Networks** to create Hub-hosted world networks, rename networks, configure network visibility (`PUBLIC`, `INVITE_ONLY`, `OWNER_ONLY`), invite players, and delete empty networks (or cascade-delete non-empty networks as an admin).
- **Rename Network**: Rename the owning world network (`type: network` or via the Hub's network menu).
- **Remove**: Remove the Aetheryte and return its creation item (non-creative owners or `clickportals.remove.any`). Removing a Hub Aetheryte fails for regular players while child Aetherytes remain connected; admins see a cascade confirmation to remove the Hub together with all hosted networks and child Aetherytes.
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
- `[networkId]`: Required when `<variation>` has `type: network`; must reference an existing Hub-backed network UUID (or unique prefix) in `<world>`. Create the network on a Hub first via `/cp aetheryte network create`.

#### Remove an Aetheryte
```text
/cp aetheryte remove <id> [cascade]
```
- Unregisters and removes the Aetheryte, Marker entity, BetterModel/BlockDisplay model, and hologram **without dropping an item**.
- Refuses removal if `<id>` is a Hub Aetheryte that still has child Aetherytes in hosted networks unless `cascade` is appended, which removes the Hub along with all hosted networks and child Aetherytes.

#### Modify an Existing Aetheryte
```text
/cp aetheryte set <id> name <name>
/cp aetheryte set <id> owner <owner>
/cp aetheryte set <id> variation <variation>
/cp aetheryte set <id> location <world> <x> <y> <z>
/cp aetheryte set <id> visibility <PUBLIC|INVITE_ONLY|OWNER_ONLY>
/cp aetheryte set <id> network <networkId|none>
```
- **`name`**: Updates display name (`1–32` chars, supports spaces) and refreshes hologram/PDC.
- **`owner`**: Transfers ownership by player name or UUID and updates hologram/PDC.
- **`variation`**: Updates variation and refreshes model, hologram, and PDC. Blocks switching a Hub hosting networks to a non-hub or `network` variation, and blocks switching a standalone `normal` Aetheryte to `type: network` without a Hub network.
- **`location`**: Moves the Aetheryte, Marker entity, model, and hologram cleanly across chunks (and across worlds for unlinked `normal` Aetherytes). Cross-world relocation is blocked while connected to or hosting world-scoped networks.
- **`visibility`**: Updates visibility of the Aetheryte (or its owning network if attached to one) and refreshes holograms without charging player costs.
- **`network`**: Only valid for `type: network` variations; attaches to an existing Hub-backed network UUID in the same world or detaches with `none`.

#### Manage Hub-Based Networks (`/cp aetheryte network`)
```text
/cp aetheryte network list [world] [owner] [page]
/cp aetheryte network get <networkId>
/cp aetheryte network create <hubId> <name> [owner] [visibility]
/cp aetheryte network delete <networkId> [cascade]
/cp aetheryte network rename <networkId> <name>
/cp aetheryte network set <networkId> owner <player>
/cp aetheryte network set <networkId> visibility <PUBLIC|INVITE_ONLY|OWNER_ONLY>
/cp aetheryte network set <networkId> hub <hubId>
/cp aetheryte network invite <networkId> add|remove <player>
```
- **`list` / `get`**: Lists networks filtered by world/`owner` (`*` wildcard supported) with Hub and child counts, or inspects full details including invitees.
- **`create`**: Creates a new network attached to an eligible `normal` Hub Aetheryte (`network.allow-hub: true`), bypassing player limits and costs.
- **`delete`**: Deletes an empty network cleanly; refuses while child Aetherytes remain unless `cascade` is specified to remove all connected children together.
- **`rename`**: Renames the network and refreshes member holograms.
- **`set owner|visibility|hub`**: Transfers network ownership, updates shared visibility, or reassigns the network to another eligible Hub in the same world.
- **`invite add|remove`**: Adds or removes a player on the network's invite list.
