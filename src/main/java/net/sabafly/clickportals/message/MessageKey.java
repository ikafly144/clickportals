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
package net.sabafly.clickportals.message;

import java.util.Arrays;
import java.util.List;

/**
 * Enumeration of all translatable message keys used by ClickPortals (#18, #19).
 */
public enum MessageKey {

    PREFIX("prefix"),

    ITEM_NAME("item.name"),
    ITEM_LORE("item.lore", true),

    HOLOGRAM_TITLE("hologram.title"),

    COST_FREE("cost.free"),
    COST_BYPASSED("cost.bypassed"),
    COST_SEPARATOR("cost.separator"),
    COST_VAULT("cost.vault"),
    COST_EXP_POINTS("cost.exp-points"),
    COST_EXP_LEVELS("cost.exp-levels"),
    COST_FOOD("cost.food"),
    COST_INSUFFICIENT_VAULT("cost.insufficient.vault"),
    COST_INSUFFICIENT_EXP_POINTS("cost.insufficient.exp-points"),
    COST_INSUFFICIENT_EXP_LEVELS("cost.insufficient.exp-levels"),
    COST_INSUFFICIENT_FOOD("cost.insufficient.food"),

    DIALOG_CREATE_TITLE("dialog.create.title"),
    DIALOG_CREATE_DESCRIPTION("dialog.create.description"),
    DIALOG_CREATE_VARIATION("dialog.create.variation"),
    DIALOG_CREATE_COST("dialog.create.cost"),
    DIALOG_CREATE_COST_BYPASSED("dialog.create.cost-bypassed"),
    DIALOG_CREATE_NAME_INPUT_LABEL("dialog.create.name-input-label"),
    DIALOG_CREATE_DEFAULT_NAME("dialog.create.default-name"),

    DIALOG_BUTTON_CONFIRM_CREATE("dialog.button.confirm-create"),
    DIALOG_BUTTON_CANCEL("dialog.button.cancel"),
    DIALOG_BUTTON_CLOSE("dialog.button.close"),
    DIALOG_BUTTON_OPEN_REMOVE_CONFIRM("dialog.button.open-remove-confirm"),
    DIALOG_BUTTON_OPEN_REMOVE_CONFIRM_TOOLTIP("dialog.button.open-remove-confirm-tooltip"),
    DIALOG_BUTTON_CONFIRM_REMOVE("dialog.button.confirm-remove"),
    DIALOG_BUTTON_BACK("dialog.button.back"),
    DIALOG_BUTTON_PREV_PAGE("dialog.button.prev-page"),
    DIALOG_BUTTON_NEXT_PAGE("dialog.button.next-page"),
    DIALOG_BUTTON_APPLY_SEARCH("dialog.button.apply-search"),
    DIALOG_BUTTON_CLEAR_SEARCH("dialog.button.clear-search"),
    DIALOG_BUTTON_FAVORITE_ADD("dialog.button.favorite-add"),
    DIALOG_BUTTON_FAVORITE_REMOVE("dialog.button.favorite-remove"),
    DIALOG_BUTTON_OPEN_OPERATIONS("dialog.button.open-operations"),
    DIALOG_BUTTON_OPEN_OPERATIONS_TOOLTIP("dialog.button.open-operations-tooltip"),
    DIALOG_BUTTON_OPEN_RENAME("dialog.button.open-rename"),
    DIALOG_BUTTON_OPEN_RENAME_TOOLTIP("dialog.button.open-rename-tooltip"),
    DIALOG_BUTTON_CONFIRM_RENAME("dialog.button.confirm-rename"),
    DIALOG_BUTTON_ADMIN_FORCE_REMOVE("dialog.button.admin-force-remove"),
    DIALOG_BUTTON_ADMIN_FORCE_REMOVE_TOOLTIP("dialog.button.admin-force-remove-tooltip"),
    DIALOG_BUTTON_CONFIRM_FORCE_REMOVE("dialog.button.confirm-force-remove"),
    DIALOG_BUTTON_ADMIN_TRANSFER("dialog.button.admin-transfer"),
    DIALOG_BUTTON_ADMIN_TRANSFER_TOOLTIP("dialog.button.admin-transfer-tooltip"),
    DIALOG_BUTTON_CONFIRM_TRANSFER("dialog.button.confirm-transfer"),
    DIALOG_BUTTON_ADMIN_VARIATION("dialog.button.admin-variation"),
    DIALOG_BUTTON_ADMIN_VARIATION_TOOLTIP("dialog.button.admin-variation-tooltip"),
    DIALOG_BUTTON_CONFIRM_VARIATION("dialog.button.confirm-variation"),
    DIALOG_BUTTON_ADMIN_RESYNC("dialog.button.admin-resync"),
    DIALOG_BUTTON_ADMIN_RESYNC_TOOLTIP("dialog.button.admin-resync-tooltip"),

