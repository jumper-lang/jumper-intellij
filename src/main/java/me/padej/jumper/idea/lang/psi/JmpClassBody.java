package me.padej.jumper.idea.lang.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import me.padej.jumper.idea.lang.parser.JumperElementTypes;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** `{ members }` of a class: not a scope of its own - its members are found through the class. */
public final class JmpClassBody extends JmpElement {
    public JmpClassBody(@NotNull ASTNode node) {
        super(node);
    }

    public JmpClass owner() {
        return (JmpClass) getParent();
    }

    public List<JmpVariable> fields() {
        List<JmpVariable> out = new ArrayList<>();
        for (PsiElement c = getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c instanceof JmpElement e && e.is(JumperElementTypes.VARIABLE_DECLARATION)) out.addAll(e.children(JmpVariable.class));
        }
        return out;
    }

    public List<JmpFunction> functions() {
        return children(JmpFunction.class);
    }
}
