package me.padej.jumper.idea.editor;

import com.intellij.lang.parameterInfo.*;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import me.padej.jumper.idea.lang.lexer.JumperTokenTypes;
import me.padej.jumper.idea.lang.psi.*;
import me.padej.jumper.idea.lang.resolve.JmpBuiltins;
import me.padej.jumper.idea.lang.resolve.JmpJava;
import me.padej.jumper.idea.lang.resolve.JmpSynthetic;
import me.padej.jumper.idea.lang.resolve.JmpType;
import me.padej.jumper.idea.lang.resolve.JmpTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Parameter info (lsp.Features.signatureHelp): in `f(a, |)` the signatures of what is called - a Java method
 * (every overload), a Java constructor (`new X(`), a function or method of the script, a script class's
 * constructor, a built-in - and the argument the caret is in.
 */
public final class JumperParameterInfoHandler implements ParameterInfoHandler<JmpArgumentList, JumperParameterInfoHandler.Signature> {
    /** A signature as text and where each parameter is in it. */
    public record Signature(String text, List<int[]> params) {
        static Signature of(String head, List<String> ps) {
            StringBuilder sb = new StringBuilder();
            List<int[]> ranges = new ArrayList<>();
            for (int i = 0; i < ps.size(); i++) {
                if (i > 0) sb.append(", ");
                ranges.add(new int[] {sb.length(), sb.length() + ps.get(i).length()});
                sb.append(ps.get(i));
            }
            return new Signature(sb.length() == 0 ? "<no parameters>" : sb.toString(), ranges);
        }
    }

    @Override
    public @Nullable JmpArgumentList findElementForParameterInfo(@NotNull CreateParameterInfoContext context) {
        JmpArgumentList args = argumentsAt(context.getFile(), context.getOffset());
        if (args == null) return null;
        List<Signature> sigs = signatures(args);
        if (sigs.isEmpty()) return null;
        context.setItemsToShow(sigs.toArray());
        return args;
    }

    @Override
    public void showParameterInfo(@NotNull JmpArgumentList element, @NotNull CreateParameterInfoContext context) {
        context.showHint(element, element.getTextRange().getStartOffset() + 1, this);
    }

    @Override
    public @Nullable JmpArgumentList findElementForUpdatingParameterInfo(@NotNull UpdateParameterInfoContext context) {
        return argumentsAt(context.getFile(), context.getOffset());
    }

    @Override
    public void updateParameterInfo(@NotNull JmpArgumentList args, @NotNull UpdateParameterInfoContext context) {
        int commas = 0, offset = context.getOffset();
        for (PsiElement c = args.getFirstChild(); c != null && c.getTextRange().getEndOffset() <= offset; c = c.getNextSibling())
            if (c.getNode().getElementType() == JumperTokenTypes.COMMA) commas++;
        context.setCurrentParameter(commas);
    }

    @Override
    public void updateUI(Signature s, @NotNull ParameterInfoUIContext context) {
        int current = context.getCurrentParameterIndex();
        int start = -1, end = -1;
        if (current >= 0 && current < s.params().size()) {
            start = s.params().get(current)[0];
            end = s.params().get(current)[1];
        } else if (!s.params().isEmpty() && s.text().contains("...") && current >= s.params().size()) {
            int[] last = s.params().get(s.params().size() - 1);
            start = last[0];
            end = last[1];
        }
        context.setupUIComponentPresentation(s.text(), start, end, false, false, false, context.getDefaultParameterColor());
    }

    private static @Nullable JmpArgumentList argumentsAt(PsiFile file, int offset) {
        PsiElement at = file.findElementAt(offset);
        if (at == null && offset > 0) at = file.findElementAt(offset - 1);
        JmpArgumentList args = PsiTreeUtil.getParentOfType(at, JmpArgumentList.class);
        if (args == null && offset > 0) args = PsiTreeUtil.getParentOfType(file.findElementAt(offset - 1), JmpArgumentList.class);
        return args;
    }

    private static List<Signature> signatures(JmpArgumentList args) {
        List<Signature> out = new ArrayList<>();
        PsiElement owner = args.getParent();
        if (owner instanceof JmpNewExpression n) {
            PsiElement t = n.getClassReference() == null ? null : n.getClassReference().resolve();
            if (t instanceof PsiClass c) {
                for (PsiMethod k : c.getConstructors()) if (JmpJava.isPublic(k)) out.add(java(k));
                if (c.getConstructors().length == 0) out.add(Signature.of("", List.of()));
            } else if (t instanceof JmpClass c) {
                JmpFunction k = c.getConstructor();
                out.add(k == null ? Signature.of("", List.of()) : script(k));
            }
        } else if (owner instanceof JmpCallExpression call && call.getCallee() instanceof JmpReferenceExpression callee) {
            for (ResolveResult r : callee.multiResolve(false)) {
                PsiElement t = r.getElement();
                if (t instanceof PsiMethod m) out.add(java(m));
                else if (t instanceof JmpFunction f) out.add(script(f));
                else if (t instanceof JmpSynthetic s && s.kind() == JmpSynthetic.Kind.BUILTIN)
                    for (String sig : JmpBuiltins.SIGNATURES.getOrDefault(s.getName(), List.of())) out.add(text(sig));
            }
            if (out.isEmpty() && callee.isQualified()) {   // a method on a Java receiver the resolver left without a target
                JmpType t = JmpTypes.typeOf(callee.getQualifier());
                if (t != null && t.java() != null && callee.getReferenceName() != null)
                    for (PsiMethod m : JmpJava.methodsNamed(t.java(), t.statics(), callee.getReferenceName())) out.add(java(m));
            }
        }
        out.sort(Comparator.comparingInt(s -> s.params().size()));
        return out;
    }

    private static Signature java(PsiMethod m) {
        List<String> ps = new ArrayList<>();
        PsiParameter[] params = m.getParameterList().getParameters();
        for (PsiParameter p : params) ps.add(p.getType().getPresentableText() + " " + p.getName());
        return Signature.of(m.getName(), ps);
    }

    private static Signature script(JmpFunction f) {
        List<String> ps = new ArrayList<>();
        for (JmpParameter p : f.getParameters()) ps.add(p.getText().replaceAll("\\s+", " "));
        return Signature.of(f.getName(), ps);
    }

    /** `int add(int a, int b)`: what is between the parentheses. */
    private static Signature text(String sig) {
        String inner = sig.substring(sig.indexOf('(') + 1, sig.lastIndexOf(')')).strip();
        List<String> ps = new ArrayList<>();
        if (!inner.isEmpty()) for (String p : inner.split(",")) ps.add(p.strip());
        return Signature.of(sig.substring(0, sig.indexOf('(')), ps);
    }
}
