package me.padej.jumper.idea.annotator;

import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.IncorrectOperationException;
import me.padej.jumper.idea.lang.psi.JmpReferenceExpression;
import me.padej.jumper.idea.lang.psi.JumperFile;
import me.padej.jumper.idea.lang.resolve.JmpJava;
import me.padej.jumper.idea.workspace.JumperPolicy;
import me.padej.jumper.idea.workspace.JumperWorkspace;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * "Import a.b.C" for an undefined capitalized name (lsp.Features.codeActions): the public classes of that simple
 * name the project knows - the server's jars, the JDK - that the script's policy lets it see, java.* first.
 */
public final class JumperImportClassFix implements IntentionAction, HighPriorityAction {
    private final JmpReferenceExpression ref;

    public JumperImportClassFix(JmpReferenceExpression ref) {
        this.ref = ref;
    }

    @Override
    public @NotNull String getText() {
        List<String> cs = candidates();
        return cs.size() == 1 ? "Import " + cs.get(0) : "Import class";
    }

    @Override
    public @NotNull String getFamilyName() {
        return "Import class";
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        return ref.isValid() && ref.getReferenceName() != null && !candidates().isEmpty();
    }

    private List<String> candidates() {
        String name = ref.getReferenceName();
        if (name == null) return List.of();
        Project project = ref.getProject();
        Set<String> out = new LinkedHashSet<>();
        JumperPolicy policy = ref.getContainingFile() instanceof JumperFile f ? JumperPolicy.of(JumperWorkspace.contextFor(f)) : null;
        for (PsiClass c : PsiShortNamesCache.getInstance(project).getClassesByName(name, JmpJava.scope(project))) {
            String fq = c.getQualifiedName();
            if (fq == null || c.getContainingClass() != null || !JmpJava.isPublic(c)) continue;
            if (policy != null && !policy.visible(c)) continue;
            out.add(fq);
            if (out.size() >= 8) break;
        }
        List<String> list = new ArrayList<>(out);
        list.sort(Comparator.comparing((String n) -> !n.startsWith("java.")).thenComparing(n -> n));
        return list;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        List<String> cs = candidates();
        if (cs.isEmpty()) return;
        if (cs.size() == 1) { addImport(project, file, cs.get(0)); return; }   // in the write action (startInWriteAction)
        if (editor == null) {
            WriteCommandAction.runWriteCommandAction(project, "Import " + cs.get(0), null, () -> addImport(project, file, cs.get(0)), file);
            return;
        }
        JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<>("Class to Import", cs) {
            @Override
            public PopupStep<?> onChosen(String selected, boolean finalChoice) {
                WriteCommandAction.runWriteCommandAction(project, "Import " + selected, null, () -> addImport(project, file, selected), file);
                return FINAL_CHOICE;
            }
        }).showInBestPositionFor(editor);
    }

    /** After the last import at the top, else before the first line of code (Features.importLine). */
    static void addImport(Project project, PsiFile file, String fq) {
        Document doc = PsiDocumentManager.getInstance(project).getDocument(file);
        if (doc == null) return;
        String[] lines = doc.getText().split("\n", -1);
        int after = -1, firstCode = -1;
        boolean inComment = false;
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i].strip();
            if (inComment) { if (l.contains("*/")) inComment = false; continue; }
            if (l.startsWith("/*")) { if (!l.contains("*/")) inComment = true; continue; }
            if (l.isEmpty() || l.startsWith("//")) continue;
            if (l.startsWith("import ")) { after = i + 1; continue; }
            firstCode = i;
            break;
        }
        int line = after >= 0 ? after : Math.max(firstCode, 0);
        int offset = line >= doc.getLineCount() ? doc.getTextLength() : doc.getLineStartOffset(line);
        doc.insertString(offset, "import " + fq + ";\n" + (after < 0 && firstCode >= 0 ? "\n" : ""));
        PsiDocumentManager.getInstance(project).commitDocument(doc);
    }

    @Override
    public boolean startInWriteAction() {
        return candidates().size() == 1;
    }
}
