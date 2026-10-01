package me.padej.jumper.idea.annotator;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import me.padej.jumper.idea.lang.lexer.JumperLiterals;
import me.padej.jumper.idea.lang.lexer.JumperTokenTypes;
import me.padej.jumper.idea.lang.psi.*;
import me.padej.jumper.idea.lang.resolve.*;
import me.padej.jumper.idea.workspace.JumperContext;
import me.padej.jumper.idea.workspace.JumperPolicy;
import me.padej.jumper.idea.workspace.JumperWorkspace;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static me.padej.jumper.idea.lang.parser.JumperElementTypes.*;

/**
 * The language's diagnostics (ScalaAnnotator's place): what `jmp --check` and the language server report, with
 * their messages -
 * <ul>
 *   <li>lexical errors (a bad escape, a number too large, an unclosed comment) - Lexer;</li>
 *   <li>what the parser checks beyond the grammar: undefined names, redeclarations, `break` outside a loop,
 *       `this` outside a class, static types (`int x = "s"`, `true + 1`), a constructor's arity, cyclic
 *       inheritance, switch forms, `return value` in a void function, what a config (.jmc) may not have;</li>
 *   <li>the access policy: a class the script's policy closes is an error (Access denied), as it is when the
 *       script is parsed; a closed method is a warning, as the language server gives it;</li>
 *   <li>calls that will fail where the receiver's Java type is known (workspace.MemberCheck): no such method, no
 *       overload with that many arguments - warnings.</li>
 * </ul>
 */
public final class JumperAnnotator implements Annotator {
    @Override
    public void annotate(@NotNull PsiElement e, @NotNull AnnotationHolder h) {
        IElementType t = e.getNode() == null ? null : e.getNode().getElementType();
        if (e.getFirstChild() == null && t != null) lexical(e, t, h);   // tokens and comments
        if (e.getParent() instanceof JumperFile f && isFirst(e)) fileNotes(f, h);
        if (!(e instanceof JmpElement x)) return;
        JumperFile file = x.getContainingFile() instanceof JumperFile jf ? jf : null;
        if (file == null) return;
        if (file.isConfig()) config(x, h);
        if (x instanceof JmpReferenceExpression r) reference(r, file, h);
        else if (x instanceof JmpCallExpression c) call(c, file, h);
        else if (x instanceof JmpNewExpression n) newExpression(n, h);
        else if (x instanceof JmpImportStatement i) importStatement(i, file, h);
        else if (x instanceof JmpModuleImport m) moduleImport(m, file, h);
        else if (x instanceof JmpVariable v) variable(v, h);
        else if (x instanceof JmpClass c) classDeclaration(c, h);
        else if (x instanceof JmpBlock || x instanceof JmpParameterList) duplicates(x, h);
        else if (x.is(BREAK_STATEMENT) || x.is(CONTINUE_STATEMENT)) loopOnly(x, h);
        else if (x.is(THIS_EXPRESSION) || x.is(SUPER_EXPRESSION)) thisOrSuper(x, h);
        else if (x.is(RETURN_STATEMENT)) returnStatement(x, h);
        else if (x.is(ASSIGNMENT_EXPRESSION)) assignment(x, h);
        else if (x.is(BINARY_EXPRESSION)) binary(x, h);
        else if (x.is(PREFIX_EXPRESSION) || x.is(POSTFIX_EXPRESSION)) increment(x, h);
        else if (x.is(SWITCH_STATEMENT) || x.is(SWITCH_EXPRESSION)) switchForm(x, h);
        if (x.getParent() instanceof JumperFile) duplicates(file, x, h);
    }

    private static void error(AnnotationHolder h, PsiElement at, String msg) {
        h.newAnnotation(HighlightSeverity.ERROR, msg).range(at).create();
    }

    private static void warning(AnnotationHolder h, PsiElement at, String msg) {
        h.newAnnotation(HighlightSeverity.WARNING, msg).range(at).create();
    }

    // ------------------------------------------------------------------ lexical

