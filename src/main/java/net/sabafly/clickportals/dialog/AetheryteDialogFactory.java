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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import net.sabafly.clickportals.cost.CostBundle;
import net.sabafly.clickportals.cost.CostFormatter;
import net.sabafly.clickportals.cost.CostQuote;
import net.sabafly.clickportals.dialog.WarpDestinationQuery.Candidate;
import net.sabafly.clickportals.dialog.WarpDestinationQuery.PageSlice;
import net.sabafly.clickportals.dialog.WarpDestinationQuery.WorldGroup;
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
        final String defaultName = messages.createDialogDefaultName(player);

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
                    service.placeAetheryte(clicker, world, blockX, blockY, blockZ, enteredName, normalizedVar);
                }, SINGLE_USE_CALLBACK))
                .build();

        final ActionButton cancelButton = ActionButton.builder(messages.buttonCancel(player))
                .width(BUTTON_WIDTH_SECONDARY)
                .build();

        final CostBundle placementCostBundle = new CostBundle(variation.placement().cost());
        final Component costLine;
        if (!placementCostBundle.isEmpty()) {
            final Map<String, Double> costVars = Map.of(
                    "owned_count", (double) service.registry().countByOwner(player.getUniqueId()),
                    "variation_count", (double) service.registry().countByOwnerAndVariation(player.getUniqueId(), normalizedVar)
            );
            final CostQuote quote = placementCostBundle.quote(player, costVars, service.economyProvider());
            final Component costDisplay = CostFormatter.format(player, quote, service.economyProvider(), messages, true);
            final boolean bypassed = player.getGameMode() == GameMode.CREATIVE || Permissions.canBypassPlaceCost(player);
            if (bypassed) {
                costLine = Component.newline().append(messages.get(player, MessageKey.DIALOG_CREATE_COST_BYPASSED, Placeholder.component("cost", costDisplay)));
            } else {
                costLine = Component.newline().append(messages.get(player, MessageKey.DIALOG_CREATE_COST, Placeholder.component("cost", costDisplay)));
            }
        } else {
            costLine = Component.empty();
        }

        final Component bodyComponent = messages.createDialogDescription(player, world.getName(), blockX, blockY, blockZ)
                .append(Component.newline())
                .append(messages.createDialogVariation(player, variation.displayName()))
                .append(costLine);

        final Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(messages.createDialogTitle(player))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(List.of(DialogBody.plainMessage(bodyComponent)))
                        .inputs(List.of(
                                DialogInput.text(INPUT_KEY_NAME, messages.createDialogNameInputLabel(player))
                                        .width(BUTTON_WIDTH_PRIMARY)
                                        .maxLength(32)
                                        .initial(defaultName)
                                        .build()
                        ))
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
            case WORLD, FAVORITES, ALL -> showDestinationList(player, latestCurrent, state);
            case OPERATIONS -> openOperationsDialog(player, latestCurrent, state);
        }
    }

    private void showRootMenu(final Player player, final Aetheryte current, final WarpDialogState state) {
        final Map<UUID, String> ownerNameCache = new HashMap<>();
        final String currentOwner = service.resolveOwnerName(current, player, ownerNameCache);
        final List<Aetheryte> allAetherytes = service.registry().getAll();

        final Map<UUID, Boolean> worldRouteCache = new HashMap<>();
        final Set<UUID> favorites = service.getFavorites(player);

        final List<WorldGroup> worldGroups = WarpDestinationQuery.groupReachableWorlds(
                current,
                allAetherytes,
                dest -> isWorldReachable(player, current, dest, worldRouteCache),
                AetheryteDialogFactory::resolveWorldEnvironment
        );

        int totalReachableCount = 0;
        for (final WorldGroup group : worldGroups) {
            totalReachableCount += group.destinationCount();
        }
        int reachableFavoritesCount = 0;
        if (!favorites.isEmpty() && totalReachableCount > 0) {
            for (final Aetheryte dest : allAetherytes) {
                if (!dest.id().equals(current.id())
                        && favorites.contains(dest.id())
                        && isWorldReachable(player, current, dest, worldRouteCache)) {
                    reachableFavoritesCount++;
                }
            }
        }

        final List<ActionButton> buttons = buildRootButtons(player, current, state, worldGroups, reachableFavoritesCount, totalReachableCount);

        Component body = messages.warpRootBody(player, current, currentOwner);
        if (allAetherytes.size() <= 1) {
            body = body.append(Component.newline()).append(messages.warpDialogBody(player, current, currentOwner, 0));
        } else if (totalReachableCount == 0) {
            body = body.append(Component.newline()).append(messages.get(
                    player,
                    MessageKey.DIALOG_WARP_BODY_NO_ROUTE,
                    Placeholder.unparsed("name", current.name()),
                    Placeholder.unparsed("owner", currentOwner)
            ));
        }

        final Component finalBody = body;
        final Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(messages.warpDialogTitle(player, current))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .body(List.of(DialogBody.plainMessage(finalBody)))
                        .build())
                .type(DialogType.multiAction(buttons)
                        .columns(1)
                        .exitAction(createCloseActionButton(player))
                        .build())
        );
        player.showDialog(dialog);
    }

    private List<ActionButton> buildRootButtons(
            final Player player,
            final Aetheryte current,
            final WarpDialogState state,
            final List<WorldGroup> worldGroups,
            final int reachableFavoritesCount,
            final int totalReachableCount
    ) {
        final List<ActionButton> buttons = new ArrayList<>(worldGroups.size() + 3);
        buttons.add(ActionButton.builder(
                        messages.get(player, MessageKey.DIALOG_WARP_CATEGORY_FAVORITES,
                                Placeholder.unparsed("count", Integer.toString(reachableFavoritesCount))))
                .tooltip(messages.get(player, MessageKey.DIALOG_WARP_CATEGORY_FAVORITES_TOOLTIP))
                .width(BUTTON_WIDTH_PRIMARY)
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player clicker) {
                        openWarpDialog(clicker, current, state.toFavorites());
                    }
                }, SINGLE_USE_CALLBACK))
                .build());

        for (final WorldGroup group : worldGroups) {
            final Component label = messages.warpWorldCategoryLabel(
                    player,
                    group.environment(),
                    group.worldName(),
                    group.destinationCount(),
                    group.currentWorld()
            );
            final Component tooltip = messages.get(
                    player,
                    MessageKey.DIALOG_WARP_CATEGORY_WORLD_TOOLTIP,
                    Placeholder.unparsed("world", group.worldName()),
                    Placeholder.unparsed("count", Integer.toString(group.destinationCount()))
            );
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

        if (totalReachableCount > 0) {
            buttons.add(ActionButton.builder(
                            messages.get(player, MessageKey.DIALOG_WARP_CATEGORY_ALL,
                                    Placeholder.unparsed("count", Integer.toString(totalReachableCount))))
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

    private void showDestinationList(final Player player, final Aetheryte current, final WarpDialogState state) {
        final Map<UUID, String> ownerNameCache = new HashMap<>();
        final Map<UUID, Boolean> worldRouteCache = new HashMap<>();
        final Set<UUID> favorites = service.getFavorites(player);
        final List<Aetheryte> allAetherytes = service.registry().getAll();

        final List<Candidate> candidates = WarpDestinationQuery.queryCandidates(
                current,
                player.getUniqueId(),
                allAetherytes,
                state,
                favorites,
                dest -> isWorldReachable(player, current, dest, worldRouteCache),
                dest -> service.resolveOwnerName(dest, player, ownerNameCache)
        );
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
        final Component baseTooltip = messages.warpDestinationTooltip(
                player,
                current,
                destination,
                candidate.ownerName(),
                evaluation.costDisplay(),
                evaluation.allowed(),
                evaluation.denyReason()
        );
        final Component tooltip = state.favoriteEditMode()
                ? messages.get(player, MessageKey.DIALOG_WARP_TOOLTIP_FAVORITE_TOGGLE)
                        .append(Component.newline())
                        .append(baseTooltip)
                : baseTooltip;

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
                                messages.operationsDialogBody(player, current, ownerName, variation.displayName())
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
        final ActionButton confirmRemove = ActionButton.builder(messages.buttonConfirmRemove(player))
                .width(BUTTON_WIDTH_SECONDARY)
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player clicker) {
                        clicker.closeDialog();
                        service.removeAetheryte(clicker, target.id(), true);
                    }
                }, SINGLE_USE_CALLBACK))
                .build();

        showConfirmationDialog(
                player,
                messages.removeConfirmDialogTitle(player, target),
                messages.removeConfirmDialogBody(player, target),
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
        final ActionButton confirmForceRemove = ActionButton.builder(messages.get(player, MessageKey.DIALOG_BUTTON_CONFIRM_FORCE_REMOVE))
                .width(BUTTON_WIDTH_SECONDARY)
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player clicker) {
                        clicker.closeDialog();
                        service.removeAetheryte(clicker, target.id(), false);
                    }
                }, SINGLE_USE_CALLBACK))
                .build();

        showConfirmationDialog(
                player,
                messages.get(player, MessageKey.DIALOG_FORCE_REMOVE_TITLE, Placeholder.unparsed("name", target.name())),
                messages.get(player, MessageKey.DIALOG_FORCE_REMOVE_BODY, Placeholder.unparsed("name", target.name())),
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

    private Component resolveScopeLabel(
            final Player player,
            final WarpDialogState state,
            final List<Aetheryte> allAetherytes
    ) {
        return switch (state.mode()) {
            case FAVORITES -> messages.get(player, MessageKey.DIALOG_WARP_SCOPE_FAVORITES);
            case ALL -> messages.get(player, MessageKey.DIALOG_WARP_SCOPE_ALL);
            case WORLD -> {
                final UUID targetWorldId = state.targetWorldId();
                if (targetWorldId == null) {
                    yield Component.empty();
                }
                final World loaded = Bukkit.getServer() != null ? Bukkit.getWorld(targetWorldId) : null;
                if (loaded != null) {
                    yield Component.text(loaded.getName());
                }
                yield allAetherytes.stream()
                        .filter(a -> a.worldId().equals(targetWorldId))
                        .map(Aetheryte::worldName)
                        .findFirst()
                        .map(Component::text)
                        .orElseGet(() -> Component.text(targetWorldId.toString()));
            }
            default -> Component.empty();
        };
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
