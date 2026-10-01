package me.padej.jumper.idea.lang.resolve;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.util.IncorrectOperationException;
import me.padej.jumper.idea.lang.psi.JmpLiteral;
import me.padej.jumper.idea.lang.psi.JmpModuleImport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** The path of `import "utils.jmp";`: the module file. */
public final class JmpModuleReference extends PsiReferenceBase<JmpLiteral> {
    public JmpModuleReference(@NotNull JmpLiteral element, TextRange range) {
        super(element, range, false);
    }

    @Override
    public @Nullable PsiElement resolve() {
        return myElement.getParent() instanceof JmpModuleImport mi ? mi.resolveModule() : null;
    }

    @Override
    public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
        return myElement;   // a renamed module file: the import keeps its text (the path may leave out .jmp)
    }
}
