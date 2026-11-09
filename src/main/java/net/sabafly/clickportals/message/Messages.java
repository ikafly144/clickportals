package net.sabafly.clickportals.message;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.sabafly.clickportals.aetheryte.Aetheryte;

/**
 * Adventure Component factory for all player-facing messages and UI labels (#18).
 */
public final class Messages {

    private static final TextColor AETHERYTE_AQUA = TextColor.color(0x55FFFF);
    private static final TextColor ACCENT_GOLD = TextColor.color(0xFFAA00);

    private Messages() {
    }

    /**
     * Returns the standard chat prefix for ClickPortals messages.
     */
    public static Component prefix() {
        return Component.text()
                .append(Component.text("[", NamedTextColor.DARK_GRAY))
                .append(Component.text("ClickPortals", AETHERYTE_AQUA))
                .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                .build();
    }

    /**
     * Returns the display name component for the Aetheryte creation item.
     */
    public static Component aetheryteItemName() {
        return Component.text("エーテライト", AETHERYTE_AQUA)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true);
    }

    /**
     * Returns the lore lines for the Aetheryte creation item.
     */
    public static List<Component> aetheryteItemLore() {
        return List.of(
                Component.text("ブロックに向かって右クリックして", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("エーテライト（ワープポイント）を設置します。", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        );
    }

    /**
     * Formats the hologram text displayed above a placed Aetheryte.
     */
    public static Component hologramTitle(final String aetheryteName) {
        return Component.text()
                .append(Component.text("✦ ", AETHERYTE_AQUA))
                .append(Component.text(aetheryteName, NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text(" ✦", AETHERYTE_AQUA))
                .build();
    }

    /**
     * Returns the title for the Aetheryte creation dialog.
     */
    public static Component createDialogTitle() {
        return Component.text("エーテライトの設置", AETHERYTE_AQUA, TextDecoration.BOLD);
    }

    /**
     * Returns the description body for the Aetheryte creation dialog showing target coordinates.
     */
    public static Component createDialogDescription(final String worldName, final int x, final int y, final int z) {
        return Component.text()
                .append(Component.text("設置座標: ", NamedTextColor.GRAY))
                .append(Component.text(worldName + " (" + x + ", " + y + ", " + z + ")", NamedTextColor.WHITE))
                .build();
    }

    /**
     * Returns the label for the Aetheryte name input field.
     */
    public static Component createDialogNameInputLabel() {
        return Component.text("エーテライト名", NamedTextColor.AQUA);
    }

    /**
     * Returns the label for the confirm placement button.
     */
    public static Component buttonConfirmCreate() {
        return Component.text("設置する", NamedTextColor.GREEN, TextDecoration.BOLD);
    }

    /**
     * Returns the label for generic cancel buttons.
     */
    public static Component buttonCancel() {
        return Component.text("キャンセル", NamedTextColor.GRAY);
    }

    /**
     * Returns the label for generic close buttons.
     */
    public static Component buttonClose() {
        return Component.text("閉じる", NamedTextColor.GRAY);
    }

    /**
     * Returns the title for the warp destination selection dialog.
     */
    public static Component warpDialogTitle(final Aetheryte current) {
        return Component.text()
                .append(Component.text("✦ エーテライト: ", AETHERYTE_AQUA))
                .append(Component.text(current.name(), NamedTextColor.WHITE, TextDecoration.BOLD))
                .build();
    }

    /**
     * Returns the body message for the warp destination selection dialog.
     */
    public static Component warpDialogBody(final Aetheryte current, final int destinationCount) {
        if (destinationCount == 0) {
            return Component.text()
                    .append(Component.text("現在地: ", NamedTextColor.GRAY))
                    .append(Component.text(current.name(), NamedTextColor.WHITE))
                    .append(Component.newline())
                    .append(Component.text("ワープ可能な他のエーテライトがまだありません。", NamedTextColor.YELLOW))
                    .build();
        }
        return Component.text()
                .append(Component.text("ワープ先のエーテライトを選択してください。", NamedTextColor.GRAY))
                .build();
    }

    /**
     * Returns the button label for a warp destination entry.
     */
    public static Component warpDestinationLabel(final Aetheryte destination) {
        return Component.text()
                .append(Component.text("✦ ", AETHERYTE_AQUA))
                .append(Component.text(destination.name(), NamedTextColor.WHITE))
                .build();
    }

    /**
     * Returns the button tooltip for a warp destination entry showing world and coordinates.
     */
    public static Component warpDestinationTooltip(final Aetheryte destination) {
        return Component.text()
                .append(Component.text("ワールド: ", NamedTextColor.GRAY))
                .append(Component.text(destination.worldName(), ACCENT_GOLD))
                .append(Component.newline())
                .append(Component.text("座標: ", NamedTextColor.GRAY))
                .append(Component.text(
                        destination.blockX() + ", " + destination.blockY() + ", " + destination.blockZ(),
                        NamedTextColor.WHITE
                ))
                .append(Component.newline())
                .append(Component.text("クリックしてワープ", NamedTextColor.GREEN))
                .build();
    }

    /**
     * Returns the label for the button that opens the Aetheryte removal confirmation dialog.
     */
    public static Component buttonOpenRemoveConfirm() {
        return Component.text("このエーテライトを撤去", NamedTextColor.RED);
    }

    /**
     * Returns the tooltip for the button that opens the Aetheryte removal confirmation dialog.
     */
    public static Component buttonOpenRemoveConfirmTooltip() {
        return Component.text("このエーテライトを撤去し、アイテムを回収します", NamedTextColor.GRAY);
    }

    /**
     * Returns the title for the Aetheryte removal confirmation dialog.
     */
    public static Component removeConfirmDialogTitle(final Aetheryte aetheryte) {
        return Component.text()
                .append(Component.text("エーテライトの撤去確認: ", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text(aetheryte.name(), NamedTextColor.WHITE))
                .build();
    }

    /**
     * Returns the body message for the Aetheryte removal confirmation dialog.
     */
    public static Component removeConfirmDialogBody(final Aetheryte aetheryte) {
        return Component.text()
                .append(Component.text("本当に「", NamedTextColor.GRAY))
                .append(Component.text(aetheryte.name(), NamedTextColor.WHITE))
                .append(Component.text("」を撤去しますか？", NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text("撤去するとエーテライトアイテムが返却されます。", NamedTextColor.YELLOW))
                .build();
    }

    /**
     * Returns the label for the final confirm removal button.
     */
    public static Component buttonConfirmRemove() {
        return Component.text("撤去する", NamedTextColor.RED, TextDecoration.BOLD);
    }

    /**
     * Returns the notification message sent when an Aetheryte is placed.
     */
    public static Component aetheryteCreated(final Aetheryte aetheryte) {
        return prefix().append(
                Component.text()
                        .append(Component.text("エーテライト「", NamedTextColor.GREEN))
                        .append(Component.text(aetheryte.name(), NamedTextColor.WHITE, TextDecoration.BOLD))
                        .append(Component.text("」を設置しました。", NamedTextColor.GREEN))
        );
    }

    /**
     * Returns the notification message sent when an Aetheryte is removed.
     */
    public static Component aetheryteRemoved(final String name) {
        return prefix().append(
                Component.text()
                        .append(Component.text("エーテライト「", NamedTextColor.YELLOW))
                        .append(Component.text(name, NamedTextColor.WHITE, TextDecoration.BOLD))
                        .append(Component.text("」を撤去しました。", NamedTextColor.YELLOW))
        );
    }

    /**
     * Returns the notification message sent when a player warps to an Aetheryte.
     */
    public static Component warpedTo(final Aetheryte destination) {
        return prefix().append(
                Component.text()
                        .append(Component.text("「", NamedTextColor.AQUA))
                        .append(Component.text(destination.name(), NamedTextColor.WHITE, TextDecoration.BOLD))
                        .append(Component.text("」へワープしました。", NamedTextColor.AQUA))
        );
    }

    /**
     * Returns the error message when an invalid Aetheryte name is provided.
     */
    public static Component errorInvalidName() {
        return prefix().append(Component.text("有効なエーテライト名を入力してください（1〜32文字）。", NamedTextColor.RED));
    }

    /**
     * Returns the error message when the target placement block is not replaceable.
     */
    public static Component errorSpaceNotEmpty() {
        return prefix().append(Component.text("設置予定位置にブロックが存在するため設置できません。", NamedTextColor.RED));
    }

    /**
     * Returns the error message when an Aetheryte already exists at the target coordinates.
     */
    public static Component errorAlreadyExists() {
        return prefix().append(Component.text("その場所には既にエーテライトが存在します。", NamedTextColor.RED));
    }

    /**
     * Returns the error message when the player does not hold or carry an Aetheryte item.
     */
    public static Component errorMissingItem() {
        return prefix().append(Component.text("エーテライトアイテムを所持していません。", NamedTextColor.RED));
    }

    /**
     * Returns the error message when the target Aetheryte no longer exists.
     */
    public static Component errorNotFound() {
        return prefix().append(Component.text("対象のエーテライトが見つかりません。", NamedTextColor.RED));
    }

    /**
     * Returns the error message when a player attempts to remove an Aetheryte they do not own.
     */
    public static Component errorNotOwner() {
        return prefix().append(Component.text("このエーテライトを撤去する権限がありません（設置者のみ撤去可能）。", NamedTextColor.RED));
    }

    /**
     * Returns the error message when the destination landing area is obstructed or hazardous.
     */
    public static Component errorDestinationUnsafe() {
        return prefix().append(Component.text("ワープ先の着地地点が塞がれているか安全ではないためワープできません。", NamedTextColor.RED));
    }

    /**
     * Returns the error message when a player attempts to break an Aetheryte block directly.
     */
    public static Component errorProtectedBreak() {
        return prefix().append(Component.text("エーテライトは直接破壊できません。右クリックメニューから撤去してください。", NamedTextColor.RED));
    }

    /**
     * Returns the confirmation message when giving Aetheryte items via command.
     */
    public static Component commandGivenItem(final String playerName, final int amount) {
        return prefix().append(
                Component.text()
                        .append(Component.text(playerName, NamedTextColor.WHITE))
                        .append(Component.text(" にエーテライトを " + amount + " 個付与しました。", NamedTextColor.GREEN))
        );
    }
}
