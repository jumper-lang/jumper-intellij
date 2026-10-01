package me.padej.jumper.idea.workspace;

import com.intellij.psi.tree.IElementType;
import me.padej.jumper.idea.lang.lexer.JumperLexer;
import me.padej.jumper.idea.lang.lexer.JumperLiterals;
import me.padej.jumper.idea.lang.lexer.JumperTokenTypes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static me.padej.jumper.idea.lang.lexer.JumperTokenTypes.*;

/**
 * Reads files that are data written in Jumper - a host descriptor (`host.jmc`), an access policy (`*.jma`) -
 * from their tokens, never running anything. The language evaluates them (Config.parse, Policy.build); these
 * files are literals and calls with literal arguments in practice, and that is what is read here:
 * <ul>
 *   <li>{@link #values}: `T name = value;` at the top level, a value being a string, a number, `true`/`false`,
 *       an array `[...]` or a table `{ k: v }` of those, or strings joined with `+`;</li>
 *   <li>{@link #calls}: chains of calls `A.m("x").n("y", 1);` - the receiver and each call with its literal arguments.</li>
 * </ul>
 */
public final class JumperData {
    private JumperData() {}

    private record Tok(IElementType type, String text) {}

    private static List<Tok> tokens(CharSequence text) {
        List<Tok> out = new ArrayList<>();
        JumperLexer lx = new JumperLexer();
        lx.start(text, 0, text.length(), 0);
        for (IElementType t; (t = lx.getTokenType()) != null; lx.advance()) {
            if (t == WHITE_SPACE || JumperTokenTypes.COMMENTS.contains(t)) continue;
            out.add(new Tok(t, text.subSequence(lx.getTokenStart(), lx.getTokenEnd()).toString()));
        }
        return out;
    }

    /** Top-level `T name = value;` -> value (String, Long, Double, Boolean, List, Map); what cannot be read goes to problems. */
    public static Map<String, Object> values(CharSequence text, List<String> problems) {
        List<Tok> ts = tokens(text);
        Map<String, Object> out = new LinkedHashMap<>();
        int[] p = {0};
        while (p[0] < ts.size()) {
            int start = p[0];
            Tok t = ts.get(p[0]);
            boolean typed = TYPE_KEYWORDS.contains(t.type) || t.type == IDENT;
            if (typed && p[0] + 2 < ts.size() && ts.get(p[0] + 1).type == IDENT && ts.get(p[0] + 2).type == EQ) {
                String name = ts.get(p[0] + 1).text;
                p[0] += 3;
                Object v = value(ts, p);
                if (v == NONE) problems.add(name + ": only literal values are read (strings, numbers, arrays, tables)");
                else out.put(name, v);
            }
            // to the end of the statement
            int depth = 0;
            while (p[0] < ts.size()) {
                IElementType u = ts.get(p[0]).type;
                if (u == LBRACE || u == LBRACKET || u == LPAREN) depth++;
                else if (u == RBRACE || u == RBRACKET || u == RPAREN) depth--;
                else if (u == SEMI && depth <= 0) { p[0]++; break; }
                p[0]++;
            }
            if (p[0] == start) p[0]++;
        }
        return out;
    }

    private static final Object NONE = new Object();

    private static Object value(List<Tok> ts, int[] p) {
        Object v = single(ts, p);
        while (v instanceof String s && p[0] < ts.size() && ts.get(p[0]).type == PLUS) {
            p[0]++;
            Object r = single(ts, p);
            if (r == NONE) return NONE;
            v = s + (r instanceof Double || r instanceof Long || r instanceof Boolean || r instanceof String ? r : "");
        }
        return v;
    }

