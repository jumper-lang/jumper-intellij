package me.padej.jumper.idea.editor;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import me.padej.jumper.idea.lang.psi.*;
import me.padej.jumper.idea.lang.resolve.*;
import me.padej.jumper.idea.workspace.JumperContext;
import me.padej.jumper.idea.workspace.JumperData;
import me.padej.jumper.idea.workspace.JumperHooks;
import me.padej.jumper.idea.workspace.JumperWorkspace;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Hover and quick documentation (lsp.Features.hover): a declaration of the file shows its line - with the type
 * a `dyn` variable is known to have, and the hook a function implements; a built-in its signatures; a host
 * global its type and who defines it; a value of a config (.jmc) what it is. Java classes and members are the
 * Java documentation's (their Javadoc, from sources or decompiled).
 */
public final class JumperDocumentationProvider extends AbstractDocumentationProvider {
    @Override
    public @Nullable String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
        if (element instanceof JmpNamedElement n) return StringUtil.escapeXmlEntities(declarationLine(n));
        if (element instanceof JmpSynthetic s) return StringUtil.escapeXmlEntities(syntheticHead(s));
        return null;
    }

    @Override
    public @Nullable String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
        if (element instanceof JmpSynthetic s) return synthetic(s);
        if (!(element instanceof JmpNamedElement n)) return null;
        StringBuilder sb = new StringBuilder(code(declarationLine(n)));
        if (n instanceof JmpVariable v) {
            JmpTypeElement te = v.getTypeElement();
            if (te == null || te.isDyn()) {
                JmpType t = JmpTypes.variableType(v);
                if (t != null) sb.append(para("Type: <code>" + esc(t.qualifiedName()) + "</code>"));
            }
            String value = configValue(v);
            if (value != null) sb.append(para("Value: <code>" + esc(value) + "</code>"));
        }
        if (n instanceof JmpParameter p && (p.getTypeElement() == null || p.getTypeElement().isDyn())) {
            JmpType t = JmpTypes.parameterType(p);
            if (t != null) sb.append(para("Type: <code>" + esc(t.qualifiedName()) + "</code> (the host passes it)"));
        }
        if (n instanceof JmpFunction f && JmpPsiUtil.isTopLevel(f) && f.getContainingFile() instanceof JumperFile file && f.getName() != null) {
            JumperContext ctx = JumperWorkspace.contextFor(file);
            JumperHooks.Hook h = JumperHooks.find(ctx, f.getName(), f);
            if (h != null) {
                PsiMethod m = h.method(f.getParameters().size());
                sb.append(para("Implements <code>" + esc(h.owner().getName() + (m == null ? "" : "." + m.getName())) + "</code>"
                        + (m == null ? "" : ":")));
                if (m != null) sb.append(code(JumperHooks.signature(m)));
                sb.append(para(esc(ctx.hostName()) + " calls it; Go to Declaration on the name opens the API method."));
            }
        }
        return sb.toString();
    }

    private static String synthetic(JmpSynthetic s) {
        if (s.kind() == JmpSynthetic.Kind.BUILTIN) {
            StringBuilder sb = new StringBuilder();
            for (String sig : JmpBuiltins.SIGNATURES.getOrDefault(s.getName(), List.of())) sb.append(code(sig));
            String doc = JmpBuiltins.DOCS.get(s.getName());
            if (doc != null) sb.append(para(esc(doc)));
            return sb.append(para("Built-in function of Jumper.")).toString();
        }
        JumperContext ctx = s.getContainingFile() instanceof JumperFile f ? JumperWorkspace.contextFor(f) : null;
        return code(syntheticHead(s)) + para("Defined for scripts by " + esc(ctx == null ? "the host" : ctx.hostName()) + ".");
    }

    private static String syntheticHead(JmpSynthetic s) {
        return s.kind() == JmpSynthetic.Kind.BUILTIN ? JmpBuiltins.SIGNATURES.get(s.getName()).get(0) : s.detail() + " " + s.getName();
    }

    /** The line the name is declared on (Features.hover: the declaration's line), trimmed. */
    private static String declarationLine(JmpNamedElement n) {
        PsiFile file = n.getContainingFile();
        Document d = PsiDocumentManager.getInstance(n.getProject()).getDocument(file);
        String text = file.getText();
        int at = n.getTextOffset();
        if (d != null && at <= d.getTextLength()) {
            int line = d.getLineNumber(at);
            return text.substring(d.getLineStartOffset(line), d.getLineEndOffset(line)).strip();
        }
        int s = text.lastIndexOf('\n', Math.max(0, at - 1)) + 1, e = text.indexOf('\n', at);
        return text.substring(s, e < 0 ? text.length() : e).strip();
    }

    /** In a .jmc: the value of a top-level variable, when it is a literal (a config only computes, the language shows it). */
    private static @Nullable String configValue(JmpVariable v) {
        if (!(v.getContainingFile() instanceof JumperFile f) || !f.isConfig() || !JmpPsiUtil.isTopLevel(v)) return null;
        Map<String, Object> values = JumperData.values(f.getText(), new ArrayList<>());
        if (!values.containsKey(v.getName())) return null;
        Object value = values.get(v.getName());
        String shown = value instanceof String str ? "\"" + str + "\"" : String.valueOf(value);
        return shown.length() > 200 ? shown.substring(0, 200) + "..." : shown;
    }

    private static String code(String s) {
        return "<pre><code>" + esc(s) + "</code></pre>";
    }

    private static String para(String html) {
        return "<p>" + html + "</p>";
    }

    private static String esc(String s) {
        return StringUtil.escapeXmlEntities(s);
    }
}