    private static void lexical(PsiElement leaf, IElementType t, AnnotationHolder h) {
        JumperLiterals.Problem p = JumperLiterals.check(t, leaf.getText());
        if (p != null) {
            int start = leaf.getTextRange().getStartOffset() + p.offset();
            TextRange r = p.offset() == 0 ? leaf.getTextRange() : new TextRange(start, Math.min(leaf.getTextRange().getEndOffset(), start + 2));
            h.newAnnotation(HighlightSeverity.ERROR, p.message()).range(r).create();
            return;
        }
        // 2147483648 and 9223372036854775808L exist only as the magnitude of the smallest value
        if ((t == JumperTokenTypes.INT || t == JumperTokenTypes.LONG) && JumperLiterals.isMinValueMagnitude(t, leaf.getText())) {
            PsiElement lit = leaf.getParent();
            PsiElement prefix = lit == null ? null : lit.getParent();
            boolean negated = prefix instanceof JmpElement pe && pe.is(PREFIX_EXPRESSION)
                    && pe.getFirstChild().getNode().getElementType() == JumperTokenTypes.MINUS;
            if (!negated) error(h, leaf, t == JumperTokenTypes.INT ? "Integer literal too large (use L suffix): " + leaf.getText()
                    : "Long literal too large: " + leaf.getText());
        }
    }

    /** The first thing of the file that is not white space (the notes go on its line). */
    private static boolean isFirst(PsiElement e) {
        if (e instanceof PsiWhiteSpace) return false;
        for (PsiElement p = e.getPrevSibling(); p != null; p = p.getPrevSibling()) if (!(p instanceof PsiWhiteSpace)) return false;
        return true;
    }

    /** How the file's context was found when it is not certain (the language server's hints on the first line). */
    private static void fileNotes(JumperFile f, AnnotationHolder h) {
        JumperContext ctx = JumperWorkspace.contextFor(f);
        if (ctx.notes().isEmpty()) return;
        PsiElement first = f.getFirstChild();
        while (first instanceof PsiWhiteSpace) first = first.getNextSibling();
        if (first == null) return;
        int end = first.getTextRange().getStartOffset();
        int lineEnd = f.getText().indexOf('\n', end);
        TextRange r = new TextRange(end, lineEnd < 0 ? f.getTextLength() : lineEnd);
        if (r.isEmpty()) return;
        for (String note : ctx.notes()) h.newAnnotation(HighlightSeverity.WEAK_WARNING, "Context: " + note).range(r).create();
    }

    // ------------------------------------------------------------------ config

    /** Parser.config(): what a .jmc may not contain - data, variables, if/else, switch, ternaries only. */
    private static void config(JmpElement x, AnnotationHolder h) {
        String what = null;
        PsiElement at = x.getFirstChild();
        if (x instanceof JmpImportStatement || x instanceof JmpModuleImport) what = "import";
        else if (LOOPS.contains(x.type())) what = "A loop";
        else if (x instanceof JmpClass) what = "A class";
        else if (x.is(TRY_STATEMENT) || x.is(THROW_STATEMENT)) what = "try/throw";
        else if (x instanceof JmpFunction f) { what = "A function"; at = f.getNameIdentifier() != null ? f.getNameIdentifier() : at; }
        else if (x instanceof JmpLambda) {
            what = "A lambda";
            com.intellij.lang.ASTNode arrow = x.getNode().findChildByType(JumperTokenTypes.ARROW);
            if (arrow != null) at = arrow.getPsi();
        }
        if (what != null && at != null) error(h, at, what + " is not allowed in a config (.jmc)");
    }

    // ------------------------------------------------------------------ names

