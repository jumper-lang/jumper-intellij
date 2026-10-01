package me.padej.jumper.idea.lang.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * `import "utils.jmp";` - the module's top-level names become names of this block, from its start (the language
 * loads modules while hoisting). The path is relative to the importing file's folder; `.jmp` may be left out.
 */
public final class JmpModuleImport extends JmpElement {
    public JmpModuleImport(@NotNull ASTNode node) {
        super(node);
    }

    public @Nullable JmpLiteral getPathLiteral() {
        return child(JmpLiteral.class);
    }

    public @Nullable String getSpec() {
        JmpLiteral l = getPathLiteral();
        return l == null ? null : l.stringValue();
    }

    /** The module file, as Modules.resolve finds it: relative to this file's folder, `.jmp` added when missing. */
    public @Nullable JumperFile resolveModule() {
        String spec = getSpec();
        PsiFile own = getContainingFile().getOriginalFile();
        VirtualFile vf = own.getVirtualFile();
        if (spec == null || vf == null || vf.getParent() == null) return null;
        VirtualFile dir = vf.getParent();
        VirtualFile m = dir.findFileByRelativePath(spec);
        if ((m == null || m.isDirectory()) && !spec.endsWith(".jmp")) m = dir.findFileByRelativePath(spec + ".jmp");
        if (m == null || m.isDirectory()) return null;
        PsiFile f = PsiManager.getInstance(getProject()).findFile(m);
        return f instanceof JumperFile jf ? jf : null;
    }
}
