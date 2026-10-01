package me.padej.jumper.idea.lang.resolve;

import com.intellij.icons.AllIcons;
import com.intellij.navigation.ItemPresentation;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.FakePsiElement;
import me.padej.jumper.idea.JumperLanguage;
import com.intellij.lang.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.Objects;

/**
 * A name that exists for a script but is declared nowhere in a file: a built-in function (`println`), a global
 * the host defines (`server`, typed by the host descriptor). The target of their references - for hover,
 * highlighting, completion; going to a host global opens the class of its type.
 */
public final class JmpSynthetic extends FakePsiElement {
    public enum Kind { BUILTIN, HOST_GLOBAL }

    private final Kind kind;
    private final String name;
    /** A host global: its type (a Java class name, "function", "dyn"); a built-in: its first signature. */
    private final String detail;
    private final PsiElement context;

    public JmpSynthetic(Kind kind, String name, String detail, PsiElement context) {
        this.kind = kind;
        this.name = name;
        this.detail = detail;
        this.context = context;
    }

    public Kind kind() {
        return kind;
    }

    public String detail() {
        return detail;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public PsiElement getParent() {
        return context.getContainingFile();
    }

    @Override
    public @NotNull Language getLanguage() {
        return JumperLanguage.INSTANCE;
    }

    /** The class of a host global's type, if it is one the project knows. */
    public @Nullable PsiClass typeClass() {
        return kind == Kind.HOST_GLOBAL ? JmpJava.findClass(detail, context) : null;
    }

    @Override
    public @NotNull PsiElement getNavigationElement() {
        PsiClass c = typeClass();
        return c != null ? c : this;
    }

    @Override
    public void navigate(boolean requestFocus) {
        PsiElement n = getNavigationElement();
        if (n != this && n instanceof Navigatable nav) nav.navigate(requestFocus);
    }

    @Override
    public boolean canNavigate() {
        return typeClass() != null;
    }

    @Override
    public @Nullable Icon getIcon(boolean open) {
        return kind == Kind.BUILTIN ? AllIcons.Nodes.Function : AllIcons.Nodes.Variable;
    }

    @Override
    public ItemPresentation getPresentation() {
        return new ItemPresentation() {
            @Override
            public String getPresentableText() {
                return name;
            }

            @Override
            public String getLocationString() {
                return kind == Kind.BUILTIN ? "built-in" : detail;
            }

            @Override
            public Icon getIcon(boolean unused) {
                return JmpSynthetic.this.getIcon(false);
            }
        };
    }

    @Override
    public boolean isEquivalentTo(PsiElement another) {
        return equals(another);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof JmpSynthetic s && s.kind == kind && s.name.equals(name) && Objects.equals(s.getContainingFile(), getContainingFile());
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, name);
    }

    @Override
    public String toString() {
        return kind + " " + name;
    }
}
