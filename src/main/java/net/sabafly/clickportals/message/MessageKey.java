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
    DIALOG_CREATE_NAME_INPUT_LABEL("dialog.create.name-input-label"),
    DIALOG_CREATE_DEFAULT_NAME("dialog.create.default-name"),

    DIALOG_BUTTON_CONFIRM_CREATE("dialog.button.confirm-create"),
    DIALOG_BUTTON_CANCEL("dialog.button.cancel"),
    DIALOG_BUTTON_CLOSE("dialog.button.close"),
    DIALOG_BUTTON_OPEN_REMOVE_CONFIRM("dialog.button.open-remove-confirm"),
    DIALOG_BUTTON_OPEN_REMOVE_CONFIRM_TOOLTIP("dialog.button.open-remove-confirm-tooltip"),
    DIALOG_BUTTON_CONFIRM_REMOVE("dialog.button.confirm-remove"),

    DIALOG_WARP_TITLE("dialog.warp.title"),
    DIALOG_WARP_BODY_EMPTY("dialog.warp.body-empty"),
    DIALOG_WARP_BODY_SELECT("dialog.warp.body-select"),
    DIALOG_WARP_DESTINATION_AVAILABLE("dialog.warp.destination-available"),
    DIALOG_WARP_DESTINATION_UNAVAILABLE("dialog.warp.destination-unavailable"),
    DIALOG_WARP_DISTANCE_BLOCKS("dialog.warp.distance-blocks"),
    DIALOG_WARP_DISTANCE_OTHER_DIMENSION("dialog.warp.distance-other-dimension"),
    DIALOG_WARP_TOOLTIP_ALLOWED("dialog.warp.tooltip-allowed"),
    DIALOG_WARP_TOOLTIP_DENIED("dialog.warp.tooltip-denied"),

    DIALOG_REMOVE_TITLE("dialog.remove.title"),
    DIALOG_REMOVE_BODY("dialog.remove.body"),

    AETHERYTE_CREATED("aetheryte.created"),
    AETHERYTE_REMOVED("aetheryte.removed"),

    WARP_SUCCESS("warp.success"),

    ERROR_INVALID_NAME("error.invalid-name"),
    ERROR_SPACE_NOT_EMPTY("error.space-not-empty"),
    ERROR_CLEARANCE_INSUFFICIENT("error.clearance-insufficient"),
    ERROR_ALREADY_EXISTS("error.already-exists"),
    ERROR_LIMIT_REACHED_GLOBAL("error.limit-reached-global"),
    ERROR_LIMIT_REACHED_VARIATION("error.limit-reached-variation"),
    ERROR_PLACEMENT_COOLDOWN("error.placement-cooldown"),
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

    COMMAND_GIVEN_ITEM("command.given-item"),
    COMMAND_CONSOLE_SPECIFY_PLAYER("command.console-specify-player"),
    COMMAND_UNKNOWN_VARIATION("command.unknown-variation"),
    COMMAND_RELOADED("command.reloaded");

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