    private static void reference(JmpReferenceExpression r, JumperFile file, AnnotationHolder h) {
        PsiElement name = r.getReferenceNameElement();
        String n = r.getReferenceName();
        if (name == null || n == null || name.getNode().getElementType() != JumperTokenTypes.IDENT) return;
        if (r.inImport()) return;   // the import statement reports
        ResolveResult[] rs = r.multiResolve(false);
        PsiElement target = rs.length == 0 ? null : r.resolve() != null ? r.resolve() : rs[0].getElement();
        if (target == null) {
            if (!r.isQualified()) undefined(r, name, n, h);
            else scriptMemberMissing(r, name, n, h);
            return;
        }
        if (target instanceof PsiClass cls) javaClassUse(r, name, cls, file, h);
        else if ((target instanceof PsiField || target instanceof PsiMethod && !r.isCallee()) && r.isQualified()) {
            JumperPolicy policy = policyOf(file);
            JmpType q = JmpTypes.typeOf(r.getQualifier());
            if (policy != null && q != null && q.java() != null && !policy.allowed((PsiMember) target, q.java()))
                warning(h, name, "Access denied: " + q.java().getQualifiedName() + "." + n + " (closed by the access policy)");
        }
    }

    private static void undefined(JmpReferenceExpression r, PsiElement name, String n, AnnotationHolder h) {
        boolean capital = Character.isUpperCase(n.charAt(0));
        // without a JDK (a server folder opened on its own) a class cannot be told from a mistake
        if (capital && !JmpJava.available(r)) return;
        if (r.getParent() instanceof JmpReferenceExpression outer && outer.getQualifier() == r && !JmpJava.available(r)) return;
        // `java` of `java.util.X` where the package is not known either: one error on the whole name is enough
        var ann = h.newAnnotation(HighlightSeverity.ERROR, "Undefined variable '" + n + "'").range(name);
        if (capital) ann = ann.withFix(new JumperImportClassFix(r));
        ann.create();
    }

    /** `obj.nope` where obj is an instance of a script class of the file: instances are sealed ("No field 'nope' in C"). */
    private static void scriptMemberMissing(JmpReferenceExpression r, PsiElement name, String n, AnnotationHolder h) {
        if (r.nameTokenType() == JumperTokenTypes.CLASS) return;
        JmpElement q = r.getQualifier();
        JmpType t = q != null && !q.is(THIS_EXPRESSION) && !q.is(SUPER_EXPRESSION) ? JmpTypes.typeOf(q) : null;
        if (q != null && (q.is(THIS_EXPRESSION) || q.is(SUPER_EXPRESSION))) {
            JmpClass c = JmpPsiUtil.enclosingClass(r);
            if (c != null && !hasUnknownParent(c)) warning(h, name, "No field '" + n + "' in " + c.getName());
            return;
        }
        if (t == null || t.script() == null || hasUnknownParent(t.script())) return;
        if (!t.statics() && subclassHas(t.script(), n)) return;   // a `Vec o` may hold a Vec3 that has it
        warning(h, name, (t.statics() ? "No static member '" : "No field '") + n + "' in " + t.script().getName());
    }

    /** Does a script class of the file that extends `c` declare `name`? */
    private static boolean subclassHas(JmpClass c, String name) {
        boolean[] found = {false};
        com.intellij.psi.util.PsiTreeUtil.processElements(c.getContainingFile(), e -> {
            if (e instanceof JmpClass k && k != c && k.findMember(name) != null) {
                for (JmpClass p = k.getSuperClass(); p != null && p != k; p = p.getSuperClass()) if (p == c) { found[0] = true; return false; }
            }
            return true;
        });
        return found[0];
    }

    /** The class extends something that is not a script class of the file (a module's, a mistake): its members are not all known. */
    private static boolean hasUnknownParent(JmpClass c) {
        for (int guard = 0; c != null && guard < 32; guard++) {
            if (c.getExtendsReference() != null && c.getSuperClass() == null) return true;
            c = c.getSuperClass();
        }
        return false;
    }

