# Permissions

ClickPortals registers all permission nodes in `paper-plugin.yml` with defaults designed so players can craft, place, and use Aetherytes immediately while moderation, bypasses, and administrative commands stay restricted to operators (`op`).

## Permission Nodes

| Permission Node | Default | Description |
|---|---|---|
| `clickportals.use` | `true` | Allows opening Aetheryte warp dialogs and teleporting. |
| `clickportals.place` | `true` | Allows placing Aetheryte creation items. |
| `clickportals.remove.any` | `op` | Allows removing Aetherytes owned by other players (owners can always remove their own). |
| `clickportals.rename.any` | `op` | Allows renaming Aetherytes owned by other players (owners can always rename their own). |
| `clickportals.command.give` | `op` | Allows using `/clickportals give` (`/cp give`). |
| `clickportals.command.reload` | `op` | Allows using `/clickportals reload` (`/cp reload`). |
| `clickportals.command.aetheryte` | `op` | Allows using `/clickportals aetheryte` (`/cp aetheryte`) management subcommands. |
| `clickportals.bypass.limit` | `op` | Bypasses per-player Aetheryte placement limits, network placement limits, and placement cooldowns. |
| `clickportals.bypass.distance` | `op` | Bypasses minimum placement distance and maximum network distance restrictions (also implied by `clickportals.bypass.limit`). |
| `clickportals.bypass.cost.warp` | `op` | Bypasses all warp costs (distance, dimension, visibility, and pet surcharges). |
| `clickportals.bypass.cost.place` | `op` | Bypasses placement, visibility-change, and network create/join costs. |
| `clickportals.bypass.dimension` | `op` | Bypasses per-dimension `allow-place` and `allow-use` restrictions in `config.yml`. |
| `clickportals.bypass.visibility` | `op` | Allows seeing and warping to `INVITE_ONLY` and `OWNER_ONLY` Aetherytes and networks owned by others. |
| `clickportals.notify.update` | `op` | Receives in-game Modrinth update notifications upon joining when a newer compatible release is available. |
| `clickportals.admin` | `op` | Full administrative access; inherits all 14 permissions listed above as children. |

---

## Managing with LuckPerms (Examples)

Restrict Aetheryte placement to VIP players while letting everyone warp:
```sh
lp group default permission set clickportals.place false
lp group vip permission set clickportals.place true
```

Allow moderators to manage Aetherytes via `/cp aetheryte` and bypass visibility without giving full bypasses:
```sh
lp group mod permission set clickportals.command.aetheryte true
lp group mod permission set clickportals.bypass.visibility true
lp group mod permission set clickportals.remove.any true
```

Give VIPs free warps:
```sh
lp group vip permission set clickportals.bypass.cost.warp true
```
