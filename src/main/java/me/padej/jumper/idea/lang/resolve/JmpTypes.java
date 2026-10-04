package me.padej.jumper.idea.lang.resolve;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.*;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import me.padej.jumper.idea.lang.lexer.JumperTokenTypes;
import me.padej.jumper.idea.lang.psi.*;
import me.padej.jumper.idea.workspace.JumperHooks;
import me.padej.jumper.idea.workspace.JumperWorkspace;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 *   <li>the variable of a for-each (`for (dyn p : server.players())`): what the loop takes out of the value - the
 *       elements of an array, an Iterable, an Iterator, the keys of a Map, the characters of a String (strings) -
 *       when the value's Java type says it (`List<ScriptPlayer>`, `ScriptPlayer[]`), unless the loop gives the
 *       variable other values;</li>
 *   <li>a chain: the return type of a method (the overloads that take that many arguments, if they agree), a
 *       field, a property `getName()` - with the type arguments of the receiver put in (`players().get(0)` of a
 *       `List<ScriptPlayer>` is a ScriptPlayer); a function of the file declared with a class type.</li>
 * </ul>
 * A primitive, a type variable end the chain (no members are looked up on them); so does an array, which a for-each
 * still takes elements out of.
 * <p>
 * Every result is cached on its element until the code or the context of the file (its host, its policy) changes.
 */
public final class JmpTypes {
    private JmpTypes() {}

    private static final Key<CachedValue<JmpType>> TYPE = Key.create("jumper.type");
    private static final Key<CachedValue<JmpType>> DECLARED_TYPE = Key.create("jumper.declaredType");

    public static @Nullable JmpType typeOf(@Nullable PsiElement e) {
        if (!(e instanceof JmpElement je)) return null;
        return cached(je, TYPE, () -> compute(je));
    }