    /** A Java class named by a script: seen by its policy? In a config or a policy file, none but Policy. */
    private static void javaClassUse(JmpReferenceExpression r, PsiElement name, PsiClass cls, JumperFile file, AnnotationHolder h) {
        if (r.getParent() instanceof JmpReferenceExpression outer && outer.getQualifier() == r && outer.resolve() instanceof PsiClass)
            return;   // `java.util.Map.Entry`: the outer part is checked
        String fq = cls.getQualifiedName();
        if (file.isConfig()) { error(h, name, "Access denied: " + fq + " (a config sees no Java)"); return; }
        if (file.isPolicy()) {
            if (!"me.padej.jumper.security.Policy".equals(fq)) error(h, name, "Access denied: " + fq + " (a policy sees only Policy)");
            return;
        }
        JumperPolicy policy = policyOf(file);
        if (policy != null && !policy.visible(cls)) error(h, name, "Access denied: " + fq);
    }

    private static JumperPolicy policyOf(JumperFile file) {
        return file.kind() == me.padej.jumper.idea.JumperFileType.Kind.SCRIPT ? JumperPolicy.of(JumperWorkspace.contextFor(file)) : null;
    }

    // ------------------------------------------------------------------ calls (MemberCheck)

    private static void call(JmpCallExpression c, JumperFile file, AnnotationHolder h) {
        if (!(c.getCallee() instanceof JmpReferenceExpression callee) || !callee.isQualified()) return;
        PsiElement name = callee.getReferenceNameElement();
        String n = callee.getReferenceName();
        if (name == null || n == null) return;
        JmpType t = JmpTypes.typeOf(callee.getQualifier());
        if (t == null || t.java() == null) return;
        PsiClass cls = t.java();
        String owner = cls.getName();
        int args = c.getArguments().size();
        List<PsiMethod> byName = JmpJava.methodsNamed(cls, t.statics(), n);
        if (byName.isEmpty()) {
            // `obj.f(x)` where f is a public field holding a function: a call of the field, not a method
            if (JmpJava.field(cls, t.statics(), n) != null) return;
            warning(h, name, "No " + (t.statics() ? "static " : "") + "method '" + n + "' in " + owner);
            return;
        }
        List<PsiMethod> fit = JmpJava.arity(byName, args);
        if (fit.isEmpty()) {
            warning(h, name, owner + "." + n + " takes " + arities(byName) + ", not " + args);
            return;
        }
        JumperPolicy policy = policyOf(file);
        if (policy != null && fit.stream().noneMatch(m -> policy.allowed(m, cls)))
            warning(h, name, "Access denied: " + cls.getQualifiedName() + "." + n + " (closed by the access policy)");
    }

    private static String arities(List<PsiMethod> ms) {
        TreeSet<Integer> ns = new TreeSet<>();
        for (PsiMethod m : ms) ns.add(m.getParameterList().getParametersCount());
        StringBuilder sb = new StringBuilder();
        for (int n : ns) {
            if (sb.length() > 0) sb.append(" or ");
            sb.append(n);
        }
        return sb.append(ns.size() == 1 && ns.first() == 1 ? " argument" : " arguments").toString();
    }

    /** `new C(a, b)` of a script class: the arity of its constructor (Parser.checkNewArity). */
    private static void newExpression(JmpNewExpression n, AnnotationHolder h) {
        JmpReferenceExpression ref = n.getClassReference();
        if (ref == null || n.getArgumentList() == null) return;
        PsiElement t = ref.resolve();
        if (t instanceof JmpClass c && !hasUnknownParent(c)) {
            int expected = c.constructorArity(), got = n.getArguments().size();
            if (expected != got)
                error(h, n.getFirstChild(), "Constructor " + c.getName() + " expects " + expected + " argument" + (expected == 1 ? "" : "s") + ", got " + got);
        } else if (t instanceof PsiClass c && (c.isInterface() || c.hasModifierProperty(PsiModifier.ABSTRACT))) {
            warning(h, ref, "Cannot instantiate " + c.getQualifiedName());
        }
    }

    // ------------------------------------------------------------------ imports

