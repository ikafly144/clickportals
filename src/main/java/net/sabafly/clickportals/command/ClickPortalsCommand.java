/*
 * ClickPortals
 * Copyright (C) 2026 ikafly144
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package net.sabafly.clickportals.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import java.util.List;
import java.util.Map;
import net.sabafly.clickportals.ClickPortals;
import net.sabafly.clickportals.item.AetheryteItemFactory;
import net.sabafly.clickportals.message.MessageKey;
import net.sabafly.clickportals.message.Messages;
import net.sabafly.clickportals.permission.Permissions;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Brigadier command tree for {@code /clickportals} (#17, #18, #19).
 */
@SuppressWarnings("UnstableApiUsage")
public final class ClickPortalsCommand {

    private ClickPortalsCommand() {
    }

    /**
     * Builds the {@code /clickportals} Brigadier command node.
     */
    public static LiteralCommandNode<CommandSourceStack> createCommand(
            final ClickPortals plugin,
            final Messages messages
    ) {
        return Commands.literal("clickportals")
                .requires(source -> Permissions.canUseCommand(source.getSender()))
                .then(Commands.literal("give")
                        .requires(source -> Permissions.canGive(source.getSender()))
                        .executes(ctx -> {
                            final CommandSender sender = ctx.getSource().getSender();
                            if (!(sender instanceof Player player)) {
                                messages.send(sender, MessageKey.COMMAND_CONSOLE_SPECIFY_PLAYER);
                                return Command.SINGLE_SUCCESS;
                            }
                            giveItems(plugin, messages, sender, List.of(player), 1);
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("targets", ArgumentTypes.players())
                                .executes(ctx -> {
                                    final PlayerSelectorArgumentResolver resolver =
                                            ctx.getArgument("targets", PlayerSelectorArgumentResolver.class);
                                    final List<Player> targets = resolver.resolve(ctx.getSource());
                                    giveItems(plugin, messages, ctx.getSource().getSender(), targets, 1);
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                        .executes(ctx -> {
                                            final PlayerSelectorArgumentResolver resolver =
                                                    ctx.getArgument("targets", PlayerSelectorArgumentResolver.class);
                                            final List<Player> targets = resolver.resolve(ctx.getSource());
                                            final int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                            giveItems(plugin, messages, ctx.getSource().getSender(), targets, amount);
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                )
                .then(Commands.literal("reload")
                        .requires(source -> Permissions.canReload(source.getSender()))
                        .executes(ctx -> {
                            final CommandSender sender = ctx.getSource().getSender();
                            plugin.reloadPlugin(sender);
                            return Command.SINGLE_SUCCESS;
                        })
                )
                .build();
    }

    /**
     * Gives Aetheryte items to each target player on their entity scheduler, dropping any overflow at their feet.
     */
    private static void giveItems(
            final Plugin plugin,
            final Messages messages,
            final CommandSender sender,
            final List<Player> targets,
            final int amount
    ) {
        for (final Player target : targets) {
            final ItemStack item = AetheryteItemFactory.createItem(amount);
            target.getScheduler().run(plugin, task -> {
                final Map<Integer, ItemStack> leftovers = target.getInventory().addItem(item);
                if (!leftovers.isEmpty()) {
                    final Location loc = target.getLocation();
                    final World world = target.getWorld();
                    if (loc != null) {
                        for (final ItemStack leftover : leftovers.values()) {
                            world.dropItemNaturally(loc, leftover);
                        }
                    }
                }
            }, null);
            sender.sendMessage(messages.commandGivenItem(sender, target.getName(), amount));
        }
    }
}
