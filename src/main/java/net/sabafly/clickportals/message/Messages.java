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

    public static Component prefix() {
        return Component.text()
                .append(Component.text("[", NamedTextColor.DARK_GRAY))
                .append(Component.text("ClickPortals", AETHERYTE_AQUA))
                .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                .build();
    }

    public static Component aetheryteItemName() {
        return Component.text("エーテライト", AETHERYTE_AQUA)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true);
    }

    public static List<Component> aetheryteItemLore() {
        return List.of(
                Component.text("ブロックに向かって右クリックして", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("エーテライト（ワープポイント）を設置します。", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        );
    }

    public static Component hologramTitle(final String aetheryteName) {
        return Component.text()
                .append(Component.text("✦ ", AETHERYTE_AQUA))
                .append(Component.text(aetheryteName, NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text(" ✦", AETHERYTE_AQUA))
                .build();
    }

    public static Component createDialogTitle() {
        return Component.text("エーテライトの設置", AETHERYTE_AQUA, TextDecoration.BOLD);
    }

    public static Component createDialogDescription(final String worldName, final int x, final int y, final int z) {
        return Component.text()
                .append(Component.text("設置座標: ", NamedTextColor.GRAY))
                .append(Component.text(worldName + " (" + x + ", " + y + ", " + z + ")", NamedTextColor.WHITE))
                .build();
    }

    public static Component createDialogNameInputLabel() {
        return Component.text("エーテライト名", NamedTextColor.AQUA);
    }

    public static Component buttonConfirmCreate() {
        return Component.text("設置する", NamedTextColor.GREEN, TextDecoration.BOLD);
    }

    public static Component buttonCancel() {
        return Component.text("キャンセル", NamedTextColor.GRAY);
    }

    public static Component buttonClose() {
        return Component.text("閉じる", NamedTextColor.GRAY);
    }

    public static Component warpDialogTitle(final Aetheryte current) {
        return Component.text()
                .append(Component.text("✦ エーテライト: ", AETHERYTE_AQUA))
                .append(Component.text(current.name(), NamedTextColor.WHITE, TextDecoration.BOLD))
                .build();
    }

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

    public static Component warpDestinationLabel(final Aetheryte destination) {
        return Component.text()
                .append(Component.text("✦ ", AETHERYTE_AQUA))
                .append(Component.text(destination.name(), NamedTextColor.WHITE))
                .build();
    }

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

    public static Component buttonOpenRemoveConfirm() {
        return Component.text("このエーテライトを撤去", NamedTextColor.RED);
    }

    public static Component buttonOpenRemoveConfirmTooltip() {
        return Component.text("このエーテライトを撤去し、アイテムを回収します", NamedTextColor.GRAY);
    }

    public static Component removeConfirmDialogTitle(final Aetheryte aetheryte) {
        return Component.text()
                .append(Component.text("エーテライトの撤去確認: ", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text(aetheryte.name(), NamedTextColor.WHITE))
                .build();
    }

    public static Component removeConfirmDialogBody(final Aetheryte aetheryte) {
        return Component.text()
                .append(Component.text("本当に「", NamedTextColor.GRAY))
                .append(Component.text(aetheryte.name(), NamedTextColor.WHITE))
                .append(Component.text("」を撤去しますか？", NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text("撤去するとエーテライトアイテムが返却されます。", NamedTextColor.YELLOW))
                .build();
    }

    public static Component buttonConfirmRemove() {
        return Component.text("撤去する", NamedTextColor.RED, TextDecoration.BOLD);
    }

    public static Component aetheryteCreated(final Aetheryte aetheryte) {
        return prefix().append(
                Component.text()
                        .append(Component.text("エーテライト「", NamedTextColor.GREEN))
                        .append(Component.text(aetheryte.name(), NamedTextColor.WHITE, TextDecoration.BOLD))
                        .append(Component.text("」を設置しました。", NamedTextColor.GREEN))
        );
    }

    public static Component aetheryteRemoved(final String name) {
        return prefix().append(
                Component.text()
                        .append(Component.text("エーテライト「", NamedTextColor.YELLOW))
                        .append(Component.text(name, NamedTextColor.WHITE, TextDecoration.BOLD))
                        .append(Component.text("」を撤去しました。", NamedTextColor.YELLOW))
        );
    }

    public static Component warpedTo(final Aetheryte destination) {
        return prefix().append(
                Component.text()
                        .append(Component.text("「", NamedTextColor.AQUA))
                        .append(Component.text(destination.name(), NamedTextColor.WHITE, TextDecoration.BOLD))
                        .append(Component.text("」へワープしました。", NamedTextColor.AQUA))
        );
    }

    public static Component errorInvalidName() {
        return prefix().append(Component.text("有効なエーテライト名を入力してください（1〜32文字）。", NamedTextColor.RED));
    }

    public static Component errorSpaceNotEmpty() {
        return prefix().append(Component.text("設置予定位置にブロックが存在するため設置できません。", NamedTextColor.RED));
    }

    public static Component errorAlreadyExists() {
        return prefix().append(Component.text("その場所には既にエーテライトが存在します。", NamedTextColor.RED));
    }

    public static Component errorMissingItem() {
        return prefix().append(Component.text("エーテライトアイテムを所持していません。", NamedTextColor.RED));
    }

    public static Component errorNotFound() {
        return prefix().append(Component.text("対象のエーテライトが見つかりません。", NamedTextColor.RED));
    }

    public static Component errorDestinationUnsafe() {
        return prefix().append(Component.text("ワープ先の着地地点が塞がれているか安全ではないためワープできません。", NamedTextColor.RED));
    }

    public static Component errorProtectedBreak() {
        return prefix().append(Component.text("エーテライトは直接破壊できません。右クリックメニューから撤去してください。", NamedTextColor.RED));
    }

    public static Component commandGivenItem(final String playerName, final int amount) {
        return prefix().append(
                Component.text()
                        .append(Component.text(playerName, NamedTextColor.WHITE))
                        .append(Component.text(" にエーテライトを " + amount + " 個付与しました。", NamedTextColor.GREEN))
        );
    }
}