    private static void importStatement(JmpImportStatement i, JumperFile file, AnnotationHolder h) {
        String fq = i.getQualifiedName();
        if (fq == null || file.isConfig()) return;
        PsiClass c = JmpScopes.importedClass(i);
        PsiElement at = i.getFirstChild();
        if (c == null) {
            if (JmpJava.available(i)) error(h, i.getImportReference(), "Class not found: " + fq);
            return;
        }
        if (c.getContainingClass() != null) error(h, i.getImportReference(), "Class not found: " + fq + " (a nested class is not imported: write " + c.getContainingClass().getName() + "." + c.getName() + ")");
        if (file.isPolicy()) error(h, at, "Access denied: " + fq + " (a policy sees only Policy)");
        JumperPolicy policy = policyOf(file);
        if (policy != null && !policy.visible(c)) error(h, i.getImportReference(), "Access denied: " + fq);
        // over another binding of this scope (Parser.importStmt)
        String simple = i.getSimpleName();
        PsiElement scope = i.getParent();
        for (PsiElement s = scope.getFirstChild(); s != null && s != i; s = s.getNextSibling()) {
            String other = declaredName(s);
            if (simple != null && simple.equals(other))
                error(h, at, "Imported name '" + simple + "' conflicts with a declaration in this scope");
        }
    }

    private static String declaredName(PsiElement s) {
        if (s instanceof JmpFunction || s instanceof JmpClass) return ((JmpNamedElement) s).getName();
        if (s instanceof JmpElement e && e.is(VARIABLE_DECLARATION)) {
            for (JmpVariable v : e.children(JmpVariable.class)) return v.getName();
        }
        return null;
    }

    private static void moduleImport(JmpModuleImport m, JumperFile file, AnnotationHolder h) {
        if (file.isConfig()) return;
        JmpLiteral lit = m.getPathLiteral();
        if (lit == null) return;
        if (m.resolveModule() == null) { error(h, lit, "Module not found: " + m.getSpec()); return; }
        JumperPolicy policy = policyOf(file);
        if (policy != null && !policy.modulesAllowed())
            error(h, m.getFirstChild(), "Access denied: import \"" + m.getSpec() + "\" (modules are not allowed by the access policy)");
    }

    // ------------------------------------------------------------------ declarations

    private static void variable(JmpVariable v, AnnotationHolder h) {
        JmpElement init = v.getInitializer();
        PsiElement name = v.getNameIdentifier();
        if (init == null || name == null) return;
        JmpVarType target = JmpVarType.declared(v.getTypeElement());
        String msg = JmpVarType.checkAssignable(target, JmpVarType.of(init));
        if (msg != null) { error(h, name, msg); return; }
        classTarget(v.getTypeElement(), init, name, h);
    }

    /** `Point p = 5` (Parser.checked): a primitive or a string into a script class type; another script class. */
    private static void classTarget(JmpTypeElement te, JmpElement value, PsiElement at, AnnotationHolder h) {
        if (te == null || te.reference() == null || !(te.reference().resolve() instanceof JmpClass target)) return;
        JmpVarType vt = JmpVarType.of(value);
        if (vt != JmpVarType.DYN) { error(h, at, "Cannot assign " + vt.display() + " to " + target.getName()); return; }
        JmpClass known = staticClass(value);
        if (known != null) {
            for (JmpClass c = known; c != null; c = c.getSuperClass()) if (c == target) return;
            if (!hasUnknownParent(known)) error(h, at, "Cannot assign " + known.getName() + " to " + target.getName());
        }
    }

    /** Expr.staticClass: `new C()`, a variable or parameter declared `C x`, a function declared to return C. */
    private static JmpClass staticClass(JmpElement value) {
        if (value instanceof JmpNewExpression n) return n.getClassReference() != null && n.getClassReference().resolve() instanceof JmpClass c ? c : null;
        JmpTypeElement te = null;
        if (value instanceof JmpReferenceExpression r && !r.isQualified()) {
            PsiElement t = r.resolve();
            te = t instanceof JmpVariable v ? v.getTypeElement() : t instanceof JmpParameter p ? p.getTypeElement() : null;
        } else if (value instanceof JmpCallExpression c && c.getCallee() instanceof JmpReferenceExpression cr && cr.resolve() instanceof JmpFunction f) {
            te = f.getTypeElement();
        }
        return te != null && te.reference() != null && te.reference().resolve() instanceof JmpClass c ? c : null;
    }

