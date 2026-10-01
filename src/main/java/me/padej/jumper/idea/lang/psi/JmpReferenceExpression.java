package me.padej.jumper.idea.lang.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.tree.IElementType;
import me.padej.jumper.idea.lang.lexer.JumperTokenTypes;
import me.padej.jumper.idea.lang.resolve.JmpReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A name in an expression - `x`, or `q.x` with its qualifier - and the class name of a type, a `new`, an
 * `extends`, an `import`. Its reference resolves it the way the language does (see JmpResolver).
 */
public final class JmpReferenceExpression extends JmpElement {
    public JmpReferenceExpression(@NotNull ASTNode node) {
        super(node);
    }

    /** `q` of `q.x`, or null. */
    public @Nullable JmpElement getQualifier() {
        PsiElement f = getFirstChild();
        return f != null && JmpPsiUtil.isExpression(f) ? (JmpElement) f : null;
    }

    /** The name token (an IDENT, or a keyword: `int(x)`, `String`, `C.class`, `t.default`), or null if missing. */
    public @Nullable PsiElement getReferenceNameElement() {
        PsiElement last = getLastChild();
        if (last == null) return null;
        IElementType t = last.getNode().getElementType();
        return t == JumperTokenTypes.IDENT || JumperTokenTypes.KEYWORDS.contains(t) ? last : null;
    }

    public @Nullable String getReferenceName() {
        PsiElement n = getReferenceNameElement();
        return n == null ? null : n.getText();
    }

    public @Nullable IElementType nameTokenType() {
        PsiElement n = getReferenceNameElement();
        return n == null ? null : n.getNode().getElementType();
    }

    public boolean isQualified() {
        return getQualifier() != null;
    }

    /** Is this the callee of a call: `name(...)`, `q.name(...)`? */
    public boolean isCallee() {
        return getParent() instanceof JmpCallExpression c && c.getCallee() == this;
    }

    /** In `import a.b.C;` (the whole name or a part of it). */
    public boolean inImport() {
        PsiElement p = getParent();
        while (p instanceof JmpReferenceExpression) p = p.getParent();
        return p instanceof JmpImportStatement;
    }

    /** The class name of a declaration's type (`Point p`, `Random r`). */
    public boolean isTypeName() {
        return getParent() instanceof JmpTypeElement;
    }

    @Override
    public PsiReference getReference() {
        PsiElement n = getReferenceNameElement();
        if (n == null) return null;
        return new JmpReference(this, TextRange.from(n.getStartOffsetInParent(), n.getTextLength()));
    }

    public ResolveResult[] multiResolve(boolean incompleteCode) {
        PsiReference r = getReference();
        return r instanceof PsiPolyVariantReference pr ? pr.multiResolve(incompleteCode) : ResolveResult.EMPTY_ARRAY;
    }

    /** The target, if there is exactly one valid (or one at all). */
    public @Nullable PsiElement resolve() {
        PsiReference r = getReference();
        return r == null ? null : r.resolve();
    }
}
