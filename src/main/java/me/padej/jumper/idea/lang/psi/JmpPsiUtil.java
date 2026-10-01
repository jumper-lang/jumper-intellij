package me.padej.jumper.idea.lang.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import me.padej.jumper.idea.lang.parser.JumperElementTypes;
import org.jetbrains.annotations.Nullable;

import static me.padej.jumper.idea.lang.parser.JumperElementTypes.*;

public final class JmpPsiUtil {
    private JmpPsiUtil() {}

    public static IElementType typeOf(@Nullable PsiElement e) {
        return e == null || e.getNode() == null ? null : e.getNode().getElementType();
    }

    public static boolean isExpression(@Nullable PsiElement e) {
        return e instanceof JmpElement && EXPRESSIONS.contains(typeOf(e));
    }

    /** Declared at the top level of its file: a module exports it. */
    public static boolean isTopLevel(JmpNamedElement e) {
        PsiElement p = e.getParent();
        if (p instanceof PsiFile) return true;
        return e instanceof JmpVariable && p != null && typeOf(p) == VARIABLE_DECLARATION && p.getParent() instanceof PsiFile;
    }

    /** The class whose body the element is in (a method, a field initializer, a function inside a method), or null. */
    public static @Nullable JmpClass enclosingClass(PsiElement e) {
        for (PsiElement p = e.getParent(); p != null && !(p instanceof PsiFile); p = p.getParent()) {
            if (p instanceof JmpClassBody b) return b.owner();
        }
        return null;
    }

    /** The method or constructor (not a nested function or lambda) the element is in, or null. */
    public static @Nullable JmpFunction enclosingMember(PsiElement e) {
        for (PsiElement p = e.getParent(); p != null && !(p instanceof PsiFile); p = p.getParent()) {
            if (p instanceof JmpFunction f && f.isMethod()) return f;
            if (p instanceof JmpClassBody) return null;
        }
        return null;
    }

    /**
     * Is `this` there: inside a class body, but not in a static method or a static field's initializer
     * (Parser: "'this' is not available in a static method or static initializer").
     */
    public static boolean hasThis(PsiElement e) {
        for (PsiElement p = e.getParent(); p != null && !(p instanceof PsiFile); p = p.getParent()) {
            if (p instanceof JmpFunction f && f.isMethod()) return !f.isStatic();
            if (p instanceof JmpVariable v && v.isField()) return !v.isStatic();
            if (p instanceof JmpClassBody) return true;
        }
        return false;
    }

    /** Inside the body of a loop of its own function (a nested function or lambda starts afresh): Parser's FuncState.loops. */
    public static boolean inLoop(PsiElement e) {
        PsiElement child = e;
        for (PsiElement p = e.getParent(); p != null && !(p instanceof PsiFile); child = p, p = p.getParent()) {
            if (p instanceof JmpFunction || p instanceof JmpLambda || p instanceof JmpClassBody) return false;
            IElementType t = typeOf(p);
            if (LOOPS.contains(t) && isLoopBody((JmpElement) p, child)) return true;
        }
        return false;
    }

    /** Is `child` the body of the loop (not its condition or header)? */
    private static boolean isLoopBody(JmpElement loop, PsiElement child) {
        if (loop.is(DO_WHILE_STATEMENT)) return !isExpression(child);
        // the body is the last statement child; the condition/header parts are expressions or the init
        PsiElement last = loop.getLastChild();
        while (last != null && (last.getNode().getElementType() == com.intellij.psi.TokenType.WHITE_SPACE
                || me.padej.jumper.idea.lang.lexer.JumperTokenTypes.COMMENTS.contains(last.getNode().getElementType()))) last = last.getPrevSibling();
        return last == child;
    }

    public static boolean is(PsiElement e, IElementType t) {
        return typeOf(e) == t;
    }

    /** The statement (a direct child of a block, a file, a branch) the element is in. */
    public static @Nullable PsiElement statementOf(PsiElement e) {
        for (PsiElement p = e; p != null && !(p instanceof PsiFile); p = p.getParent()) {
            if (JumperElementTypes.STATEMENTS.contains(typeOf(p))) return p;
        }
        return null;
    }
}
