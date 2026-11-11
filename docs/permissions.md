# Permissions Reference

ClickPortals registers all permission nodes in `paper-plugin.yml` with sensible defaults so that regular players can immediately place and use Aetherytes while administrative operations remain restricted to operators.

## Permission Nodes

| Permission Node | Default | Description |
|---|---|---|
| `clickportals.use` | `true` | Allows using Aetheryte warps and opening the warp destination dialog. |
| `clickportals.place` | `true` | Allows placing new Aetherytes using an Aetheryte creation item. |
| `clickportals.remove.any` | `op` | Allows removing Aetherytes placed by other players (owners can always remove their own). |
| `clickportals.command.give` | `op` | Allows executing `/clickportals give` (`/cp give`) to distribute Aetheryte creation items. |
| `clickportals.command.reload` | `op` | Allows executing `/clickportals reload` (`/cp reload`) to reload configuration and translations. |
| `clickportals.bypass.limit` | `op` | Bypasses per-player Aetheryte placement limits and placement cooldowns. |
| `clickportals.bypass.cost.warp` | `op` | Bypasses warp costs when teleporting between Aetherytes. |
| `clickportals.bypass.cost.place` | `op` | Bypasses placement costs when creating a new Aetheryte (reserved for #15; currently inactive and does not prevent creation item consumption). |
| `clickportals.admin` | `op` | Grants all ClickPortals permissions listed above via child permission inheritance. |

## Commands

| Command | Alias | Permission | Description |
|---|---|---|---|
| `/clickportals give [targets] [amount] [variation]` | `/cp give` | `clickportals.command.give` | Gives Aetheryte creation item(s) of the specified variation (defaults to `default`) to the executing player or specified targets. |
| `/clickportals reload` | `/cp reload` | `clickportals.command.reload` | Reloads `config.yml`, `lang/*.yml` translations, `aetheryte/*.yml` variations, crafting recipes, and client-side holograms. |