    private static void assignment(JmpElement a, AnnotationHolder h) {
        List<JmpElement> xs = a.expressions();
        if (xs.size() != 2) return;
        JmpElement target = xs.get(0), value = xs.get(1);
        PsiElement op = null;
        for (PsiElement c = a.getFirstChild(); c != null; c = c.getNextSibling())
            if (JumperTokenTypes.ASSIGN_OPS.contains(c.getNode().getElementType())) { op = c; break; }
        if (op == null) return;
        // lvalues: a name, a member, an index (Parser.checkLvalue / makeAssign)
        if (!(target instanceof JmpReferenceExpression) && !target.is(INDEX_EXPRESSION)) {
            error(h, op, "Invalid assignment target");
            return;
        }
        if (target instanceof JmpReferenceExpression r) {
            PsiElement t = r.resolve();
            if (t instanceof JmpClass || t instanceof PsiClass) { error(h, op, "Cannot assign to class '" + r.getReferenceName() + "'"); return; }
            if (t instanceof JmpSynthetic || t instanceof JmpFunction && !r.isQualified() && ((JmpFunction) t).isMethod()) {
                error(h, op, "Cannot assign to a constant or builtin");
                return;
            }
            if (!r.isQualified() && isModuleName(r, t)) { error(h, op, "Cannot assign to a constant or builtin"); return; }
            // `obj.f = v` is checked when it runs; a name, `this.f` and `Cls.f` statically (Parser.makeAssign)
            JmpElement q = r.getQualifier();
            boolean staticTarget = q == null || q.is(THIS_EXPRESSION)
                    || q instanceof JmpReferenceExpression qr && qr.resolve() instanceof JmpClass;
            if (!staticTarget) return;
            JmpTypeElement te = t instanceof JmpVariable v ? v.getTypeElement() : t instanceof JmpParameter p ? p.getTypeElement() : null;
            JmpVarType tt = JmpVarType.declared(te);
            if (tt == JmpVarType.DYN) return;
            JmpVarType vt = op.getNode().getElementType() == JumperTokenTypes.EQ ? JmpVarType.of(value)
                    : JmpVarType.binaryType(compoundOp(op.getNode().getElementType()), tt, JmpVarType.of(value));
            String msg = JmpVarType.checkAssignable(tt, vt);
            if (msg != null) error(h, op, msg);
        }
    }

    /** A name imported from a module is a constant (Modules: "an imported name cannot be assigned"). */
    private static boolean isModuleName(JmpReferenceExpression r, PsiElement t) {
        return t instanceof JmpNamedElement n && n.getContainingFile() != r.getContainingFile().getOriginalFile()
                && n.getContainingFile() != r.getContainingFile();
    }

    private static IElementType compoundOp(IElementType op) {
        if (op == JumperTokenTypes.PLUSEQ) return JumperTokenTypes.PLUS;
        if (op == JumperTokenTypes.MINUSEQ) return JumperTokenTypes.MINUS;
        if (op == JumperTokenTypes.STAREQ) return JumperTokenTypes.STAR;
        if (op == JumperTokenTypes.SLASHEQ) return JumperTokenTypes.SLASH;
        return JumperTokenTypes.PERCENT;
    }

    private static void binary(JmpElement b, AnnotationHolder h) {
        List<JmpElement> xs = b.expressions();
        PsiElement op = JmpVarType.operator(b);
        if (xs.size() != 2 || op == null) return;
        String msg = JmpVarType.binaryError(op.getNode().getElementType(), JmpVarType.of(xs.get(0)), JmpVarType.of(xs.get(1)), op.getText());
        if (msg != null) error(h, op, msg);
    }

