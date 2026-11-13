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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ExpressionParserTest {

    @Test
    void evaluatesBasicArithmeticAndPrecedence() {
        final Expression expr = ExpressionParser.parse("10 + 2 * 3 - 4 / 2");
        assertThat(expr.evaluate()).isEqualTo(14.0);
    }

    @Test
    void evaluatesPowerAndParentheses() {
        final Expression expr = ExpressionParser.parse("2 ^ 3 ^ 2"); // right associative: 2 ^ 9 = 512
        assertThat(expr.evaluate()).isEqualTo(512.0);

        final Expression parens = ExpressionParser.parse("(2 ^ 3) ^ 2");
        assertThat(parens.evaluate()).isEqualTo(64.0);
    }

    @Test
    void evaluatesVariablesAndMissingVariables() {
        final Expression expr = ExpressionParser.parse("$block_distance * 3.5 + $chunk_distance + 10");
        assertThat(expr.referencedVariables()).containsExactlyInAnyOrder("block_distance", "chunk_distance");

        final double res = expr.evaluate(Map.of("block_distance", 100.0, "chunk_distance", 5.0));
        assertThat(res).isEqualTo(365.0);

        final double missing = expr.evaluate(Map.of("block_distance", 10.0));
        assertThat(missing).isEqualTo(45.0); // chunk_distance defaults to 0.0
    }

    @Test
    void evaluatesFunctions() {
        assertThat(ExpressionParser.parse("min(10, 5, 20)").evaluate()).isEqualTo(5.0);
        assertThat(ExpressionParser.parse("max(10, 5, 20)").evaluate()).isEqualTo(20.0);
        assertThat(ExpressionParser.parse("clamp(15, 0, 10)").evaluate()).isEqualTo(10.0);
        assertThat(ExpressionParser.parse("clamp(-5, 0, 10)").evaluate()).isEqualTo(0.0);
        assertThat(ExpressionParser.parse("clamp(7, 0, 10)").evaluate()).isEqualTo(7.0);
        assertThat(ExpressionParser.parse("abs(-42)").evaluate()).isEqualTo(42.0);
        assertThat(ExpressionParser.parse("floor(3.9)").evaluate()).isEqualTo(3.0);
        assertThat(ExpressionParser.parse("ceil(3.1)").evaluate()).isEqualTo(4.0);
        assertThat(ExpressionParser.parse("round(3.6)").evaluate()).isEqualTo(4.0);
        assertThat(ExpressionParser.parse("sqrt(16)").evaluate()).isEqualTo(4.0);
    }

    @Test
    void evaluatesTernaryAndConditionals() {
        final Expression expr = ExpressionParser.parse("$block_distance > 100 ? 50 : 10");
        assertThat(expr.evaluate(Map.of("block_distance", 150.0))).isEqualTo(50.0);
        assertThat(expr.evaluate(Map.of("block_distance", 50.0))).isEqualTo(10.0);

        final Expression logical = ExpressionParser.parse("($a > 0 && $b > 0) || $c == 1 ? 99 : 0");
        assertThat(logical.evaluate(Map.of("a", 1.0, "b", 1.0, "c", 0.0))).isEqualTo(99.0);
        assertThat(logical.evaluate(Map.of("a", 1.0, "b", 0.0, "c", 1.0))).isEqualTo(99.0);
        assertThat(logical.evaluate(Map.of("a", 0.0, "b", 0.0, "c", 0.0))).isEqualTo(0.0);
    }

    @Test
    void expandsPercentageShorthand() {
        final Expression expr = ExpressionParser.parse("10%", "balance");
        assertThat(expr.evaluate(Map.of("balance", 2500.0))).isCloseTo(250.0, offset(1e-9));

        final Expression decimalPercent = ExpressionParser.parse("12.5%", "balance");
        assertThat(decimalPercent.evaluate(Map.of("balance", 1000.0))).isCloseTo(125.0, offset(1e-9));
    }

    @Test
    void divisionByZeroReturnsZero() {
        final Expression expr = ExpressionParser.parse("100 / 0");
        assertThat(expr.evaluate()).isEqualTo(0.0);

        final Expression modZero = ExpressionParser.parse("100 % 0");
        assertThat(modZero.evaluate()).isEqualTo(0.0);
    }

    @Test
    void rejectsInvalidSyntaxWithPosition() {
        assertThatThrownBy(() -> ExpressionParser.parse("10 +"))
                .isInstanceOf(ExpressionException.class);

        assertThatThrownBy(() -> ExpressionParser.parse("foo("))
                .isInstanceOf(ExpressionException.class);

        assertThatThrownBy(() -> ExpressionParser.parse("10%"))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("target variable");
    }
}
