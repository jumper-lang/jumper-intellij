package me.padej.jumper.idea.lang.psi;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** `new C(args)` - C a script class, a Java class (`new java.util.HashMap()`), or a class from an expression (`new registry.cat()`). */
public final class JmpNewExpression extends JmpElement {
    public JmpNewExpression(@NotNull ASTNode node) {
        super(node);
    }

    /** The class part: a name (JmpReferenceExpression) or an index expression. */
    public @Nullable JmpElement getClassExpression() {
        return firstExpression();
    }

    public @Nullable JmpReferenceExpression getClassReference() {
        return getClassExpression() instanceof JmpReferenceExpression r ? r : null;
    }

    public @Nullable JmpArgumentList getArgumentList() {
        return child(JmpArgumentList.class);
    }

    public List<JmpElement> getArguments() {
        JmpArgumentList l = getArgumentList();
        return l == null ? List.of() : l.expressions();
    }
}
