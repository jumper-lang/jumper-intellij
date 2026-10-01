package me.padej.jumper.idea.lang.resolve;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import me.padej.jumper.idea.lang.lexer.JumperTokenTypes;
import me.padej.jumper.idea.lang.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static me.padej.jumper.idea.lang.parser.JumperElementTypes.*;

/**
 * What a name refers to (Parser.variable and member, lsp.Features.definition):
 * <ul>
 *   <li>a bare name: a declaration visible there ({@link JmpScopes}), a host global, a built-in; else a Java class
 *       - imported (`import a.b.C;`) or of java.lang; else the first part of a qualified name - a package;</li>
 *   <li>`q.name`: a member of what q is - of a package (a class or a subpackage), of a Java class (its statics) or a
 *       Java object (methods, fields, properties `getName()`), of a script class or its instance; `this.x`, `super.m`;</li>
 *   <li>a name in `import a.b.C;`: the package or the class by its full name.</li>
 * </ul>
 * A method call has as many results as the overloads of its name; those that take that many arguments are the valid ones.
 */
public final class JmpResolver implements ResolveCache.PolyVariantResolver<JmpReference> {
    public static final JmpResolver INSTANCE = new JmpResolver();

    @Override
    public ResolveResult @NotNull [] resolve(@NotNull JmpReference ref, boolean incompleteCode) {
        JmpReferenceExpression e = ref.getElement();
        String name = e.getReferenceName();
        if (name == null) return ResolveResult.EMPTY_ARRAY;
        return e.isQualified() ? qualified(e, name) : unqualified(e, name);
    }

    private static ResolveResult[] one(@Nullable PsiElement t) {
        return t == null ? ResolveResult.EMPTY_ARRAY : new ResolveResult[] {new PsiElementResolveResult(t)};
    }

    private static ResolveResult[] unqualified(JmpReferenceExpression e, String name) {
        var t = e.nameTokenType();
        if (t == JumperTokenTypes.KW_STRING) return one(JmpJava.findClass("java.lang.String", e));
        if (t == JumperTokenTypes.KW_INT || t == JumperTokenTypes.KW_LONG || t == JumperTokenTypes.KW_DOUBLE)
            return one(new JmpSynthetic(JmpSynthetic.Kind.BUILTIN, name, JmpBuiltins.SIGNATURES.get(name).get(0), e));
        if (e.inImport()) return one(importTarget(e));
        PsiElement[] found = {null};
        // the type of a declaration (`Point p`): only what can be a class; `new cls(...)` takes any value
        boolean typePosition = e.isTypeName();
        JmpScopes.processDeclarations(e, (n, target) -> {
            if (!n.equals(name)) return true;
            if (typePosition && !(target instanceof JmpClass || target instanceof PsiClass)) return true;
            found[0] = target;
            return false;
        });
        if (found[0] != null) return one(found[0]);
        PsiFile file = e.getContainingFile();
        if (file instanceof JumperFile jf) {
            PsiClass c = JmpScopes.fileImport(jf, name);
            if (c != null) return one(c);
        }
        PsiClass lang = JmpJava.javaLang(name, e);
        if (lang != null) return one(lang);
        // `java` of `java.util.HashMap`: the first part of a qualified name
        if (e.getParent() instanceof JmpReferenceExpression outer && outer.getQualifier() == e) {
            PsiPackage p = JmpJava.findPackage(name, e);
            if (p != null) return one(p);
        }
        return ResolveResult.EMPTY_ARRAY;
    }

    /** `import a.b.C;`: each part by its full name - a package, the class (the last part). */
    private static @Nullable PsiElement importTarget(JmpReferenceExpression e) {
        String fq = e.getText().replaceAll("\\s+", "");
        PsiClass c = JmpJava.findClass(fq, e);
        if (c != null) return c;
        return JmpJava.findPackage(fq, e);
    }

    private static ResolveResult[] qualified(JmpReferenceExpression e, String name) {
        JmpElement q = e.getQualifier();
        if (e.nameTokenType() == JumperTokenTypes.CLASS) return ResolveResult.EMPTY_ARRAY;   // C.class
        if (e.inImport()) return one(importTarget(e));
        if (q != null && q.is(THIS_EXPRESSION)) {
            JmpClass cls = JmpPsiUtil.enclosingClass(e);
            return one(cls == null ? null : cls.findMember(name));
        }
        if (q != null && q.is(SUPER_EXPRESSION)) {
            JmpClass cls = JmpPsiUtil.enclosingClass(e);
            JmpClass sup = cls == null ? null : cls.getSuperClass();
            return one(sup == null ? null : sup.findMember(name));
        }
        if (q instanceof JmpReferenceExpression qr) {
            PsiElement qt = qr.resolve();
            if (qt instanceof PsiPackage pkg) {
                String fq = pkg.getQualifiedName().isEmpty() ? name : pkg.getQualifiedName() + "." + name;
                PsiClass c = JmpJava.findClass(fq, e);
                if (c != null) return one(c);
                return one(JmpJava.findPackage(fq, e));
            }
        }
        JmpType type = JmpTypes.typeOf(q);
        if (type == null) return ResolveResult.EMPTY_ARRAY;
        if (type.script() != null) return one(type.script().findMember(name));
        return javaMember(e, type.java(), type.statics(), name);
    }

    /** `recv.name` on a Java type: the methods of that name for a call, else a field, a nested class, a property, a method as a value. */
    private static ResolveResult[] javaMember(JmpReferenceExpression e, PsiClass cls, boolean statics, String name) {
        if (e.isCallee()) {
            List<PsiMethod> ms = JmpJava.methodsNamed(cls, statics, name);
            if (ms.isEmpty()) return ResolveResult.EMPTY_ARRAY;
            int args = ((JmpCallExpression) e.getParent()).getArguments().size();
            List<PsiMethod> fit = JmpJava.arity(ms, args);
            List<ResolveResult> out = new ArrayList<>();
            for (PsiMethod m : ms) out.add(new PsiElementResolveResult(m, fit.contains(m)));
            return out.toArray(ResolveResult.EMPTY_ARRAY);
        }
        PsiField f = JmpJava.field(cls, statics, name);
        if (f != null) return one(f);
        if (statics) {
            PsiClass inner = JmpJava.inner(cls, name);
            if (inner != null) return one(inner);
        } else {
            PsiMethod g = JmpJava.getter(cls, name);
            if (g != null) return one(g);
        }
        List<PsiMethod> ms = JmpJava.methodsNamed(cls, statics, name);
        List<ResolveResult> out = new ArrayList<>();
        for (PsiMethod m : ms) out.add(new PsiElementResolveResult(m));
        return out.toArray(ResolveResult.EMPTY_ARRAY);
    }
}
