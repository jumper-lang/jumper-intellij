package me.padej.jumper.idea.lang.resolve;

import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import me.padej.jumper.idea.lang.psi.JmpClass;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * What an expression is known to be without running anything (lsp.Features.Type, workspace.MemberCheck.Type):
 * instances of a Java class or of a script class, or the class itself (`statics`: `Math.max`, `Point.origin`).
 * <p>
 * {@code javaType} is the Java type as declared, with its type arguments - `List<ScriptPlayer>` of
 * `server.players()` - so that what a for-each takes out of it is known; an array is a type with no class (no
 * members are looked up on it, a for-each still knows its elements).
 */
public record JmpType(@Nullable PsiClass java, @Nullable JmpClass script, boolean statics, @Nullable PsiType javaType) {
    public static JmpType java(PsiClass c, boolean statics) {
        return c == null ? null : new JmpType(c, null, statics, null);
    }

    public static JmpType script(JmpClass c, boolean statics) {
        return c == null ? null : new JmpType(null, c, statics, null);
    }

    /** Instances of a Java type: a class (with its type arguments) or an array. */
    public static JmpType instances(@Nullable PsiClass c, PsiType type) {
        return new JmpType(c, null, false, type);
    }

    public boolean isArray() {
        return javaType instanceof PsiArrayType;
    }

    public String presentableName() {
        if (javaType != null && !statics) return javaType.getPresentableText();
        return java != null ? java.getName() : script != null ? script.getName() : "?";
    }

    public String qualifiedName() {
        if (javaType != null && !statics) return javaType.getCanonicalText();
        return java != null ? java.getQualifiedName() : script != null ? script.getName() : "?";
    }

    /**
     * The type of a value that is one or the other: the same type, or - the same class with other type arguments - the
     * class alone; null when they differ.
     */
    public static @Nullable JmpType common(@Nullable JmpType a, @Nullable JmpType b) {
        if (a == null || b == null) return null;
        if (a.equals(b)) return a;
        if (a.java != null && a.java.equals(b.java) && a.statics == b.statics && a.script == null && b.script == null)
            return new JmpType(a.java, null, a.statics, null);
        return null;
    }

    // PsiType.equals of class types resolves them: the text says the same here
    @Override
    public boolean equals(Object o) {
        return o instanceof JmpType t && statics == t.statics && Objects.equals(java, t.java) && Objects.equals(script, t.script)
                && Objects.equals(text(javaType), text(t.javaType));
    }

    @Override
    public int hashCode() {
        return Objects.hash(java, script, statics, text(javaType));
    }

    private static @Nullable String text(@Nullable PsiType t) {
        return t == null ? null : t.getCanonicalText();
    }
}
