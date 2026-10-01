package me.padej.jumper.idea.lang.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.tree.IElementType;
import me.padej.jumper.idea.lang.lexer.JumperTokenTypes;
import me.padej.jumper.idea.lang.psi.*;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static me.padej.jumper.idea.lang.lexer.JumperTokenTypes.*;
import static me.padej.jumper.idea.lang.parser.JumperElementTypes.*;

/**
 * The language's static types (ast.VarType): what the parser knows about an expression - dyn, int, long, double,
 * boolean, String - and the checks it makes with them (Parser.checkAssignable, makeBinary): `int x = "s"` and
 * `true + 1` are errors before the script runs.
 */
public enum JmpVarType {
    DYN, INT, LONG, DOUBLE, BOOLEAN, STRING;

    public boolean isNumeric() {
        return this == INT || this == LONG || this == DOUBLE;
    }

    /** How the language names it in messages: `int`, `string`. */
    public String display() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /** VarType.arith: a number op a number. */
    public static JmpVarType arith(JmpVarType a, JmpVarType b) {
        if (!a.isNumeric() || !b.isNumeric()) return DYN;
        if (a == DOUBLE || b == DOUBLE) return DOUBLE;
        if (a == LONG || b == LONG) return LONG;
        return INT;
    }

    public static @Nullable JmpVarType ofKeyword(@Nullable IElementType k) {
        if (k == KW_INT) return INT;
        if (k == KW_LONG) return LONG;
        if (k == KW_DOUBLE) return DOUBLE;
        if (k == KW_BOOLEAN) return BOOLEAN;
        if (k == KW_STRING) return STRING;
        if (k == JumperTokenTypes.DYN) return DYN;
        return null;
    }

    /** The declared type of a type element: a keyword's, DYN for dyn and for a class. */
    public static JmpVarType declared(@Nullable JmpTypeElement te) {
        if (te == null) return DYN;
        JmpVarType t = ofKeyword(te.keyword());
        return t == null ? DYN : t;
    }

    /** Parser.checkAssignable: the message when `value` cannot go into `target`, or null. */
    public static @Nullable String checkAssignable(JmpVarType target, JmpVarType value) {
        if (target == DYN || value == DYN) return null;
        boolean ok = switch (target) {
            case INT -> value == INT;
            case LONG -> value == INT || value == LONG;
            case DOUBLE -> value.isNumeric();
            case BOOLEAN -> value == BOOLEAN;
            case STRING -> value == STRING;
            default -> true;
        };
        return ok ? null : "Cannot assign " + value.display() + " to " + target.display();
    }

