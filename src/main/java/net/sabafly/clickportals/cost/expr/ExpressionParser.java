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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Recursive-descent compiler for mathematical and conditional expressions (#7, #8).
 */
public final class ExpressionParser {

    private ExpressionParser() {
    }

    /**
     * Parses an expression string into a compiled {@link Expression}.
     *
     * @param input expression string
     * @return compiled expression
     * @throws ExpressionException if syntax or tokenization fails
     */
    public static Expression parse(final String input) {
        return parse(input, null);
    }

    /**
     * Parses an expression string, automatically expanding percentage shorthand (e.g. {@code "10%"})
     * to {@code "$targetVariable * 0.1"} when a default target variable is provided (#7).
     *
     * @param input expression string or percentage shorthand
     * @param defaultPercentageVariable variable name (without {@code $}) to scale when percentage shorthand is used
     * @return compiled expression
     * @throws ExpressionException if syntax or tokenization fails
     */
    public static Expression parse(final String input, final @Nullable String defaultPercentageVariable) {
        if (input == null || input.isBlank()) {
            return Expression.ZERO;
        }
        final String trimmed = input.strip();

        // Shorthand percentage detection: "10%" or "12.5%"
        if (trimmed.endsWith("%")) {
            final String numPart = trimmed.substring(0, trimmed.length() - 1).strip();
            try {
                final double percent = Double.parseDouble(numPart);
                final double ratio = percent / 100.0;
                if (defaultPercentageVariable == null || defaultPercentageVariable.isBlank()) {
                    throw new ExpressionException("Percentage shorthand '%" + "' requires a target variable context", trimmed.length() - 1);
                }
                final String targetVar = defaultPercentageVariable.startsWith("$")
                        ? defaultPercentageVariable.substring(1)
                        : defaultPercentageVariable;
                return new MultiplyExpr(new VariableExpr(targetVar), new ConstantExpr(ratio));
            } catch (final NumberFormatException ignored) {
                // Not a simple number followed by %, proceed to standard tokenizer
            }
        }

        final List<Token> tokens = tokenize(trimmed);
        final ParserState parser = new ParserState(tokens);
        final Expression expr = parser.parseExpression();
        if (parser.peek().type != TokenType.EOF) {
            throw new ExpressionException("Unexpected token after expression: " + parser.peek().text, parser.peek().position);
        }
        return expr;
    }

    // -------------------------------------------------------------------------
    // Tokenizer
    // -------------------------------------------------------------------------

    private enum TokenType {
        NUMBER,
        VARIABLE,
        IDENTIFIER,
        PLUS,
        MINUS,
        STAR,
        SLASH,
        PERCENT,
        CARET,
        EQ,
        NEQ,
        LT,
        LTE,
        GT,
        GTE,
        AND,
        OR,
        NOT,
        QUESTION,
        COLON,
        LPAREN,
        RPAREN,
        COMMA,
        EOF
    }

    private record Token(TokenType type, String text, double numberValue, int position) {
        static Token op(final TokenType type, final String text, final int pos) {
            return new Token(type, text, 0.0, pos);
        }
    }

    private static List<Token> tokenize(final String input) {
        final List<Token> tokens = new ArrayList<>();
        final int len = input.length();
        int i = 0;

        while (i < len) {
            final char c = input.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            final int start = i;

            // Numbers: digits with optional decimal point
            if (Character.isDigit(c) || (c == '.' && i + 1 < len && Character.isDigit(input.charAt(i + 1)))) {
                while (i < len && (Character.isDigit(input.charAt(i)) || input.charAt(i) == '.')) {
                    i++;
                }
                final String raw = input.substring(start, i);
                try {
                    final double val = Double.parseDouble(raw);
                    tokens.add(new Token(TokenType.NUMBER, raw, val, start));
                } catch (final NumberFormatException e) {
                    throw new ExpressionException("Invalid number: " + raw, start);
                }
                continue;
            }

            // Variables: $var_name
            if (c == '$') {
                i++;
                final int varStart = i;
                while (i < len && (Character.isLetterOrDigit(input.charAt(i)) || input.charAt(i) == '_')) {
                    i++;
                }
                if (i == varStart) {
                    throw new ExpressionException("Expected variable name after '$'", start);
                }
                final String varName = input.substring(varStart, i);
                tokens.add(new Token(TokenType.VARIABLE, varName, 0.0, start));
                continue;
            }

            // Identifiers / Function names: letter followed by letters, digits, or underscores
            if (Character.isLetter(c) || c == '_') {
                while (i < len && (Character.isLetterOrDigit(input.charAt(i)) || input.charAt(i) == '_')) {
                    i++;
                }
                final String ident = input.substring(start, i);
                tokens.add(new Token(TokenType.IDENTIFIER, ident, 0.0, start));
                continue;
            }

            // Two-character operators
            if (i + 1 < len) {
                final String twoChar = input.substring(i, i + 2);
                final TokenType twoType = switch (twoChar) {
                    case "==" -> TokenType.EQ;
                    case "!=" -> TokenType.NEQ;
                    case "<=" -> TokenType.LTE;
                    case ">=" -> TokenType.GTE;
                    case "&&" -> TokenType.AND;
                    case "||" -> TokenType.OR;
                    default -> null;
                };
                if (twoType != null) {
                    tokens.add(Token.op(twoType, twoChar, start));
                    i += 2;
                    continue;
                }
            }

            // Single-character operators
            final TokenType singleType = switch (c) {
                case '+' -> TokenType.PLUS;
                case '-' -> TokenType.MINUS;
                case '*' -> TokenType.STAR;
                case '/' -> TokenType.SLASH;
                case '%' -> TokenType.PERCENT;
                case '^' -> TokenType.CARET;
                case '<' -> TokenType.LT;
                case '>' -> TokenType.GT;
                case '!' -> TokenType.NOT;
                case '?' -> TokenType.QUESTION;
                case ':' -> TokenType.COLON;
                case '(' -> TokenType.LPAREN;
                case ')' -> TokenType.RPAREN;
                case ',' -> TokenType.COMMA;
                default -> null;
            };

            if (singleType != null) {
                tokens.add(Token.op(singleType, String.valueOf(c), start));
                i++;
                continue;
            }

            throw new ExpressionException("Unexpected character: '" + c + "'", start);
        }

        tokens.add(new Token(TokenType.EOF, "", 0.0, len));
        return tokens;
    }

    // -------------------------------------------------------------------------
    // Parser
    // -------------------------------------------------------------------------

    private static final class ParserState {
        private final List<Token> tokens;
        private int current = 0;

        ParserState(final List<Token> tokens) {
            this.tokens = tokens;
        }

        Token peek() {
            return tokens.get(current);
        }

        Token advance() {
            final Token t = peek();
            if (t.type != TokenType.EOF) {
                current++;
            }
            return t;
        }

        boolean match(final TokenType type) {
            if (peek().type == type) {
                advance();
                return true;
            }
            return false;
        }

        Expression parseExpression() {
            return ternary();
        }

        private Expression ternary() {
            final Expression condition = logicalOr();
            if (match(TokenType.QUESTION)) {
                final Expression thenBranch = ternary();
                if (!match(TokenType.COLON)) {
                    throw new ExpressionException("Expected ':' in ternary expression", peek().position);
                }
                final Expression elseBranch = ternary();
                return new TernaryExpr(condition, thenBranch, elseBranch);
            }
            return condition;
        }

        private Expression logicalOr() {
            Expression expr = logicalAnd();
            while (match(TokenType.OR)) {
                final Expression right = logicalAnd();
                expr = new LogicalOrExpr(expr, right);
            }
            return expr;
        }

        private Expression logicalAnd() {
            Expression expr = equality();
            while (match(TokenType.AND)) {
                final Expression right = equality();
                expr = new LogicalAndExpr(expr, right);
            }
            return expr;
        }

        private Expression equality() {
            Expression expr = relational();
            while (true) {
                if (match(TokenType.EQ)) {
                    expr = new BinaryOpExpr(expr, relational(), BinaryOp.EQ);
                } else if (match(TokenType.NEQ)) {
                    expr = new BinaryOpExpr(expr, relational(), BinaryOp.NEQ);
                } else {
                    break;
                }
            }
            return expr;
        }

        private Expression relational() {
            Expression expr = additive();
            while (true) {
                if (match(TokenType.LT)) {
                    expr = new BinaryOpExpr(expr, additive(), BinaryOp.LT);
                } else if (match(TokenType.LTE)) {
                    expr = new BinaryOpExpr(expr, additive(), BinaryOp.LTE);
                } else if (match(TokenType.GT)) {
                    expr = new BinaryOpExpr(expr, additive(), BinaryOp.GT);
                } else if (match(TokenType.GTE)) {
                    expr = new BinaryOpExpr(expr, additive(), BinaryOp.GTE);
                } else {
                    break;
                }
            }
            return expr;
        }

        private Expression additive() {
            Expression expr = multiplicative();
            while (true) {
                if (match(TokenType.PLUS)) {
                    expr = new BinaryOpExpr(expr, multiplicative(), BinaryOp.ADD);
                } else if (match(TokenType.MINUS)) {
                    expr = new BinaryOpExpr(expr, multiplicative(), BinaryOp.SUB);
                } else {
                    break;
                }
            }
            return expr;
        }

        private Expression multiplicative() {
            Expression expr = unary();
            while (true) {
                if (match(TokenType.STAR)) {
                    expr = new BinaryOpExpr(expr, unary(), BinaryOp.MUL);
                } else if (match(TokenType.SLASH)) {
                    expr = new BinaryOpExpr(expr, unary(), BinaryOp.DIV);
                } else if (match(TokenType.PERCENT)) {
                    expr = new BinaryOpExpr(expr, unary(), BinaryOp.MOD);
                } else {
                    break;
                }
            }
            return expr;
        }

        private Expression unary() {
            if (match(TokenType.PLUS)) {
                return unary();
            }
            if (match(TokenType.MINUS)) {
                return new UnaryMinusExpr(unary());
            }
            if (match(TokenType.NOT)) {
                return new LogicalNotExpr(unary());
            }
            return power();
        }

        private Expression power() {
            final Expression base = primary();
            if (match(TokenType.CARET)) {
                final Expression exp = unary(); // Right-associative
                return new PowerExpr(base, exp);
            }
            return base;
        }

        private Expression primary() {
            final Token t = advance();
            return switch (t.type) {
                case NUMBER -> new ConstantExpr(t.numberValue);
                case VARIABLE -> new VariableExpr(t.text);
                case IDENTIFIER -> parseFunctionCall(t);
                case LPAREN -> {
                    final Expression inner = parseExpression();
                    if (!match(TokenType.RPAREN)) {
                        throw new ExpressionException("Expected ')'", peek().position);
                    }
                    yield inner;
                }
                default -> throw new ExpressionException("Expected expression, found: " + (t.type == TokenType.EOF ? "end of input" : t.text), t.position);
            };
        }

        private Expression parseFunctionCall(final Token fnToken) {
            if (!match(TokenType.LPAREN)) {
                throw new ExpressionException("Expected '(' after function name '" + fnToken.text + "'", peek().position);
            }
            final List<Expression> args = new ArrayList<>();
            if (peek().type != TokenType.RPAREN) {
                do {
                    args.add(parseExpression());
                } while (match(TokenType.COMMA));
            }
            if (!match(TokenType.RPAREN)) {
                throw new ExpressionException("Expected ')' after function arguments", peek().position);
            }

            final String name = fnToken.text.toLowerCase(Locale.ROOT);
            return switch (name) {
                case "min" -> {
                    if (args.isEmpty()) {
                        throw new ExpressionException("min() requires at least 1 argument", fnToken.position);
                    }
                    yield new MinExpr(args);
                }
                case "max" -> {
                    if (args.isEmpty()) {
                        throw new ExpressionException("max() requires at least 1 argument", fnToken.position);
                    }
                    yield new MaxExpr(args);
                }
                case "clamp" -> {
                    if (args.size() != 3) {
                        throw new ExpressionException("clamp(x, min, max) requires exactly 3 arguments", fnToken.position);
                    }
                    yield new ClampExpr(args.get(0), args.get(1), args.get(2));
                }
                case "abs" -> {
                    validateArgCount("abs", args, 1, fnToken.position);
                    yield new SingleArgFuncExpr(args.get(0), Math::abs);
                }
                case "floor" -> {
                    validateArgCount("floor", args, 1, fnToken.position);
                    yield new SingleArgFuncExpr(args.get(0), Math::floor);
                }
                case "ceil" -> {
                    validateArgCount("ceil", args, 1, fnToken.position);
                    yield new SingleArgFuncExpr(args.get(0), Math::ceil);
                }
                case "round" -> {
                    validateArgCount("round", args, 1, fnToken.position);
                    yield new SingleArgFuncExpr(args.get(0), x -> (double) Math.round(x));
                }
                case "sqrt" -> {
                    validateArgCount("sqrt", args, 1, fnToken.position);
                    yield new SingleArgFuncExpr(args.get(0), x -> x < 0.0 ? 0.0 : Math.sqrt(x));
                }
                case "log" -> {
                    validateArgCount("log", args, 1, fnToken.position);
                    yield new SingleArgFuncExpr(args.get(0), x -> x <= 0.0 ? 0.0 : Math.log(x));
                }
                case "log10" -> {
                    validateArgCount("log10", args, 1, fnToken.position);
                    yield new SingleArgFuncExpr(args.get(0), x -> x <= 0.0 ? 0.0 : Math.log10(x));
                }
                case "pow" -> {
                    validateArgCount("pow", args, 2, fnToken.position);
                    yield new PowerExpr(args.get(0), args.get(1));
                }
                default -> throw new ExpressionException("Unknown function '" + fnToken.text + "'", fnToken.position);
            };
        }

        private static void validateArgCount(final String fn, final List<Expression> args, final int expected, final int pos) {
            if (args.size() != expected) {
                throw new ExpressionException(fn + "() requires " + expected + " argument(s), given " + args.size(), pos);
            }
        }
    }

    // -------------------------------------------------------------------------
    // AST Nodes
    // -------------------------------------------------------------------------

    private record ConstantExpr(double value) implements Expression {
        @Override
        public double evaluate(final Map<String, Double> variables) {
            return value;
        }
    }

    private record VariableExpr(String name) implements Expression {
        @Override
        public double evaluate(final Map<String, Double> variables) {
            final Double val = variables.get(name);
            return val != null ? val : 0.0;
        }

        @Override
        public Set<String> referencedVariables() {
            return Set.of(name);
        }
    }

    private record MultiplyExpr(Expression left, Expression right) implements Expression {
        @Override
        public double evaluate(final Map<String, Double> variables) {
            return left.evaluate(variables) * right.evaluate(variables);
        }

        @Override
        public Set<String> referencedVariables() {
            final Set<String> vars = new HashSet<>(left.referencedVariables());
            vars.addAll(right.referencedVariables());
            return Collections.unmodifiableSet(vars);
        }
    }

    private record UnaryMinusExpr(Expression operand) implements Expression {
        @Override
        public double evaluate(final Map<String, Double> variables) {
            return -operand.evaluate(variables);
        }

        @Override
        public Set<String> referencedVariables() {
            return operand.referencedVariables();
        }
    }

    private record LogicalNotExpr(Expression operand) implements Expression {
        @Override
        public double evaluate(final Map<String, Double> variables) {
            return operand.evaluate(variables) != 0.0 ? 0.0 : 1.0;
        }

        @Override
        public Set<String> referencedVariables() {
            return operand.referencedVariables();
        }
    }

    private record PowerExpr(Expression base, Expression exp) implements Expression {
        @Override
        public double evaluate(final Map<String, Double> variables) {
            final double res = Math.pow(base.evaluate(variables), exp.evaluate(variables));
            return Double.isFinite(res) ? res : 0.0;
        }

        @Override
        public Set<String> referencedVariables() {
            final Set<String> vars = new HashSet<>(base.referencedVariables());
            vars.addAll(exp.referencedVariables());
            return Collections.unmodifiableSet(vars);
        }
    }

    private enum BinaryOp {
        ADD, SUB, MUL, DIV, MOD, EQ, NEQ, LT, LTE, GT, GTE
    }

    private record BinaryOpExpr(Expression left, Expression right, BinaryOp op) implements Expression {
        @Override
        public double evaluate(final Map<String, Double> variables) {
            final double l = left.evaluate(variables);
            final double r = right.evaluate(variables);
            return switch (op) {
                case ADD -> l + r;
                case SUB -> l - r;
                case MUL -> l * r;
                case DIV -> r == 0.0 ? 0.0 : l / r;
                case MOD -> r == 0.0 ? 0.0 : l % r;
                case EQ -> Math.abs(l - r) < 1e-9 ? 1.0 : 0.0;
                case NEQ -> Math.abs(l - r) >= 1e-9 ? 1.0 : 0.0;
                case LT -> l < r ? 1.0 : 0.0;
                case LTE -> l <= r ? 1.0 : 0.0;
                case GT -> l > r ? 1.0 : 0.0;
                case GTE -> l >= r ? 1.0 : 0.0;
            };
        }

        @Override
        public Set<String> referencedVariables() {
            final Set<String> vars = new HashSet<>(left.referencedVariables());
            vars.addAll(right.referencedVariables());
            return Collections.unmodifiableSet(vars);
        }
    }

    private record LogicalOrExpr(Expression left, Expression right) implements Expression {
        @Override
        public double evaluate(final Map<String, Double> variables) {
            return (left.evaluate(variables) != 0.0 || right.evaluate(variables) != 0.0) ? 1.0 : 0.0;
        }

        @Override
        public Set<String> referencedVariables() {
            final Set<String> vars = new HashSet<>(left.referencedVariables());
            vars.addAll(right.referencedVariables());
            return Collections.unmodifiableSet(vars);
        }
    }

    private record LogicalAndExpr(Expression left, Expression right) implements Expression {
        @Override
        public double evaluate(final Map<String, Double> variables) {
            return (left.evaluate(variables) != 0.0 && right.evaluate(variables) != 0.0) ? 1.0 : 0.0;
        }

        @Override
        public Set<String> referencedVariables() {
            final Set<String> vars = new HashSet<>(left.referencedVariables());
            vars.addAll(right.referencedVariables());
            return Collections.unmodifiableSet(vars);
        }
    }

    private record TernaryExpr(Expression condition, Expression thenBranch, Expression elseBranch) implements Expression {
        @Override
        public double evaluate(final Map<String, Double> variables) {
            final boolean cond = condition.evaluate(variables) != 0.0;
            return cond ? thenBranch.evaluate(variables) : elseBranch.evaluate(variables);
        }

        @Override
        public Set<String> referencedVariables() {
            final Set<String> vars = new HashSet<>(condition.referencedVariables());
            vars.addAll(thenBranch.referencedVariables());
            vars.addAll(elseBranch.referencedVariables());
            return Collections.unmodifiableSet(vars);
        }
    }

    private record MinExpr(List<Expression> args) implements Expression {
        @Override
        public double evaluate(final Map<String, Double> variables) {
            double min = Double.POSITIVE_INFINITY;
            for (final Expression arg : args) {
                final double v = arg.evaluate(variables);
                if (v < min) {
                    min = v;
                }
            }
            return Double.isFinite(min) ? min : 0.0;
        }

        @Override
        public Set<String> referencedVariables() {
            final Set<String> vars = new HashSet<>();
            for (final Expression arg : args) {
                vars.addAll(arg.referencedVariables());
            }
            return Collections.unmodifiableSet(vars);
        }
    }

    private record MaxExpr(List<Expression> args) implements Expression {
        @Override
        public double evaluate(final Map<String, Double> variables) {
            double max = Double.NEGATIVE_INFINITY;
            for (final Expression arg : args) {
                final double v = arg.evaluate(variables);
                if (v > max) {
                    max = v;
                }
            }
            return Double.isFinite(max) ? max : 0.0;
        }

        @Override
        public Set<String> referencedVariables() {
            final Set<String> vars = new HashSet<>();
            for (final Expression arg : args) {
                vars.addAll(arg.referencedVariables());
            }
            return Collections.unmodifiableSet(vars);
        }
    }

    private record ClampExpr(Expression val, Expression min, Expression max) implements Expression {
        @Override
        public double evaluate(final Map<String, Double> variables) {
            final double v = val.evaluate(variables);
            final double lo = min.evaluate(variables);
            final double hi = max.evaluate(variables);
            return Math.max(lo, Math.min(hi, v));
        }

        @Override
        public Set<String> referencedVariables() {
            final Set<String> vars = new HashSet<>(val.referencedVariables());
            vars.addAll(min.referencedVariables());
            vars.addAll(max.referencedVariables());
            return Collections.unmodifiableSet(vars);
        }
    }

    @FunctionalInterface
    private interface MathFunc {
        double apply(double v);
    }

    private record SingleArgFuncExpr(Expression arg, MathFunc func) implements Expression {
        @Override
        public double evaluate(final Map<String, Double> variables) {
            final double res = func.apply(arg.evaluate(variables));
            return Double.isFinite(res) ? res : 0.0;
        }

        @Override
        public Set<String> referencedVariables() {
            return arg.referencedVariables();
        }
    }
}