    DIALOG_WARP_TITLE("dialog.warp.title"),
    DIALOG_WARP_HEADER("dialog.warp.header"),
    DIALOG_WARP_BODY_EMPTY("dialog.warp.body-empty"),
    DIALOG_WARP_BODY_NO_ROUTE("dialog.warp.body-no-route"),
    DIALOG_WARP_BODY_SELECT("dialog.warp.body-select"),
    DIALOG_WARP_ROOT_BODY("dialog.warp.root-body"),
    DIALOG_WARP_CATEGORY_FAVORITES("dialog.warp.category-favorites"),
    DIALOG_WARP_CATEGORY_FAVORITES_TOOLTIP("dialog.warp.category-favorites-tooltip"),
    DIALOG_WARP_CATEGORY_WORLD_OVERWORLD("dialog.warp.category-world-overworld"),
    DIALOG_WARP_CATEGORY_WORLD_NETHER("dialog.warp.category-world-nether"),
    DIALOG_WARP_CATEGORY_WORLD_END("dialog.warp.category-world-end"),
    DIALOG_WARP_CATEGORY_WORLD_CUSTOM("dialog.warp.category-world-custom"),
    DIALOG_WARP_CATEGORY_WORLD_CURRENT_BADGE("dialog.warp.category-world-current-badge"),
    DIALOG_WARP_CATEGORY_WORLD_TOOLTIP("dialog.warp.category-world-tooltip"),
    DIALOG_WARP_CATEGORY_ALL("dialog.warp.category-all"),
    DIALOG_WARP_CATEGORY_ALL_TOOLTIP("dialog.warp.category-all-tooltip"),
    DIALOG_WARP_PAGE_INDICATOR("dialog.warp.page-indicator"),
    DIALOG_WARP_SEARCH_INPUT_LABEL("dialog.warp.search-input-label"),
    DIALOG_WARP_SORT_BUTTON("dialog.warp.sort-button"),
    DIALOG_WARP_SORT_TOOLTIP("dialog.warp.sort-tooltip"),
    DIALOG_WARP_SORT_DISTANCE("dialog.warp.sort.distance"),
    DIALOG_WARP_SORT_NAME("dialog.warp.sort.name"),
    DIALOG_WARP_SORT_OWNER("dialog.warp.sort.owner"),
    DIALOG_WARP_SORT_CREATED("dialog.warp.sort.created"),
    DIALOG_WARP_LIST_HEADER("dialog.warp.list-header"),
    DIALOG_WARP_LIST_HEADER_SEARCH("dialog.warp.list-header-search"),
    DIALOG_WARP_LIST_EMPTY_FILTERED("dialog.warp.list-empty-filtered"),
    DIALOG_WARP_SCOPE_FAVORITES("dialog.warp.scope-favorites"),
    DIALOG_WARP_SCOPE_ALL("dialog.warp.scope-all"),
    DIALOG_WARP_FAVORITE_BADGE("dialog.warp.favorite-badge"),
    DIALOG_WARP_FAVORITE_EDIT_OFF("dialog.warp.favorite-edit-off"),
    DIALOG_WARP_FAVORITE_EDIT_ON("dialog.warp.favorite-edit-on"),
    DIALOG_WARP_FAVORITE_EDIT_TOOLTIP("dialog.warp.favorite-edit-tooltip"),
    DIALOG_WARP_FAVORITE_EMPTY("dialog.warp.favorite-empty"),
    DIALOG_WARP_TOOLTIP_FAVORITE_TOGGLE("dialog.warp.tooltip-favorite-toggle"),
    DIALOG_WARP_DESTINATION_AVAILABLE("dialog.warp.destination-available"),
    DIALOG_WARP_DESTINATION_UNAVAILABLE("dialog.warp.destination-unavailable"),
    DIALOG_WARP_DISTANCE_BLOCKS("dialog.warp.distance-blocks"),
    DIALOG_WARP_DISTANCE_OTHER_DIMENSION("dialog.warp.distance-other-dimension"),
    DIALOG_WARP_TOOLTIP_ALLOWED("dialog.warp.tooltip-allowed"),
    DIALOG_WARP_TOOLTIP_DENIED("dialog.warp.tooltip-denied"),
    DIALOG_WARP_UNKNOWN_OWNER("dialog.warp.unknown-owner"),

    DIALOG_REMOVE_TITLE("dialog.remove.title"),
    DIALOG_REMOVE_BODY("dialog.remove.body"),

