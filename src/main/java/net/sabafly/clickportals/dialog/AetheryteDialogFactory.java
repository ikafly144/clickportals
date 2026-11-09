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
import net.kyori.adventure.text.event.ClickCallback;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import net.sabafly.clickportals.message.Messages;
import net.sabafly.clickportals.service.AetheryteService;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Paper Dialog API factory for Aetheryte creation, warp destination selection, and removal (#13).
 */
@SuppressWarnings("UnstableApiUsage")
public final class AetheryteDialogFactory {

    private static final String INPUT_KEY_NAME = "name";
    private static final ClickCallback.Options SINGLE_USE_CALLBACK = ClickCallback.Options.builder()
            .uses(1)
            .lifetime(Duration.ofMinutes(5))
            .build();

    private final AetheryteService service;

    public AetheryteDialogFactory(final AetheryteService service) {
        this.service = service;
    }

    /**
     * Opens the Aetheryte creation dialog asking the player to input a name.
     */
    public void openCreateDialog(
            final Player player,
            final World world,
            final int blockX,
            final int blockY,
            final int blockZ
    ) {
        final String defaultName = player.getName() + "のエーテライト";

        final ActionButton confirmButton = ActionButton.builder(Messages.buttonConfirmCreate())
                .width(150)
                .action(DialogAction.customClick((view, audience) -> {
                    if (!(audience instanceof Player clicker)) {
                        return;
                    }
                    final String enteredName = view.getText(INPUT_KEY_NAME);
                    if (enteredName == null) {
                        clicker.sendMessage(Messages.errorInvalidName());
                        return;
                    }
                    service.placeAetheryte(clicker, world, blockX, blockY, blockZ, enteredName);
                }, SINGLE_USE_CALLBACK))
                .build();

        final ActionButton cancelButton = ActionButton.builder(Messages.buttonCancel())
                .width(150)
                .build();

        final Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Messages.createDialogTitle())
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(List.of(
                                DialogBody.plainMessage(Messages.createDialogDescription(world.getName(), blockX, blockY, blockZ))
                        ))
                        .inputs(List.of(
                                DialogInput.text(INPUT_KEY_NAME, Messages.createDialogNameInputLabel())
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
     * Opens the warp destination selection and management dialog when interacting with an Aetheryte.
     */
    public void openWarpDialog(final Player player, final Aetheryte current) {
        final List<Aetheryte> allDestinations = service.registry().getAll().stream()
                .filter(a -> !a.id().equals(current.id()))
                .toList();

        final List<ActionButton> buttons = new ArrayList<>(allDestinations.size() + 1);
        for (final Aetheryte destination : allDestinations) {
            buttons.add(ActionButton.builder(Messages.warpDestinationLabel(destination))
                    .tooltip(Messages.warpDestinationTooltip(destination))
                    .width(200)
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player clicker) {
                            service.warpToAetheryte(clicker, destination.id());
                        }
                    }, SINGLE_USE_CALLBACK))
                    .build());
        }

        // Management button to remove this Aetheryte
        buttons.add(ActionButton.builder(Messages.buttonOpenRemoveConfirm())
                .tooltip(Messages.buttonOpenRemoveConfirmTooltip())
                .width(200)
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player clicker) {
                        openRemoveConfirmDialog(clicker, current);
                    }
                }, SINGLE_USE_CALLBACK))
                .build());

        final ActionButton closeButton = ActionButton.builder(Messages.buttonClose())
                .width(150)
                .build();

        final Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Messages.warpDialogTitle(current))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(List.of(
                                DialogBody.plainMessage(Messages.warpDialogBody(current, allDestinations.size()))
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
        final ActionButton confirmRemove = ActionButton.builder(Messages.buttonConfirmRemove())
                .width(150)
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player clicker) {
                        service.removeAetheryte(clicker, target.id());
                    }
                }, SINGLE_USE_CALLBACK))
                .build();

        final ActionButton cancel = ActionButton.builder(Messages.buttonCancel())
                .width(150)
                .build();

        final Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Messages.removeConfirmDialogTitle(target))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(List.of(
                                DialogBody.plainMessage(Messages.removeConfirmDialogBody(target))
                        ))
                        .build())
                .type(DialogType.confirmation(confirmRemove, cancel))
        );

        player.showDialog(dialog);
    }
}
