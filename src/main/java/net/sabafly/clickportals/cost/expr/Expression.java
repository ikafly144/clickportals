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

import java.util.Map;
import java.util.Set;

/**
 * Compiled mathematical expression evaluating against a variable map (#7, #8).
 */
@FunctionalInterface
public interface Expression {

    /**
     * Constant zero expression.
     */
    Expression ZERO = variables -> 0.0;

    /**
     * Evaluates this expression using values from the given variable map.
     * Missing variables evaluate to {@code 0.0}.
     *
     * @param variables map of variable names (without leading {@code $}) to values
     * @return the evaluated double result
     */
    double evaluate(Map<String, Double> variables);

    /**
     * Evaluates this expression with an empty variable map.
     */
    default double evaluate() {
        return evaluate(Map.of());
    }

    /**
     * Returns variable names referenced by this expression, if tracked.
     */
    default Set<String> referencedVariables() {
        return Set.of();
    }
}
