package me.padej.jumper.idea.lang.resolve;

import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.*;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import me.padej.jumper.idea.lang.lexer.JumperTokenTypes;
import me.padej.jumper.idea.lang.psi.*;
import me.padej.jumper.idea.workspace.JumperHooks;
import me.padej.jumper.idea.workspace.JumperWorkspace;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static me.padej.jumper.idea.lang.parser.JumperElementTypes.*;

/**
 * The static type of an expression where the text tells it (lsp.Features.rootType/chainType and
 * workspace.MemberCheck.typeOf):
 * <ul>
 *   <li>a Java class named (its statics), a host global (by the host descriptor), `new C(...)`, a string;</li>
 *   <li>a variable or parameter declared with a Java class or a script class, `String`;</li>
 *   <li>a `dyn` variable whose every assignment has one such type (`dyn w = server.world();`) - a variable given
 *       values of different or unknown types has none: its type is known only at run time;</li>
 *   <li>a `dyn` parameter of a hook (`void onJoin(dyn p)`): the type of that parameter of the API method the host
 *       calls it through (`ScriptEvents.onJoin(ScriptPlayer)`), unless the function gives it other values;</li>
 *   <li>a chain: the return type of a method (the overloads that take that many arguments, if they agree), a
 *       field, a property `getName()`; a function of the file declared with a class type.</li>
 * </ul>
 * A primitive, an array, a type variable end the chain (no members are looked up on them).
 */
public final class JmpTypes {
    private JmpTypes() {}

    public static @Nullable JmpType typeOf(@Nullable PsiElement e) {
        if (!(e instanceof JmpElement je)) return null;
        return CachedValuesManager.getCachedValue(je, () -> CachedValueProvider.Result.create(
                RecursionManager.doPreventingRecursion(je, true, () -> compute(je)), PsiModificationTracker.MODIFICATION_COUNT));
    }

    private static @Nullable JmpType compute(JmpElement e) {
        if (e instanceof JmpLiteral l) return l.isString() ? string(e) : null;
        if (e.is(PARENTHESIZED_EXPRESSION)) return typeOf(e.firstExpression());
        if (e.is(THIS_EXPRESSION)) return JmpType.script(JmpPsiUtil.enclosingClass(e), false);
        if (e.is(ASSIGNMENT_EXPRESSION)) {
            List<JmpElement> xs = e.expressions();
            return xs.size() == 2 ? typeOf(xs.get(1)) : null;
        }
        if (e.is(CONDITIONAL_EXPRESSION)) {
            List<JmpElement> xs = e.expressions();
            if (xs.size() != 3) return null;
            JmpType a = typeOf(xs.get(1)), b = typeOf(xs.get(2));
            return Objects.equals(a, b) ? a : null;
        }
        if (e instanceof JmpNewExpression n) return instanceOf(n.getClassReference());
        if (e instanceof JmpCallExpression c) return callType(c);
        if (e instanceof JmpReferenceExpression r) return referenceType(r);
        return null;
    }

    private static JmpType string(PsiElement context) {
        return JmpType.java(JmpJava.findClass("java.lang.String", context), false);
    }

    /** What `new C(...)` makes, or the type `C x` declares: instances of the class the name resolves to. */
    public static @Nullable JmpType instanceOf(@Nullable JmpReferenceExpression classRef) {
        if (classRef == null) return null;
        if (classRef.nameTokenType() == JumperTokenTypes.KW_STRING) return string(classRef);
        PsiElement t = classRef.resolve();
        if (t instanceof PsiClass c) return JmpType.java(c, false);
        if (t instanceof JmpClass c) return JmpType.script(c, false);
        return null;
    }

    /** The type a declaration names: `String`, a class; `dyn` and the primitives have none. */
    public static @Nullable JmpType declared(@Nullable JmpTypeElement te) {
        if (te == null) return null;
        if (te.keyword() == JumperTokenTypes.KW_STRING) return string(te);
        return te.keyword() != null ? null : instanceOf(te.reference());
    }

    public static @Nullable JmpType fromPsiType(@Nullable PsiType t) {
        PsiClass c = JmpJava.classOf(t);
        return c == null ? null : JmpType.java(c, false);
    }

    private static @Nullable JmpType referenceType(JmpReferenceExpression r) {
        if (r.nameTokenType() == JumperTokenTypes.KW_STRING) return JmpType.java(JmpJava.findClass("java.lang.String", r), true);
        PsiElement t = r.resolve();
        if (t instanceof PsiClass c) return JmpType.java(c, true);
        if (t instanceof JmpClass c) return JmpType.script(c, true);
        if (t instanceof JmpSynthetic s) return s.kind() == JmpSynthetic.Kind.HOST_GLOBAL ? JmpType.java(s.typeClass(), false) : null;
        if (t instanceof PsiField f) return fromPsiType(f.getType());
        if (t instanceof PsiMethod m && !r.isCallee()) return m.getParameterList().getParametersCount() == 0 ? fromPsiType(m.getReturnType()) : null;
        if (t instanceof JmpParameter p) return parameterType(p);
        if (t instanceof JmpVariable v) return variableType(v);
        return null;
    }

