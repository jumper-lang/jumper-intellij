package me.padej.jumper.idea.editor;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import me.padej.jumper.idea.lang.psi.*;
import me.padej.jumper.idea.lang.resolve.JmpTypes;
import me.padej.jumper.idea.lang.resolve.JmpType;
import me.padej.jumper.idea.workspace.JumperContext;
import me.padej.jumper.idea.workspace.JumperHooks;
import me.padej.jumper.idea.workspace.JumperWorkspace;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static me.padej.jumper.idea.lang.parser.JumperElementTypes.*;

/**
 * One node of the outline (Symbols.outline, Features.documentSymbols): a class has its fields, methods and
 * constructor; a function what is declared in it (functions and classes); a top-level function the host calls
 * says which hook it implements.
 */
final class JumperStructureViewElement implements StructureViewTreeElement, SortableTreeElement {
    private final NavigatablePsiElement element;

    JumperStructureViewElement(NavigatablePsiElement element) {
        this.element = element;
    }

    @Override
    public Object getValue() {
        return element;
    }

    @Override
    public void navigate(boolean requestFocus) {
        element.navigate(requestFocus);
    }

    @Override
    public boolean canNavigate() {
        return element.canNavigate();
    }

    @Override
    public boolean canNavigateToSource() {
        return element.canNavigateToSource();
    }

    @Override
    public @NotNull String getAlphaSortKey() {
        String n = element.getName();
        return n == null ? "" : n;
    }

    @Override
    public @NotNull ItemPresentation getPresentation() {
        if (element instanceof PsiFile f) return new PresentationData(f.getName(), null, f.getIcon(0), null);
        JmpNamedElement n = (JmpNamedElement) element;
        String detail = n.detail();
        if (n instanceof JmpVariable v && (detail == null || "dyn".equals(detail))) {
            JmpType t = JmpTypes.variableType(v);
            if (t != null) detail = t.presentableName();
        }
        String location = null;
        if (n instanceof JmpFunction f && JmpPsiUtil.isTopLevel(f) && f.getContainingFile() instanceof JumperFile file) {
            JumperContext ctx = JumperWorkspace.contextFor(file);
            JumperHooks.Hook h = f.getName() == null ? null : JumperHooks.find(ctx, f.getName(), f);
            if (h != null) location = "hook: " + h.owner().getName();
        }
        String text = n.getName() + (detail == null ? "" : detail.startsWith("(") ? detail : ": " + detail);
        return new PresentationData(text, location, n.kindIcon(), null);
    }

    @Override
    public TreeElement @NotNull [] getChildren() {
        List<TreeElement> out = new ArrayList<>();
        if (element instanceof JumperFile f) collect(f, out);
        else if (element instanceof JmpClass c) {
            JmpClassBody body = c.getBody();
            if (body != null) collect(body, out);
        } else if (element instanceof JmpFunction f && f.getBody() != null) nested(f.getBody(), out);
        return out.toArray(TreeElement.EMPTY_ARRAY);
    }

    /** Declarations directly in a container: functions, classes, variables. */
    private static void collect(PsiElement container, List<TreeElement> out) {
        for (PsiElement c = container.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c instanceof JmpFunction || c instanceof JmpClass) out.add(new JumperStructureViewElement((NavigatablePsiElement) c));
            else if (c instanceof JmpElement e && e.is(VARIABLE_DECLARATION))
                for (JmpVariable v : e.children(JmpVariable.class)) out.add(new JumperStructureViewElement(v));
        }
    }

    /** Functions and classes declared inside a function's body, at any depth of its blocks (not its variables). */
    private static void nested(PsiElement block, List<TreeElement> out) {
        for (PsiElement c = block.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c instanceof JmpFunction || c instanceof JmpClass) out.add(new JumperStructureViewElement((NavigatablePsiElement) c));
            else if (c instanceof JmpElement e && !(c instanceof JmpLambda)) nested(e, out);
        }
    }
}