    DIALOG_OPERATIONS_TITLE("dialog.operations.title"),
    DIALOG_OPERATIONS_BODY("dialog.operations.body"),
    DIALOG_RENAME_TITLE("dialog.rename.title"),
    DIALOG_RENAME_BODY("dialog.rename.body"),
    DIALOG_RENAME_INPUT_LABEL("dialog.rename.input-label"),
    DIALOG_FORCE_REMOVE_TITLE("dialog.force-remove.title"),
    DIALOG_FORCE_REMOVE_BODY("dialog.force-remove.body"),
    DIALOG_TRANSFER_TITLE("dialog.transfer.title"),
    DIALOG_TRANSFER_BODY("dialog.transfer.body"),
    DIALOG_TRANSFER_INPUT_LABEL("dialog.transfer.input-label"),
    DIALOG_VARIATION_TITLE("dialog.variation.title"),
    DIALOG_VARIATION_BODY("dialog.variation.body"),
    DIALOG_VARIATION_SELECT_LABEL("dialog.variation.select-label"),

    AETHERYTE_CREATED("aetheryte.created"),
    AETHERYTE_REMOVED("aetheryte.removed"),
    AETHERYTE_COST_PAID("aetheryte.cost-paid"),
    AETHERYTE_RENAMED("aetheryte.renamed"),
    AETHERYTE_FORCE_REMOVED("aetheryte.force-removed"),
    AETHERYTE_OWNER_TRANSFERRED("aetheryte.owner-transferred"),
    AETHERYTE_VARIATION_CHANGED("aetheryte.variation-changed"),
    AETHERYTE_VISUALS_RESYNCED("aetheryte.visuals-resynced"),

    WARP_SUCCESS("warp.success"),
    WARP_COST_PAID("warp.cost-paid"),
    WARP_CAST_STARTED("warp.cast-started"),
    WARP_CAST_PROGRESS("warp.cast-progress"),
    WARP_CAST_CANCELLED("warp.cast-cancelled"),
    WARP_CAST_CANCELLED_MOVE("warp.cast-cancelled-move"),
    WARP_CAST_CANCELLED_DAMAGE("warp.cast-cancelled-damage"),

    ERROR_INVALID_NAME("error.invalid-name"),
    ERROR_SPACE_NOT_EMPTY("error.space-not-empty"),
    ERROR_CLEARANCE_INSUFFICIENT("error.clearance-insufficient"),
    ERROR_ALREADY_EXISTS("error.already-exists"),
    ERROR_LIMIT_REACHED_GLOBAL("error.limit-reached-global"),
    ERROR_LIMIT_REACHED_VARIATION("error.limit-reached-variation"),
    ERROR_FAVORITES_LIMIT_REACHED("error.favorites-limit-reached"),
    ERROR_PLACEMENT_COOLDOWN("error.placement-cooldown"),
    ERROR_PLACEMENT_TOO_CLOSE("error.placement-too-close"),
    ERROR_MISSING_ITEM("error.missing-item"),
    ERROR_NOT_FOUND("error.not-found"),
    ERROR_NOT_OWNER("error.not-owner"),
    ERROR_NO_PERMISSION("error.no-permission"),
    ERROR_NO_WARP_PERMISSION("error.no-warp-permission"),
    ERROR_WARP_REQUIREMENTS_NOT_MET("error.warp-requirements-not-met"),
    ERROR_INSUFFICIENT_COST("error.insufficient-cost"),
    ERROR_TOO_CLOSE("error.too-close"),
    ERROR_TOO_FAR("error.too-far"),
    ERROR_NO_ROUTE("error.no-route"),
    ERROR_PLACEMENT_COST_INSUFFICIENT("error.placement-cost-insufficient"),
    ERROR_WARP_DENIED("error.warp-denied"),
    ERROR_DESTINATION_UNSAFE("error.destination-unsafe"),
    ERROR_PROTECTED_BREAK("error.protected-break"),
    ERROR_PLAYER_NOT_FOUND("error.player-not-found"),

    COMMAND_GIVEN_ITEM("command.given-item"),
    COMMAND_CONSOLE_SPECIFY_PLAYER("command.console-specify-player"),
    COMMAND_UNKNOWN_VARIATION("command.unknown-variation"),
    COMMAND_RELOADED("command.reloaded"),
    COMMAND_RELOAD_FAILED("command.reload-failed");

    private final String path;
    private final boolean listValue;

    MessageKey(final String path) {
        this(path, false);
    }

    MessageKey(final String path, final boolean listValue) {
        this.path = path;
        this.listValue = listValue;
    }

    /**
     * Returns the dot-separated YAML key path (e.g. {@code "dialog.create.title"}).
     */
    public String path() {
        return path;
    }

    /**
     * Returns the split YAML node path segments for Configurate navigation.
     */
    public List<String> nodePath() {
        return Arrays.asList(path.split("\\.", -1));
    }

    /**
     * Returns whether this key expects a list of strings rather than a single string.
     */
    public boolean isList() {
        return listValue;
    }
}