    private static @Nullable JmpType callType(JmpCallExpression c) {
        if (!(c.getCallee() instanceof JmpReferenceExpression callee)) return null;
        int args = c.getArguments().size();
        ResolveResult[] rs = callee.multiResolve(false);
        List<PsiMethod> ms = new ArrayList<>();
        for (ResolveResult r : rs) {
            PsiElement t = r.getElement();
            if (t instanceof PsiMethod m) ms.add(m);
            else if (t instanceof JmpFunction f) return declared(f.getTypeElement());
            else if (t instanceof JmpSynthetic s && s.kind() == JmpSynthetic.Kind.BUILTIN)
                return JmpBuiltins.returnsString(s.getName()) ? string(c) : null;
        }
        if (ms.isEmpty()) return null;
        return fromPsiType(JmpJava.commonReturnType(JmpJava.arity(ms, args)));
    }

    /**
     * A variable: its declared type, else (a `dyn` one) the type of every value it is given - its initializer and
     * each `x = value` - when they all agree; a `null` tells nothing, `x += ...` / `x++` make it unknown
     * (MemberCheck.Locals: a variable given values of more than one type has none).
     */
    public static @Nullable JmpType variableType(JmpVariable v) {
        JmpTypeElement te = v.getTypeElement();
        if (te != null && !te.isDyn()) return declared(te);
        if (v.isForEach()) return null;
        JmpType[] found = {null};
        boolean[] mixed = {false};
        JmpElement init = v.getInitializer();
        if (init != null && !isNull(init)) note(typeOf(init), found, mixed);
        PsiElement scope = v.isField() ? v.getContainingFile() : enclosingScope(v);
        assignments(v, scope, found, mixed);
        return mixed[0] ? null : found[0];
    }

    /**
     * A parameter: its declared type; a `dyn` (or untyped) parameter of a hook - a top-level function the host calls
     * through its API (JumperHooks) - has the type of the same parameter of the API method, as long as the
     * function does not give it values of another type.
     */
    public static @Nullable JmpType parameterType(JmpParameter p) {
        JmpTypeElement te = p.getTypeElement();
        if (te != null && !te.isDyn()) return declared(te);
        JmpType hook = hookParameterType(p);
        if (hook == null) return null;
        JmpType[] found = {hook};
        boolean[] mixed = {false};
        assignments(p, p.getParent() == null ? null : p.getParent().getParent(), found, mixed);
        return mixed[0] ? null : found[0];
    }

    private static @Nullable JmpType hookParameterType(JmpParameter p) {
        if (!(p.getParent() instanceof JmpParameterList list) || !(list.getParent() instanceof JmpFunction f)) return null;
        if (!JmpPsiUtil.isTopLevel(f) || f.getName() == null || !(f.getContainingFile() instanceof JumperFile file)) return null;
        List<JmpParameter> ps = f.getParameters();
        int i = ps.indexOf(p);
        if (i < 0) return null;
        JumperHooks.Hook h = JumperHooks.find(JumperWorkspace.contextFor(file), f.getName(), f);
        PsiMethod m = h == null ? null : h.method(ps.size());
        if (m == null || m.getParameterList().getParametersCount() != ps.size()) return null;
        return fromPsiType(m.getParameterList().getParameters()[i].getType());
    }

    /** The values `x = value` gives the variable or parameter `target` in `scope`; `x += ...` / `x++` make it unknown. */
    private static void assignments(PsiElement target, @Nullable PsiElement scope, JmpType[] found, boolean[] mixed) {
        String name = target instanceof JmpNamedElement n ? n.getName() : null;
        if (scope != null && name != null && !mixed[0]) {
            PsiTreeUtil.processElements(scope, el -> {
                if (!(el instanceof JmpElement a)) return true;
                boolean assign = a.is(ASSIGNMENT_EXPRESSION), inc = a.is(PREFIX_EXPRESSION) || a.is(POSTFIX_EXPRESSION);
                if (!assign && !inc) return true;
                JmpElement t = a.firstExpression();
                if (!(t instanceof JmpReferenceExpression ref) || ref.isQualified() || !name.equals(ref.getReferenceName())) return true;
                if (ref.resolve() != target) return true;
                if (inc || a.getNode().findChildByType(JumperTokenTypes.EQ) == null) { mixed[0] = true; return false; }
                List<JmpElement> xs = a.expressions();
                JmpElement value = xs.size() == 2 ? xs.get(1) : null;
                if (value != null && !isNull(value)) note(typeOf(value), found, mixed);
                return !mixed[0];
            });
        }
    }

    private static void note(@Nullable JmpType t, JmpType[] found, boolean[] mixed) {
        if (t == null) { mixed[0] = true; return; }
        if (found[0] == null) found[0] = t;
        else if (!found[0].equals(t)) mixed[0] = true;
    }

    private static boolean isNull(JmpElement e) {
        return e instanceof JmpLiteral l && l.tokenType() == JumperTokenTypes.NULL;
    }

    /** The block (or file, or loop header, or switch) a local variable lives in. */
    private static PsiElement enclosingScope(PsiElement v) {
        for (PsiElement p = v.getParent(); p != null; p = p.getParent()) {
            if (p instanceof JmpBlock || p instanceof PsiFile) return p;
            if (p instanceof JmpElement e && (e.is(FOR_STATEMENT) || e.is(SWITCH_STATEMENT))) return p;
        }
        return v.getContainingFile();
    }
}