    /** The static type of an expression (Expr.type after Parser.makeBinary and friends). */
    public static JmpVarType of(@Nullable PsiElement e) {
        if (!(e instanceof JmpElement x)) return DYN;
        if (x instanceof JmpLiteral l) {
            IElementType t = l.tokenType();
            if (t == JumperTokenTypes.INT) return INT;   // 2147483648 is an error unless negated, and then it is an int
            if (t == JumperTokenTypes.LONG) return LONG;
            if (t == JumperTokenTypes.DOUBLE) return DOUBLE;
            if (t == JumperTokenTypes.STRING) return STRING;
            if (t == TRUE || t == FALSE) return BOOLEAN;
            return DYN;
        }
        if (x.is(PARENTHESIZED_EXPRESSION)) return of(x.firstExpression());
        if (x.is(PREFIX_EXPRESSION)) {
            IElementType op = x.getFirstChild().getNode().getElementType();
            JmpVarType t = of(x.firstExpression());
            if (op == NOT) return BOOLEAN;
            if (op == MINUS || op == PLUS) return t.isNumeric() ? t : DYN;
            return t.isNumeric() ? t : DYN;   // ++x
        }
        if (x.is(POSTFIX_EXPRESSION)) {
            JmpVarType t = of(x.firstExpression());
            return t.isNumeric() ? t : DYN;
        }
        if (x.is(CONDITIONAL_EXPRESSION)) {
            List<JmpElement> xs = x.expressions();
            if (xs.size() != 3) return DYN;
            JmpVarType a = of(xs.get(1)), b = of(xs.get(2));
            if (a == b) return a;
            return a.isNumeric() && b.isNumeric() ? arith(a, b) : DYN;
        }
        if (x.is(ASSIGNMENT_EXPRESSION)) return of(x.firstExpression());
        if (x.is(BINARY_EXPRESSION)) return binary(x);
        if (x instanceof JmpReferenceExpression r) {
            if (r.isQualified()) return DYN;
            PsiElement t = r.resolve();
            if (t instanceof JmpVariable v) return declared(v.getTypeElement());
            if (t instanceof JmpParameter p) return declared(p.getTypeElement());
            return DYN;
        }
        if (x instanceof JmpCallExpression c && c.getCallee() instanceof JmpReferenceExpression callee && !callee.isQualified()) {
            String n = callee.getReferenceName();
            ResolveResult[] rs = callee.multiResolve(false);
            PsiElement t = rs.length == 1 ? rs[0].getElement() : null;
            if (t instanceof JmpSynthetic s && s.kind() == JmpSynthetic.Kind.BUILTIN && c.getArguments().size() == 1) {
                if ("len".equals(n)) return INT;
                if ("str".equals(n) || "type".equals(n)) return STRING;
                JmpVarType a = of(c.getArguments().get(0));
                if (a.isNumeric()) return "int".equals(n) ? INT : "long".equals(n) ? LONG : "double".equals(n) ? DOUBLE : DYN;
            }
            if (t instanceof JmpFunction f && f.getTypeElement() != null) return declared(f.getTypeElement());
        }
        return DYN;
    }

    /** The operator token of a binary expression. */
    public static @Nullable PsiElement operator(JmpElement bin) {
        for (PsiElement c = bin.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (OPERATORS.contains(c.getNode().getElementType())) return c;
        }
        return null;
    }

    private static JmpVarType binary(JmpElement x) {
        List<JmpElement> xs = x.expressions();
        PsiElement op = operator(x);
        if (xs.size() != 2 || op == null) return DYN;
        return binaryType(op.getNode().getElementType(), of(xs.get(0)), of(xs.get(1)));
    }

    public static JmpVarType binaryType(IElementType op, JmpVarType l, JmpVarType r) {
        if (op == EQEQ || op == NE || op == LT || op == LE || op == GT || op == GE) return BOOLEAN;
        if (op == ANDAND || op == OROR) return l == BOOLEAN && r == BOOLEAN ? BOOLEAN : DYN;
        if (op == PLUS && (l == STRING || r == STRING)) return STRING;
        if (op == SHL || op == SHR || op == USHR) return l == INT || l == LONG ? l : DYN;
        if (op == AMP || op == PIPE || op == CARET) return l.isNumeric() && r.isNumeric() ? arith(l, r) : DYN;
        if (l.isNumeric() && r.isNumeric()) return arith(l, r);
        if (op != PLUS && (l == DOUBLE && r == DYN || l == DYN && r == DOUBLE)) return DOUBLE;
        return DYN;
    }

    /** Parser.makeBinary's errors, or null. */
    public static @Nullable String binaryError(IElementType op, JmpVarType l, JmpVarType r, String opText) {
        boolean bitwise = op == AMP || op == PIPE || op == CARET || op == SHL || op == SHR || op == USHR;
        boolean arithmetic = op == PLUS || op == MINUS || op == STAR || op == SLASH || op == PERCENT;
        if (!bitwise && !arithmetic) return null;
        if (op == PLUS && (l == STRING || r == STRING)) return null;
        if (l == BOOLEAN || r == BOOLEAN) return "Cannot apply '" + opText + "' to boolean";
        if (arithmetic && op != PLUS && l == STRING && r == STRING) return "Cannot apply '" + opText + "' to strings";
        if (bitwise && (l == DOUBLE || r == DOUBLE)) return "Operator " + opText + " requires integers, got double";
        return null;
    }
}