    /** `++b` on a boolean; `f()++` (Parser.makeInc / checkLvalue). */
    private static void increment(JmpElement x, AnnotationHolder h) {
        PsiElement op = x.is(PREFIX_EXPRESSION) ? x.getFirstChild() : x.getLastChild();
        IElementType ot = op.getNode().getElementType();
        if (ot != JumperTokenTypes.PLUSPLUS && ot != JumperTokenTypes.MINUSMINUS) return;
        JmpElement target = x.firstExpression();
        if (!(target instanceof JmpReferenceExpression) && !(target != null && target.is(INDEX_EXPRESSION))) {
            error(h, op, "Invalid assignment target");
            return;
        }
        if (JmpVarType.of(target) == JmpVarType.BOOLEAN) error(h, op, "Cannot increment boolean");
    }

    private static void returnStatement(JmpElement r, AnnotationHolder h) {
        JmpElement value = r.firstExpression();
        JmpFunction f = null;
        for (PsiElement p = r.getParent(); p != null && !(p instanceof PsiFile); p = p.getParent()) {
            if (p instanceof JmpLambda) return;
            if (p instanceof JmpFunction fn) { f = fn; break; }
        }
        if (f == null || value == null) return;
        JmpTypeElement te = f.getTypeElement();
        if (te == null || te.isVoid()) {
            error(h, r.getFirstChild(), "Cannot return a value from a void function");
            return;
        }
        String msg = JmpVarType.checkAssignable(JmpVarType.declared(te), JmpVarType.of(value));
        if (msg != null) error(h, r.getFirstChild(), msg);
        else classTarget(te, value, r.getFirstChild(), h);
    }

    private static void loopOnly(JmpElement x, AnnotationHolder h) {
        if (!JmpPsiUtil.inLoop(x)) error(h, x.getFirstChild(), "'" + x.getFirstChild().getText() + "' outside of a loop");
    }

    private static void thisOrSuper(JmpElement x, AnnotationHolder h) {
        String word = x.getText();
        if (JmpPsiUtil.enclosingClass(x) == null) error(h, x, "'" + word + "' is only valid inside a class");
        else if (!JmpPsiUtil.hasThis(x)) error(h, x, "'" + word + "' is not available in a static method or static initializer");
        else if (x.is(SUPER_EXPRESSION)) {
            JmpClass c = JmpPsiUtil.enclosingClass(x);
            if (c != null && c.getExtendsReference() == null) error(h, x, "Class " + c.getName() + " has no superclass");
        }
    }

    private static void classDeclaration(JmpClass c, AnnotationHolder h) {
        PsiElement name = c.getNameIdentifier();
        if (name == null) return;
        // A extends A, A extends B extends A (Parser.classDecl)
        JmpReferenceExpression ext = c.getExtendsReference();
        if (ext != null && ext.resolve() == c) {
            error(h, ext, "Cyclic inheritance: class " + c.getName() + " cannot extend " + ext.getReferenceName() + " (itself)");
            return;
        }
        Set<JmpClass> seen = new HashSet<>();
        for (JmpClass k = c.getSuperClass(); k != null; k = k.getSuperClass()) {
            if (k == c || !seen.add(k)) {
                error(h, ext != null ? ext : name, "Cyclic inheritance: class " + c.getName() + " cannot extend "
                        + (ext == null ? "?" : ext.getReferenceName()) + (c.getSuperClass() == c ? " (itself)" : ", which already extends " + c.getName()));
                return;
            }
            if (seen.size() > 32) break;
        }
        if (ext != null && ext.resolve() instanceof PsiClass jc)
            error(h, ext, "Cannot extend Java class " + jc.getQualifiedName() + ": a Jumper class can only extend a Jumper class (Java interfaces are implemented by passing functions)");
        Map<String, PsiElement> fields = new HashMap<>(), methods = new HashMap<>();
        JmpFunction ctor = null;
        JmpClass parent = c.getSuperClass();
        for (JmpVariable f : c.getFields()) {
            String n = f.getName();
            if (n == null) continue;
            if (fields.putIfAbsent(n, f) != null) error(h, f.getNameIdentifier(), "Field '" + n + "' is already declared in " + c.getName());
            else if (parent != null && parent.findMember(n) instanceof JmpVariable)
                error(h, f.getNameIdentifier(), "Field '" + n + "' is already declared in a superclass of " + c.getName());
        }
        JmpClassBody body = c.getBody();
        if (body == null) return;
        for (JmpFunction m : body.functions()) {
            if (m.isConstructor()) {
                if (!c.getName().equals(m.getName())) continue;   // "needs a return type": the parser's
                if (ctor != null) error(h, m.getNameIdentifier(), "Class " + c.getName() + " already has a constructor");
                ctor = m;
                continue;
            }
            String n = m.getName();
            if (n != null && methods.putIfAbsent(n, m) != null)
                error(h, m.getNameIdentifier(), "Method '" + n + "' is already declared in " + c.getName());
        }
    }

