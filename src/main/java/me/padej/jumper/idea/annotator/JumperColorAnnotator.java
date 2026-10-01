package me.padej.jumper.idea.annotator;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import me.padej.jumper.idea.highlighter.JumperHighlighterColors;
import me.padej.jumper.idea.lang.lexer.JumperTokenTypes;
import me.padej.jumper.idea.lang.psi.*;
import me.padej.jumper.idea.lang.resolve.JmpSynthetic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * What each name is, as colors (lsp.SemanticTokens; ScalaColorSchemeAnnotator): declarations - functions,
 * classes, fields, variables, parameters; uses by what they resolve to - a parameter, a field, a host global, a
 * built-in, a Java class or interface, a static method; keys of table literals; escapes in strings. A name that
 * resolves to nothing is colored by its shape, as the language server does (`a.b()` a method, `a.B` a class).
 */
public final class JumperColorAnnotator implements Annotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (element instanceof JmpNamedElement n) {
            PsiElement id = n.getNameIdentifier();
            TextAttributesKey key = declarationKey(n);
            if (id != null && key != null) color(holder, id, key);
        } else if (element instanceof JmpTableKey k) {
            PsiElement name = k.nameToken();
            if (name != null) color(holder, name, JumperHighlighterColors.TABLE_KEY);
        } else if (element instanceof JmpReferenceExpression r) {
            PsiElement name = r.getReferenceNameElement();
            if (name == null || name.getNode().getElementType() != JumperTokenTypes.IDENT && !isBuiltinKeyword(r)) return;
            TextAttributesKey key = referenceKey(r);
            if (key != null) color(holder, name, key);
        } else if (element instanceof JmpLiteral l && l.isString()) {
            escapes(l, holder);
        }
    }

    private static boolean isBuiltinKeyword(JmpReferenceExpression r) {
        var t = r.nameTokenType();
        return t == JumperTokenTypes.KW_INT || t == JumperTokenTypes.KW_LONG || t == JumperTokenTypes.KW_DOUBLE;
    }

    static void color(AnnotationHolder holder, PsiElement e, TextAttributesKey key) {
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(e).textAttributes(key).create();
    }

    static @Nullable TextAttributesKey declarationKey(JmpNamedElement n) {
        if (n instanceof JmpClass) return JumperHighlighterColors.CLASS_NAME;
        if (n instanceof JmpFunction f) return f.isConstructor() ? JumperHighlighterColors.CLASS_NAME : JumperHighlighterColors.FUNCTION_DECLARATION;
        if (n instanceof JmpParameter) return JumperHighlighterColors.PARAMETER;
        if (n instanceof JmpVariable v) return variableKey(v);
        return null;
    }

    private static TextAttributesKey variableKey(JmpVariable v) {
        if (v.isField()) return v.isStatic() ? JumperHighlighterColors.STATIC_FIELD : JumperHighlighterColors.FIELD;
        return JmpPsiUtil.isTopLevel(v) ? JumperHighlighterColors.TOP_LEVEL_VARIABLE : JumperHighlighterColors.LOCAL_VARIABLE;
    }

    static @Nullable TextAttributesKey referenceKey(JmpReferenceExpression r) {
        PsiElement t = r.resolve();
        if (t == null) {
            var rs = r.multiResolve(false);
            if (rs.length > 0) t = rs[0].getElement();
        }
        boolean call = r.isCallee();
        if (t instanceof JmpVariable v) return variableKey(v);
        if (t instanceof JmpParameter) return JumperHighlighterColors.PARAMETER;
        if (t instanceof JmpFunction f) return f.isMethod() ? JumperHighlighterColors.METHOD_CALL : JumperHighlighterColors.FUNCTION_CALL;
        if (t instanceof JmpClass) return JumperHighlighterColors.CLASS_NAME;
        if (t instanceof JmpSynthetic s)
            return s.kind() == JmpSynthetic.Kind.BUILTIN ? JumperHighlighterColors.BUILTIN_FUNCTION : JumperHighlighterColors.HOST_GLOBAL;
        if (t instanceof PsiClass c) return c.isInterface() ? JumperHighlighterColors.INTERFACE_NAME : JumperHighlighterColors.CLASS_NAME;
        if (t instanceof PsiMethod m) {
            if (!call) return JumperHighlighterColors.PROPERTY;
            return m.hasModifierProperty(PsiModifier.STATIC) ? JumperHighlighterColors.STATIC_METHOD_CALL : JumperHighlighterColors.METHOD_CALL;
        }
        if (t instanceof PsiField f) return f.hasModifierProperty(PsiModifier.STATIC) ? JumperHighlighterColors.STATIC_FIELD : JumperHighlighterColors.FIELD;
        if (t instanceof PsiPackage) return null;
        // unresolved: by its shape (SemanticTokens)
        if (!r.isQualified()) return call ? JumperHighlighterColors.FUNCTION_CALL : null;
        if (call) return JumperHighlighterColors.METHOD_CALL;
        String name = r.getReferenceName();
        if (name != null && looksLikeClass(name)) return JumperHighlighterColors.CLASS_NAME;
        return JumperHighlighterColors.PROPERTY;
    }

    private static boolean looksLikeClass(String name) {
        if (name.isEmpty() || !Character.isUpperCase(name.charAt(0))) return false;
        for (int i = 0; i < name.length(); i++) if (Character.isLowerCase(name.charAt(i))) return true;
        return name.length() == 1;
    }

    /** `\n`, `é` in a string: the escape color (a bad one is the other annotator's error). */
    private static void escapes(JmpLiteral l, AnnotationHolder holder) {
        String text = l.getText();
        int start = l.getTextRange().getStartOffset();
        for (int i = 1; i < text.length() - 1; i++) {
            if (text.charAt(i) != '\\') continue;
            char e = text.charAt(i + 1);
            int len = "ntr0\\\"'".indexOf(e) >= 0 ? 2 : e == 'u' && i + 5 < text.length() && isHex(text.substring(i + 2, i + 6)) ? 6 : 0;
            if (len > 0) {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(TextRange.from(start + i, len))
                        .textAttributes(JumperHighlighterColors.VALID_ESCAPE).create();
                i += len - 1;
            } else i++;
        }
    }

    private static boolean isHex(String s) {
        for (int i = 0; i < s.length(); i++) if (Character.digit(s.charAt(i), 16) < 0) return false;
        return true;
    }
}
