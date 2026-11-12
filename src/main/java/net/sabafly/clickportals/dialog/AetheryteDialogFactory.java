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
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import net.sabafly.clickportals.cost.CostBundle;
import net.sabafly.clickportals.cost.CostFormatter;
import net.sabafly.clickportals.cost.CostQuote;
import net.sabafly.clickportals.message.MessageKey;
import net.sabafly.clickportals.message.Messages;
import net.sabafly.clickportals.service.AetheryteService;
import net.sabafly.clickportals.service.AetheryteService.WarpEvaluation;
import net.sabafly.clickportals.variation.AetheryteVariation;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Paper Dialog API factory for Aetheryte creation, warp destination selection, and removal (#13, #18, #19, #37).
 */
@SuppressWarnings("UnstableApiUsage")
public final class AetheryteDialogFactory {

    private static final String INPUT_KEY_NAME = "name";
    private static final ClickCallback.Options SINGLE_USE_CALLBACK = ClickCallback.Options.builder()
            .uses(1)
            .lifetime(Duration.ofMinutes(5))
            .build();

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
                .width(150)
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
                .width(150)
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
            costLine = Component.newline().append(messages.get(player, MessageKey.DIALOG_CREATE_COST, Placeholder.component("cost", costDisplay)));
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
                        .body(List.of(
                                DialogBody.plainMessage(bodyComponent)
                        ))
                        .inputs(List.of(
                                DialogInput.text(INPUT_KEY_NAME, messages.createDialogNameInputLabel(player))
                                        .width(240)
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
     * Opens the warp destination selection and management dialog when interacting with an Aetheryte (#13).
     */
    public void openWarpDialog(final Player player, final Aetheryte current) {
        final List<Aetheryte> allDestinations = service.registry().getAll().stream()
                .filter(a -> !a.id().equals(current.id()))
                .toList();

        final List<ActionButton> buttons = new ArrayList<>(allDestinations.size() + 1);
        for (final Aetheryte destination : allDestinations) {
            final WarpEvaluation evaluation = service.evaluateWarp(player, current, destination);
            final Component label = evaluation.allowed()
                    ? messages.warpDestinationLabel(player, destination, evaluation.costDisplay())
                    : messages.warpDestinationUnavailableLabel(player, destination, evaluation.costDisplay());
            final Component tooltip = messages.warpDestinationTooltip(
                    player,
                    current,
                    destination,
                    evaluation.costDisplay(),
                    evaluation.allowed(),
                    evaluation.denyReason()
            );

            buttons.add(ActionButton.builder(label)
                    .tooltip(tooltip)
                    .width(220)
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player clicker) {
                            service.warpToAetheryte(clicker, current, destination.id());
                        }
                    }, SINGLE_USE_CALLBACK))
                    .build());
        }

        if (AetheryteService.canRemove(player, current)) {
            buttons.add(ActionButton.builder(messages.buttonOpenRemoveConfirm(player))
                    .tooltip(messages.buttonOpenRemoveConfirmTooltip(player))
                    .width(220)
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player clicker) {
                            openRemoveConfirmDialog(clicker, current);
                        }
                    }, SINGLE_USE_CALLBACK))
                    .build());
        }

        final ActionButton closeButton = ActionButton.builder(messages.buttonClose(player))
                .width(150)
                .build();

        if (buttons.isEmpty()) {
            final Dialog noticeDialog = Dialog.create(factory -> factory.empty()
                    .base(DialogBase.builder(messages.warpDialogTitle(player, current))
                            .canCloseWithEscape(true)
                            .pause(false)
                            .afterAction(DialogBase.DialogAfterAction.CLOSE)
                            .body(List.of(
                                    DialogBody.plainMessage(messages.warpDialogBody(player, current, 0))
                            ))
                            .build())
                    .type(DialogType.notice(closeButton))
            );
            player.showDialog(noticeDialog);
            return;
        }

        final Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(messages.warpDialogTitle(player, current))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(List.of(
                                DialogBody.plainMessage(messages.warpDialogBody(player, current, allDestinations.size()))
                        ))
                        .build())
                .type(DialogType.multiAction(buttons)
                        .columns(1)
                        .exitAction(closeButton)
                        .build())
        );

        player.showDialog(dialog);
    }

    /**
     * Opens a confirmation dialog before removing an Aetheryte.
     */
    public void openRemoveConfirmDialog(final Player player, final Aetheryte target) {
        final ActionButton confirmRemove = ActionButton.builder(messages.buttonConfirmRemove(player))
                .width(150)
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player clicker) {
                        service.removeAetheryte(clicker, target.id());
                    }
                }, SINGLE_USE_CALLBACK))
                .build();

        final ActionButton cancel = ActionButton.builder(messages.buttonCancel(player))
                .width(150)
                .build();

        final Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(messages.removeConfirmDialogTitle(player, target))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(List.of(
                                DialogBody.plainMessage(messages.removeConfirmDialogBody(player, target))
                        ))
                        .build())
                .type(DialogType.confirmation(confirmRemove, cancel))
        );

        player.showDialog(dialog);
    }
}
