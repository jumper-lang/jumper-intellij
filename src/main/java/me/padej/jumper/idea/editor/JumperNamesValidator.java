package me.padej.jumper.idea.editor;

import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.project.Project;
import me.padej.jumper.idea.lang.lexer.JumperTokenTypes;
import org.jetbrains.annotations.NotNull;

/** A name is a letter or `_`, then letters, digits, `_` (Lexer); the 34 reserved words are not names (lsp rename). */
public final class JumperNamesValidator implements NamesValidator {
    @Override
    public boolean isKeyword(@NotNull String name, Project project) {
        return JumperTokenTypes.KEYWORD_MAP.containsKey(name);
    }

    @Override
    public boolean isIdentifier(@NotNull String name, Project project) {
        if (name.isEmpty() || isKeyword(name, project)) return false;
        if (!Character.isLetter(name.charAt(0)) && name.charAt(0) != '_') return false;
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') return false;
        }
        return true;
    }
}
