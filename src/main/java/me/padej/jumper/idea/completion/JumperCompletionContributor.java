package me.padej.jumper.idea.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.completion.util.ParenthesesInsertHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import me.padej.jumper.idea.JumperLanguage;
import me.padej.jumper.idea.lang.psi.*;
import me.padej.jumper.idea.lang.resolve.*;
import me.padej.jumper.idea.workspace.JumperContext;
import me.padej.jumper.idea.workspace.JumperHooks;
import me.padej.jumper.idea.workspace.JumperPolicy;
import me.padej.jumper.idea.workspace.JumperWorkspace;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Completion (lsp.Features.complete):
 * <ul>
 *   <li>after a dot - the members of what is before it: a Java object's methods and fields (only those the
 *       access policy leaves open, without wait/notify), a class's statics, a script class's members, a package's
 *       classes and subpackages;</li>
 *   <li>in `import a.b.` - packages and classes, what the policy closes not offered;</li>
 *   <li>naming a function at the top level - the functions the host calls that the script does not declare yet,
 *       with their parameters (`onJoin(dyn player)`);</li>
 *   <li>elsewhere - the names visible there, the host's globals, the built-ins, imported classes, the keywords
 *       (in a config only those a config may use);</li>
 *   <li>in a policy (.jma), inside `Policy.allowPackage("…")` and the like - packages and classes.</li>
 * </ul>
 */
public final class JumperCompletionContributor extends CompletionContributor {
    static final List<String> KEYWORDS = List.of("dyn", "int", "long", "double", "boolean", "String", "void", "class", "extends",
            "static", "new", "return", "if", "else", "while", "for", "do", "break", "continue", "switch", "case", "default", "true",
            "false", "null", "import", "this", "super", "try", "catch", "finally", "throw");
    static final Set<String> CONFIG_KEYWORDS = Set.of("dyn", "int", "long", "double", "boolean", "String", "if", "else",
            "switch", "case", "default", "true", "false", "null", "return");
    /** Object's thread-monitor methods: callable, never what a script wants. */
    static final Set<String> OBJECT_NOISE = Set.of("wait", "notify", "notifyAll");

