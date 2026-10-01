package me.padej.jumper.idea.lang.psi;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class JmpParameterList extends JmpElement {
    public JmpParameterList(@NotNull ASTNode node) {
        super(node);
    }

    public List<JmpParameter> parameters() {
        return children(JmpParameter.class);
    }
}
