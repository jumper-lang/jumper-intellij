package me.padej.jumper.idea.lang.resolve;

import com.intellij.psi.PsiClass;
import me.padej.jumper.idea.lang.psi.JmpClass;
import org.jetbrains.annotations.Nullable;

/**
 * What an expression is known to be without running anything (lsp.Features.Type, workspace.MemberCheck.Type):
 * instances of a Java class or of a script class, or the class itself (`statics`: `Math.max`, `Point.origin`).
 */
public record JmpType(@Nullable PsiClass java, @Nullable JmpClass script, boolean statics) {
    public static JmpType java(PsiClass c, boolean statics) {
        return c == null ? null : new JmpType(c, null, statics);
    }

    public static JmpType script(JmpClass c, boolean statics) {
        return c == null ? null : new JmpType(null, c, statics);
    }

    public String presentableName() {
        return java != null ? java.getName() : script != null ? script.getName() : "?";
    }

    public String qualifiedName() {
        return java != null ? java.getQualifiedName() : script != null ? script.getName() : "?";
    }
}
