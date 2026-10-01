package me.padej.jumper.idea.lang.psi;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** A node of the Jumper tree; the kind of a node is its element type (most constructs need no class of their own). */
public class JmpElement extends ASTWrapperPsiElement {
    public JmpElement(@NotNull ASTNode node) {
        super(node);
    }

    public IElementType type() {
        return getNode().getElementType();
    }

    public boolean is(IElementType t) {
        return type() == t;
    }

    /** The first child node of one of the types. */
    public @Nullable PsiElement child(IElementType... types) {
        ASTNode n = getNode().findChildByType(TokenSet.create(types));
        return n == null ? null : n.getPsi();
    }

    public @Nullable <T extends PsiElement> T child(Class<T> cls) {
        for (PsiElement c = getFirstChild(); c != null; c = c.getNextSibling()) if (cls.isInstance(c)) return cls.cast(c);
        return null;
    }

    public <T extends PsiElement> List<T> children(Class<T> cls) {
        List<T> out = new ArrayList<>();
        for (PsiElement c = getFirstChild(); c != null; c = c.getNextSibling()) if (cls.isInstance(c)) out.add(cls.cast(c));
        return out;
    }

    /** The child nodes that are expressions, in order. */
    public List<JmpElement> expressions() {
        List<JmpElement> out = new ArrayList<>();
        for (PsiElement c = getFirstChild(); c != null; c = c.getNextSibling())
            if (JmpPsiUtil.isExpression(c)) out.add((JmpElement) c);
        return out;
    }

    public @Nullable JmpElement firstExpression() {
        for (PsiElement c = getFirstChild(); c != null; c = c.getNextSibling()) if (JmpPsiUtil.isExpression(c)) return (JmpElement) c;
        return null;
    }

    @Override
    public String toString() {
        return getNode().getElementType().toString();
    }
}
