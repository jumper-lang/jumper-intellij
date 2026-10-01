package me.padej.jumper.idea.highlighter;

import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler;
import me.padej.jumper.idea.lang.lexer.JumperTokenTypes;

/** Typing `"` or `'` closes the string (both quote a string of any length). */
public final class JumperQuoteHandler extends SimpleTokenSetQuoteHandler {
    public JumperQuoteHandler() {
        super(JumperTokenTypes.STRING);
    }
}
