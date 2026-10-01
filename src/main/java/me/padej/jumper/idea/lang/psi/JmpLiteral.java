package me.padej.jumper.idea.lang.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.tree.IElementType;
import me.padej.jumper.idea.lang.lexer.JumperLiterals;
import me.padej.jumper.idea.lang.lexer.JumperTokenTypes;
import me.padej.jumper.idea.lang.resolve.JmpModuleReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A number, a string, `true`, `false`, `null`. The string of `import "file.jmp";` refers to the module. */
public final class JmpLiteral extends JmpElement {
    public JmpLiteral(@NotNull ASTNode node) {
        super(node);
    }

    public IElementType tokenType() {
        PsiElement f = getFirstChild();
        return f == null ? null : f.getNode().getElementType();
    }

    public boolean isString() {
        return tokenType() == JumperTokenTypes.STRING;
    }

    /** The value of a string literal (escapes decoded), else null. */
    public @Nullable String stringValue() {
        return isString() ? JumperLiterals.stringValue(getText()) : null;
    }

    @Override
    public PsiReference getReference() {
        if (isString() && getParent() instanceof JmpModuleImport && getTextLength() >= 2)
            return new JmpModuleReference(this, new TextRange(1, getTextLength() - (getText().endsWith(getText().substring(0, 1)) ? 1 : 0)));
        return null;
    }

    @Override
    public PsiReference @NotNull [] getReferences() {
        PsiReference r = getReference();
        return r == null ? ReferenceProvidersRegistry.getReferencesFromProviders(this) : new PsiReference[] {r};
    }
}
