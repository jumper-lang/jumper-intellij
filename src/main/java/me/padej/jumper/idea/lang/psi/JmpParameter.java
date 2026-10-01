package me.padej.jumper.idea.lang.psi;

import com.intellij.icons.AllIcons;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

/** `[T] name` of a function, a method, a lambda - or the `e` of `catch (e)`. Without a type it is `dyn`. */
public final class JmpParameter extends JmpNamedElement {
    public JmpParameter(@NotNull ASTNode node) {
        super(node);
    }

    public @Nullable JmpTypeElement getTypeElement() {
        return child(JmpTypeElement.class);
    }

    @Override
    public String kindName() {
        return "parameter";
    }

    @Override
    public Icon kindIcon() {
        return AllIcons.Nodes.Parameter;
    }

    @Override
    public @Nullable String detail() {
        JmpTypeElement t = getTypeElement();
        return t == null ? "dyn" : t.getText();
    }
}
