package me.padej.jumper.idea.editor;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewModelBase;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import me.padej.jumper.idea.lang.psi.JmpClass;
import me.padej.jumper.idea.lang.psi.JmpFunction;
import me.padej.jumper.idea.lang.psi.JmpVariable;
import me.padej.jumper.idea.lang.psi.JumperFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** The outline (lsp documentSymbol): classes with their members, functions, top-level variables - and what is declared inside functions. */
public final class JumperStructureViewFactory implements PsiStructureViewFactory {
    @Override
    public @Nullable StructureViewBuilder getStructureViewBuilder(@NotNull PsiFile psiFile) {
        if (!(psiFile instanceof JumperFile file)) return null;
        return new TreeBasedStructureViewBuilder() {
            @Override
            public @NotNull StructureViewModel createStructureViewModel(@Nullable Editor editor) {
                return new Model(file, editor);
            }
        };
    }

    private static final class Model extends StructureViewModelBase implements StructureViewModel.ElementInfoProvider {
        Model(JumperFile file, Editor editor) {
            super(file, editor, new JumperStructureViewElement(file));
            withSorters(Sorter.ALPHA_SORTER);
            withSuitableClasses(JmpClass.class, JmpFunction.class, JmpVariable.class);
        }

        @Override
        public boolean isAlwaysShowsPlus(StructureViewTreeElement element) {
            return false;
        }

        @Override
        public boolean isAlwaysLeaf(StructureViewTreeElement element) {
            return element.getValue() instanceof JmpVariable;
        }
    }
}
