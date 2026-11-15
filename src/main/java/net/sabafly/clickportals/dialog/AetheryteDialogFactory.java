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
package net.sabafly.clickportals.dialog;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import net.sabafly.clickportals.aetheryte.AetheryteNetwork;
import net.sabafly.clickportals.aetheryte.AetheryteVisibility;
import net.sabafly.clickportals.cost.CostBundle;
import net.sabafly.clickportals.cost.CostFormatter;
import net.sabafly.clickportals.cost.CostQuote;
import net.sabafly.clickportals.dialog.WarpDestinationQuery.Candidate;
import net.sabafly.clickportals.dialog.WarpDestinationQuery.NetworkGroup;
import net.sabafly.clickportals.dialog.WarpDestinationQuery.PageSlice;
import net.sabafly.clickportals.dialog.WarpDestinationQuery.WorldGroup;
import net.sabafly.clickportals.economy.EconomyProvider;
import net.sabafly.clickportals.message.MessageKey;
import net.sabafly.clickportals.message.Messages;
import net.sabafly.clickportals.permission.Permissions;
import net.sabafly.clickportals.service.AetheryteService;
import net.sabafly.clickportals.service.AetheryteService.WarpEvaluation;
import net.sabafly.clickportals.variation.AetheryteVariation;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Paper Dialog API factory for Aetheryte creation, hierarchical warp navigation, favorites, search/sort,
 * and operations/admin management (#13, #18, #19, #37, #53, #54, #55, #56, #57).
 */
@SuppressWarnings("UnstableApiUsage")
public final class AetheryteDialogFactory {

    private static final String INPUT_KEY_NAME = "name";
    private static final String INPUT_KEY_SEARCH = "search";
    private static final String INPUT_KEY_TARGET_PLAYER = "target_player";
    private static final String INPUT_KEY_VARIATION = "variation";
    private static final String INPUT_KEY_VISIBILITY = "visibility";
    private static final String INPUT_KEY_NETWORK_MODE = "network_mode";
    private static final String INPUT_KEY_NETWORK_NAME = "network_name";
    private static final String NETWORK_MODE_JOIN_PREFIX = "join:";
    private static final int BUTTON_WIDTH_PRIMARY = 240;
    private static final int BUTTON_WIDTH_SECONDARY = 150;

    private static final ClickCallback.Options SINGLE_USE_CALLBACK = ClickCallback.Options.builder()
            .uses(1)
            .lifetime(Duration.ofMinutes(30))
            .build();

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final AetheryteService service;
    private final Messages messages;

    /**
     * Creates a new {@link AetheryteDialogFactory} using the {@link Messages} from {@code service}.
     */
    public AetheryteDialogFactory(final AetheryteService service) {
        this(service, service.messages());
    }

    /**
     * Creates a new {@link AetheryteDialogFactory} with an explicit {@link Messages} instance.
     */
    public AetheryteDialogFactory(final AetheryteService service, final Messages messages) {
        this.service = service;
        this.messages = messages;
    }

    /**
     * Opens the Aetheryte creation dialog for the {@code default} variation.
     */
    public void openCreateDialog(
            final Player player,
            final World world,
            final int blockX,
            final int blockY,
            final int blockZ
    ) {
        openCreateDialog(player, world, blockX, blockY, blockZ, Aetheryte.DEFAULT_VARIATION);
    }

    /**
     * Opens the Aetheryte creation dialog asking the player to input a name for the specified variation (#37).
     */
    public void openCreateDialog(
            final Player player,
            final World world,
            final int blockX,
            final int blockY,
            final int blockZ,
            final String variationId
    ) {
        final String normalizedVar = Aetheryte.normalizeVariation(variationId);
        final AetheryteVariation variation = service.variationRegistry().getOrDefault(normalizedVar);
        final boolean isNetworkVariation = variation.isNetwork();
        final List<AetheryteNetwork> joinableNetworks = isNetworkVariation
                ? service.listJoinableNetworks(player, world.getUID())
                : List.of();
        if (isNetworkVariation && joinableNetworks.isEmpty()) {
            messages.send(player, MessageKey.ERROR_NO_JOINABLE_NETWORKS);
            service.playErrorSound(player, normalizedVar);
            return;
        }

        final String defaultName = messages.createDialogDefaultName(player);
        final EconomyProvider economy = service.economyProvider();
        final boolean bypassedCost = player.getGameMode() == GameMode.CREATIVE || Permissions.canBypassPlaceCost(player);
        final Map<String, Double> costVars = Map.of(
                "owned_count", (double) service.registry().countByOwner(player.getUniqueId()),
                "variation_count", (double) service.registry().countByOwnerAndVariation(player.getUniqueId(), normalizedVar)
        );

        final ActionButton confirmButton = ActionButton.builder(messages.buttonConfirmCreate(player))
                .width(BUTTON_WIDTH_SECONDARY)
                .action(DialogAction.customClick((view, audience) -> {
                    if (!(audience instanceof Player clicker)) {
                        return;
                    }
                    final String enteredName = view.getText(INPUT_KEY_NAME);
                    if (enteredName == null) {
                        messages.send(clicker, MessageKey.ERROR_INVALID_NAME);
                        return;
                    }
                    if (isNetworkVariation) {
                        final String selectedNetworkMode = view.getText(INPUT_KEY_NETWORK_MODE);
                        if (selectedNetworkMode == null || !selectedNetworkMode.startsWith(NETWORK_MODE_JOIN_PREFIX)) {
                            messages.send(clicker, MessageKey.ERROR_NETWORK_NOT_FOUND);
                            return;
                        }
                        final String rawNetId = selectedNetworkMode.substring(NETWORK_MODE_JOIN_PREFIX.length());
                        final UUID joinNetworkId;
                        try {
                            joinNetworkId = UUID.fromString(rawNetId);
                        } catch (final IllegalArgumentException e) {
                            messages.send(clicker, MessageKey.ERROR_NETWORK_NOT_FOUND);
                            return;
                        }
                        final AetheryteVisibility effectiveVisibility = service.registry().getNetworkById(joinNetworkId)
                                .map(AetheryteNetwork::visibility)
                                .orElse(AetheryteVisibility.PUBLIC);
                        service.placeAetheryteInNetwork(
                                clicker, world, blockX, blockY, blockZ,
                                enteredName, normalizedVar, effectiveVisibility, joinNetworkId, null);
                    } else {
                        final AetheryteVisibility selectedVisibility = AetheryteVisibility.fromString(
                                view.getText(INPUT_KEY_VISIBILITY));
                        service.placeAetheryte(
                                clicker, world, blockX, blockY, blockZ, enteredName, normalizedVar, selectedVisibility);
                    }
                }, SINGLE_USE_CALLBACK))
                .build();

        final ActionButton cancelButton = ActionButton.builder(messages.buttonCancel(player))
                .width(BUTTON_WIDTH_SECONDARY)
                .build();

        final AetheryteVisibility previewVisibility = isNetworkVariation
                ? joinableNetworks.getFirst().visibility()
                : AetheryteVisibility.PUBLIC;
        final CostQuote defaultPlacementQuote = service.computePlacementCostQuote(
                player, player.getUniqueId(), normalizedVar, previewVisibility, economy);
        final Component costLine;
        if (!defaultPlacementQuote.isFree()) {
            final Component costDisplay = CostFormatter.format(player, defaultPlacementQuote, economy, messages, true);
            if (bypassedCost) {
                costLine = Component.newline().append(messages.get(player, MessageKey.DIALOG_CREATE_COST_BYPASSED, Placeholder.component("cost", costDisplay)));
            } else {
                costLine = Component.newline().append(messages.get(player, MessageKey.DIALOG_CREATE_COST, Placeholder.component("cost", costDisplay)));
            }
        } else {
            costLine = Component.empty();
        }

        final List<DialogInput> inputs = new ArrayList<>();
        inputs.add(DialogInput.text(INPUT_KEY_NAME, messages.createDialogNameInputLabel(player))
                .width(BUTTON_WIDTH_PRIMARY)
                .maxLength(32)
                .initial(defaultName)
                .build());

        final Component networkCostLine;
        if (isNetworkVariation) {
            final CostBundle joinBundle = new CostBundle(variation.network().cost().join());
            final CostQuote joinExtraQuote = joinBundle.isEmpty()
                    ? CostQuote.FREE
                    : joinBundle.quote(player, costVars, economy);

            final List<SingleOptionDialogInput.OptionEntry> networkOptions = new ArrayList<>(joinableNetworks.size());
            boolean first = true;
            for (final AetheryteNetwork net : joinableNetworks) {
                Component joinLabel = messages.get(
                        player,
                        MessageKey.DIALOG_CREATE_NETWORK_OPTION_JOIN,
                        Placeholder.unparsed("name", net.name()),
                        Placeholder.component("visibility", messages.visibilityLabel(player, net.visibility()))
                );
                if (!bypassedCost && !joinExtraQuote.isFree()) {
                    joinLabel = joinLabel.append(Component.text(" (+"))
                            .append(CostFormatter.format(player, joinExtraQuote, economy, messages, true))
                            .append(Component.text(")"));
                }
                networkOptions.add(SingleOptionDialogInput.OptionEntry.create(
                        NETWORK_MODE_JOIN_PREFIX + net.id(),
                        joinLabel,
                        first
                ));
                first = false;
            }

            inputs.add(DialogInput.singleOption(
                            INPUT_KEY_NETWORK_MODE,
                            messages.get(player, MessageKey.DIALOG_CREATE_NETWORK_MODE_LABEL),
                            networkOptions)
                    .width(BUTTON_WIDTH_PRIMARY)
                    .build());

            Component extraLines = Component.newline().append(messages.get(player, MessageKey.DIALOG_CREATE_NETWORK_INFO));
            if (!joinExtraQuote.isFree()) {
                final Component joinDisplay = CostFormatter.format(player, joinExtraQuote, economy, messages, true);
                extraLines = extraLines.append(Component.newline()).append(messages.get(
                        player,
                        MessageKey.DIALOG_CREATE_NETWORK_COST,
                        Placeholder.component("create_cost", Component.text("-")),
                        Placeholder.component("join_cost", joinDisplay)
                ));
            }
            networkCostLine = extraLines;
        } else {
            inputs.add(DialogInput.singleOption(
                            INPUT_KEY_VISIBILITY,
                            messages.get(player, MessageKey.DIALOG_CREATE_VISIBILITY_LABEL),
                            buildVisibilityOptions(player, normalizedVar, AetheryteVisibility.PUBLIC, !bypassedCost, economy))
                    .width(BUTTON_WIDTH_PRIMARY)
                    .build());
            networkCostLine = Component.empty();
        }

        final List<Component> bypassReasons = service.collectPlacementBypassReasons(
                player,
                world,
                blockX,
                blockY,
                blockZ,
                normalizedVar
        );
        final Component bypassLine = bypassReasons.isEmpty()
                ? Component.empty()
                : Component.newline().append(messages.createDialogBypassNotice(player, bypassReasons));

        final Component bodyComponent = messages.createDialogDescription(
                        player,
                        service.resolveDimensionDisplayComponent(world.getName()),
                        blockX,
                        blockY,
                        blockZ
                )
                .append(Component.newline())
                .append(messages.createDialogVariation(player, variation.displayName()))
                .append(costLine)
                .append(networkCostLine)
                .append(bypassLine);

        final Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(messages.createDialogTitle(player))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(List.of(DialogBody.plainMessage(bodyComponent)))
                        .inputs(inputs)
                        .build())
                .type(DialogType.confirmation(confirmButton, cancelButton))
        );

        player.showDialog(dialog);
    }

    /**
     * Opens the hierarchical warp destination and management dialog when interacting with an Aetheryte (#13, #54).
     */
    public void openWarpDialog(final Player player, final Aetheryte rawCurrent) {
        final Aetheryte current = service.refreshOwnerNameIfNeeded(player, rawCurrent);
        final WarpSortOrder initialSort = service.getWarpSortOrder(player);
        openWarpDialog(player, current, WarpDialogState.root(initialSort));
    }

    /**
     * Opens a specific screen of the hierarchical warp Dialog for {@code state} (#54, #55, #56, #57).
     */
    public void openWarpDialog(final Player player, final Aetheryte current, final WarpDialogState state) {
        final Optional<Aetheryte> latestOpt = service.registry().getById(current.id());
        if (latestOpt.isEmpty()) {
            player.closeDialog();
            messages.send(player, MessageKey.ERROR_NOT_FOUND);
            return;
        }
        final Aetheryte latestCurrent = latestOpt.get();
        switch (state.mode()) {
            case ROOT -> showRootMenu(player, latestCurrent, state);
            case NETWORK, WORLD, FAVORITES, ALL -> showDestinationList(player, latestCurrent, state);
            case OPERATIONS -> openOperationsDialog(player, latestCurrent, state);
        }
    }

    private void showRootMenu(final Player player, final Aetheryte current, final WarpDialogState state) {
        final Map<UUID, String> ownerNameCache = new HashMap<>();
        final String currentOwner = service.resolveOwnerName(current, player, ownerNameCache);
        final String currentWorldName = AetheryteService.resolveWorldName(current.worldId(), current.worldName());
        final Component currentWorldDisplay = service.resolveDimensionDisplayComponent(currentWorldName);
        final Component header = messages.warpDialogHeader(player, current, currentOwner, currentWorldDisplay);

        final List<ActionButton> buttons;
        final Component body;
        if (!service.isDimensionUseAllowed(player, currentWorldName)) {
            buttons = List.of(createOperationsButton(player, current, state));
            body = messages.get(
                    player,
                    MessageKey.DIALOG_WARP_BODY_DIMENSION_DISABLED,
                    Placeholder.component("world", currentWorldDisplay)
            );
        } else {
            final List<Aetheryte> allAetherytes = service.registry().getAll();
            final RootMenuPlan plan = planRootMenu(player, current, allAetherytes);
            buttons = buildRootButtons(player, current, state, plan);
            body = buildRootMenuBody(
                    player,
                    current,
                    currentOwner,
                    currentWorldDisplay,
                    currentWorldName,
                    allAetherytes.size(),
                    plan.totalReachableCount(),
                    plan.reachableDestinations()
            );
        }

        final Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(messages.warpDialogTitle(player, current))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .body(List.of(
                                DialogBody.plainMessage(header),
                                DialogBody.plainMessage(body)
                        ))
                        .build())
                .type(DialogType.multiAction(buttons)
                        .columns(1)
                        .exitAction(createCloseActionButton(player))
                        .build())
        );
        player.showDialog(dialog);
    }

    record RootMenuPlan(
            List<NetworkGroup> networkGroups,
            List<WorldGroup> worldGroups,
            int reachableFavoritesCount,
            int normalReachableCount,
            int totalReachableCount,
            List<Aetheryte> reachableDestinations
    ) {
    }

    RootMenuPlan planRootMenu(
            final Player player,
            final Aetheryte current,
            final List<Aetheryte> allAetherytes
    ) {
        final Map<UUID, Boolean> worldRouteCache = new HashMap<>();
        final Set<UUID> favorites = service.getFavorites(player);

        final List<AetheryteNetwork> hostedNetworks = current.networkId() == null
                ? service.registry().getNetworksByHub(current.id())
                : List.of();
        final Set<UUID> excludedNetworkIds = extractNetworkIds(hostedNetworks);
        final List<NetworkGroup> networkGroups = hostedNetworks.isEmpty()
                ? List.of()
                : WarpDestinationQuery.groupHostedNetworks(
                        current,
                        allAetherytes,
                        hostedNetworks,
                        dest -> isDestinationSelectable(player, current, dest, worldRouteCache)
                );
        final List<WorldGroup> worldGroups = WarpDestinationQuery.groupReachableWorlds(
                current,
                allAetherytes,
                dest -> isDestinationSelectable(player, current, dest, worldRouteCache),
                AetheryteDialogFactory::resolveWorldEnvironment,
                service::resolveDimensionPlainName,
                excludedNetworkIds
        );

        int normalReachableCount = 0;
        for (final WorldGroup group : worldGroups) {
            normalReachableCount += group.destinationCount();
        }
        int networkReachableCount = 0;
        for (final NetworkGroup group : networkGroups) {
            networkReachableCount += group.destinationCount();
        }
        final int totalReachableCount = normalReachableCount + networkReachableCount;
        int reachableFavoritesCount = 0;
        final List<Aetheryte> reachableDestinations = totalReachableCount > 0 ? new ArrayList<>(totalReachableCount) : List.of();
        if (totalReachableCount > 0) {
            for (final Aetheryte dest : allAetherytes) {
                if (!dest.id().equals(current.id()) && isDestinationSelectable(player, current, dest, worldRouteCache)) {
                    reachableDestinations.add(dest);
                    if (favorites.contains(dest.id())) {
                        reachableFavoritesCount++;
                    }
                }
            }
        }
        return new RootMenuPlan(
                networkGroups,
                worldGroups,
                reachableFavoritesCount,
                normalReachableCount,
                totalReachableCount,
                List.copyOf(reachableDestinations)
        );
    }

    private List<ActionButton> buildRootButtons(
            final Player player,
            final Aetheryte current,
            final WarpDialogState state,
            final RootMenuPlan plan
    ) {
        final List<ActionButton> buttons = new ArrayList<>(plan.networkGroups().size() + plan.worldGroups().size() + 3);
        for (final NetworkGroup group : plan.networkGroups()) {
            buttons.add(ActionButton.builder(
                            messages.warpNetworkCategoryLabel(player, group.networkName(), group.destinationCount()))
                    .tooltip(messages.warpNetworkCategoryTooltip(player, group.networkName(), group.destinationCount()))
                    .width(BUTTON_WIDTH_PRIMARY)
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player clicker) {
                            openWarpDialog(clicker, current, state.toNetwork(group.networkId()));
                        }
                    }, SINGLE_USE_CALLBACK))
                    .build());
        }

        buttons.add(ActionButton.builder(
                        messages.get(player, MessageKey.DIALOG_WARP_CATEGORY_FAVORITES,
                                Placeholder.unparsed("count", Integer.toString(plan.reachableFavoritesCount()))))
                .tooltip(messages.get(player, MessageKey.DIALOG_WARP_CATEGORY_FAVORITES_TOOLTIP))
                .width(BUTTON_WIDTH_PRIMARY)
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player clicker) {
                        openWarpDialog(clicker, current, state.toFavorites());
                    }
                }, SINGLE_USE_CALLBACK))
                .build());

        for (final WorldGroup group : plan.worldGroups()) {
            final Component worldDisplay = service.resolveDimensionDisplayComponent(group.worldName());
            final Component label = messages.warpWorldCategoryLabel(
                    player,
                    group.environment(),
                    worldDisplay,
                    group.destinationCount(),
                    group.currentWorld()
            );
            final Component tooltip = buildWorldCategoryTooltip(player, worldDisplay, group);
            buttons.add(ActionButton.builder(label)
                    .tooltip(tooltip)
                    .width(BUTTON_WIDTH_PRIMARY)
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player clicker) {
                            openWarpDialog(clicker, current, state.toWorld(group.worldId()));
                        }
                    }, SINGLE_USE_CALLBACK))
                    .build());
        }

        if (plan.normalReachableCount() > 0) {
            buttons.add(ActionButton.builder(
                            messages.get(player, MessageKey.DIALOG_WARP_CATEGORY_ALL,
                                    Placeholder.unparsed("count", Integer.toString(plan.normalReachableCount()))))
                    .tooltip(messages.get(player, MessageKey.DIALOG_WARP_CATEGORY_ALL_TOOLTIP))
                    .width(BUTTON_WIDTH_PRIMARY)
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player clicker) {
                            openWarpDialog(clicker, current, state.toAll());
                        }
                    }, SINGLE_USE_CALLBACK))
                    .build());
        }

        buttons.add(createOperationsButton(player, current, state));
        return buttons;
    }

    List<Candidate> queryDestinationCandidates(
            final Player player,
            final Aetheryte current,
            final WarpDialogState state,
            final List<Aetheryte> allAetherytes
    ) {
        final Map<UUID, String> ownerNameCache = new HashMap<>();
        final Map<UUID, Boolean> worldRouteCache = new HashMap<>();
        final Set<UUID> favorites = service.getFavorites(player);
        final Set<UUID> excludedNetworkIds = current.networkId() == null
                ? extractNetworkIds(service.registry().getNetworksByHub(current.id()))
                : Set.of();

        return WarpDestinationQuery.queryCandidates(
                current,
                player.getUniqueId(),
                allAetherytes,
                state,
                favorites,
                dest -> isDestinationSelectable(player, current, dest, worldRouteCache),
                dest -> service.resolveOwnerName(dest, player, ownerNameCache),
                service::resolveDimensionPlainName,
                excludedNetworkIds
        );
    }

    private void showDestinationList(final Player player, final Aetheryte current, final WarpDialogState state) {
        final List<Aetheryte> allAetherytes = service.registry().getAll();
        final List<Candidate> candidates = queryDestinationCandidates(player, current, state, allAetherytes);
        final PageSlice<Candidate> slice = WarpDestinationQuery.paginate(
                candidates,
                state.page(),
                WarpDestinationQuery.DEFAULT_PAGE_SIZE
        );

        final Component scopeLabel = resolveScopeLabel(player, state, allAetherytes);
        Component body = messages.warpListHeader(
                player,
                scopeLabel,
                state.sortOrder(),
                state.searchQuery(),
                slice.page(),
                slice.totalPages(),
                slice.totalItems()
        );
        if (slice.items().isEmpty()) {
            final MessageKey emptyKey = (state.mode() == WarpDialogState.ViewMode.FAVORITES && state.searchQuery().isEmpty())
                    ? MessageKey.DIALOG_WARP_FAVORITE_EMPTY
                    : MessageKey.DIALOG_WARP_LIST_EMPTY_FILTERED;
            body = body.append(Component.newline()).append(messages.get(player, emptyKey));
        }

        final List<ActionButton> buttons = new ArrayList<>(slice.items().size() + 7);
        addListControlButtons(buttons, player, current, state);
        final List<Aetheryte> pageDestinations = slice.items().stream().map(Candidate::aetheryte).toList();
        final List<WarpEvaluation> evaluations = service.evaluateWarps(player, current, pageDestinations);
        final Locale playerLocale = messages.locale(player);
        for (int i = 0; i < slice.items().size(); i++) {
            final ActionButton destButton = buildDestinationButton(
                    player,
                    playerLocale,
                    current,
                    state,
                    slice.items().get(i),
                    evaluations.get(i)
            );
            if (destButton != null) {
                buttons.add(destButton);
            }
        }
        addListNavigationButtons(buttons, player, current, state, slice);

        final Component finalBody = body;
        final Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(messages.warpDialogTitle(player, current))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .body(List.of(DialogBody.plainMessage(finalBody)))
                        .inputs(List.of(
                                DialogInput.text(INPUT_KEY_SEARCH, messages.get(player, MessageKey.DIALOG_WARP_SEARCH_INPUT_LABEL))
                                        .width(BUTTON_WIDTH_PRIMARY)
                                        .maxLength(64)
                                        .initial(state.searchQuery())
                                        .build()
                        ))
                        .build())
                .type(DialogType.multiAction(buttons)
                        .columns(1)
                        .exitAction(createCloseActionButton(player))
                        .build())
        );

        player.showDialog(dialog);
    }

    private void addListControlButtons(
            final List<ActionButton> buttons,
            final Player player,
            final Aetheryte current,
            final WarpDialogState state
    ) {
        buttons.add(ActionButton.builder(messages.get(player, MessageKey.DIALOG_BUTTON_APPLY_SEARCH))
                .width(BUTTON_WIDTH_PRIMARY)
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player clicker) {
                        final String query = readSearchInput(view, state.searchQuery());
                        openWarpDialog(clicker, current, state.withSearchQuery(query));
                    }
                }, SINGLE_USE_CALLBACK))
                .build());

        if (!state.searchQuery().isEmpty()) {
            buttons.add(ActionButton.builder(messages.get(player, MessageKey.DIALOG_BUTTON_CLEAR_SEARCH))
                    .width(BUTTON_WIDTH_PRIMARY)
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player clicker) {
                            openWarpDialog(clicker, current, state.withSearchQuery(""));
                        }
                    }, SINGLE_USE_CALLBACK))
                    .build());
        }

        final Component sortLabel = messages.get(
                player,
                MessageKey.DIALOG_WARP_SORT_BUTTON,
                Placeholder.component("sort", messages.warpSortOrderLabel(player, state.sortOrder()))
        );
        buttons.add(ActionButton.builder(sortLabel)
                .tooltip(messages.get(player, MessageKey.DIALOG_WARP_SORT_TOOLTIP))
                .width(BUTTON_WIDTH_PRIMARY)
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player clicker) {
                        final String query = readSearchInput(view, state.searchQuery());
                        final WarpSortOrder nextSort = state.sortOrder().next();
                        service.setWarpSortOrder(clicker, nextSort);
                        openWarpDialog(clicker, current, state.withSearchQuery(query).withSortOrder(nextSort));
                    }
                }, SINGLE_USE_CALLBACK))
                .build());

        final MessageKey editModeKey = state.favoriteEditMode()
                ? MessageKey.DIALOG_WARP_FAVORITE_EDIT_ON
                : MessageKey.DIALOG_WARP_FAVORITE_EDIT_OFF;
        buttons.add(ActionButton.builder(messages.get(player, editModeKey))
                .tooltip(messages.get(player, MessageKey.DIALOG_WARP_FAVORITE_EDIT_TOOLTIP))
                .width(BUTTON_WIDTH_PRIMARY)
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player clicker) {
                        final String query = readSearchInput(view, state.searchQuery());
                        final WarpDialogState updated = preservePageIfQueryUnchanged(state, query)
                                .withFavoriteEditMode(!state.favoriteEditMode());
                        openWarpDialog(clicker, current, updated);
                    }
                }, SINGLE_USE_CALLBACK))
                .build());
    }

    private @Nullable ActionButton buildDestinationButton(
            final Player player,
            final Locale playerLocale,
            final Aetheryte current,
            final WarpDialogState state,
            final Candidate candidate,
            final WarpEvaluation evaluation
    ) {
        final Aetheryte destination = candidate.aetheryte();
        if (evaluation.denyReasonCode() == WarpEvaluation.DenyReason.NO_ROUTE) {
            return null;
        }
        final Component label = evaluation.allowed()
                ? messages.warpDestinationLabel(playerLocale, destination, candidate.ownerName(), evaluation.costDisplay(), candidate.favorite())
                : messages.warpDestinationUnavailableLabel(playerLocale, destination, candidate.ownerName(), evaluation.costDisplay(), candidate.favorite());
        final Component tooltip = buildDestinationTooltip(player, current, state, candidate, evaluation);

        return ActionButton.builder(label)
                .tooltip(tooltip)
                .width(BUTTON_WIDTH_PRIMARY)
                .action(DialogAction.customClick((view, audience) -> {
                    if (!(audience instanceof Player clicker)) {
                        return;
                    }
                    if (state.favoriteEditMode()) {
                        service.toggleFavorite(clicker, destination.id());
                        final String query = readSearchInput(view, state.searchQuery());
                        openWarpDialog(clicker, current, preservePageIfQueryUnchanged(state, query));
                    } else {
                        clicker.closeDialog();
                        service.warpToAetheryte(clicker, current, destination.id());
                    }
                }, SINGLE_USE_CALLBACK))
                .build();
    }

    Component buildRootMenuBody(
            final Player player,
            final Aetheryte current,
            final String currentOwner,
            final Component currentWorldDisplay,
            final String currentWorldName,
            final int totalAetherytesCount,
            final int totalReachableCount,
            final List<Aetheryte> reachableDestinations
    ) {
        final Component baseBody;
        if (totalAetherytesCount <= 1) {
            baseBody = messages.warpDialogBody(player, current, currentOwner, 0);
        } else if (totalReachableCount == 0) {
            baseBody = messages.get(
                    player,
                    MessageKey.DIALOG_WARP_BODY_NO_ROUTE,
                    Placeholder.unparsed("name", current.name()),
                    Placeholder.unparsed("owner", currentOwner)
            );
        } else {
            baseBody = messages.warpRootBody(player, current, currentOwner, currentWorldDisplay);
        }

        Component body = baseBody;
        if (!service.dimensionConfig().isUseAllowed(currentWorldName)) {
            body = body.append(Component.newline()).append(messages.get(
                    player,
                    MessageKey.DIALOG_WARP_BODY_DIMENSION_BYPASSED,
                    Placeholder.component("world", currentWorldDisplay)
            ));
        }
        if (totalReachableCount > 0) {
            final Optional<Component> costBypassReason = service.collectRootWarpCostBypassReason(
                    player,
                    current,
                    reachableDestinations
            );
            if (costBypassReason.isPresent()) {
                body = body.append(Component.newline()).append(messages.warpBodyCostBypassed(player, costBypassReason.get()));
            }
        }
        return body;
    }

    Component buildWorldCategoryTooltip(
            final Player player,
            final Component worldDisplay,
            final WorldGroup group
    ) {
        Component tooltip = messages.get(
                player,
                MessageKey.DIALOG_WARP_CATEGORY_WORLD_TOOLTIP,
                Placeholder.component("world", worldDisplay),
                Placeholder.unparsed("count", Integer.toString(group.destinationCount()))
        );
        if (Permissions.canBypassDimension(player) && !service.dimensionConfig().isUseAllowed(group.worldName())) {
            tooltip = tooltip.append(Component.newline()).append(messages.warpTooltipBypassed(
                    player,
                    List.of(messages.bypassReasonDimensionUse(player, worldDisplay))
            ));
        }
        return tooltip;
    }

    Component buildDestinationTooltip(
            final Player player,
            final Aetheryte current,
            final WarpDialogState state,
            final Candidate candidate,
            final WarpEvaluation evaluation
    ) {
        final Aetheryte destination = candidate.aetheryte();
        Component baseTooltip = messages.warpDestinationTooltip(
                player,
                current,
                destination,
                service.resolveDimensionDisplayComponent(destination.worldId(), destination.worldName()),
                candidate.ownerName(),
                evaluation.costDisplay(),
                evaluation.allowed(),
                evaluation.denyReason()
        );
        if (evaluation.allowed()) {
            final List<Component> bypassReasons = service.collectDestinationBypassReasons(
                    player,
                    current,
                    destination
            );
            if (!bypassReasons.isEmpty()) {
                baseTooltip = baseTooltip.append(Component.newline()).append(messages.warpTooltipBypassed(
                        player,
                        bypassReasons
                ));
            }
        }
        return state.favoriteEditMode()
                ? messages.get(player, MessageKey.DIALOG_WARP_TOOLTIP_FAVORITE_TOGGLE)
                        .append(Component.newline())
                        .append(baseTooltip)
                : baseTooltip;
    }

    private void addListNavigationButtons(
            final List<ActionButton> buttons,
            final Player player,
            final Aetheryte current,
            final WarpDialogState state,
            final PageSlice<Candidate> slice
    ) {
        if (slice.hasPrevious()) {
            buttons.add(ActionButton.builder(messages.get(player, MessageKey.DIALOG_BUTTON_PREV_PAGE))
                    .width(BUTTON_WIDTH_PRIMARY)
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player clicker) {
                            final String query = readSearchInput(view, state.searchQuery());
                            final WarpDialogState next = navigateListPage(state, query, slice.page() - 1);
                            openWarpDialog(clicker, current, next);
                        }
                    }, SINGLE_USE_CALLBACK))
                    .build());
        }

        if (slice.hasNext()) {
            buttons.add(ActionButton.builder(messages.get(player, MessageKey.DIALOG_BUTTON_NEXT_PAGE))
                    .width(BUTTON_WIDTH_PRIMARY)
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player clicker) {
                            final String query = readSearchInput(view, state.searchQuery());
                            final WarpDialogState next = navigateListPage(state, query, slice.page() + 1);
                            openWarpDialog(clicker, current, next);
                        }
                    }, SINGLE_USE_CALLBACK))
                    .build());
        }

        buttons.add(createBackToRootButton(player, current, state));
    }

    /**
     * Opens the dedicated operations and management dialog for {@code rawCurrent} (#57).
     */
    public void openOperationsDialog(final Player player, final Aetheryte rawCurrent, final WarpDialogState state) {
        final Optional<Aetheryte> latestOpt = service.registry().getById(rawCurrent.id());
        if (latestOpt.isEmpty()) {
            player.closeDialog();
            messages.send(player, MessageKey.ERROR_NOT_FOUND);
            return;
        }
        final Aetheryte current = latestOpt.get();
        final String ownerName = service.resolveOwnerName(current, player);
        final AetheryteVariation variation = service.variationRegistry().getOrDefault(current.variation());
        final boolean favorited = service.isFavorite(player, current.id());

        final List<ActionButton> buttons = new ArrayList<>();
        buttons.add(ActionButton.builder(messages.get(
                        player,
                        favorited ? MessageKey.DIALOG_BUTTON_FAVORITE_REMOVE : MessageKey.DIALOG_BUTTON_FAVORITE_ADD))
                .width(BUTTON_WIDTH_PRIMARY)
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player clicker) {
                        service.toggleFavorite(clicker, current.id());
                        openOperationsDialog(clicker, current, state);
                    }
                }, SINGLE_USE_CALLBACK))
                .build());

        if (AetheryteService.canRename(player, current)) {
            buttons.add(ActionButton.builder(messages.get(player, MessageKey.DIALOG_BUTTON_OPEN_RENAME))
                    .tooltip(messages.get(player, MessageKey.DIALOG_BUTTON_OPEN_RENAME_TOOLTIP))
                    .width(BUTTON_WIDTH_PRIMARY)
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player clicker) {
                            openRenameDialog(clicker, current, state);
                        }
                    }, SINGLE_USE_CALLBACK))
                    .build());
        }

        final @Nullable AetheryteNetwork network = service.networkFor(current);
        final Aetheryte effectiveCurrent = network != null
                ? current.withVisibility(network.visibility()).withInvitedPlayers(network.invitedPlayers())
                : current;

        if (current.networkId() == null
                && variation.canBeNetworkHub()
                && Permissions.canManageHub(player, player.getUniqueId(), current)) {
            final int hostedCount = service.registry().getNetworksByHub(current.id()).size();
            buttons.add(ActionButton.builder(messages.get(
                            player,
                            MessageKey.DIALOG_BUTTON_MANAGE_NETWORKS,
                            Placeholder.unparsed("count", Integer.toString(hostedCount))
                    ))
                    .tooltip(messages.get(player, MessageKey.DIALOG_BUTTON_MANAGE_NETWORKS_TOOLTIP))
                    .width(BUTTON_WIDTH_PRIMARY)
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player clicker) {
                            openHubNetworksDialog(clicker, current, state);
                        }
                    }, SINGLE_USE_CALLBACK))
                    .build());
        }

        if (service.canManageVisibilityOrNetwork(player, current)) {
            buttons.add(ActionButton.builder(messages.get(player, MessageKey.DIALOG_BUTTON_VISIBILITY))
                    .tooltip(messages.get(player, MessageKey.DIALOG_BUTTON_VISIBILITY_TOOLTIP))
                    .width(BUTTON_WIDTH_PRIMARY)
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player clicker) {
                            openVisibilityDialog(clicker, current, state);
                        }
                    }, SINGLE_USE_CALLBACK))
                    .build());
            if (network != null) {
                buttons.add(ActionButton.builder(messages.get(player, MessageKey.DIALOG_BUTTON_RENAME_NETWORK))
                        .tooltip(messages.get(player, MessageKey.DIALOG_BUTTON_RENAME_NETWORK_TOOLTIP))
                        .width(BUTTON_WIDTH_PRIMARY)
                        .action(DialogAction.customClick((view, audience) -> {
                            if (audience instanceof Player clicker) {
                                openRenameNetworkDialog(clicker, current, network, state);
                            }
                        }, SINGLE_USE_CALLBACK))
                        .build());
            }
        }

        if (AetheryteService.canRemove(player, current)) {
            buttons.add(ActionButton.builder(messages.buttonOpenRemoveConfirm(player))
                    .tooltip(messages.buttonOpenRemoveConfirmTooltip(player))
                    .width(BUTTON_WIDTH_PRIMARY)
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player clicker) {
                            openRemoveConfirmDialog(clicker, current, state);
                        }
                    }, SINGLE_USE_CALLBACK))
                    .build());
        }

        if (AetheryteService.canAdmin(player)) {
            addAdminOperationButtons(buttons, player, current, state);
        }

        buttons.add(createBackToRootButton(player, current, state));

        final Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(messages.operationsDialogTitle(player, current))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .body(List.of(DialogBody.plainMessage(
                                messages.operationsDialogBody(
                                        player,
                                        effectiveCurrent,
                                        ownerName,
                                        service.resolveDimensionDisplayComponent(current.worldId(), current.worldName()),
                                        variation.displayName(),
                                        network != null ? network.name() : null
                                )
                        )))
                        .build())
                .type(DialogType.multiAction(buttons)
                        .columns(1)
                        .exitAction(createCloseActionButton(player))
                        .build())
        );
        player.showDialog(dialog);
    }

    private void addAdminOperationButtons(
            final List<ActionButton> buttons,
            final Player player,
            final Aetheryte current,
            final WarpDialogState state
    ) {
        buttons.add(ActionButton.builder(messages.get(player, MessageKey.DIALOG_BUTTON_ADMIN_TRANSFER))
                .tooltip(messages.get(player, MessageKey.DIALOG_BUTTON_ADMIN_TRANSFER_TOOLTIP))
                .width(BUTTON_WIDTH_PRIMARY)
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player clicker) {
                        openTransferDialog(clicker, current, state);
                    }
                }, SINGLE_USE_CALLBACK))
                .build());

        buttons.add(ActionButton.builder(messages.get(player, MessageKey.DIALOG_BUTTON_ADMIN_VARIATION))
                .tooltip(messages.get(player, MessageKey.DIALOG_BUTTON_ADMIN_VARIATION_TOOLTIP))
                .width(BUTTON_WIDTH_PRIMARY)
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player clicker) {
                        openVariationDialog(clicker, current, state);
                    }
                }, SINGLE_USE_CALLBACK))
                .build());

        buttons.add(ActionButton.builder(messages.get(player, MessageKey.DIALOG_BUTTON_ADMIN_RESYNC))
                .tooltip(messages.get(player, MessageKey.DIALOG_BUTTON_ADMIN_RESYNC_TOOLTIP))
                .width(BUTTON_WIDTH_PRIMARY)
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player clicker) {
                        clicker.closeDialog();
                        service.resyncVisuals(clicker, current.id());
                    }
                }, SINGLE_USE_CALLBACK))
                .build());

        buttons.add(ActionButton.builder(messages.get(player, MessageKey.DIALOG_BUTTON_ADMIN_FORCE_REMOVE))
                .tooltip(messages.get(player, MessageKey.DIALOG_BUTTON_ADMIN_FORCE_REMOVE_TOOLTIP))
                .width(BUTTON_WIDTH_PRIMARY)
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player clicker) {
                        openForceRemoveConfirmDialog(clicker, current, state);
                    }
                }, SINGLE_USE_CALLBACK))
                .build());
    }

    /**
     * Opens a confirmation dialog before removing an Aetheryte.
     */
    public void openRemoveConfirmDialog(final Player player, final Aetheryte target) {
        openRemoveConfirmDialog(player, target, null);
    }

    private void openRemoveConfirmDialog(
            final Player player,
            final Aetheryte target,
            @Nullable final WarpDialogState returnState
    ) {
        final int hostedNets = target.networkId() == null ? service.registry().getNetworksByHub(target.id()).size() : 0;
        final int childCount = target.networkId() == null ? service.registry().countChildAetherytesByHub(target.id()) : 0;
        if (childCount > 0 && !AetheryteService.canAdmin(player)) {
            player.closeDialog();
            player.sendMessage(messages.errorHubHasChildNetworks(player, target.name(), hostedNets, childCount));
            service.playErrorSound(player, target.variation());
            return;
        }
        final ActionButton confirmRemove = ActionButton.builder(messages.buttonConfirmRemove(player))
                .width(BUTTON_WIDTH_SECONDARY)
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player clicker) {
                        clicker.closeDialog();
                        service.removeAetheryte(clicker, target.id(), true);
                    }
                }, SINGLE_USE_CALLBACK))
                .build();

        Component body = messages.removeConfirmDialogBody(player, target);
        if (childCount > 0) {
            body = body.append(messages.removeHubCascadeWarning(player, hostedNets, childCount));
        }
        showConfirmationDialog(
                player,
                messages.removeConfirmDialogTitle(player, target),
                body,
                List.of(),
                confirmRemove,
                target,
                returnState
        );
    }

    private void openForceRemoveConfirmDialog(
            final Player player,
            final Aetheryte target,
            final WarpDialogState returnState
    ) {
        final int hostedNets = target.networkId() == null ? service.registry().getNetworksByHub(target.id()).size() : 0;
        final int childCount = target.networkId() == null ? service.registry().countChildAetherytesByHub(target.id()) : 0;
        final ActionButton confirmForceRemove = ActionButton.builder(messages.get(player, MessageKey.DIALOG_BUTTON_CONFIRM_FORCE_REMOVE))
                .width(BUTTON_WIDTH_SECONDARY)
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player clicker) {
                        clicker.closeDialog();
                        service.removeAetheryte(clicker, target.id(), false);
                    }
                }, SINGLE_USE_CALLBACK))
                .build();

        Component body = messages.get(player, MessageKey.DIALOG_FORCE_REMOVE_BODY, Placeholder.unparsed("name", target.name()));
        if (childCount > 0) {
            body = body.append(messages.removeHubCascadeWarning(player, hostedNets, childCount));
        }
        showConfirmationDialog(
                player,
                messages.get(player, MessageKey.DIALOG_FORCE_REMOVE_TITLE, Placeholder.unparsed("name", target.name())),
                body,
                List.of(),
                confirmForceRemove,
                target,
                returnState
        );
    }

    private void openRenameDialog(
            final Player player,
            final Aetheryte target,
            final WarpDialogState returnState
    ) {
        final ActionButton confirmRename = ActionButton.builder(messages.get(player, MessageKey.DIALOG_BUTTON_CONFIRM_RENAME))
                .width(BUTTON_WIDTH_SECONDARY)
                .action(DialogAction.customClick((view, audience) -> {
                    if (!(audience instanceof Player clicker)) {
                        return;
                    }
                    clicker.closeDialog();
                    final String enteredName = view.getText(INPUT_KEY_NAME);
                    if (enteredName == null) {
                        messages.send(clicker, MessageKey.ERROR_INVALID_NAME);
                        return;
                    }
                    service.renameAetheryte(clicker, target.id(), enteredName);
                }, SINGLE_USE_CALLBACK))
                .build();

        showConfirmationDialog(
                player,
                messages.get(player, MessageKey.DIALOG_RENAME_TITLE),
                messages.get(player, MessageKey.DIALOG_RENAME_BODY, Placeholder.unparsed("name", target.name())),
                List.of(
                        DialogInput.text(INPUT_KEY_NAME, messages.get(player, MessageKey.DIALOG_RENAME_INPUT_LABEL))
                                .width(BUTTON_WIDTH_PRIMARY)
                                .maxLength(32)
                                .initial(target.name())
                                .build()
                ),
                confirmRename,
                target,
                returnState
        );
    }

    private void openTransferDialog(
            final Player player,
            final Aetheryte target,
            final WarpDialogState returnState
    ) {
        final String currentOwner = service.resolveOwnerName(target, player);
        final ActionButton confirmTransfer = ActionButton.builder(messages.get(player, MessageKey.DIALOG_BUTTON_CONFIRM_TRANSFER))
                .width(BUTTON_WIDTH_SECONDARY)
                .action(DialogAction.customClick((view, audience) -> {
                    if (!(audience instanceof Player clicker)) {
                        return;
                    }
                    clicker.closeDialog();
                    final String targetPlayer = view.getText(INPUT_KEY_TARGET_PLAYER);
                    if (targetPlayer == null || targetPlayer.isBlank()) {
                        messages.send(clicker, MessageKey.ERROR_PLAYER_NOT_FOUND, Placeholder.unparsed("player", ""));
                        return;
                    }
                    service.transferOwnership(clicker, target.id(), targetPlayer);
                }, SINGLE_USE_CALLBACK))
                .build();

        showConfirmationDialog(
                player,
                messages.get(player, MessageKey.DIALOG_TRANSFER_TITLE, Placeholder.unparsed("name", target.name())),
                messages.get(player, MessageKey.DIALOG_TRANSFER_BODY, Placeholder.unparsed("owner", currentOwner)),
                List.of(
                        DialogInput.text(INPUT_KEY_TARGET_PLAYER, messages.get(player, MessageKey.DIALOG_TRANSFER_INPUT_LABEL))
                                .width(BUTTON_WIDTH_PRIMARY)
                                .maxLength(32)
                                .initial("")
                                .build()
                ),
                confirmTransfer,
                target,
                returnState
        );
    }

    private void openVariationDialog(
            final Player player,
            final Aetheryte target,
            final WarpDialogState returnState
    ) {
        final Map<String, AetheryteVariation> variations = service.variationRegistry().all();
        final List<SingleOptionDialogInput.OptionEntry> options = new ArrayList<>(variations.size());
        for (final Map.Entry<String, AetheryteVariation> entry : variations.entrySet()) {
            final String id = entry.getKey();
            final Component display = MINI_MESSAGE.deserialize(entry.getValue().displayName())
                    .append(Component.text(" (" + id + ")"));
            options.add(SingleOptionDialogInput.OptionEntry.create(
                    id,
                    display,
                    id.equals(target.variation())
            ));
        }

        final ActionButton confirmVariation = ActionButton.builder(messages.get(player, MessageKey.DIALOG_BUTTON_CONFIRM_VARIATION))
                .width(BUTTON_WIDTH_SECONDARY)
                .action(DialogAction.customClick((view, audience) -> {
                    if (!(audience instanceof Player clicker)) {
                        return;
                    }
                    clicker.closeDialog();
                    final String selected = view.getText(INPUT_KEY_VARIATION);
                    if (selected == null || selected.isBlank()) {
                        return;
                    }
                    service.changeVariation(clicker, target.id(), selected);
                }, SINGLE_USE_CALLBACK))
                .build();

        showConfirmationDialog(
                player,
                messages.get(player, MessageKey.DIALOG_VARIATION_TITLE, Placeholder.unparsed("name", target.name())),
                messages.get(player, MessageKey.DIALOG_VARIATION_BODY, Placeholder.unparsed("variation", target.variation())),
                List.of(
                        DialogInput.singleOption(
                                        INPUT_KEY_VARIATION,
                                        messages.get(player, MessageKey.DIALOG_VARIATION_SELECT_LABEL),
                                        options
                                )
                                .width(BUTTON_WIDTH_PRIMARY)
                                .build()
                ),
                confirmVariation,
                target,
                returnState
        );
    }

    private void showConfirmationDialog(
            final Player player,
            final Component title,
            final Component body,
            final List<DialogInput> inputs,
            final ActionButton confirmButton,
            final Aetheryte target,
            @Nullable final WarpDialogState returnState
    ) {
        final ActionButton cancel = createCancelOrReturnButton(player, target, returnState);
        final Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title)
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .body(List.of(DialogBody.plainMessage(body)))
                        .inputs(inputs)
                        .build())
                .type(DialogType.confirmation(confirmButton, cancel))
        );
        player.showDialog(dialog);
    }

    private void showCustomConfirmationDialog(
            final Player player,
            final Component title,
            final Component body,
            final List<DialogInput> inputs,
            final ActionButton confirmButton,
            final ActionButton cancelButton
    ) {
        final Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title)
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .body(List.of(DialogBody.plainMessage(body)))
                        .inputs(inputs)
                        .build())
                .type(DialogType.confirmation(confirmButton, cancelButton))
        );
        player.showDialog(dialog);
    }

    private List<SingleOptionDialogInput.OptionEntry> buildVisibilityOptions(
            final Player player,
            final String variationId,
            final AetheryteVisibility selectedMode,
            final boolean includeCost,
            final EconomyProvider economy
    ) {
        final List<SingleOptionDialogInput.OptionEntry> options = new ArrayList<>(AetheryteVisibility.values().length);
        for (final AetheryteVisibility mode : AetheryteVisibility.values()) {
            Component optionLabel = messages.visibilityLabel(player, mode);
            if (includeCost) {
                final CostQuote modeQuote = service.computePlacementCostQuote(
                        player, player.getUniqueId(), variationId, mode, economy);
                if (!modeQuote.isFree()) {
                    final Component modeCostDisplay = CostFormatter.format(player, modeQuote, economy, messages, true);
                    optionLabel = optionLabel.append(Component.text(" (")).append(modeCostDisplay).append(Component.text(")"));
                }
            }
            options.add(SingleOptionDialogInput.OptionEntry.create(
                    mode.name(),
                    optionLabel,
                    mode == selectedMode
            ));
        }
        return options;
    }

    private ActionButton createOperationsButton(
            final Player player,
            final Aetheryte current,
            final WarpDialogState state
    ) {
        return ActionButton.builder(messages.get(player, MessageKey.DIALOG_BUTTON_OPEN_OPERATIONS))
                .tooltip(messages.get(player, MessageKey.DIALOG_BUTTON_OPEN_OPERATIONS_TOOLTIP))
                .width(BUTTON_WIDTH_PRIMARY)
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player clicker) {
                        openWarpDialog(clicker, current, state.toOperations());
                    }
                }, SINGLE_USE_CALLBACK))
                .build();
    }

    private void openRenameNetworkDialog(
            final Player player,
            final Aetheryte target,
            final AetheryteNetwork network,
            final WarpDialogState returnState
    ) {
        final ActionButton confirmRename = ActionButton.builder(messages.get(player, MessageKey.DIALOG_BUTTON_CONFIRM_RENAME))
                .width(BUTTON_WIDTH_SECONDARY)
                .action(DialogAction.customClick((view, audience) -> {
                    if (!(audience instanceof Player clicker)) {
                        return;
                    }
                    clicker.closeDialog();
                    final String enteredName = view.getText(INPUT_KEY_NETWORK_NAME);
                    if (enteredName == null) {
                        messages.send(clicker, MessageKey.ERROR_INVALID_NAME);
                        return;
                    }
                    service.renameNetwork(clicker, target.id(), enteredName);
                }, SINGLE_USE_CALLBACK))
                .build();

        showConfirmationDialog(
                player,
                messages.get(player, MessageKey.DIALOG_RENAME_NETWORK_TITLE),
                messages.get(player, MessageKey.DIALOG_RENAME_NETWORK_BODY, Placeholder.unparsed("name", network.name())),
                List.of(
                        DialogInput.text(INPUT_KEY_NETWORK_NAME, messages.get(player, MessageKey.DIALOG_RENAME_NETWORK_INPUT_LABEL))
                                .width(BUTTON_WIDTH_PRIMARY)
                                .maxLength(32)
                                .initial(network.name())
                                .build()
                ),
                confirmRename,
                target,
                returnState
        );
    }

    private void openHubNetworksDialog(
            final Player player,
            final Aetheryte rawHub,
            final WarpDialogState returnState
    ) {
        final Aetheryte hub = service.registry().getById(rawHub.id()).orElse(rawHub);
        final AetheryteVariation hubVar = service.variationFor(hub);
        final List<AetheryteNetwork> hosted = service.registry().getNetworksByHub(hub.id());
        final EconomyProvider economy = service.economyProvider();
        final boolean bypassedCost = player.getGameMode() == GameMode.CREATIVE || Permissions.canBypassPlaceCost(player);

        final List<ActionButton> buttons = new ArrayList<>(hosted.size() + 1);
        final CostBundle createBundle = new CostBundle(hubVar.network().cost().create());
        final CostQuote createQuote = (!bypassedCost && !createBundle.isEmpty())
                ? createBundle.quote(
                        player,
                        Map.of(
                                "owned_count", (double) service.registry().countByOwner(hub.ownerId()),
                                "variation_count", (double) service.registry().countByOwnerAndVariation(hub.ownerId(), hub.variation())
                        ),
                        economy)
                : CostQuote.FREE;
        Component createButtonLabel = messages.get(player, MessageKey.DIALOG_BUTTON_CREATE_NETWORK);
        if (!createQuote.isFree()) {
            createButtonLabel = createButtonLabel.append(Component.text(" ("))
                    .append(CostFormatter.format(player, createQuote, economy, messages, true))
                    .append(Component.text(")"));
        }
        buttons.add(ActionButton.builder(createButtonLabel)
                .width(BUTTON_WIDTH_PRIMARY)
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player clicker) {
                        openCreateNetworkDialog(clicker, hub, returnState);
                    }
                }, SINGLE_USE_CALLBACK))
                .build());

        for (final AetheryteNetwork net : hosted) {
            final int memberCount = service.registry().countAetherytesInNetwork(net.id());
            final Component entryLabel = messages.get(
                    player,
                    MessageKey.DIALOG_HUB_NETWORKS_ENTRY,
                    Placeholder.unparsed("name", net.name()),
                    Placeholder.component("visibility", messages.visibilityLabel(player, net.visibility())),
                    Placeholder.unparsed("members", Integer.toString(memberCount))
            );
            buttons.add(ActionButton.builder(entryLabel)
                    .width(BUTTON_WIDTH_PRIMARY)
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player clicker) {
                            openNetworkManageDialog(clicker, hub, net, returnState);
                        }
                    }, SINGLE_USE_CALLBACK))
                    .build());
        }

        Component body = messages.get(
                player,
                MessageKey.DIALOG_HUB_NETWORKS_BODY,
                Placeholder.unparsed("name", hub.name()),
                Placeholder.unparsed("count", Integer.toString(hosted.size()))
        );
        if (hosted.isEmpty()) {
            body = body.append(Component.newline()).append(messages.get(player, MessageKey.DIALOG_HUB_NETWORKS_EMPTY));
        }
        if (!createQuote.isFree()) {
            body = body.append(messages.get(
                    player,
                    MessageKey.DIALOG_HUB_NETWORKS_CREATE_COST,
                    Placeholder.component("cost", CostFormatter.format(player, createQuote, economy, messages, true))
            ));
        }

        final ActionButton backButton = ActionButton.builder(messages.get(player, MessageKey.DIALOG_BUTTON_BACK))
                .width(BUTTON_WIDTH_SECONDARY)
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player clicker) {
                        openOperationsDialog(clicker, hub, returnState);
                    }
                }, SINGLE_USE_CALLBACK))
                .build();

        final Component dialogBody = body;
        final Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(messages.get(
                                player,
                                MessageKey.DIALOG_HUB_NETWORKS_TITLE,
                                Placeholder.unparsed("name", hub.name())
                        ))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .body(List.of(DialogBody.plainMessage(dialogBody)))
                        .build())
                .type(DialogType.multiAction(buttons)
                        .columns(1)
                        .exitAction(backButton)
                        .build())
        );
        player.showDialog(dialog);
    }

    private void openCreateNetworkDialog(
            final Player player,
            final Aetheryte hub,
            final WarpDialogState returnState
    ) {
        final AetheryteVariation hubVar = service.variationFor(hub);
        final EconomyProvider economy = service.economyProvider();
        final boolean bypassedCost = player.getGameMode() == GameMode.CREATIVE || Permissions.canBypassPlaceCost(player);
        final CostBundle createBundle = new CostBundle(hubVar.network().cost().create());
        final CostQuote createQuote = (!bypassedCost && !createBundle.isEmpty())
                ? createBundle.quote(
                        player,
                        Map.of(
                                "owned_count", (double) service.registry().countByOwner(hub.ownerId()),
                                "variation_count", (double) service.registry().countByOwnerAndVariation(hub.ownerId(), hub.variation())
                        ),
                        economy)
                : CostQuote.FREE;

        final ActionButton confirmCreate = ActionButton.builder(messages.get(player, MessageKey.DIALOG_BUTTON_CREATE_NETWORK))
                .width(BUTTON_WIDTH_SECONDARY)
                .action(DialogAction.customClick((view, audience) -> {
                    if (!(audience instanceof Player clicker)) {
                        return;
                    }
                    clicker.closeDialog();
                    final String enteredName = view.getText(INPUT_KEY_NETWORK_NAME);
                    if (enteredName == null || enteredName.isBlank()) {
                        messages.send(clicker, MessageKey.ERROR_INVALID_NAME);
                        return;
                    }
                    final AetheryteVisibility selectedVisibility = AetheryteVisibility.fromString(
                            view.getText(INPUT_KEY_VISIBILITY));
                    service.createNetworkOnHub(clicker, hub.id(), enteredName, selectedVisibility);
                }, SINGLE_USE_CALLBACK))
                .build();

        final ActionButton backButton = ActionButton.builder(messages.get(player, MessageKey.DIALOG_BUTTON_BACK))
                .width(BUTTON_WIDTH_SECONDARY)
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player clicker) {
                        openHubNetworksDialog(clicker, hub, returnState);
                    }
                }, SINGLE_USE_CALLBACK))
                .build();

        Component body = messages.get(
                player,
                MessageKey.DIALOG_CREATE_NETWORK_DIALOG_BODY,
                Placeholder.unparsed("name", hub.name())
        );
        if (!createQuote.isFree()) {
            body = body.append(messages.get(
                    player,
                    MessageKey.DIALOG_HUB_NETWORKS_CREATE_COST,
                    Placeholder.component("cost", CostFormatter.format(player, createQuote, economy, messages, true))
            ));
        }

        showCustomConfirmationDialog(
                player,
                messages.get(player, MessageKey.DIALOG_CREATE_NETWORK_DIALOG_TITLE),
                body,
                List.of(
                        DialogInput.text(INPUT_KEY_NETWORK_NAME, messages.get(player, MessageKey.DIALOG_CREATE_NETWORK_DIALOG_NAME_LABEL))
                                .width(BUTTON_WIDTH_PRIMARY)
                                .maxLength(32)
                                .initial("")
                                .build(),
                        DialogInput.singleOption(
                                        INPUT_KEY_VISIBILITY,
                                        messages.get(player, MessageKey.DIALOG_CREATE_VISIBILITY_LABEL),
                                        buildVisibilityOptions(player, hub.variation(), AetheryteVisibility.PUBLIC, false, economy))
                                .width(BUTTON_WIDTH_PRIMARY)
                                .build()
                ),
                confirmCreate,
                backButton
        );
    }

    private void openNetworkManageDialog(
            final Player player,
            final Aetheryte rawHub,
            final AetheryteNetwork rawNetwork,
            final WarpDialogState returnState
    ) {
        final Aetheryte hub = service.registry().getById(rawHub.id()).orElse(rawHub);
        final Optional<AetheryteNetwork> netOpt = service.registry().getNetworkById(rawNetwork.id());
        if (netOpt.isEmpty()) {
            player.closeDialog();
            messages.send(player, MessageKey.ERROR_NETWORK_NOT_FOUND);
            return;
        }
        final AetheryteNetwork network = netOpt.get();
        final int memberCount = service.registry().countAetherytesInNetwork(network.id());
        final String ownerName = service.resolvePlayerName(network.ownerId(), player);

        final List<ActionButton> buttons = new ArrayList<>();
        if (Permissions.canManageNetwork(player, player.getUniqueId(), network)) {
            buttons.add(ActionButton.builder(messages.get(player, MessageKey.DIALOG_BUTTON_RENAME_NETWORK))
                    .tooltip(messages.get(player, MessageKey.DIALOG_BUTTON_RENAME_NETWORK_TOOLTIP))
                    .width(BUTTON_WIDTH_PRIMARY)
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player clicker) {
                            openRenameNetworkByIdDialog(clicker, hub, network, returnState);
                        }
                    }, SINGLE_USE_CALLBACK))
                    .build());

            buttons.add(ActionButton.builder(messages.get(player, MessageKey.DIALOG_BUTTON_VISIBILITY))
                    .tooltip(messages.get(player, MessageKey.DIALOG_BUTTON_VISIBILITY_TOOLTIP))
                    .width(BUTTON_WIDTH_PRIMARY)
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player clicker) {
                            openNetworkVisibilityDialog(clicker, hub, network, returnState);
                        }
                    }, SINGLE_USE_CALLBACK))
                    .build());

            buttons.add(ActionButton.builder(messages.get(player, MessageKey.DIALOG_BUTTON_DELETE_NETWORK))
                    .width(BUTTON_WIDTH_PRIMARY)
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player clicker) {
                            openNetworkDeleteConfirmDialog(clicker, hub, network, returnState);
                        }
                    }, SINGLE_USE_CALLBACK))
                    .build());
        }

        final ActionButton backButton = ActionButton.builder(messages.get(player, MessageKey.DIALOG_BUTTON_BACK))
                .width(BUTTON_WIDTH_SECONDARY)
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player clicker) {
                        openHubNetworksDialog(clicker, hub, returnState);
                    }
                }, SINGLE_USE_CALLBACK))
                .build();

        final Component body = messages.get(
                player,
                MessageKey.DIALOG_MANAGE_NETWORK_BODY,
                Placeholder.unparsed("name", network.name()),
                Placeholder.unparsed("owner", ownerName),
                Placeholder.unparsed("hub", hub.name()),
                Placeholder.component("visibility", messages.visibilityLabel(player, network.visibility())),
                Placeholder.unparsed("members", Integer.toString(memberCount)),
                Placeholder.unparsed("invited", Integer.toString(network.invitedPlayers().size()))
        );

        final Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(messages.get(
                                player,
                                MessageKey.DIALOG_MANAGE_NETWORK_TITLE,
                                Placeholder.unparsed("name", network.name())
                        ))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .body(List.of(DialogBody.plainMessage(body)))
                        .build())
                .type(DialogType.multiAction(buttons)
                        .columns(1)
                        .exitAction(backButton)
                        .build())
        );
        player.showDialog(dialog);
    }

    private void openRenameNetworkByIdDialog(
            final Player player,
            final Aetheryte hub,
            final AetheryteNetwork network,
            final WarpDialogState returnState
    ) {
        final ActionButton confirmRename = ActionButton.builder(messages.get(player, MessageKey.DIALOG_BUTTON_CONFIRM_RENAME))
                .width(BUTTON_WIDTH_SECONDARY)
                .action(DialogAction.customClick((view, audience) -> {
                    if (!(audience instanceof Player clicker)) {
                        return;
                    }
                    clicker.closeDialog();
                    final String enteredName = view.getText(INPUT_KEY_NETWORK_NAME);
                    if (enteredName == null) {
                        messages.send(clicker, MessageKey.ERROR_INVALID_NAME);
                        return;
                    }
                    service.renameNetworkById(clicker, network.id(), enteredName);
                }, SINGLE_USE_CALLBACK))
                .build();

        final ActionButton backButton = ActionButton.builder(messages.get(player, MessageKey.DIALOG_BUTTON_BACK))
                .width(BUTTON_WIDTH_SECONDARY)
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player clicker) {
                        openNetworkManageDialog(clicker, hub, network, returnState);
                    }
                }, SINGLE_USE_CALLBACK))
                .build();

        showCustomConfirmationDialog(
                player,
                messages.get(player, MessageKey.DIALOG_RENAME_NETWORK_TITLE),
                messages.get(player, MessageKey.DIALOG_RENAME_NETWORK_BODY, Placeholder.unparsed("name", network.name())),
                List.of(
                        DialogInput.text(INPUT_KEY_NETWORK_NAME, messages.get(player, MessageKey.DIALOG_RENAME_NETWORK_INPUT_LABEL))
                                .width(BUTTON_WIDTH_PRIMARY)
                                .maxLength(32)
                                .initial(network.name())
                                .build()
                ),
                confirmRename,
                backButton
        );
    }

    private void openNetworkDeleteConfirmDialog(
            final Player player,
            final Aetheryte hub,
            final AetheryteNetwork rawNetwork,
            final WarpDialogState returnState
    ) {
        final AetheryteNetwork network = service.registry().getNetworkById(rawNetwork.id()).orElse(rawNetwork);
        final int memberCount = service.registry().countAetherytesInNetwork(network.id());
        final boolean admin = AetheryteService.canAdmin(player);
        if (memberCount > 0 && !admin) {
            player.closeDialog();
            player.sendMessage(messages.errorNetworkHasAetherytes(player, network.name(), memberCount));
            service.playErrorSound(player, hub.variation());
            return;
        }

        final ActionButton confirmDelete = ActionButton.builder(messages.get(player, MessageKey.DIALOG_BUTTON_CONFIRM_DELETE_NETWORK))
                .width(BUTTON_WIDTH_SECONDARY)
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player clicker) {
                        clicker.closeDialog();
                        service.deleteNetworkById(clicker, network.id(), memberCount > 0 && admin);
                    }
                }, SINGLE_USE_CALLBACK))
                .build();

        final ActionButton backButton = ActionButton.builder(messages.get(player, MessageKey.DIALOG_BUTTON_BACK))
                .width(BUTTON_WIDTH_SECONDARY)
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player clicker) {
                        openNetworkManageDialog(clicker, hub, network, returnState);
                    }
                }, SINGLE_USE_CALLBACK))
                .build();

        Component body = messages.get(
                player,
                MessageKey.DIALOG_DELETE_NETWORK_BODY,
                Placeholder.unparsed("name", network.name()),
                Placeholder.unparsed("members", Integer.toString(memberCount))
        );
        if (memberCount > 0) {
            body = body.append(messages.deleteNetworkCascadeWarning(player, memberCount));
        }

        showCustomConfirmationDialog(
                player,
                messages.get(player, MessageKey.DIALOG_DELETE_NETWORK_TITLE, Placeholder.unparsed("name", network.name())),
                body,
                List.of(),
                confirmDelete,
                backButton
        );
    }

    private void openNetworkVisibilityDialog(
            final Player player,
            final Aetheryte hub,
            final AetheryteNetwork rawNetwork,
            final WarpDialogState returnState
    ) {
        final Optional<AetheryteNetwork> netOpt = service.registry().getNetworkById(rawNetwork.id());
        if (netOpt.isEmpty()) {
            player.closeDialog();
            messages.send(player, MessageKey.ERROR_NETWORK_NOT_FOUND);
            return;
        }
        final AetheryteNetwork network = netOpt.get();
        showVisibilityMenu(
                player,
                network.name(),
                network.visibility(),
                network.invitedPlayers(),
                service.resolveNetworkVisibilityVariationId(network, hub.variation()),
                network.ownerId(),
                clicker -> openNetworkVisibilityDialog(clicker, hub, network, returnState),
                (clicker, mode) -> service.changeNetworkVisibilityById(clicker, network.id(), mode),
                clicker -> openNetworkInviteAddDialog(clicker, hub, network, returnState),
                clicker -> openNetworkInviteRemoveDialog(clicker, hub, network, returnState),
                clicker -> openNetworkManageDialog(clicker, hub, network, returnState)
        );
    }

    private void openNetworkInviteAddDialog(
            final Player player,
            final Aetheryte hub,
            final AetheryteNetwork network,
            final WarpDialogState returnState
    ) {
        showInviteAddMenu(
                player,
                network.name(),
                (clicker, targetPlayer) -> service.addNetworkInviteeById(clicker, network.id(), targetPlayer),
                clicker -> openNetworkVisibilityDialog(clicker, hub, network, returnState)
        );
    }

    private void openNetworkInviteRemoveDialog(
            final Player player,
            final Aetheryte hub,
            final AetheryteNetwork rawNetwork,
            final WarpDialogState returnState
    ) {
        final AetheryteNetwork network = service.registry().getNetworkById(rawNetwork.id()).orElse(rawNetwork);
        showInviteRemoveMenu(
                player,
                network.name(),
                network.invitedPlayers(),
                (clicker, inviteeId) -> service.removeNetworkInviteeById(clicker, network.id(), inviteeId),
                clicker -> openNetworkVisibilityDialog(clicker, hub, network, returnState)
        );
    }

    private void openVisibilityDialog(
            final Player player,
            final Aetheryte rawTarget,
            final WarpDialogState returnState
    ) {
        final Aetheryte target = service.registry().getById(rawTarget.id()).orElse(rawTarget);
        final @Nullable AetheryteNetwork network = service.networkFor(target);
        final AetheryteVisibility effectiveVisibility = network != null ? network.visibility() : target.visibility();
        final Set<UUID> effectiveInvitees = network != null ? network.invitedPlayers() : target.invitedPlayers();
        final String titleName = network != null ? network.name() : target.name();
        final String costVariationId = network != null
                ? service.resolveNetworkVisibilityVariationId(network, target.variation())
                : target.variation();
        final UUID costOwnerId = network != null ? network.ownerId() : target.ownerId();
        showVisibilityMenu(
                player,
                titleName,
                effectiveVisibility,
                effectiveInvitees,
                costVariationId,
                costOwnerId,
                clicker -> openVisibilityDialog(clicker, target, returnState),
                (clicker, mode) -> service.changeVisibility(clicker, target.id(), mode),
                clicker -> openInviteAddDialog(clicker, target, returnState),
                clicker -> openInviteRemoveDialog(clicker, target, returnState),
                clicker -> openOperationsDialog(clicker, target, returnState)
        );
    }

    private void showVisibilityMenu(
            final Player player,
            final String titleName,
            final AetheryteVisibility effectiveVisibility,
            final Set<UUID> effectiveInvitees,
            final String costVariationId,
            final UUID costOwnerId,
            final Consumer<Player> onRefresh,
            final BiConsumer<Player, AetheryteVisibility> onSelectMode,
            final Consumer<Player> onOpenInviteAdd,
            final Consumer<Player> onOpenInviteRemove,
            final Consumer<Player> onBack
    ) {
        final EconomyProvider economy = service.economyProvider();
        final boolean creative = player.getGameMode() == GameMode.CREATIVE;
        final boolean bypassCost = Permissions.canBypassPlaceCost(player);

        final List<ActionButton> buttons = new ArrayList<>();
        for (final AetheryteVisibility mode : AetheryteVisibility.values()) {
            final boolean isCurrent = effectiveVisibility == mode;
            final Component label = messages.visibilityOptionButtonLabel(player, mode, isCurrent);
            final Component tooltip = buildVisibilityModeTooltip(
                    player, costOwnerId, costVariationId, mode, isCurrent, creative, bypassCost, economy);
            buttons.add(ActionButton.builder(label)
                    .tooltip(tooltip)
                    .width(BUTTON_WIDTH_PRIMARY)
                    .action(DialogAction.customClick((view, audience) -> {
                        if (!(audience instanceof Player clicker)) {
                            return;
                        }
                        if (isCurrent) {
                            onRefresh.accept(clicker);
                            return;
                        }
                        clicker.closeDialog();
                        onSelectMode.accept(clicker, mode);
                    }, SINGLE_USE_CALLBACK))
                    .build());
        }

        buttons.add(ActionButton.builder(messages.get(player, MessageKey.DIALOG_BUTTON_INVITE_ADD))
                .width(BUTTON_WIDTH_PRIMARY)
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player clicker) {
                        onOpenInviteAdd.accept(clicker);
                    }
                }, SINGLE_USE_CALLBACK))
                .build());

        buttons.add(ActionButton.builder(messages.get(
                        player,
                        MessageKey.DIALOG_BUTTON_INVITE_REMOVE,
                        Placeholder.unparsed("count", Integer.toString(effectiveInvitees.size()))
                ))
                .width(BUTTON_WIDTH_PRIMARY)
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player clicker) {
                        onOpenInviteRemove.accept(clicker);
                    }
                }, SINGLE_USE_CALLBACK))
                .build());

        final ActionButton backButton = ActionButton.builder(messages.get(player, MessageKey.DIALOG_BUTTON_BACK))
                .width(BUTTON_WIDTH_SECONDARY)
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player clicker) {
                        onBack.accept(clicker);
                    }
                }, SINGLE_USE_CALLBACK))
                .build();

        final Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(messages.get(
                                player,
                                MessageKey.DIALOG_VISIBILITY_TITLE,
                                Placeholder.unparsed("name", titleName)
                        ))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .body(List.of(DialogBody.plainMessage(messages.get(
                                player,
                                MessageKey.DIALOG_VISIBILITY_BODY,
                                Placeholder.component("visibility", messages.visibilityLabel(player, effectiveVisibility)),
                                Placeholder.unparsed("invited", Integer.toString(effectiveInvitees.size()))
                        ))))
                        .build())
                .type(DialogType.multiAction(buttons)
                        .columns(1)
                        .exitAction(backButton)
                        .build())
        );
        player.showDialog(dialog);
    }

    private Component buildVisibilityModeTooltip(
            final Player player,
            final UUID ownerId,
            final String variationId,
            final AetheryteVisibility mode,
            final boolean isCurrent,
            final boolean creative,
            final boolean bypassCost,
            final EconomyProvider economy
    ) {
        final Component modeLabel = messages.visibilityLabel(player, mode);
        if (!isCurrent && !creative && !bypassCost) {
            final CostQuote targetQuote = service.computePlacementCostQuote(
                    player, ownerId, variationId, mode, economy);
            if (!targetQuote.isFree()) {
                final Component costText = CostFormatter.format(
                        messages.locale(player), targetQuote, economy, messages, false);
                return messages.get(
                        player,
                        MessageKey.DIALOG_VISIBILITY_TOOLTIP_CHARGE,
                        Placeholder.component("mode", modeLabel),
                        Placeholder.component("cost", costText)
                );
            }
        }
        return messages.get(
                player,
                MessageKey.DIALOG_VISIBILITY_TOOLTIP_NO_CHANGE,
                Placeholder.component("mode", modeLabel)
        );
    }

    private void openInviteAddDialog(
            final Player player,
            final Aetheryte target,
            final WarpDialogState returnState
    ) {
        final @Nullable AetheryteNetwork network = service.networkFor(target);
        final String displayName = network != null ? network.name() : target.name();
        showInviteAddMenu(
                player,
                displayName,
                (clicker, targetPlayer) -> service.addInvitee(clicker, target.id(), targetPlayer),
                clicker -> openVisibilityDialog(clicker, target, returnState)
        );
    }

    private void showInviteAddMenu(
            final Player player,
            final String displayName,
            final BiConsumer<Player, String> onInvite,
            final Consumer<Player> onBack
    ) {
        final ActionButton confirmInvite = ActionButton.builder(messages.get(player, MessageKey.DIALOG_BUTTON_INVITE_ADD))
                .width(BUTTON_WIDTH_SECONDARY)
                .action(DialogAction.customClick((view, audience) -> {
                    if (!(audience instanceof Player clicker)) {
                        return;
                    }
                    clicker.closeDialog();
                    final String targetPlayer = view.getText(INPUT_KEY_TARGET_PLAYER);
                    if (targetPlayer == null || targetPlayer.isBlank()) {
                        messages.send(clicker, MessageKey.ERROR_PLAYER_NOT_FOUND, Placeholder.unparsed("player", ""));
                        return;
                    }
                    onInvite.accept(clicker, targetPlayer);
                }, SINGLE_USE_CALLBACK))
                .build();

        final ActionButton cancelButton = ActionButton.builder(messages.buttonCancel(player))
                .width(BUTTON_WIDTH_SECONDARY)
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player clicker) {
                        onBack.accept(clicker);
                    }
                }, SINGLE_USE_CALLBACK))
                .build();

        showCustomConfirmationDialog(
                player,
                messages.get(player, MessageKey.DIALOG_INVITE_ADD_TITLE, Placeholder.unparsed("name", displayName)),
                messages.get(player, MessageKey.DIALOG_INVITE_ADD_BODY, Placeholder.unparsed("name", displayName)),
                List.of(
                        DialogInput.text(INPUT_KEY_TARGET_PLAYER, messages.get(player, MessageKey.DIALOG_INVITE_ADD_INPUT_LABEL))
                                .width(BUTTON_WIDTH_PRIMARY)
                                .maxLength(32)
                                .initial("")
                                .build()
                ),
                confirmInvite,
                cancelButton
        );
    }

    private void openInviteRemoveDialog(
            final Player player,
            final Aetheryte rawTarget,
            final WarpDialogState returnState
    ) {
        final Aetheryte target = service.registry().getById(rawTarget.id()).orElse(rawTarget);
        final @Nullable AetheryteNetwork network = service.networkFor(target);
        final String displayName = network != null ? network.name() : target.name();
        final Set<UUID> effectiveInvitees = network != null ? network.invitedPlayers() : target.invitedPlayers();
        showInviteRemoveMenu(
                player,
                displayName,
                effectiveInvitees,
                (clicker, inviteeId) -> service.removeInvitee(clicker, target.id(), inviteeId),
                clicker -> openVisibilityDialog(clicker, target, returnState)
        );
    }

    private void showInviteRemoveMenu(
            final Player player,
            final String displayName,
            final Set<UUID> effectiveInvitees,
            final BiConsumer<Player, UUID> onRemove,
            final Consumer<Player> onBack
    ) {
        final record InviteeOption(UUID id, String name) {
        }
        final List<InviteeOption> invitees = new ArrayList<>(effectiveInvitees.size());
        for (final UUID inviteeId : effectiveInvitees) {
            invitees.add(new InviteeOption(inviteeId, service.resolvePlayerName(inviteeId, player)));
        }
        invitees.sort(Comparator.comparing(InviteeOption::name, String.CASE_INSENSITIVE_ORDER));

        final List<ActionButton> buttons = new ArrayList<>(invitees.size());
        for (final InviteeOption invitee : invitees) {
            buttons.add(ActionButton.builder(Component.text("✖ " + invitee.name()))
                    .width(BUTTON_WIDTH_PRIMARY)
                    .action(DialogAction.customClick((view, audience) -> {
                        if (!(audience instanceof Player clicker)) {
                            return;
                        }
                        clicker.closeDialog();
                        onRemove.accept(clicker, invitee.id());
                    }, SINGLE_USE_CALLBACK))
                    .build());
        }

        final ActionButton backButton = ActionButton.builder(messages.get(player, MessageKey.DIALOG_BUTTON_BACK))
                .width(BUTTON_WIDTH_SECONDARY)
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player clicker) {
                        onBack.accept(clicker);
                    }
                }, SINGLE_USE_CALLBACK))
                .build();

        final Component body = invitees.isEmpty()
                ? messages.get(player, MessageKey.DIALOG_INVITE_EMPTY)
                : messages.get(player, MessageKey.DIALOG_INVITE_REMOVE_BODY, Placeholder.unparsed("name", displayName));

        final Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(messages.get(
                                player,
                                MessageKey.DIALOG_INVITE_REMOVE_TITLE,
                                Placeholder.unparsed("name", displayName)
                        ))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .body(List.of(DialogBody.plainMessage(body)))
                        .build())
                .type(buttons.isEmpty()
                        ? DialogType.notice(backButton)
                        : DialogType.multiAction(buttons)
                                .columns(1)
                                .exitAction(backButton)
                                .build())
        );
        player.showDialog(dialog);
    }

    private ActionButton createBackToRootButton(
            final Player player,
            final Aetheryte current,
            final WarpDialogState state
    ) {
        return ActionButton.builder(messages.get(player, MessageKey.DIALOG_BUTTON_BACK))
                .width(BUTTON_WIDTH_PRIMARY)
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player clicker) {
                        openWarpDialog(clicker, current, state.toRoot());
                    }
                }, SINGLE_USE_CALLBACK))
                .build();
    }

    private ActionButton createCloseActionButton(final Player player) {
        return ActionButton.builder(messages.buttonClose(player))
                .width(BUTTON_WIDTH_SECONDARY)
                .action(DialogAction.customClick((view, audience) -> audience.closeDialog(), SINGLE_USE_CALLBACK))
                .build();
    }

    private ActionButton createCancelOrReturnButton(
            final Player player,
            final Aetheryte target,
            @Nullable final WarpDialogState returnState
    ) {
        return ActionButton.builder(messages.buttonCancel(player))
                .width(BUTTON_WIDTH_SECONDARY)
                .action(DialogAction.customClick((view, audience) -> {
                    if (returnState != null && audience instanceof Player clicker) {
                        openOperationsDialog(clicker, target, returnState);
                    } else {
                        audience.closeDialog();
                    }
                }, SINGLE_USE_CALLBACK))
                .build();
    }

    private boolean isDestinationSelectable(
            final Player player,
            final Aetheryte current,
            final Aetheryte destination,
            final Map<UUID, Boolean> worldRouteCache
    ) {
        return service.canAccessDestination(player, destination)
                && service.isNetworkRoutable(current, destination)
                && isWorldReachable(player, current, destination, worldRouteCache);
    }

    private boolean isWorldReachable(
            final Player player,
            final Aetheryte current,
            final Aetheryte destination,
            final Map<UUID, Boolean> worldRouteCache
    ) {
        final Boolean cached = worldRouteCache.get(destination.worldId());
        if (cached != null) {
            return cached;
        }
        final WarpEvaluation eval = service.evaluateWarp(player, current, destination);
        final boolean reachable = eval.denyReasonCode() != WarpEvaluation.DenyReason.NO_ROUTE;
        worldRouteCache.put(destination.worldId(), reachable);
        return reachable;
    }

    Component resolveScopeLabel(
            final Player player,
            final WarpDialogState state,
            final List<Aetheryte> allAetherytes
    ) {
        return switch (state.mode()) {
            case FAVORITES -> messages.get(player, MessageKey.DIALOG_WARP_SCOPE_FAVORITES);
            case ALL -> messages.get(player, MessageKey.DIALOG_WARP_SCOPE_ALL);
            case NETWORK -> {
                final UUID targetNetworkId = state.targetNetworkId();
                if (targetNetworkId == null) {
                    yield Component.empty();
                }
                final String networkName = service.registry().getNetworkById(targetNetworkId)
                        .map(AetheryteNetwork::name)
                        .orElseGet(targetNetworkId::toString);
                yield messages.warpNetworkScopeLabel(player, networkName);
            }
            case WORLD -> {
                final UUID targetWorldId = state.targetWorldId();
                if (targetWorldId == null) {
                    yield Component.empty();
                }
                final World loaded = Bukkit.getServer() != null ? Bukkit.getWorld(targetWorldId) : null;
                if (loaded != null) {
                    yield service.resolveDimensionDisplayComponent(loaded.getName());
                }
                yield allAetherytes.stream()
                        .filter(a -> a.worldId().equals(targetWorldId))
                        .map(Aetheryte::worldName)
                        .findFirst()
                        .map(service::resolveDimensionDisplayComponent)
                        .orElseGet(() -> Component.text(targetWorldId.toString()));
            }
            default -> Component.empty();
        };
    }

    private static Set<UUID> extractNetworkIds(final List<AetheryteNetwork> hostedNetworks) {
        if (hostedNetworks.isEmpty()) {
            return Set.of();
        }
        return hostedNetworks.stream()
                .map(AetheryteNetwork::id)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static World.@Nullable Environment resolveWorldEnvironment(final UUID worldId) {
        if (Bukkit.getServer() == null) {
            return null;
        }
        final World world = Bukkit.getWorld(worldId);
        return world != null ? world.getEnvironment() : null;
    }

    private static String readSearchInput(final DialogResponseView view, final String fallback) {
        final String text = view.getText(INPUT_KEY_SEARCH);
        return text != null ? text.strip() : fallback;
    }

    private static WarpDialogState preservePageIfQueryUnchanged(final WarpDialogState state, final String newQuery) {
        return newQuery.equals(state.searchQuery()) ? state : state.withSearchQuery(newQuery);
    }

    /**
     * Applies {@code targetPage} only when {@code inputQuery} matches the active search query; otherwise resets to page 0.
     */
    static WarpDialogState navigateListPage(
            final WarpDialogState state,
            final String inputQuery,
            final int targetPage
    ) {
        if (inputQuery.equals(state.searchQuery())) {
            return state.withPage(targetPage);
        }
        return state.withSearchQuery(inputQuery);
    }
}
