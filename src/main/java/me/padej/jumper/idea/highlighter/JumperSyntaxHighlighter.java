package me.padej.jumper.idea.highlighter;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import me.padej.jumper.idea.lang.lexer.JumperLexer;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

import static me.padej.jumper.idea.lang.lexer.JumperTokenTypes.*;

/** Coloring by the tokens alone - immediate, before any analysis; the annotator adds what names are. */
public final class JumperSyntaxHighlighter extends SyntaxHighlighterBase {
    private static final Map<IElementType, TextAttributesKey> KEYS = new HashMap<>();

    static {
        for (IElementType k : KEYWORDS.getTypes()) KEYS.put(k, JumperHighlighterColors.KEYWORD);
        KEYS.put(DYN, JumperHighlighterColors.DYN);
        for (IElementType n : NUMBERS.getTypes()) KEYS.put(n, JumperHighlighterColors.NUMBER);
        KEYS.put(STRING, JumperHighlighterColors.STRING);
        KEYS.put(LINE_COMMENT, JumperHighlighterColors.LINE_COMMENT);
        KEYS.put(BLOCK_COMMENT, JumperHighlighterColors.BLOCK_COMMENT);
        for (IElementType o : OPERATORS.getTypes()) KEYS.put(o, JumperHighlighterColors.OPERATOR);
        KEYS.put(LPAREN, JumperHighlighterColors.PARENTHESES);
        KEYS.put(RPAREN, JumperHighlighterColors.PARENTHESES);
        KEYS.put(LBRACE, JumperHighlighterColors.BRACES);
        KEYS.put(RBRACE, JumperHighlighterColors.BRACES);
        KEYS.put(LBRACKET, JumperHighlighterColors.BRACKETS);
        KEYS.put(RBRACKET, JumperHighlighterColors.BRACKETS);
        KEYS.put(COMMA, JumperHighlighterColors.COMMA);
        KEYS.put(DOT, JumperHighlighterColors.DOT);
        KEYS.put(SEMI, JumperHighlighterColors.SEMICOLON);
        KEYS.put(IDENT, JumperHighlighterColors.IDENTIFIER);
        KEYS.put(BAD_CHARACTER, JumperHighlighterColors.BAD_CHARACTER);
    }

    @Override
    public @NotNull Lexer getHighlightingLexer() {
        return new JumperLexer();
    }

    @Override
    public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
        return pack(KEYS.get(tokenType));
    }
}
