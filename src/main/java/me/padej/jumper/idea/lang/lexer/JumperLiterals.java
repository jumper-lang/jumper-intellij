package me.padej.jumper.idea.lang.lexer;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

import static me.padej.jumper.idea.lang.lexer.JumperTokenTypes.*;

/**
 * What the language's lexer says about a literal that {@link JumperLexer} let through: the checks of
 * Lexer.readNumber / readString with the same messages, and the value of a literal.
 */
public final class JumperLiterals {
    private JumperLiterals() {}

    /** A lexical error in the token's text and where it is (an offset in the text), or null. */
    public record Problem(String message, int offset) {}

    public static @Nullable Problem check(IElementType type, String text) {
        if (type == STRING) return checkString(text);
        if (type == INT || type == LONG || type == DOUBLE) return checkNumber(type, text);
        if (type == BLOCK_COMMENT && !(text.length() >= 4 && text.endsWith("*/"))) return new Problem("Unterminated comment", 0);
        if (type == BAD_CHARACTER) return new Problem("Unexpected character '" + text + "'", 0);
        return null;
    }

    private static Problem checkString(String text) {
        char quote = text.charAt(0);
        if (text.length() < 2 || text.charAt(text.length() - 1) != quote || endsEscaped(text)) {
            return new Problem("Unterminated string", 0);   // the language: "Newline in string literal" when a line break follows
        }
        for (int i = 1; i < text.length() - 1; i++) {
            char ch = text.charAt(i);
            if (ch != '\\') continue;
            char e = text.charAt(++i);
            switch (e) {
                case 'n', 't', 'r', '0', '\\', '"', '\'' -> { }
                case 'u' -> {
                    for (int k = 1; k <= 4; k++) {
                        if (i + k >= text.length() - 1 || Character.digit(text.charAt(i + k), 16) < 0)
                            return new Problem("Bad escape \\u: expected 4 hex digits", i - 1);
                    }
                    i += 4;
                }
                default -> { return new Problem("Bad escape \\" + e, i - 1); }
            }
        }
        return null;
    }

    /** `"abc\"`: the last quote is escaped - the string is not closed. */
    private static boolean endsEscaped(String text) {
        int n = 0;
        for (int i = text.length() - 2; i >= 1 && text.charAt(i) == '\\'; i--) n++;
        return n % 2 == 1;
    }

    private static Problem checkNumber(IElementType type, String text) {
        String t = text;
        if (t.startsWith("0x") || t.startsWith("0X")) {
            boolean isLong = t.endsWith("L") || t.endsWith("l");
            String hex = t.substring(2, isLong ? t.length() - 1 : t.length()).replace("_", "");
            if (hex.isEmpty()) return new Problem("Hex literal without digits: " + text, 0);
            String digits = hex.replaceFirst("^0+", "");
            if (digits.length() > (isLong ? 16 : 8))
                return new Problem(isLong ? "Hex literal too large for long: " + text : "Hex literal too large for int (use L suffix): " + text, 0);
            return null;
        }
        int e = Math.max(t.indexOf('e'), t.indexOf('E'));
        if (e >= 0) {
            String exp = t.substring(e + 1);
            if (exp.startsWith("+") || exp.startsWith("-")) exp = exp.substring(1);
            if (exp.endsWith("d") || exp.endsWith("D")) exp = exp.substring(0, exp.length() - 1);
            if (exp.isEmpty()) return new Problem("Exponent without digits: " + text, 0);
        }
        if (type == LONG) {
            String clean = t.substring(0, t.length() - 1).replace("_", "");
            try {
                Long.parseLong(clean);
            } catch (NumberFormatException ex) {
                if (!clean.equals("9223372036854775808")) return new Problem("Long literal too large: " + text, 0);
            }
        } else if (type == INT) {
            String clean = t.replace("_", "");
            try {
                Integer.parseInt(clean);
            } catch (NumberFormatException ex) {
                if (!clean.equals("2147483648")) return new Problem("Integer literal too large (use L suffix): " + text, 0);
            }
        }
        return null;
    }

    /** `2147483648` / `9223372036854775808L`: valid only right after a unary minus (the parser checks it). */
    public static boolean isMinValueMagnitude(IElementType type, String text) {
        String clean = text.replace("_", "");
        return type == INT && clean.equals("2147483648") || type == LONG && clean.equalsIgnoreCase("9223372036854775808L");
    }

    /** The value of a string literal: without its quotes, escapes decoded (a broken escape is kept as it is). */
    public static String stringValue(String text) {
        if (text.isEmpty()) return "";
        char quote = text.charAt(0);
        int stop = text.length() > 1 && text.charAt(text.length() - 1) == quote && !endsEscaped(text) ? text.length() - 1 : text.length();
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < stop; i++) {
            char ch = text.charAt(i);
            if (ch != '\\' || i + 1 >= stop) { sb.append(ch); continue; }
            char e = text.charAt(++i);
            switch (e) {
                case 'n' -> sb.append('\n');
                case 't' -> sb.append('\t');
                case 'r' -> sb.append('\r');
                case '0' -> sb.append('\0');
                case 'u' -> {
                    if (i + 4 < stop + 1 && i + 4 <= text.length() - 1) {
                        try {
                            sb.append((char) Integer.parseInt(text.substring(i + 1, i + 5), 16));
                            i += 4;
                        } catch (NumberFormatException ex) {
                            sb.append("\\u");
                        }
                    } else sb.append("\\u");
                }
                default -> sb.append(e);
            }
        }
        return sb.toString();
    }
}
