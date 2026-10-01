package me.padej.jumper.idea.editor;

import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.psi.PsiElement;
import me.padej.jumper.idea.lang.psi.JmpNamedElement;
import me.padej.jumper.idea.lang.psi.JmpPsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Rename in place: a name declared inside a function or a block; a top-level one (a module may import it) through the dialog. */
public final class JumperRefactoringSupportProvider extends RefactoringSupportProvider {
    @Override
    public boolean isMemberInplaceRenameAvailable(@NotNull PsiElement element, @Nullable PsiElement context) {
        return element instanceof JmpNamedElement;
    }

    @Override
    public boolean isInplaceRenameAvailable(@NotNull PsiElement element, PsiElement context) {
        return element instanceof JmpNamedElement n && !JmpPsiUtil.isTopLevel(n);
    }
}
