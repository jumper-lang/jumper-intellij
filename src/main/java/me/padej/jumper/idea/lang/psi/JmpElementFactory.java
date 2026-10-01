package me.padej.jumper.idea.lang.psi;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import me.padej.jumper.idea.JumperFileType;
import me.padej.jumper.idea.lang.lexer.JumperTokenTypes;

/** Pieces of Jumper made from text: what rename and quick fixes put into a file. */
public final class JmpElementFactory {
    private JmpElementFactory() {}

    public static JumperFile file(Project project, String text) {
        return (JumperFile) PsiFileFactory.getInstance(project).createFileFromText("dummy.jmp", JumperFileType.SCRIPT, text);
    }

    /** An IDENT leaf with this name. */
    public static PsiElement identifier(Project project, String name) {
        JmpVariable v = PsiTreeUtil.findChildOfType(file(project, "dyn " + name + ";"), JmpVariable.class);
        PsiElement id = v == null ? null : v.getNameIdentifier();
        if (id == null || id.getNode().getElementType() != JumperTokenTypes.IDENT || !id.getText().equals(name))
            throw new IncorrectOperationException("'" + name + "' is not a valid name");
        return id;
    }

    public static JmpImportStatement importStatement(Project project, String qualifiedName) {
        JmpImportStatement i = PsiTreeUtil.findChildOfType(file(project, "import " + qualifiedName + ";"), JmpImportStatement.class);
        if (i == null) throw new IncorrectOperationException("bad import: " + qualifiedName);
        return i;
    }
}
