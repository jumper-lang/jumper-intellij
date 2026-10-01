package me.padej.jumper.idea.lang.psi;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.IncorrectOperationException;
import me.padej.jumper.idea.lang.lexer.JumperTokenTypes;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

/** A declaration of the file: a variable, parameter, function, method, constructor or class. Its name is its IDENT child. */
public abstract class JmpNamedElement extends JmpElement implements PsiNameIdentifierOwner {
    protected JmpNamedElement(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public @Nullable PsiElement getNameIdentifier() {
        ASTNode n = getNode().findChildByType(JumperTokenTypes.IDENT);
        return n == null ? null : n.getPsi();
    }

    @Override
    public @Nullable String getName() {
        PsiElement id = getNameIdentifier();
        return id == null ? null : id.getText();
    }

    @Override
    public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
        PsiElement id = getNameIdentifier();
        if (id != null) id.replace(JmpElementFactory.identifier(getProject(), name));
        return this;
    }

    @Override
    public int getTextOffset() {
        PsiElement id = getNameIdentifier();
        return id != null ? id.getTextOffset() : super.getTextOffset();
    }

    /** One of the kinds of the outline: "variable", "field", "parameter", "function", "method", "constructor", "class". */
    public abstract String kindName();

    public abstract Icon kindIcon();

    /** What the outline and the lookup show after the name: a type, a parameter list. */
    public @Nullable String detail() {
        return null;
    }

    @Override
    public Icon getIcon(int flags) {
        return kindIcon();
    }

    @Override
    public @NotNull SearchScope getUseScope() {
        // a name declared inside a function, a class, a block is seen in its file only; a top-level one
        // may be imported by a module (import "file.jmp")
        return JmpPsiUtil.isTopLevel(this) ? super.getUseScope() : new LocalSearchScope(getContainingFile());
    }

    @Override
    public ItemPresentation getPresentation() {
        return new ItemPresentation() {
            @Override
            public String getPresentableText() {
                String d = detail();
                return getName() + (d == null ? "" : d.startsWith("(") ? d : ": " + d);
            }

            @Override
            public String getLocationString() {
                return getContainingFile() == null ? null : getContainingFile().getName();
            }

            @Override
            public Icon getIcon(boolean unused) {
                return kindIcon();
            }
        };
    }
}
