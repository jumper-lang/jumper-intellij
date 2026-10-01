package me.padej.jumper.idea.lang.psi;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/** `{ statements }`: a scope - its functions and classes exist from its start, its variables after their declarators. */
public final class JmpBlock extends JmpElement {
    public JmpBlock(@NotNull ASTNode node) {
        super(node);
    }
}
