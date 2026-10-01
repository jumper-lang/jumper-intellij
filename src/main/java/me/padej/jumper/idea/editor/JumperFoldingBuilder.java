package me.padej.jumper.idea.editor;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import me.padej.jumper.idea.lang.lexer.JumperTokenTypes;
import me.padej.jumper.idea.lang.psi.JmpImportStatement;
import me.padej.jumper.idea.lang.psi.JmpModuleImport;
import me.padej.jumper.idea.lang.psi.JmpPsiUtil;
import me.padej.jumper.idea.lang.psi.JumperFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static me.padej.jumper.idea.lang.parser.JumperElementTypes.*;

/** Folding (lsp.Features.foldingRanges): blocks, class bodies, tables, arrays and argument lists over lines, block comments, the imports. */
public final class JumperFoldingBuilder extends FoldingBuilderEx implements DumbAware {
    @Override
    public FoldingDescriptor @NotNull [] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document, boolean quick) {
        List<FoldingDescriptor> out = new ArrayList<>();
        PsiTreeUtil.processElements(root, e -> {
            IElementType t = JmpPsiUtil.typeOf(e);
            if (t == BLOCK || t == CLASS_BODY || t == TABLE_LITERAL || t == ARRAY_LITERAL || t == ARGUMENT_LIST || t == SWITCH_STATEMENT
                    || t == SWITCH_EXPRESSION) {
                TextRange r = bracketRange(e);
                if (r != null && multiline(document, r)) out.add(new FoldingDescriptor(e.getNode(), r));
            } else if (e instanceof PsiComment c && t == JumperTokenTypes.BLOCK_COMMENT && multiline(document, c.getTextRange())) {
                out.add(new FoldingDescriptor(c.getNode(), c.getTextRange()));
            }
            return true;
        });
        if (root instanceof JumperFile f) imports(f, document, out);
        return out.toArray(FoldingDescriptor.EMPTY_ARRAY);
    }

    /** From the node's first `{` `(` `[` to its end. */
    private static TextRange bracketRange(PsiElement e) {
        for (PsiElement c = e.getFirstChild(); c != null; c = c.getNextSibling()) {
            IElementType t = JmpPsiUtil.typeOf(c);
            if (t == JumperTokenTypes.LBRACE || t == JumperTokenTypes.LPAREN || t == JumperTokenTypes.LBRACKET) {
                int start = c.getTextRange().getStartOffset(), end = e.getTextRange().getEndOffset();
                return end - start > 2 ? new TextRange(start, end) : null;
            }
        }
        return null;
    }

    private static boolean multiline(Document d, TextRange r) {
        return d.getLineNumber(r.getStartOffset()) < d.getLineNumber(r.getEndOffset());
    }

    /** The imports at the top: from the first to the last, when they span lines. */
    private static void imports(JumperFile f, Document d, List<FoldingDescriptor> out) {
        PsiElement first = null, last = null;
        for (PsiElement c = f.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c instanceof JmpImportStatement || c instanceof JmpModuleImport) {
                if (first == null) first = c;
                last = c;
            } else if (!(c instanceof PsiWhiteSpace) && !(c instanceof PsiComment) && first != null) break;
        }
        if (first == null || first == last) return;
        // after the first `import` word
        int start = first.getTextRange().getStartOffset() + "import ".length();
        TextRange r = new TextRange(Math.min(start, last.getTextRange().getEndOffset()), last.getTextRange().getEndOffset());
        if (multiline(d, r)) out.add(new FoldingDescriptor(first.getNode(), r, null, "..."));
    }

    @Override
    public String getPlaceholderText(@NotNull ASTNode node) {
        IElementType t = node.getElementType();
        if (t == JumperTokenTypes.BLOCK_COMMENT) return "/*...*/";
        if (t == ARGUMENT_LIST) return "(...)";
        if (t == ARRAY_LITERAL && node.getText().startsWith("[")) return "[...]";
        if (t == IMPORT_STATEMENT || t == MODULE_IMPORT) return "...";
        return "{...}";
    }

    @Override
    public boolean isCollapsedByDefault(@NotNull ASTNode node) {
        IElementType t = node.getElementType();
        return t == IMPORT_STATEMENT || t == MODULE_IMPORT;
    }
}