    /** A value computed once per element until the code or the file's context changes (a cycle gives null). */
    private static @Nullable JmpType cached(PsiElement e, Key<CachedValue<JmpType>> key, java.util.function.Supplier<JmpType> f) {
        return CachedValuesManager.getCachedValue(e, key, () -> CachedValueProvider.Result.create(
                RecursionManager.doPreventingRecursion(e, true, f::get),
                PsiModificationTracker.MODIFICATION_COUNT, JumperWorkspace.MODIFICATIONS));
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
            return JmpType.common(typeOf(xs.get(1)), typeOf(xs.get(2)));
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

    /** Instances of a Java type: a class with its type arguments, an array; a primitive, a type variable - null. */
    public static @Nullable JmpType fromPsiType(@Nullable PsiType t) {
        t = upperBound(t);
        if (t instanceof PsiArrayType) return JmpType.instances(null, t);
        PsiClass c = JmpJava.classOf(t);
        return c == null ? null : JmpType.instances(c, t);
    }

    /** `? extends X`, a captured one: X; `?`, `? super X`: nothing known. */
    private static @Nullable PsiType upperBound(@Nullable PsiType t) {
        if (t instanceof PsiCapturedWildcardType c) t = c.getUpperBound();
        if (t instanceof PsiWildcardType w) t = w.isExtends() ? w.getExtendsBound() : null;
        return t;
    }

    /**
     * The type of a member read on a receiver: the declared type with the receiver's type arguments put in
     * (`E get(int)` on a `List<ScriptPlayer>` gives a ScriptPlayer).
     */
    private static @Nullable PsiType memberType(@Nullable PsiType declared, PsiMember m, @Nullable JmpType receiver) {
        if (declared == null || receiver == null || receiver.statics() || receiver.java() == null) return declared;
        PsiClass owner = m.getContainingClass();
        if (owner == null || m.hasModifierProperty(PsiModifier.STATIC)) return declared;
        PsiClass rc = receiver.java();
        PsiSubstitutor rs = PsiSubstitutor.EMPTY;
        if (receiver.javaType() instanceof PsiClassType rt) {
            PsiClassType.ClassResolveResult rr = rt.resolveGenerics();
            if (rr.getElement() != null) {
                rc = rr.getElement();
                rs = rr.getSubstitutor();
            }
        }
        PsiSubstitutor s = TypeConversionUtil.getClassSubstitutor(owner, rc, rs);
        return s == null ? declared : s.substitute(declared);
    }

    /**
     * What a for-each takes out of a value of this type (Ops.iter): the elements of an array, of an Iterable or of
     * an Iterator, the keys of a Map, the characters of a String (strings). Null when the type does not say (a raw
     * `List`, `List<?>`, elements of Object).
     */
    public static @Nullable JmpType elementType(@Nullable JmpType of, PsiElement context) {
        if (of == null || of.statics()) return null;
        PsiType t = of.javaType();
        if (t instanceof PsiArrayType a) return members(fromPsiType(a.getComponentType()));
        PsiClass c = of.java();
        if (c == null) return null;
        // a class without its type arguments (a host global, `new C()`): what its supertypes say (`extends ArrayList<P>`)
        if (t == null) t = JavaPsiFacade.getElementFactory(c.getProject()).createType(c);
        if (CommonClassNames.JAVA_LANG_STRING.equals(c.getQualifiedName())) return string(context);
        for (String base : ITERATED) {
            if (!InheritanceUtil.isInheritor(c, base)) continue;
            return members(fromPsiType(PsiUtil.substituteTypeParameter(t, base, 0, false)));
        }
        return null;
    }

    /** In the order Ops.iter tries them. */
    private static final List<String> ITERATED = List.of(CommonClassNames.JAVA_LANG_ITERABLE, CommonClassNames.JAVA_UTIL_MAP,
            CommonClassNames.JAVA_UTIL_ITERATOR);

    /** A type members can be looked up on: an Object tells nothing. */
    private static @Nullable JmpType members(@Nullable JmpType t) {
        return t == null || t.java() != null && CommonClassNames.JAVA_LANG_OBJECT.equals(t.java().getQualifiedName()) ? null : t;
    }

    private static @Nullable JmpType referenceType(JmpReferenceExpression r) {
        if (r.nameTokenType() == JumperTokenTypes.KW_STRING) return JmpType.java(JmpJava.findClass("java.lang.String", r), true);
        PsiElement t = r.resolve();
        if (t instanceof PsiClass c) return JmpType.java(c, true);
        if (t instanceof JmpClass c) return JmpType.script(c, true);
        if (t instanceof JmpSynthetic s) return s.kind() == JmpSynthetic.Kind.HOST_GLOBAL ? JmpType.java(s.typeClass(), false) : null;
        if (t instanceof PsiField f) return fromPsiType(memberType(f.getType(), f, receiverType(r)));
        if (t instanceof PsiMethod m && !r.isCallee())
            return m.getParameterList().getParametersCount() == 0 ? fromPsiType(memberType(m.getReturnType(), m, receiverType(r))) : null;
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
        JmpType receiver = receiverType(callee);
        JmpType out = null;
        for (PsiMethod m : JmpJava.arity(ms, args)) {
            JmpType t = fromPsiType(memberType(m.getReturnType(), m, receiver));
            if (t == null || out != null && !out.equals(t)) return null;   // the overloads must agree (Members.returnType)
            out = t;
        }
        return out;
    }

    private static @Nullable JmpType receiverType(JmpReferenceExpression r) {
        return r.isQualified() ? typeOf(r.getQualifier()) : null;
    }

    /**
     * A variable: its declared type, else (a `dyn` one) the type of every value it is given - its initializer and
     * each `x = value` - when they all agree; a `null` tells nothing, `x += ...` / `x++` make it unknown
     * (MemberCheck.Locals: a variable given values of more than one type has none).
     */
    public static @Nullable JmpType variableType(JmpVariable v) {
        JmpTypeElement te = v.getTypeElement();
        if (te != null && !te.isDyn()) return declared(te);
        return cached(v, DECLARED_TYPE, () -> inferVariable(v));
    }

    private static @Nullable JmpType inferVariable(JmpVariable v) {
        JmpType[] found = {null};
        boolean[] mixed = {false};
        PsiElement scope;
        if (v.isForEach()) {
            // `for (dyn p : xs)`: an element of xs, and whatever the body gives p
            PsiElement loop = v.getParent();
            JmpType element = loop instanceof JmpElement l ? elementType(typeOf(l.firstExpression()), v) : null;
            if (element == null) return null;
            found[0] = element;
            scope = loop;
        } else {
            JmpElement init = v.getInitializer();
            if (init != null && !isNull(init)) note(typeOf(init), found, mixed);
            scope = v.isField() ? v.getContainingFile() : enclosingScope(v);
        }
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
        return cached(p, DECLARED_TYPE, () -> inferParameter(p));
    }

    private static @Nullable JmpType inferParameter(JmpParameter p) {
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
        if (scope == null || name == null || mixed[0]) return;
        for (JmpElement a : writes(scope).getOrDefault(name, List.of())) {
            if (!(a.firstExpression() instanceof JmpReferenceExpression ref) || ref.resolve() != target) continue;
            boolean inc = a.is(PREFIX_EXPRESSION) || a.is(POSTFIX_EXPRESSION);
            if (inc || a.getNode().findChildByType(JumperTokenTypes.EQ) == null) { mixed[0] = true; return; }
            List<JmpElement> xs = a.expressions();
            JmpElement value = xs.size() == 2 ? xs.get(1) : null;
            if (value != null && !isNull(value)) note(typeOf(value), found, mixed);
            if (mixed[0]) return;
        }
    }

    /** `x++`, `--x` (not `-x`, `!x`). */
    private static boolean isIncrement(JmpElement a) {
        return (a.is(PREFIX_EXPRESSION) || a.is(POSTFIX_EXPRESSION))
                && a.getNode().findChildByType(com.intellij.psi.tree.TokenSet.create(JumperTokenTypes.PLUSPLUS, JumperTokenTypes.MINUSMINUS)) != null;
    }

    private static final Key<CachedValue<Map<String, List<JmpElement>>>> WRITES = Key.create("jumper.writes");

    /**
     * The assignments and increments in a scope by the name they write (`x = ...`, `x += ...`, `x++`): one walk of the
     * scope for all its variables, not one per variable.
     */
    private static Map<String, List<JmpElement>> writes(PsiElement scope) {
        return CachedValuesManager.getCachedValue(scope, WRITES, () -> {
            Map<String, List<JmpElement>> out = new HashMap<>();
            PsiTreeUtil.processElements(scope, el -> {
                if (el instanceof JmpElement a && (a.is(ASSIGNMENT_EXPRESSION) || isIncrement(a))
                        && a.firstExpression() instanceof JmpReferenceExpression ref && !ref.isQualified() && ref.getReferenceName() != null)
                    out.computeIfAbsent(ref.getReferenceName(), k -> new ArrayList<>(1)).add(a);
                return true;
            });
            return CachedValueProvider.Result.create(out, PsiModificationTracker.MODIFICATION_COUNT);
        });
    }

    private static void note(@Nullable JmpType t, JmpType[] found, boolean[] mixed) {
        if (t == null) { mixed[0] = true; return; }
        if (found[0] == null) { found[0] = t; return; }
        found[0] = JmpType.common(found[0], t);
        if (found[0] == null) mixed[0] = true;
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
