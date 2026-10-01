package me.padej.jumper.idea.lang.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import me.padej.jumper.idea.lang.lexer.JumperTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** The type of a declaration: a keyword (`dyn`, `int` ... `String`, `void`) or a class name - a script class or a Java class. */
public final class JmpTypeElement extends JmpElement {
    public JmpTypeElement(@NotNull ASTNode node) {
        super(node);
    }

    /** The keyword of a keyword type, else null. */
    public @Nullable IElementType keyword() {
        PsiElement f = getFirstChild();
        if (f == null) return null;
        IElementType t = f.getNode().getElementType();
        return JumperTokenTypes.KEYWORDS.contains(t) ? t : null;
    }

    /** The class name of a class type, else null. */
    public @Nullable JmpReferenceExpression reference() {
        return child(JmpReferenceExpression.class);
    }

    public boolean isDyn() {
        return keyword() == JumperTokenTypes.DYN;
    }

    public boolean isVoid() {
        return keyword() == JumperTokenTypes.VOID;
    }

    /** `int`, `long`, `double`, `boolean`: the types held without boxing. */
    public boolean isPrimitive() {
        IElementType k = keyword();
        return k == JumperTokenTypes.KW_INT || k == JumperTokenTypes.KW_LONG || k == JumperTokenTypes.KW_DOUBLE || k == JumperTokenTypes.KW_BOOLEAN;
    }
}
