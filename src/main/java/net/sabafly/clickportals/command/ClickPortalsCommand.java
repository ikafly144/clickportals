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
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.sabafly.clickportals.ClickPortals;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import net.sabafly.clickportals.item.AetheryteItemFactory;
import net.sabafly.clickportals.message.MessageKey;
import net.sabafly.clickportals.message.Messages;
import net.sabafly.clickportals.permission.Permissions;
import net.sabafly.clickportals.variation.AetheryteVariation;
import net.sabafly.clickportals.variation.VariationRegistry;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Brigadier command tree for {@code /clickportals} (#17, #18, #19, #37, #38).
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
            final VariationRegistry variationRegistry,
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
                            giveItems(
                                    plugin,
                                    variationRegistry,
                                    messages,
                                    sender,
                                    List.of(player),
                                    1,
                                    Aetheryte.DEFAULT_VARIATION
                            );
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("targets", ArgumentTypes.players())
                                .executes(ctx -> {
                                    final PlayerSelectorArgumentResolver resolver =
                                            ctx.getArgument("targets", PlayerSelectorArgumentResolver.class);
                                    final List<Player> targets = resolver.resolve(ctx.getSource());
                                    giveItems(
                                            plugin,
                                            variationRegistry,
                                            messages,
                                            ctx.getSource().getSender(),
                                            targets,
                                            1,
                                            Aetheryte.DEFAULT_VARIATION
                                    );
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                        .executes(ctx -> {
                                            final PlayerSelectorArgumentResolver resolver =
                                                    ctx.getArgument("targets", PlayerSelectorArgumentResolver.class);
                                            final List<Player> targets = resolver.resolve(ctx.getSource());
                                            final int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                            giveItems(
                                                    plugin,
                                                    variationRegistry,
                                                    messages,
                                                    ctx.getSource().getSender(),
                                                    targets,
                                                    amount,
                                                    Aetheryte.DEFAULT_VARIATION
                                            );
                                            return Command.SINGLE_SUCCESS;
                                        })
                                        .then(Commands.argument("variation", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    final String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
                                                    for (final String id : variationRegistry.ids()) {
                                                        if (id.startsWith(remaining)) {
                                                            builder.suggest(id);
                                                        }
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(ctx -> {
                                                    final PlayerSelectorArgumentResolver resolver =
                                                            ctx.getArgument("targets", PlayerSelectorArgumentResolver.class);
                                                    final List<Player> targets = resolver.resolve(ctx.getSource());
                                                    final int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                    final String variationId = StringArgumentType.getString(ctx, "variation");
                                                    giveItems(
                                                            plugin,
                                                            variationRegistry,
                                                            messages,
                                                            ctx.getSource().getSender(),
                                                            targets,
                                                            amount,
                                                            variationId
                                                    );
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
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
     * Gives Aetheryte items of the specified variation to each target player on their entity scheduler,
     * dropping any overflow at their feet (#38).
     */
    private static void giveItems(
            final Plugin plugin,
            final VariationRegistry variationRegistry,
            final Messages messages,
            final CommandSender sender,
            final List<Player> targets,
            final int amount,
            final String rawVariationId
    ) {
        final String normalizedId = Aetheryte.normalizeVariation(rawVariationId);
        final Optional<AetheryteVariation> variationOpt = variationRegistry.find(normalizedId);
        if (variationOpt.isEmpty()) {
            sender.sendMessage(messages.commandUnknownVariation(sender, rawVariationId));
            return;
        }
        final AetheryteVariation variation = variationOpt.get();

        for (final Player target : targets) {
            final ItemStack item = AetheryteItemFactory.createItem(normalizedId, variation, amount, messages);
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