    private static Object single(List<Tok> ts, int[] p) {
        if (p[0] >= ts.size()) return NONE;
        Tok t = ts.get(p[0]++);
        if (t.type == STRING) return JumperLiterals.stringValue(t.text);
        if (t.type == TRUE) return Boolean.TRUE;
        if (t.type == FALSE) return Boolean.FALSE;
        if (t.type == NULL) return null;
        if (t.type == INT || t.type == LONG) {
            try {
                String c = t.text.replace("_", "").replaceAll("[lL]$", "");
                return c.startsWith("0x") || c.startsWith("0X") ? Long.parseLong(c.substring(2), 16) : Long.parseLong(c);
            } catch (NumberFormatException e) {
                return NONE;
            }
        }
        if (t.type == DOUBLE) {
            try { return Double.parseDouble(t.text.replace("_", "").replaceAll("[dD]$", "")); } catch (NumberFormatException e) { return NONE; }
        }
        if (t.type == LBRACKET) return list(ts, p, RBRACKET);
        if (t.type == LBRACE) {
            boolean table = p[0] + 1 < ts.size() && ts.get(p[0] + 1).type == COLON || p[0] < ts.size() && ts.get(p[0]).type == RBRACE;
            if (!table) return list(ts, p, RBRACE);
            Map<String, Object> m = new LinkedHashMap<>();
            while (p[0] < ts.size() && ts.get(p[0]).type != RBRACE) {
                Tok k = ts.get(p[0]++);
                String key = k.type == STRING ? JumperLiterals.stringValue(k.text) : k.text;
                if (p[0] >= ts.size() || ts.get(p[0]).type != COLON) return NONE;
                p[0]++;
                Object v = value(ts, p);
                if (v == NONE) return NONE;
                m.put(key, v);
                if (p[0] < ts.size() && ts.get(p[0]).type == COMMA) p[0]++;
            }
            p[0]++;
            return m;
        }
        return NONE;
    }

    private static Object list(List<Tok> ts, int[] p, IElementType close) {
        List<Object> l = new ArrayList<>();
        while (p[0] < ts.size() && ts.get(p[0]).type != close) {
            Object v = value(ts, p);
            if (v == NONE) return NONE;
            l.add(v);
            if (p[0] < ts.size() && ts.get(p[0]).type == COMMA) p[0]++;
        }
        p[0]++;
        return l;
    }

    /** One call of a chain: its name and its literal arguments (NONE-free; a non-literal argument makes it null). */
    public record Call(String name, List<Object> args) {}

    /** Each statement `R.a(...).b(...)...;` that starts with `receiver`: its calls in order. */
    public static List<List<Call>> calls(CharSequence text, String receiver) {
        List<Tok> ts = tokens(text);
        List<List<Call>> out = new ArrayList<>();
        int i = 0;
        while (i < ts.size()) {
            if (ts.get(i).type == IDENT && ts.get(i).text.equals(receiver) && (i == 0 || ts.get(i - 1).type == SEMI
                    || ts.get(i - 1).type == RBRACE || ts.get(i - 1).type == LBRACE)) {
                List<Call> chain = new ArrayList<>();
                int[] p = {i + 1};
                while (p[0] + 2 < ts.size() && ts.get(p[0]).type == DOT && ts.get(p[0] + 1).type == IDENT && ts.get(p[0] + 2).type == LPAREN) {
                    String name = ts.get(p[0] + 1).text;
                    p[0] += 3;
                    List<Object> args = new ArrayList<>();
                    boolean literal = true;
                    while (p[0] < ts.size() && ts.get(p[0]).type != RPAREN) {
                        Object v = value(ts, p);
                        if (v == NONE) { literal = false; break; }
                        args.add(v);
                        if (p[0] < ts.size() && ts.get(p[0]).type == COMMA) p[0]++;
                    }
                    // to the ')' of this call
                    int depth = 0;
                    while (p[0] < ts.size()) {
                        IElementType u = ts.get(p[0]).type;
                        if (u == LPAREN) depth++;
                        else if (u == RPAREN && depth-- == 0) { p[0]++; break; }
                        else if (u == SEMI) break;
                        p[0]++;
                    }
                    chain.add(new Call(name, literal ? args : null));
                }
                if (!chain.isEmpty()) out.add(chain);
                i = Math.max(p[0], i + 1);
            } else i++;
        }
        return out;
    }
}
