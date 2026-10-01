package me.padej.jumper.idea.lang.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import me.padej.jumper.idea.lang.lexer.JumperTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** The key of `{ key: value }`: a name (a string key, not a variable), a string, a number, or `[expr]`. */
public final class JmpTableKey extends JmpElement {
    public JmpTableKey(@NotNull ASTNode node) {
        super(node);
    }

    /** `key` of `{ key: 1 }` (also `{ class: 1 }`): the word, else null. */
    public @Nullable PsiElement nameToken() {
        PsiElement f = getFirstChild();
        if (f == null || f != getLastChild()) return null;
        var t = f.getNode().getElementType();
        return t == JumperTokenTypes.IDENT || JumperTokenTypes.KEYWORDS.contains(t) ? f : null;
    }
}
