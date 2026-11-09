package net.sabafly.clickportals.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.sabafly.clickportals.item.AetheryteItemFactory;
import net.sabafly.clickportals.message.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Brigadier command tree for {@code /clickportals}.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ClickPortalsCommand {

    private ClickPortalsCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> createCommand(final Plugin plugin) {
        return Commands.literal("clickportals")
                .requires(source -> source.getSender().hasPermission("clickportals.admin") || source.getSender().isOp())
                .then(Commands.literal("give")
                        .executes(ctx -> {
                            final CommandSender sender = ctx.getSource().getSender();
                            if (!(sender instanceof Player player)) {
                                sender.sendMessage(Messages.prefix().append(
                                        Component.text("コンソールから実行する場合は対象プレイヤーを指定してください。", NamedTextColor.RED)
                                ));
                                return Command.SINGLE_SUCCESS;
                            }
                            giveItems(plugin, sender, List.of(player), 1);
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("targets", ArgumentTypes.players())
                                .executes(ctx -> {
                                    final PlayerSelectorArgumentResolver resolver = ctx.getArgument("targets", PlayerSelectorArgumentResolver.class);
                                    final List<Player> targets = resolver.resolve(ctx.getSource());
                                    giveItems(plugin, ctx.getSource().getSender(), targets, 1);
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                        .executes(ctx -> {
                                            final PlayerSelectorArgumentResolver resolver = ctx.getArgument("targets", PlayerSelectorArgumentResolver.class);
                                            final List<Player> targets = resolver.resolve(ctx.getSource());
                                            final int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                            giveItems(plugin, ctx.getSource().getSender(), targets, amount);
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                )
                .build();
    }

    private static void giveItems(
            final Plugin plugin,
            final CommandSender sender,
            final List<Player> targets,
            final int amount
    ) {
        for (final Player target : targets) {
            final ItemStack item = AetheryteItemFactory.createItem(amount);
            target.getScheduler().run(plugin, task -> target.getInventory().addItem(item), null);
            sender.sendMessage(Messages.commandGivenItem(target.getName(), amount));
        }
    }
}
