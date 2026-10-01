package me.padej.jumper.idea.editor;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.common.AbstractBlock;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import me.padej.jumper.idea.JumperLanguage;
import me.padej.jumper.idea.lang.lexer.JumperTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static me.padej.jumper.idea.lang.lexer.JumperTokenTypes.*;
import static me.padej.jumper.idea.lang.parser.JumperElementTypes.*;

/**
 * Reformat Code: the indentation of lsp.Features.formatted - a level per open brace, the closing brace a level
 * less - plus Java-like spaces around operators, after commas and keywords. Line breaks are the author's.
 */
public final class JumperFormattingModelBuilder implements FormattingModelBuilder {
    @Override
    public @NotNull FormattingModel createModel(@NotNull FormattingContext ctx) {
        CodeStyleSettings settings = ctx.getCodeStyleSettings();
        PsiFile file = ctx.getContainingFile();
        return FormattingModelProvider.createFormattingModelForPsiFile(file,
                new JumperBlock(ctx.getNode(), null, Indent.getNoneIndent(), spacing(settings)), settings);
    }

    private static final TokenSet BINARY_OPS = TokenSet.create(PLUS, MINUS, STAR, SLASH, PERCENT, EQEQ, NE, LT, LE, GT, GE,
            ANDAND, OROR, AMP, PIPE, CARET, SHL, SHR, USHR);
    /** Blocks whose children are indented a level: statements, members, table entries, array items, switch branches. */
    private static final TokenSet INDENTING = TokenSet.create(BLOCK, CLASS_BODY, TABLE_LITERAL, ARRAY_LITERAL, SWITCH_STATEMENT,
            SWITCH_EXPRESSION);
    private static final TokenSet CONTINUING = TokenSet.create(ARGUMENT_LIST, PARAMETER_LIST, NEW_ARRAY_EXPRESSION);
    private static final TokenSet CLOSERS = TokenSet.create(RBRACE, RBRACKET, RPAREN);
    private static final TokenSet OPENERS = TokenSet.create(LBRACE, LBRACKET, LPAREN);
    private static final TokenSet BODIES_OF = TokenSet.create(IF_STATEMENT, WHILE_STATEMENT, DO_WHILE_STATEMENT, FOR_STATEMENT, FOREACH_STATEMENT);

    private static SpacingBuilder spacing(CodeStyleSettings settings) {
        return new SpacingBuilder(settings, JumperLanguage.INSTANCE)
                .before(COMMA).spaceIf(false)
                .after(COMMA).spaceIf(true)
                .before(SEMI).spaceIf(false)
                .around(DOT).spaceIf(false)
                .around(ARROW).spaceIf(true)
                .around(JumperTokenTypes.ASSIGN_OPS).spaceIf(true)
                .aroundInside(BINARY_OPS, BINARY_EXPRESSION).spaceIf(true)
                .aroundInside(TokenSet.create(QUESTION, COLON), CONDITIONAL_EXPRESSION).spaceIf(true)
                .beforeInside(COLON, TABLE_ENTRY).spaceIf(false)
                .afterInside(COLON, TABLE_ENTRY).spaceIf(true)
                .beforeInside(COLON, FOREACH_STATEMENT).spaceIf(true)
                .afterInside(COLON, FOREACH_STATEMENT).spaceIf(true)
                .afterInside(TokenSet.create(NOT, MINUS, PLUS, PLUSPLUS, MINUSMINUS), PREFIX_EXPRESSION).spaceIf(false)
                .beforeInside(TokenSet.create(PLUSPLUS, MINUSMINUS), POSTFIX_EXPRESSION).spaceIf(false)
                .after(TokenSet.create(IF, WHILE, FOR, SWITCH, CATCH)).spaceIf(true)
                .before(ARGUMENT_LIST).spaceIf(false)
                .before(PARAMETER_LIST).spaceIf(false)
                .after(LPAREN).spaceIf(false)
                .before(RPAREN).spaceIf(false)
                .before(BLOCK).spaceIf(true)
                .before(CLASS_BODY).spaceIf(true)
                .before(TokenSet.create(ELSE, CATCH_SECTION, FINALLY_SECTION)).spaceIf(true);
    }

    private static final class JumperBlock extends AbstractBlock {
        private final Indent indent;
        private final SpacingBuilder spacing;

        JumperBlock(ASTNode node, @Nullable Wrap wrap, Indent indent, SpacingBuilder spacing) {
            super(node, wrap, null);
            this.indent = indent;
            this.spacing = spacing;
        }

        @Override
        protected List<Block> buildChildren() {
            List<Block> out = new ArrayList<>();
            for (ASTNode c = myNode.getFirstChildNode(); c != null; c = c.getTreeNext()) {
                if (c.getElementType() == TokenType.WHITE_SPACE || c.getTextLength() == 0) continue;
                out.add(new JumperBlock(c, null, childIndent(c), spacing));
            }
            return out;
        }

        private Indent childIndent(ASTNode child) {
            IElementType parent = myNode.getElementType(), t = child.getElementType();
            if (INDENTING.contains(parent)) {
                if (OPENERS.contains(t) || CLOSERS.contains(t)) return Indent.getNoneIndent();
                // a switch's head `switch (x) {` is not indented, its branches are
                if ((parent == SWITCH_STATEMENT || parent == SWITCH_EXPRESSION) && t != SWITCH_BRANCH && !JumperTokenTypes.COMMENTS.contains(t))
                    return Indent.getNoneIndent();
                return Indent.getNormalIndent();
            }
            if (CONTINUING.contains(parent)) return OPENERS.contains(t) || CLOSERS.contains(t) ? Indent.getNoneIndent() : Indent.getContinuationIndent();
            // `if (c) x;` - a body without braces on its own line
            if (BODIES_OF.contains(parent) && STATEMENTS.contains(t) && t != BLOCK && isBody(child)) return Indent.getNormalIndent();
            if (EXPRESSIONS.contains(parent) || parent == VARIABLE || parent == TABLE_ENTRY) return Indent.getContinuationWithoutFirstIndent();
            return Indent.getNoneIndent();
        }

        /** The statement is the body of its loop or if (not the init of a `for`). */
        private static boolean isBody(ASTNode child) {
            for (ASTNode p = child.getTreePrev(); p != null; p = p.getTreePrev()) if (p.getElementType() == RPAREN || p.getElementType() == ELSE || p.getElementType() == DO) return true;
            return false;
        }

        @Override
        public Indent getIndent() {
            return indent;
        }

        @Override
        public @Nullable Spacing getSpacing(@Nullable Block child1, @NotNull Block child2) {
            return spacing.getSpacing(this, child1, child2);
        }

        @Override
        public @NotNull ChildAttributes getChildAttributes(int newChildIndex) {
            IElementType t = myNode.getElementType();
            if (INDENTING.contains(t)) return new ChildAttributes(Indent.getNormalIndent(), null);
            if (CONTINUING.contains(t)) return new ChildAttributes(Indent.getContinuationIndent(), null);
            if (BODIES_OF.contains(t)) return new ChildAttributes(Indent.getNormalIndent(), null);
            return new ChildAttributes(Indent.getNoneIndent(), null);
        }

        @Override
        public boolean isLeaf() {
            return myNode.getFirstChildNode() == null;
        }
    }
}
