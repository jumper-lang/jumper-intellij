package me.padej.jumper.idea.lang.psi;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** `callee(args)`: a function, a method (`q.m(args)`), a constructor of the parent (`super(args)`), a built-in. */
public final class JmpCallExpression extends JmpElement {
    public JmpCallExpression(@NotNull ASTNode node) {
        super(node);
    }

    public @Nullable JmpElement getCallee() {
        return firstExpression();
    }

    public @Nullable JmpArgumentList getArgumentList() {
        return child(JmpArgumentList.class);
    }

    public List<JmpElement> getArguments() {
        JmpArgumentList l = getArgumentList();
        return l == null ? List.of() : l.expressions();
    }
}
