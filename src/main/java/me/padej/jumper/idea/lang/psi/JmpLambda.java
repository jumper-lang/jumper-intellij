package me.padej.jumper.idea.lang.psi;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** `x -> e`, `(a, int b) -> { ... }`: a function value; it returns `dyn`. */
public final class JmpLambda extends JmpElement {
    public JmpLambda(@NotNull ASTNode node) {
        super(node);
    }

    public @Nullable JmpParameterList getParameterList() {
        return child(JmpParameterList.class);
    }

    public List<JmpParameter> getParameters() {
        JmpParameterList l = getParameterList();
        return l == null ? List.of() : l.parameters();
    }
}