    public JumperCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement().withLanguage(JumperLanguage.INSTANCE), new CompletionProvider<>() {
            @Override
            protected void addCompletions(@NotNull CompletionParameters parameters, @NotNull ProcessingContext context,
                                          @NotNull CompletionResultSet result) {
                complete(parameters, result);
            }
        });
    }

    private static void complete(CompletionParameters parameters, CompletionResultSet result) {
        PsiElement pos = parameters.getPosition();
        PsiElement parent = pos.getParent();
        if (!(pos.getContainingFile() instanceof JumperFile file)) return;
        JumperFile original = (JumperFile) file.getOriginalFile();
        JumperContext ctx = JumperWorkspace.contextFor(original);
        JumperPolicy policy = file.kind() == me.padej.jumper.idea.JumperFileType.Kind.SCRIPT ? JumperPolicy.of(ctx) : null;

        if (parent instanceof JmpLiteral lit && file.isPolicy()) { policyString(lit, pos, parameters, result); return; }
        if (parent instanceof JmpFunction f && f.getNameIdentifier() == pos && JmpPsiUtil.isTopLevel(f)) {
            hooks(f, ctx, original, result);
            return;
        }
        if (!(parent instanceof JmpReferenceExpression ref)) return;
        if (ref.inImport()) { importItems(ref, policy, result); return; }
        if (ref.isQualified()) { members(ref, policy, result); return; }
        names(ref, file, policy, result);
    }

    // ------------------------------------------------------------------ members

    private static void members(JmpReferenceExpression ref, JumperPolicy policy, CompletionResultSet result) {
        JmpElement q = ref.getQualifier();
        if (q instanceof JmpReferenceExpression qr && qr.resolve() instanceof PsiPackage pkg) {
            packageItems(pkg, ref, policy, result);
            return;
        }
        if (q != null && (q.is(me.padej.jumper.idea.lang.parser.JumperElementTypes.THIS_EXPRESSION)
                || q.is(me.padej.jumper.idea.lang.parser.JumperElementTypes.SUPER_EXPRESSION))) {
            JmpClass c = JmpPsiUtil.enclosingClass(ref);
            if (q.is(me.padej.jumper.idea.lang.parser.JumperElementTypes.SUPER_EXPRESSION) && c != null) c = c.getSuperClass();
            if (c != null) for (JmpNamedElement m : c.allMembers()) result.addElement(scriptItem(m));
            return;
        }
        JmpType t = JmpTypes.typeOf(q);
        if (t == null) return;
        if (t.script() != null) {
            for (JmpNamedElement m : t.script().allMembers()) {
                if (t.statics() != isStatic(m)) continue;
                result.addElement(scriptItem(m));
            }
            return;
        }
        PsiClass cls = t.java();
        if (cls == null) return;   // an array: no members
        Set<String> seen = new HashSet<>();
        for (PsiMethod m : JmpJava.methods(cls, t.statics())) {
            if (JmpJava.isObjectMethod(m) && OBJECT_NOISE.contains(m.getName())) continue;
            if (!t.statics() && JmpJava.isStatic(m)) continue;   // on an instance: its methods
            if (policy != null && !policy.allowed(m, cls)) continue;
            if (!seen.add(JmpJava.signature(m))) continue;
            result.addElement(PrioritizedLookupElement.withPriority(methodItem(m), JmpJava.isObjectMethod(m) ? -1 : 1));
        }
        for (PsiField f : JmpJava.fields(cls, t.statics())) {
            if (policy != null && !policy.allowed(f, cls)) continue;
            result.addElement(LookupElementBuilder.create(f, f.getName()).withIcon(f.getIcon(0))
                    .withTypeText(f.getType().getPresentableText()));
        }
        if (t.statics()) {
            for (PsiClass inner : cls.getInnerClasses()) {
                if (!JmpJava.isPublic(inner) || policy != null && !policy.visible(inner)) continue;
                result.addElement(LookupElementBuilder.create(inner, Objects.requireNonNull(inner.getName())).withIcon(inner.getIcon(0)));
            }
        }
    }

    private static boolean isStatic(JmpNamedElement m) {
        return m instanceof JmpFunction f ? f.isStatic() : m instanceof JmpVariable v && v.isStatic();
    }

    static LookupElement methodItem(PsiMethod m) {
        int n = m.getParameterList().getParametersCount();
        StringBuilder tail = new StringBuilder("(");
        PsiParameter[] ps = m.getParameterList().getParameters();
        for (int i = 0; i < ps.length; i++) {
            if (i > 0) tail.append(", ");
            tail.append(ps[i].getType().getPresentableText()).append(' ').append(ps[i].getName());
        }
        tail.append(')');
        return LookupElementBuilder.create(m, m.getName()).withIcon(m.getIcon(0))
                .withTailText(tail.toString(), true)
                .withTypeText(m.getReturnType() == null ? "" : m.getReturnType().getPresentableText())
                .withInsertHandler(n == 0 ? ParenthesesInsertHandler.NO_PARAMETERS : ParenthesesInsertHandler.WITH_PARAMETERS);
    }

    static LookupElement scriptItem(JmpNamedElement e) {
        LookupElementBuilder b = LookupElementBuilder.create(e, Objects.requireNonNull(e.getName())).withIcon(e.kindIcon());
        String d = e.detail();
        if (e instanceof JmpFunction f) {
            b = b.withTailText(f.getParameterList() == null ? "()" : f.getParameterList().getText().replaceAll("\\s+", " "), true)
                    .withInsertHandler(f.getParameters().isEmpty() ? ParenthesesInsertHandler.NO_PARAMETERS : ParenthesesInsertHandler.WITH_PARAMETERS);
            if (f.getTypeElement() != null) b = b.withTypeText(f.getTypeElement().getText());
        } else if (d != null) b = b.withTypeText(d);
        return b;
    }

    // ------------------------------------------------------------------ names

    private static void names(JmpReferenceExpression ref, JumperFile file, JumperPolicy policy, CompletionResultSet result) {
        Set<String> seen = new HashSet<>();
        JmpScopes.processDeclarations(ref, (name, target) -> {
            if (!seen.add(name)) return true;
            if (target instanceof JmpNamedElement n) result.addElement(scriptItem(n));
            else if (target instanceof JmpSynthetic s) {
                LookupElementBuilder b = LookupElementBuilder.create(s, name).withIcon(s.getIcon(false));
                if (s.kind() == JmpSynthetic.Kind.BUILTIN) {
                    String sig = JmpBuiltins.SIGNATURES.get(name).get(0);
                    b = b.withTailText(sig.substring(sig.indexOf('(')), true).withTypeText("built-in")
                            .withInsertHandler(sig.contains("()") ? ParenthesesInsertHandler.NO_PARAMETERS : ParenthesesInsertHandler.WITH_PARAMETERS);
                } else b = b.withTypeText(s.detail()).withBoldness(true);
                result.addElement(b);
            } else if (target instanceof PsiClass c) {
                result.addElement(LookupElementBuilder.create(c, name).withIcon(c.getIcon(0)).withTypeText(c.getQualifiedName()));
            }
            return true;
        });
        for (JmpImportStatement imp : file.javaImports()) {
            String n = imp.getSimpleName();
            if (n != null && seen.add(n)) result.addElement(LookupElementBuilder.create(n).withIcon(AllIcons.Nodes.Class).withTypeText(imp.getQualifiedName()));
        }
        boolean config = file.isConfig();
        for (String k : KEYWORDS) {
            if (config && !CONFIG_KEYWORDS.contains(k)) continue;
            if (seen.add(k)) result.addElement(LookupElementBuilder.create(k).bold());
        }
    }

    // ------------------------------------------------------------------ hooks

    /** `void on|` at the top level: the hooks not declared yet, with their parameters (Features.hookItems). */
    private static void hooks(JmpFunction f, JumperContext ctx, JumperFile file, CompletionResultSet result) {
        Set<String> declared = new HashSet<>();
        for (JmpNamedElement d : file.topLevelDeclarations()) if (d.getName() != null) declared.add(d.getName());
        for (String name : JumperHooks.names(ctx, file)) {
            if (declared.contains(name)) continue;
            JumperHooks.Hook h = JumperHooks.find(ctx, name, file);
            PsiMethod m = h == null ? null : h.method(-1);
            StringBuilder params = new StringBuilder();
            if (m != null) {
                PsiParameter[] ps = m.getParameterList().getParameters();
                for (int i = 0; i < ps.length; i++) params.append(i > 0 ? ", " : "").append("dyn ").append(ps[i].getName());
            }
            String insert = name + "(" + params + ")";
            result.addElement(LookupElementBuilder.create(insert).withPresentableText(name).withTailText("(" + params + ")", true)
                    .withIcon(AllIcons.Gutter.ImplementingMethod)
                    .withTypeText(h == null ? "hook" : h.owner().getName() + (m == null ? "" : ": " + JumperHooks.signature(m))));
        }
    }

    // ------------------------------------------------------------------ imports and packages

    private static void importItems(JmpReferenceExpression ref, JumperPolicy policy, CompletionResultSet result) {
        JmpElement q = ref.getQualifier();
        JavaPsiFacade facade = JmpJava.facade(ref.getProject());
        if (facade == null) return;
        PsiPackage pkg = q == null ? facade.findPackage("")
                : q instanceof JmpReferenceExpression qr && qr.resolve() instanceof PsiPackage p ? p : null;
        if (pkg != null) packageItems(pkg, ref, policy, result);
    }

    private static void packageItems(PsiPackage pkg, PsiElement context, JumperPolicy policy, CompletionResultSet result) {
        var scope = JmpJava.scope(context.getProject());
        for (PsiPackage sub : pkg.getSubPackages(scope)) {
            if (sub.getName() != null) result.addElement(LookupElementBuilder.create(sub, sub.getName()).withIcon(AllIcons.Nodes.Package));
        }
        for (PsiClass c : pkg.getClasses(scope)) {
            if (c.getName() == null || !JmpJava.isPublic(c)) continue;
            if (policy != null && !policy.visible(c)) continue;   // what the policy closes is not offered
            result.addElement(LookupElementBuilder.create(c, c.getName()).withIcon(c.getIcon(0)).withTypeText(c.getQualifiedName()));
        }
    }

    /** `Policy.allowPackage("java.u|")`, `allowClass("a.b.W|")`: packages and classes, by what is typed in the string. */
    private static void policyString(JmpLiteral lit, PsiElement pos, CompletionParameters parameters, CompletionResultSet result) {
        if (!(lit.getParent() instanceof JmpArgumentList args) || !(args.getParent() instanceof JmpCallExpression call)) return;
        if (!(call.getCallee() instanceof JmpReferenceExpression callee) || callee.getReferenceName() == null) return;
        String rule = callee.getReferenceName();
        if (!rule.startsWith("allow") && !rule.startsWith("deny")) return;
        int caret = parameters.getOffset() - lit.getTextRange().getStartOffset();
        String typed = lit.getText().substring(1, Math.max(1, Math.min(caret, lit.getTextLength())));
        int dot = typed.lastIndexOf('.');
        JavaPsiFacade facade = JmpJava.facade(lit.getProject());
        PsiPackage pkg = facade == null ? null : facade.findPackage(dot < 0 ? "" : typed.substring(0, dot));
        if (pkg == null) return;
        CompletionResultSet r = result.withPrefixMatcher(typed.substring(dot + 1));
        var scope = JmpJava.scope(lit.getProject());
        for (PsiPackage sub : pkg.getSubPackages(scope))
            if (sub.getName() != null) r.addElement(LookupElementBuilder.create(sub.getName()).withIcon(AllIcons.Nodes.Package));
        if (rule.endsWith("Package")) return;
        for (PsiClass c : pkg.getClasses(scope))
            if (c.getName() != null && JmpJava.isPublic(c)) r.addElement(LookupElementBuilder.create(c.getName()).withIcon(c.getIcon(0)));
    }
}
