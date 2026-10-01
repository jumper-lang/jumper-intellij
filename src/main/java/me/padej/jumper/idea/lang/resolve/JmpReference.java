package me.padej.jumper.idea.lang.resolve;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.util.IncorrectOperationException;
import me.padej.jumper.idea.lang.lexer.JumperTokenTypes;
import me.padej.jumper.idea.lang.psi.JmpElementFactory;
import me.padej.jumper.idea.lang.psi.JmpReferenceExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** The reference of a name: resolved by {@link JmpResolver}, cached until the next change of the code. */
public final class JmpReference extends PsiPolyVariantReferenceBase<JmpReferenceExpression> {
    public JmpReference(@NotNull JmpReferenceExpression element, TextRange range) {
        super(element, range, false);
    }

    @Override
    public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
        ResolveResult[] rs = ResolveCache.getInstance(myElement.getProject()).resolveWithCaching(this, JmpResolver.INSTANCE, true, incompleteCode);
        return rs == null ? ResolveResult.EMPTY_ARRAY : rs;   // null: asked again while resolving it (a type through itself)
    }

    /** The valid target if there is one (of overloads: the one that takes as many arguments, or the first valid). */
    @Override
    public @Nullable PsiElement resolve() {
        ResolveResult[] rs = multiResolve(false);
        for (ResolveResult r : rs) if (r.isValidResult()) return r.getElement();
        return rs.length == 1 ? rs[0].getElement() : null;
    }

    @Override
    public PsiElement handleElementRename(@NotNull String newName) throws IncorrectOperationException {
        PsiElement name = myElement.getReferenceNameElement();
        if (name == null || name.getNode().getElementType() != JumperTokenTypes.IDENT) return myElement;
        // a Java property read as `obj.name` renamed through its getter: keep the property form
        if (newName.startsWith("get") && newName.length() > 3 && resolve() instanceof PsiMethod m && !m.getName().equals(name.getText())) {
            newName = Character.toLowerCase(newName.charAt(3)) + newName.substring(4);
        }
        name.replace(JmpElementFactory.identifier(myElement.getProject(), newName));
        return myElement;
    }

    @Override
    public Object @NotNull [] getVariants() {
        return EMPTY_ARRAY;   // completion is JumperCompletionContributor's
    }
}
