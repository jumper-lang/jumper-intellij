package me.padej.jumper.idea.lang.psi;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

public final class JmpArgumentList extends JmpElement {
    public JmpArgumentList(@NotNull ASTNode node) {
        super(node);
    }
}
