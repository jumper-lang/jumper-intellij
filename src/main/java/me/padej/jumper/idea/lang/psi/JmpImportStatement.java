package me.padej.jumper.idea.lang.psi;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** `import a.b.C;`: declares the simple name C, from here on (a Java import is not hoisted). */
public final class JmpImportStatement extends JmpElement {
    public JmpImportStatement(@NotNull ASTNode node) {
        super(node);
    }

    /** The whole name `a.b.C` (its last part is the class). */
    public @Nullable JmpReferenceExpression getImportReference() {
        return child(JmpReferenceExpression.class);
    }

    public @Nullable String getQualifiedName() {
        JmpReferenceExpression r = getImportReference();
        return r == null ? null : r.getText().replaceAll("\\s+", "");
    }

    public @Nullable String getSimpleName() {
        JmpReferenceExpression r = getImportReference();
        return r == null ? null : r.getReferenceName();
    }
}
