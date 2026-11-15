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
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import net.sabafly.clickportals.aetheryte.AetheryteNetwork;
import net.sabafly.clickportals.aetheryte.AetheryteVisibility;
import net.sabafly.clickportals.message.Messages;
import net.sabafly.clickportals.permission.Permissions;
import net.sabafly.clickportals.service.AetheryteService;
import net.sabafly.clickportals.variation.VariationRegistry;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Brigadier subcommand tree for {@code /clickportals aetheryte} (#74).
 */
@SuppressWarnings("UnstableApiUsage")
public final class AetheryteAdminCommand {

    private static final List<String> VISIBILITY_NAMES = List.of("PUBLIC", "INVITE_ONLY", "OWNER_ONLY");

    private AetheryteAdminCommand() {
    }

    /**
     * Builds the {@code aetheryte} Brigadier command node.
     */
    public static LiteralCommandNode<CommandSourceStack> createNode(
            final VariationRegistry variationRegistry,
            final Messages messages,
            final AetheryteService service
    ) {
        return Commands.literal("aetheryte")
                .requires(source -> Permissions.canManageAetherytes(source.getSender()))
                .then(buildListNode(service))
                .then(buildGetNode(service))
                .then(buildPlaceNode(variationRegistry, messages, service))
                .then(buildRemoveNode(service))
                .then(buildSetNode(variationRegistry, messages, service))
                .then(buildNetworkNode(variationRegistry, messages, service))
                .build();
    }

    private static LiteralCommandNode<CommandSourceStack> buildListNode(final AetheryteService service) {
        return Commands.literal("list")
                .executes(ctx -> {
                    service.adminListAetherytes(ctx.getSource().getSender(), null, null, 1);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("world", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestWorlds(true, builder))
                        .executes(ctx -> {
                            final String worldFilter = StringArgumentType.getString(ctx, "world");
                            service.adminListAetherytes(ctx.getSource().getSender(), worldFilter, null, 1);
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("owner", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestPlayers(true, builder))
                                .executes(ctx -> {
                                    final String worldFilter = StringArgumentType.getString(ctx, "world");
                                    final String ownerFilter = StringArgumentType.getString(ctx, "owner");
                                    service.adminListAetherytes(ctx.getSource().getSender(), worldFilter, ownerFilter, 1);
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            final String worldFilter = StringArgumentType.getString(ctx, "world");
                                            final String ownerFilter = StringArgumentType.getString(ctx, "owner");
                                            final int page = IntegerArgumentType.getInteger(ctx, "page");
                                            service.adminListAetherytes(
                                                    ctx.getSource().getSender(), worldFilter, ownerFilter, page);
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                )
                .build();
    }

    private static LiteralCommandNode<CommandSourceStack> buildGetNode(final AetheryteService service) {
        return Commands.literal("get")
                .then(Commands.argument("id", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestAetheryteIds(service, builder))
                        .executes(ctx -> {
                            final String rawId = StringArgumentType.getString(ctx, "id");
                            service.adminGetAetheryte(ctx.getSource().getSender(), rawId);
                            return Command.SINGLE_SUCCESS;
                        })
                )
                .build();
    }

    private static LiteralCommandNode<CommandSourceStack> buildPlaceNode(
            final VariationRegistry variationRegistry,
            final Messages messages,
            final AetheryteService service
    ) {
        return Commands.literal("place")
                .then(Commands.argument("variation", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestVariations(variationRegistry, builder))
                        .then(Commands.argument("world", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestWorlds(false, builder))
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("y", IntegerArgumentType.integer())
                                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                                        .then(Commands.argument("name", StringArgumentType.string())
                                                                .executes(ctx -> executePlace(
                                                                        ctx.getSource().getSender(), messages, service,
                                                                        StringArgumentType.getString(ctx, "variation"),
                                                                        StringArgumentType.getString(ctx, "world"),
                                                                        IntegerArgumentType.getInteger(ctx, "x"),
                                                                        IntegerArgumentType.getInteger(ctx, "y"),
                                                                        IntegerArgumentType.getInteger(ctx, "z"),
                                                                        StringArgumentType.getString(ctx, "name"),
                                                                        null, null, null
                                                                ))
                                                                .then(Commands.argument("owner", StringArgumentType.word())
                                                                        .suggests((ctx, builder) -> suggestPlayers(false, builder))
                                                                        .executes(ctx -> executePlace(
                                                                                ctx.getSource().getSender(), messages, service,
                                                                                StringArgumentType.getString(ctx, "variation"),
                                                                                StringArgumentType.getString(ctx, "world"),
                                                                                IntegerArgumentType.getInteger(ctx, "x"),
                                                                                IntegerArgumentType.getInteger(ctx, "y"),
                                                                                IntegerArgumentType.getInteger(ctx, "z"),
                                                                                StringArgumentType.getString(ctx, "name"),
                                                                                StringArgumentType.getString(ctx, "owner"),
                                                                                null, null
                                                                        ))
                                                                        .then(Commands.argument("visibility", StringArgumentType.word())
                                                                                .suggests((ctx, builder) -> suggestVisibilities(builder))
                                                                                .executes(ctx -> executePlace(
                                                                                        ctx.getSource().getSender(), messages, service,
                                                                                        StringArgumentType.getString(ctx, "variation"),
                                                                                        StringArgumentType.getString(ctx, "world"),
                                                                                        IntegerArgumentType.getInteger(ctx, "x"),
                                                                                        IntegerArgumentType.getInteger(ctx, "y"),
                                                                                        IntegerArgumentType.getInteger(ctx, "z"),
                                                                                        StringArgumentType.getString(ctx, "name"),
                                                                                        StringArgumentType.getString(ctx, "owner"),
                                                                                        StringArgumentType.getString(ctx, "visibility"),
                                                                                        null
                                                                                ))
                                                                                .then(Commands.argument("networkId", StringArgumentType.word())
                                                                                        .suggests((ctx, builder) -> suggestNetworks(service, builder))
                                                                                        .executes(ctx -> executePlace(
                                                                                                ctx.getSource().getSender(), messages, service,
                                                                                                StringArgumentType.getString(ctx, "variation"),
                                                                                                StringArgumentType.getString(ctx, "world"),
                                                                                                IntegerArgumentType.getInteger(ctx, "x"),
                                                                                                IntegerArgumentType.getInteger(ctx, "y"),
                                                                                                IntegerArgumentType.getInteger(ctx, "z"),
                                                                                                StringArgumentType.getString(ctx, "name"),
                                                                                                StringArgumentType.getString(ctx, "owner"),
                                                                                                StringArgumentType.getString(ctx, "visibility"),
                                                                                                StringArgumentType.getString(ctx, "networkId")
                                                                                        ))
                                                                                )
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
                .build();
    }

    private static int executePlace(
            final CommandSender sender,
            final Messages messages,
            final AetheryteService service,
            final String variation,
            final String worldName,
            final int x,
            final int y,
            final int z,
            final String name,
            final @Nullable String rawOwner,
            final @Nullable String rawVisibility,
            final @Nullable String rawNetworkId
    ) {
        final @Nullable World world = resolveWorldOrNotify(sender, messages, worldName);
        if (world == null) {
            return Command.SINGLE_SUCCESS;
        }
        final @Nullable AetheryteVisibility visibility;
        if (rawVisibility != null) {
            visibility = parseVisibility(rawVisibility);
            if (visibility == null) {
                sender.sendMessage(messages.commandAetheryteInvalidVisibility(sender, rawVisibility));
                return Command.SINGLE_SUCCESS;
            }
        } else {
            visibility = null;
        }
        service.adminPlaceAetheryte(sender, variation, world, x, y, z, name, rawOwner, visibility, rawNetworkId);
        return Command.SINGLE_SUCCESS;
    }

    private static LiteralCommandNode<CommandSourceStack> buildRemoveNode(final AetheryteService service) {
        return Commands.literal("remove")
                .then(Commands.argument("id", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestAetheryteIds(service, builder))
                        .executes(ctx -> {
                            final String rawId = StringArgumentType.getString(ctx, "id");
                            service.adminRemoveAetheryte(ctx.getSource().getSender(), rawId, false);
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.literal("cascade")
                                .executes(ctx -> {
                                    final String rawId = StringArgumentType.getString(ctx, "id");
                                    service.adminRemoveAetheryte(ctx.getSource().getSender(), rawId, true);
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )
                .build();
    }

    private static LiteralCommandNode<CommandSourceStack> buildSetNode(
            final VariationRegistry variationRegistry,
            final Messages messages,
            final AetheryteService service
    ) {
        return Commands.literal("set")
                .then(Commands.argument("id", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestAetheryteIds(service, builder))
                        .then(Commands.literal("name")
                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            service.adminSetName(
                                                    ctx.getSource().getSender(),
                                                    StringArgumentType.getString(ctx, "id"),
                                                    StringArgumentType.getString(ctx, "value")
                                            );
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                        .then(Commands.literal("owner")
                                .then(Commands.argument("owner", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestPlayers(false, builder))
                                        .executes(ctx -> {
                                            service.adminSetOwner(
                                                    ctx.getSource().getSender(),
                                                    StringArgumentType.getString(ctx, "id"),
                                                    StringArgumentType.getString(ctx, "owner")
                                            );
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                        .then(Commands.literal("variation")
                                .then(Commands.argument("variation", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestVariations(variationRegistry, builder))
                                        .executes(ctx -> {
                                            service.adminSetVariation(
                                                    ctx.getSource().getSender(),
                                                    StringArgumentType.getString(ctx, "id"),
                                                    StringArgumentType.getString(ctx, "variation")
                                            );
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                        .then(Commands.literal("location")
                                .then(Commands.argument("world", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestWorlds(false, builder))
                                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                                .executes(ctx -> {
                                                                    final CommandSender sender = ctx.getSource().getSender();
                                                                    final String worldName = StringArgumentType.getString(ctx, "world");
                                                                    final @Nullable World world =
                                                                            resolveWorldOrNotify(sender, messages, worldName);
                                                                    if (world == null) {
                                                                        return Command.SINGLE_SUCCESS;
                                                                    }
                                                                    service.adminSetLocation(
                                                                            sender,
                                                                            StringArgumentType.getString(ctx, "id"),
                                                                            world,
                                                                            IntegerArgumentType.getInteger(ctx, "x"),
                                                                            IntegerArgumentType.getInteger(ctx, "y"),
                                                                            IntegerArgumentType.getInteger(ctx, "z")
                                                                    );
                                                                    return Command.SINGLE_SUCCESS;
                                                                })
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("visibility")
                                .then(Commands.argument("visibility", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestVisibilities(builder))
                                        .executes(ctx -> {
                                            final CommandSender sender = ctx.getSource().getSender();
                                            final String rawVis = StringArgumentType.getString(ctx, "visibility");
                                            final @Nullable AetheryteVisibility visibility = parseVisibility(rawVis);
                                            if (visibility == null) {
                                                sender.sendMessage(
                                                        messages.commandAetheryteInvalidVisibility(sender, rawVis));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            service.adminSetVisibility(
                                                    sender,
                                                    StringArgumentType.getString(ctx, "id"),
                                                    visibility
                                            );
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                        .then(Commands.literal("network")
                                .then(Commands.argument("networkId", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestNetworks(service, builder))
                                        .executes(ctx -> {
                                            service.adminSetNetwork(
                                                    ctx.getSource().getSender(),
                                                    StringArgumentType.getString(ctx, "id"),
                                                    StringArgumentType.getString(ctx, "networkId")
                                            );
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                )
                .build();
    }

    private static LiteralCommandNode<CommandSourceStack> buildNetworkNode(
            final VariationRegistry variationRegistry,
            final Messages messages,
            final AetheryteService service
    ) {
        return Commands.literal("network")
                .then(buildNetworkListNode(service))
                .then(buildNetworkGetNode(service))
                .then(buildNetworkCreateNode(variationRegistry, messages, service))
                .then(buildNetworkDeleteNode(service))
                .then(buildNetworkRenameNode(service))
                .then(buildNetworkSetNode(variationRegistry, messages, service))
                .then(buildNetworkInviteNode(service))
                .build();
    }

    private static LiteralCommandNode<CommandSourceStack> buildNetworkListNode(final AetheryteService service) {
        return Commands.literal("list")
                .executes(ctx -> {
                    service.adminListNetworks(ctx.getSource().getSender(), null, null, 1);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("world", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestWorlds(true, builder))
                        .executes(ctx -> {
                            service.adminListNetworks(
                                    ctx.getSource().getSender(),
                                    StringArgumentType.getString(ctx, "world"),
                                    null,
                                    1
                            );
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("owner", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestPlayers(true, builder))
                                .executes(ctx -> {
                                    service.adminListNetworks(
                                            ctx.getSource().getSender(),
                                            StringArgumentType.getString(ctx, "world"),
                                            StringArgumentType.getString(ctx, "owner"),
                                            1
                                    );
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            service.adminListNetworks(
                                                    ctx.getSource().getSender(),
                                                    StringArgumentType.getString(ctx, "world"),
                                                    StringArgumentType.getString(ctx, "owner"),
                                                    IntegerArgumentType.getInteger(ctx, "page")
                                            );
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                )
                .build();
    }

    private static LiteralCommandNode<CommandSourceStack> buildNetworkGetNode(final AetheryteService service) {
        return Commands.literal("get")
                .then(Commands.argument("networkId", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestNetworkIds(service, builder))
                        .executes(ctx -> {
                            service.adminGetNetwork(
                                    ctx.getSource().getSender(),
                                    StringArgumentType.getString(ctx, "networkId")
                            );
                            return Command.SINGLE_SUCCESS;
                        })
                )
                .build();
    }

    private static LiteralCommandNode<CommandSourceStack> buildNetworkCreateNode(
            final VariationRegistry variationRegistry,
            final Messages messages,
            final AetheryteService service
    ) {
        return Commands.literal("create")
                .then(Commands.argument("hubId", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestHubAetheryteIds(variationRegistry, service, builder))
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(ctx -> executeNetworkCreate(
                                        ctx.getSource().getSender(), messages, service,
                                        StringArgumentType.getString(ctx, "hubId"),
                                        StringArgumentType.getString(ctx, "name"),
                                        null, null
                                ))
                                .then(Commands.argument("owner", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestPlayers(false, builder))
                                        .executes(ctx -> executeNetworkCreate(
                                                ctx.getSource().getSender(), messages, service,
                                                StringArgumentType.getString(ctx, "hubId"),
                                                StringArgumentType.getString(ctx, "name"),
                                                StringArgumentType.getString(ctx, "owner"),
                                                null
                                        ))
                                        .then(Commands.argument("visibility", StringArgumentType.word())
                                                .suggests((ctx, builder) -> suggestVisibilities(builder))
                                                .executes(ctx -> executeNetworkCreate(
                                                        ctx.getSource().getSender(), messages, service,
                                                        StringArgumentType.getString(ctx, "hubId"),
                                                        StringArgumentType.getString(ctx, "name"),
                                                        StringArgumentType.getString(ctx, "owner"),
                                                        StringArgumentType.getString(ctx, "visibility")
                                                ))
                                        )
                                )
                        )
                )
                .build();
    }

    private static int executeNetworkCreate(
            final CommandSender sender,
            final Messages messages,
            final AetheryteService service,
            final String hubId,
            final String name,
            final @Nullable String rawOwner,
            final @Nullable String rawVisibility
    ) {
        final @Nullable AetheryteVisibility visibility;
        if (rawVisibility != null) {
            visibility = parseVisibility(rawVisibility);
            if (visibility == null) {
                sender.sendMessage(messages.commandAetheryteInvalidVisibility(sender, rawVisibility));
                return Command.SINGLE_SUCCESS;
            }
        } else {
            visibility = null;
        }
        service.adminCreateNetwork(sender, hubId, name, rawOwner, visibility);
        return Command.SINGLE_SUCCESS;
    }

    private static LiteralCommandNode<CommandSourceStack> buildNetworkDeleteNode(final AetheryteService service) {
        return Commands.literal("delete")
                .then(Commands.argument("networkId", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestNetworkIds(service, builder))
                        .executes(ctx -> {
                            service.adminDeleteNetwork(
                                    ctx.getSource().getSender(),
                                    StringArgumentType.getString(ctx, "networkId"),
                                    false
                            );
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.literal("cascade")
                                .executes(ctx -> {
                                    service.adminDeleteNetwork(
                                            ctx.getSource().getSender(),
                                            StringArgumentType.getString(ctx, "networkId"),
                                            true
                                    );
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )
                .build();
    }

    private static LiteralCommandNode<CommandSourceStack> buildNetworkRenameNode(final AetheryteService service) {
        return Commands.literal("rename")
                .then(Commands.argument("networkId", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestNetworkIds(service, builder))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    service.adminRenameNetwork(
                                            ctx.getSource().getSender(),
                                            StringArgumentType.getString(ctx, "networkId"),
                                            StringArgumentType.getString(ctx, "name")
                                    );
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )
                .build();
    }

    private static LiteralCommandNode<CommandSourceStack> buildNetworkSetNode(
            final VariationRegistry variationRegistry,
            final Messages messages,
            final AetheryteService service
    ) {
        return Commands.literal("set")
                .then(Commands.argument("networkId", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestNetworkIds(service, builder))
                        .then(Commands.literal("owner")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestPlayers(false, builder))
                                        .executes(ctx -> {
                                            service.adminSetNetworkOwner(
                                                    ctx.getSource().getSender(),
                                                    StringArgumentType.getString(ctx, "networkId"),
                                                    StringArgumentType.getString(ctx, "player")
                                            );
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                        .then(Commands.literal("visibility")
                                .then(Commands.argument("visibility", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestVisibilities(builder))
                                        .executes(ctx -> {
                                            final CommandSender sender = ctx.getSource().getSender();
                                            final String rawVis = StringArgumentType.getString(ctx, "visibility");
                                            final @Nullable AetheryteVisibility visibility = parseVisibility(rawVis);
                                            if (visibility == null) {
                                                sender.sendMessage(
                                                        messages.commandAetheryteInvalidVisibility(sender, rawVis));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            service.adminSetNetworkVisibility(
                                                    sender,
                                                    StringArgumentType.getString(ctx, "networkId"),
                                                    visibility
                                            );
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                        .then(Commands.literal("hub")
                                .then(Commands.argument("hubId", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestHubAetheryteIds(variationRegistry, service, builder))
                                        .executes(ctx -> {
                                            service.adminSetNetworkHub(
                                                    ctx.getSource().getSender(),
                                                    StringArgumentType.getString(ctx, "networkId"),
                                                    StringArgumentType.getString(ctx, "hubId")
                                            );
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                )
                .build();
    }

    private static LiteralCommandNode<CommandSourceStack> buildNetworkInviteNode(final AetheryteService service) {
        return Commands.literal("invite")
                .then(Commands.argument("networkId", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestNetworkIds(service, builder))
                        .then(Commands.literal("add")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestPlayers(false, builder))
                                        .executes(ctx -> {
                                            service.adminUpdateNetworkInvite(
                                                    ctx.getSource().getSender(),
                                                    StringArgumentType.getString(ctx, "networkId"),
                                                    StringArgumentType.getString(ctx, "player"),
                                                    true
                                            );
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                        .then(Commands.literal("remove")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestPlayers(false, builder))
                                        .executes(ctx -> {
                                            service.adminUpdateNetworkInvite(
                                                    ctx.getSource().getSender(),
                                                    StringArgumentType.getString(ctx, "networkId"),
                                                    StringArgumentType.getString(ctx, "player"),
                                                    false
                                            );
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                )
                .build();
    }

    /**
     * Strictly parses a visibility enum/identifier string, returning {@code null} for unrecognized inputs (#74).
     */
    public static @Nullable AetheryteVisibility parseVisibility(final @Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        final String normalized = raw.strip().toLowerCase(Locale.ROOT).replace('_', '-');
        return switch (normalized) {
            case "public" -> AetheryteVisibility.PUBLIC;
            case "invite-only" -> AetheryteVisibility.INVITE_ONLY;
            case "owner-only", "private" -> AetheryteVisibility.OWNER_ONLY;
            default -> null;
        };
    }

    private static @Nullable World resolveWorldOrNotify(
            final CommandSender sender,
            final Messages messages,
            final String worldName
    ) {
        final @Nullable World world = Bukkit.getServer() != null ? Bukkit.getWorld(worldName) : null;
        if (world == null) {
            sender.sendMessage(messages.commandAetheryteUnknownWorld(sender, worldName));
        }
        return world;
    }

    private static CompletableFuture<Suggestions> suggestAetheryteIds(
            final AetheryteService service,
            final SuggestionsBuilder builder
    ) {
        final String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (final Aetheryte entry : service.registry().getAll()) {
            final String id = entry.id().toString();
            if (id.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(id);
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestVariations(
            final VariationRegistry variationRegistry,
            final SuggestionsBuilder builder
    ) {
        final String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (final String id : variationRegistry.ids()) {
            if (id.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(id);
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestWorlds(
            final boolean includeWildcard,
            final SuggestionsBuilder builder
    ) {
        final String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        if (includeWildcard && "*".startsWith(remaining)) {
            builder.suggest("*");
        }
        if (Bukkit.getServer() != null) {
            for (final World world : Bukkit.getWorlds()) {
                if (world.getName().toLowerCase(Locale.ROOT).startsWith(remaining)) {
                    builder.suggest(world.getName());
                }
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestPlayers(
            final boolean includeWildcard,
            final SuggestionsBuilder builder
    ) {
        final String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        if (includeWildcard && "*".startsWith(remaining)) {
            builder.suggest("*");
        }
        if (Bukkit.getServer() != null) {
            for (final Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(remaining)) {
                    builder.suggest(player.getName());
                }
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestVisibilities(final SuggestionsBuilder builder) {
        final String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (final String option : VISIBILITY_NAMES) {
            if (option.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(option);
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestHubAetheryteIds(
            final VariationRegistry variationRegistry,
            final AetheryteService service,
            final SuggestionsBuilder builder
    ) {
        final String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (final Aetheryte entry : service.registry().getAll()) {
            if (entry.networkId() != null) {
                continue;
            }
            if (!variationRegistry.getOrDefault(entry.variation()).canBeNetworkHub()) {
                continue;
            }
            final String id = entry.id().toString();
            if (id.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(id);
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestNetworkIds(
            final AetheryteService service,
            final SuggestionsBuilder builder
    ) {
        final String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (final AetheryteNetwork network : service.registry().getAllNetworks()) {
            final String id = network.id().toString();
            if (id.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(id);
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestNetworks(
            final AetheryteService service,
            final SuggestionsBuilder builder
    ) {
        final String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        if ("none".startsWith(remaining)) {
            builder.suggest("none");
        }
        for (final AetheryteNetwork network : service.registry().getAllNetworks()) {
            final String id = network.id().toString();
            if (id.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(id);
            }
        }
        return builder.buildFuture();
    }
}
