package me.padej.jumper.idea.lang.psi;

import com.intellij.icons.AllIcons;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import me.padej.jumper.idea.lang.lexer.JumperTokenTypes;
import me.padej.jumper.idea.lang.parser.JumperElementTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

/**
 * One declarator: `a = 1` of `dyn a = 1, b;` (a variable, or a field in a class body), or the variable of a
 * for-each. Visible after itself - `dyn x = x;` reads the outer x.
 */
public final class JmpVariable extends JmpNamedElement {
    public JmpVariable(@NotNull ASTNode node) {
        super(node);
    }

    /** The type of its declaration (shared by the declarators of one `T a, b;`). */
    public @Nullable JmpTypeElement getTypeElement() {
        PsiElement p = getParent();
        return p instanceof JmpElement e ? e.child(JmpTypeElement.class) : null;
    }

    public @Nullable JmpElement getInitializer() {
        return firstExpression();
    }

    /** A field: declared in a class body. */
    public boolean isField() {
        PsiElement p = getParent();
        return p != null && p.getParent() instanceof JmpClassBody;
    }

    public boolean isStatic() {
        PsiElement decl = getParent();
        return isField() && decl.getNode().findChildByType(JumperTokenTypes.STATIC) != null;
    }

    public boolean isForEach() {
        PsiElement p = getParent();
        return p instanceof JmpElement e && e.is(JumperElementTypes.FOREACH_STATEMENT);
    }

    public @Nullable JmpClass containingClass() {
        return isField() ? ((JmpClassBody) getParent().getParent()).owner() : null;
    }

    @Override
    public String kindName() {
        return isField() ? "field" : "variable";
    }

    @Override
    public Icon kindIcon() {
        return isField() ? AllIcons.Nodes.Field : AllIcons.Nodes.Variable;
    }

    @Override
    public @Nullable String detail() {
        JmpTypeElement t = getTypeElement();
        return t == null ? null : t.getText();
    }
}
