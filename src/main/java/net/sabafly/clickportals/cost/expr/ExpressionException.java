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
package net.sabafly.clickportals.cost.expr;

/**
 * Thrown when an expression string fails to tokenize or parse.
 */
public final class ExpressionException extends RuntimeException {

    private final int position;

    public ExpressionException(final String message) {
        this(message, -1);
    }

    public ExpressionException(final String message, final int position) {
        super(position >= 0 ? message + " (at position " + position + ")" : message);
        this.position = position;
    }

    public int position() {
        return position;
    }
}
