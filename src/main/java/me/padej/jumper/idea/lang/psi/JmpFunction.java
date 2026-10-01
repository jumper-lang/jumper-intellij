package me.padej.jumper.idea.lang.psi;

import com.intellij.icons.AllIcons;
import com.intellij.lang.ASTNode;
import me.padej.jumper.idea.lang.lexer.JumperTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.List;

/**
 * `T name(params) { body }`: a function (hoisted - it exists in its whole block), a method in a class body, or
 * the constructor `C(params) { ... }` (no type).
 */
public final class JmpFunction extends JmpNamedElement {
    public JmpFunction(@NotNull ASTNode node) {
        super(node);
    }

    /** The return type; null for a constructor. */
    public @Nullable JmpTypeElement getTypeElement() {
        return child(JmpTypeElement.class);
    }

    public @Nullable JmpParameterList getParameterList() {
        return child(JmpParameterList.class);
    }

    public List<JmpParameter> getParameters() {
        JmpParameterList l = getParameterList();
        return l == null ? List.of() : l.parameters();
    }

    public @Nullable JmpBlock getBody() {
        return child(JmpBlock.class);
    }

    public boolean isMethod() {
        return getParent() instanceof JmpClassBody;
    }

    public boolean isConstructor() {
        return isMethod() && getTypeElement() == null;
    }

    public boolean isStatic() {
        return getNode().findChildByType(JumperTokenTypes.STATIC) != null;
    }

    public @Nullable JmpClass containingClass() {
        return getParent() instanceof JmpClassBody b ? b.owner() : null;
    }

    @Override
    public String kindName() {
        return isConstructor() ? "constructor" : isMethod() ? "method" : "function";
    }

    @Override
    public Icon kindIcon() {
        return isMethod() ? AllIcons.Nodes.Method : AllIcons.Nodes.Function;
    }

    /** `(int a, b)` and the return type: what the outline shows (Symbols.function). */
    @Override
    public @Nullable String detail() {
        JmpParameterList l = getParameterList();
        String params = l == null ? "()" : l.getText().replaceAll("\\s+", " ");
        JmpTypeElement t = getTypeElement();
        return t == null || t.isVoid() ? params : params + ": " + t.getText();
    }
}
