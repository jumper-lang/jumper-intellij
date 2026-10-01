package me.padej.jumper.idea.lang.parser;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import me.padej.jumper.idea.JumperLanguage;

/** The nodes of the Jumper tree: one per construct of the grammar ({@code claude/lang-syntax.md} §2). */
public interface JumperElementTypes {
    final class JumperElementType extends IElementType {
        public JumperElementType(String debugName) {
            super(debugName, JumperLanguage.INSTANCE);
        }
    }

    // ---------- declarations
    IElementType IMPORT_STATEMENT = new JumperElementType("IMPORT_STATEMENT");        // import a.b.C;
    IElementType MODULE_IMPORT = new JumperElementType("MODULE_IMPORT");              // import "file.jmp";
    IElementType VARIABLE_DECLARATION = new JumperElementType("VARIABLE_DECLARATION"); // T a = 1, b;   (also fields)
    IElementType VARIABLE = new JumperElementType("VARIABLE");                        // one declarator (also a for-each variable)
    IElementType FUNCTION = new JumperElementType("FUNCTION");                        // function, method, constructor
    IElementType PARAMETER_LIST = new JumperElementType("PARAMETER_LIST");
    IElementType PARAMETER = new JumperElementType("PARAMETER");                      // also a lambda's and a catch's
    IElementType CLASS_DECLARATION = new JumperElementType("CLASS_DECLARATION");
    IElementType EXTENDS_CLAUSE = new JumperElementType("EXTENDS_CLAUSE");
    IElementType CLASS_BODY = new JumperElementType("CLASS_BODY");
    IElementType TYPE_ELEMENT = new JumperElementType("TYPE_ELEMENT");

    // ---------- statements
    IElementType BLOCK = new JumperElementType("BLOCK");
    IElementType EXPRESSION_STATEMENT = new JumperElementType("EXPRESSION_STATEMENT");
    IElementType EMPTY_STATEMENT = new JumperElementType("EMPTY_STATEMENT");
    IElementType IF_STATEMENT = new JumperElementType("IF_STATEMENT");
    IElementType WHILE_STATEMENT = new JumperElementType("WHILE_STATEMENT");
    IElementType DO_WHILE_STATEMENT = new JumperElementType("DO_WHILE_STATEMENT");
    IElementType FOR_STATEMENT = new JumperElementType("FOR_STATEMENT");
    IElementType FOREACH_STATEMENT = new JumperElementType("FOREACH_STATEMENT");
    IElementType RETURN_STATEMENT = new JumperElementType("RETURN_STATEMENT");
    IElementType BREAK_STATEMENT = new JumperElementType("BREAK_STATEMENT");
    IElementType CONTINUE_STATEMENT = new JumperElementType("CONTINUE_STATEMENT");
    IElementType THROW_STATEMENT = new JumperElementType("THROW_STATEMENT");
    IElementType TRY_STATEMENT = new JumperElementType("TRY_STATEMENT");
    IElementType CATCH_SECTION = new JumperElementType("CATCH_SECTION");
    IElementType FINALLY_SECTION = new JumperElementType("FINALLY_SECTION");
    IElementType SWITCH_STATEMENT = new JumperElementType("SWITCH_STATEMENT");
    IElementType SWITCH_BRANCH = new JumperElementType("SWITCH_BRANCH");
    IElementType CASE_LABEL = new JumperElementType("CASE_LABEL");

    // ---------- expressions
    IElementType ASSIGNMENT_EXPRESSION = new JumperElementType("ASSIGNMENT_EXPRESSION");
    IElementType CONDITIONAL_EXPRESSION = new JumperElementType("CONDITIONAL_EXPRESSION");
    IElementType BINARY_EXPRESSION = new JumperElementType("BINARY_EXPRESSION");
    IElementType PREFIX_EXPRESSION = new JumperElementType("PREFIX_EXPRESSION");
    IElementType POSTFIX_EXPRESSION = new JumperElementType("POSTFIX_EXPRESSION");
    IElementType LITERAL_EXPRESSION = new JumperElementType("LITERAL_EXPRESSION");
    IElementType REFERENCE_EXPRESSION = new JumperElementType("REFERENCE_EXPRESSION");
    IElementType CALL_EXPRESSION = new JumperElementType("CALL_EXPRESSION");
    IElementType ARGUMENT_LIST = new JumperElementType("ARGUMENT_LIST");
    IElementType INDEX_EXPRESSION = new JumperElementType("INDEX_EXPRESSION");
    IElementType NEW_EXPRESSION = new JumperElementType("NEW_EXPRESSION");
    IElementType NEW_ARRAY_EXPRESSION = new JumperElementType("NEW_ARRAY_EXPRESSION");
    IElementType ARRAY_LITERAL = new JumperElementType("ARRAY_LITERAL");
    IElementType TABLE_LITERAL = new JumperElementType("TABLE_LITERAL");
    IElementType TABLE_ENTRY = new JumperElementType("TABLE_ENTRY");
    IElementType TABLE_KEY = new JumperElementType("TABLE_KEY");
    IElementType LAMBDA_EXPRESSION = new JumperElementType("LAMBDA_EXPRESSION");
    IElementType PARENTHESIZED_EXPRESSION = new JumperElementType("PARENTHESIZED_EXPRESSION");
    IElementType SWITCH_EXPRESSION = new JumperElementType("SWITCH_EXPRESSION");
    IElementType THIS_EXPRESSION = new JumperElementType("THIS_EXPRESSION");
    IElementType SUPER_EXPRESSION = new JumperElementType("SUPER_EXPRESSION");

    TokenSet EXPRESSIONS = TokenSet.create(ASSIGNMENT_EXPRESSION, CONDITIONAL_EXPRESSION, BINARY_EXPRESSION, PREFIX_EXPRESSION,
            POSTFIX_EXPRESSION, LITERAL_EXPRESSION, REFERENCE_EXPRESSION, CALL_EXPRESSION, INDEX_EXPRESSION, NEW_EXPRESSION,
            NEW_ARRAY_EXPRESSION, ARRAY_LITERAL, TABLE_LITERAL, LAMBDA_EXPRESSION, PARENTHESIZED_EXPRESSION, SWITCH_EXPRESSION,
            THIS_EXPRESSION, SUPER_EXPRESSION);

    TokenSet STATEMENTS = TokenSet.create(IMPORT_STATEMENT, MODULE_IMPORT, VARIABLE_DECLARATION, FUNCTION, CLASS_DECLARATION, BLOCK,
            EXPRESSION_STATEMENT, EMPTY_STATEMENT, IF_STATEMENT, WHILE_STATEMENT, DO_WHILE_STATEMENT, FOR_STATEMENT,
            FOREACH_STATEMENT, RETURN_STATEMENT, BREAK_STATEMENT, CONTINUE_STATEMENT, THROW_STATEMENT, TRY_STATEMENT,
            SWITCH_STATEMENT);

    TokenSet LOOPS = TokenSet.create(WHILE_STATEMENT, DO_WHILE_STATEMENT, FOR_STATEMENT, FOREACH_STATEMENT);
}
