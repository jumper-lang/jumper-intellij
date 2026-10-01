package me.padej.jumper.idea.lang.psi;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import me.padej.jumper.idea.JumperFileType;
import me.padej.jumper.idea.JumperLanguage;
import me.padej.jumper.idea.lang.parser.JumperElementTypes;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** A .jmp, .jmc or .jma file: the statements of its top level (the body of the language's `<main>`). */
public final class JumperFile extends PsiFileBase {
    public JumperFile(@NotNull FileViewProvider viewProvider) {
        super(viewProvider, JumperLanguage.INSTANCE);
    }

    @Override
    public @NotNull FileType getFileType() {
        FileType t = getViewProvider().getFileType();
        return t instanceof JumperFileType ? t : JumperFileType.SCRIPT;
    }

    public JumperFileType.Kind kind() {
        return JumperFileType.kindOf(getOriginalFile().getVirtualFile() != null ? getOriginalFile().getVirtualFile() : getViewProvider().getVirtualFile());
    }

    public boolean isConfig() {
        return kind() == JumperFileType.Kind.CONFIG;
    }

    public boolean isPolicy() {
        return kind() == JumperFileType.Kind.POLICY;
    }

    public List<JmpImportStatement> javaImports() {
        List<JmpImportStatement> out = new ArrayList<>();
        for (PsiElement c = getFirstChild(); c != null; c = c.getNextSibling()) if (c instanceof JmpImportStatement i) out.add(i);
        return out;
    }

    /** The top-level declarations: what a module exports (Script.names - every name of its top level). */
    public List<JmpNamedElement> topLevelDeclarations() {
        List<JmpNamedElement> out = new ArrayList<>();
        for (PsiElement c = getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c instanceof JmpFunction f) out.add(f);
            else if (c instanceof JmpClass k) out.add(k);
            else if (c instanceof JmpElement e && e.is(JumperElementTypes.VARIABLE_DECLARATION)) out.addAll(e.children(JmpVariable.class));
        }
        return out;
    }

    @Override
    public String toString() {
        return "JumperFile:" + getName();
    }
}