    // ------------------------------------------------------------------ scopes

    /** Redeclarations in one block, or in one parameter list (Parser.declare). */
    private static void duplicates(JmpElement scope, AnnotationHolder h) {
        if (scope instanceof JmpParameterList pl) {
            Set<String> names = new HashSet<>();
            for (JmpParameter p : pl.parameters()) {
                if (p.getName() != null && !names.add(p.getName()))
                    error(h, p.getNameIdentifier(), "Variable '" + p.getName() + "' is already declared in this scope");
            }
            return;
        }
        Map<String, PsiElement> seen = new HashMap<>();
        for (PsiElement s = scope.getFirstChild(); s != null; s = s.getNextSibling()) declaredIn(s, seen, h);
    }

    /** The file: one statement at a time (the annotator visits each), against the ones before it. */
    private static void duplicates(JumperFile file, JmpElement statement, AnnotationHolder h) {
        Map<String, PsiElement> seen = new HashMap<>();
        for (PsiElement s = file.getFirstChild(); s != null; s = s.getNextSibling()) {
            if (s == statement) {
                Map<String, PsiElement> before = new HashMap<>(seen);
                declaredIn(s, before, h);
                return;
            }
            declaredIn(s, seen, null);
        }
    }

    private static void declaredIn(PsiElement s, Map<String, PsiElement> seen, AnnotationHolder h) {
        List<JmpNamedElement> decls = new ArrayList<>();
        if (s instanceof JmpFunction || s instanceof JmpClass) decls.add((JmpNamedElement) s);
        else if (s instanceof JmpElement e && e.is(VARIABLE_DECLARATION)) decls.addAll(e.children(JmpVariable.class));
        for (JmpNamedElement d : decls) {
            String n = d.getName();
            if (n == null) continue;
            if (seen.putIfAbsent(n, d) != null && h != null)
                error(h, d.getNameIdentifier(), "Variable '" + n + "' is already declared in this scope");
        }
    }

    // ------------------------------------------------------------------ switch

    private static void switchForm(JmpElement sw, AnnotationHolder h) {
        boolean isExpr = sw.is(SWITCH_EXPRESSION);
        int cases = 0, defaults = 0;
        for (PsiElement b = sw.getFirstChild(); b != null; b = b.getNextSibling()) {
            if (!(b instanceof JmpElement br) || !br.is(SWITCH_BRANCH)) continue;
            PsiElement label = br.child(CASE_LABEL);
            if (label == null) continue;
            if (label.getFirstChild().getNode().getElementType() == JumperTokenTypes.DEFAULT) {
                if (++defaults > 1) error(h, label, "Duplicate 'default' in switch");
            } else cases++;
        }
        if (!isExpr) return;
        PsiElement close = sw.getLastChild();
        if (defaults == 0) error(h, close, "switch expression must have a 'default'");
        else if (cases == 0) error(h, close, "switch expression must have at least one 'case'");
    }
}
