package me.padej.jumper.idea.lang.psi;

import com.intellij.icons.AllIcons;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveResult;
import me.padej.jumper.idea.lang.parser.JumperElementTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.ArrayList;
import java.util.List;

/** `class N [extends P] { members }`: a script class - sealed, one constructor, no overloading. */
public final class JmpClass extends JmpNamedElement {
    public JmpClass(@NotNull ASTNode node) {
        super(node);
    }

    public @Nullable JmpClassBody getBody() {
        return child(JmpClassBody.class);
    }

    /** The `P` of `extends P` (a reference; `a.b.P` - the last one), or null. */
    public @Nullable JmpReferenceExpression getExtendsReference() {
        PsiElement e = child(JumperElementTypes.EXTENDS_CLAUSE);
        return e instanceof JmpElement je ? je.child(JmpReferenceExpression.class) : null;
    }

    /** The script class this one extends, if it is one of the file (or of a module). */
    public @Nullable JmpClass getSuperClass() {
        JmpReferenceExpression r = getExtendsReference();
        if (r == null) return null;
        ResolveResult[] rs = r.multiResolve(false);
        return rs.length > 0 && rs[0].getElement() instanceof JmpClass c && c != this ? c : null;
    }

    public List<JmpVariable> getFields() {
        JmpClassBody b = getBody();
        return b == null ? List.of() : b.fields();
    }

    public List<JmpFunction> getMethods() {
        JmpClassBody b = getBody();
        if (b == null) return List.of();
        List<JmpFunction> out = new ArrayList<>();
        for (JmpFunction f : b.functions()) if (!f.isConstructor()) out.add(f);
        return out;
    }

    public @Nullable JmpFunction getConstructor() {
        JmpClassBody b = getBody();
        if (b == null) return null;
        for (JmpFunction f : b.functions()) if (f.isConstructor()) return f;
        return null;
    }

    /** A field or method of this class or of the script classes it extends (Symbols.member). */
    public @Nullable JmpNamedElement findMember(String name) {
        JmpClass c = this;
        for (int guard = 0; c != null && guard < 32; guard++) {
            for (JmpVariable f : c.getFields()) if (name.equals(f.getName())) return f;
            for (JmpFunction m : c.getMethods()) if (name.equals(m.getName())) return m;
            c = c.getSuperClass();
        }
        return null;
    }

    /** Every member, this class's first, then its superclasses' (not repeated). */
    public List<JmpNamedElement> allMembers() {
        List<JmpNamedElement> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        JmpClass c = this;
        for (int guard = 0; c != null && guard < 32; guard++) {
            for (JmpVariable f : c.getFields()) if (seen.add(f.getName())) out.add(f);
            for (JmpFunction m : c.getMethods()) if (seen.add(m.getName())) out.add(m);
            c = c.getSuperClass();
        }
        return out;
    }

    /** The number of arguments `new C(...)` takes: its constructor's, else the nearest superclass's, else 0. */
    public int constructorArity() {
        JmpClass c = this;
        for (int guard = 0; c != null && guard < 32; guard++) {
            JmpFunction k = c.getConstructor();
            if (k != null) return k.getParameters().size();
            c = c.getSuperClass();
        }
        return 0;
    }

    @Override
    public String kindName() {
        return "class";
    }

    @Override
    public Icon kindIcon() {
        return AllIcons.Nodes.Class;
    }

    @Override
    public @Nullable String detail() {
        JmpReferenceExpression e = getExtendsReference();
        return e == null ? null : "extends " + e.getText();
    }
}
