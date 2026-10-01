package me.padej.jumper.idea.lang.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import me.padej.jumper.idea.JumperLanguage;
import me.padej.jumper.idea.lang.lexer.JumperLexer;
import me.padej.jumper.idea.lang.lexer.JumperTokenTypes;
import me.padej.jumper.idea.lang.psi.*;
import org.jetbrains.annotations.NotNull;

import static me.padej.jumper.idea.lang.parser.JumperElementTypes.*;

public final class JumperParserDefinition implements ParserDefinition {
    public static final IFileElementType FILE = new IFileElementType(JumperLanguage.INSTANCE);

    @Override
    public @NotNull Lexer createLexer(Project project) {
        return new JumperLexer();
    }

    @Override
    public @NotNull PsiParser createParser(Project project) {
        return new JumperParser();
    }

    @Override
    public @NotNull IFileElementType getFileNodeType() {
        return FILE;
    }

    @Override
    public @NotNull TokenSet getWhitespaceTokens() {
        return JumperTokenTypes.WHITE_SPACES;
    }

    @Override
    public @NotNull TokenSet getCommentTokens() {
        // a stray character is skipped as the language's lexer skips it (the annotator reports it)
        return TokenSet.orSet(JumperTokenTypes.COMMENTS, TokenSet.create(JumperTokenTypes.BAD_CHARACTER));
    }

    @Override
    public @NotNull TokenSet getStringLiteralElements() {
        return JumperTokenTypes.STRINGS;
    }

    @Override
    public @NotNull PsiElement createElement(ASTNode node) {
        IElementType t = node.getElementType();
        if (t == REFERENCE_EXPRESSION) return new JmpReferenceExpression(node);
        if (t == VARIABLE) return new JmpVariable(node);
        if (t == PARAMETER) return new JmpParameter(node);
        if (t == FUNCTION) return new JmpFunction(node);
        if (t == CLASS_DECLARATION) return new JmpClass(node);
        if (t == TYPE_ELEMENT) return new JmpTypeElement(node);
        if (t == CALL_EXPRESSION) return new JmpCallExpression(node);
        if (t == NEW_EXPRESSION) return new JmpNewExpression(node);
        if (t == ARGUMENT_LIST) return new JmpArgumentList(node);
        if (t == LITERAL_EXPRESSION) return new JmpLiteral(node);
        if (t == IMPORT_STATEMENT) return new JmpImportStatement(node);
        if (t == MODULE_IMPORT) return new JmpModuleImport(node);
        if (t == BLOCK) return new JmpBlock(node);
        if (t == LAMBDA_EXPRESSION) return new JmpLambda(node);
        if (t == CLASS_BODY) return new JmpClassBody(node);
        if (t == TABLE_KEY) return new JmpTableKey(node);
        if (t == PARAMETER_LIST) return new JmpParameterList(node);
        return new JmpElement(node);
    }

    @Override
    public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
        return new JumperFile(viewProvider);
    }

    @Override
    public @NotNull SpaceRequirements spaceExistenceTypeBetweenTokens(ASTNode left, ASTNode right) {
        return SpaceRequirements.MAY;
    }
}
