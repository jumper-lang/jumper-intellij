package me.padej.jumper.idea.lang.lexer;

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

import java.util.HashMap;
import java.util.Map;

/**
 * The tokens of Jumper - one for each {@code me.padej.jumper.lexer.TokenType} of the language, plus what the
 * language's lexer skips and an editor needs: white space, comments, a bad character.
 */
public interface JumperTokenTypes {
    // literals
    IElementType INT = new JumperTokenType("INT");
    IElementType LONG = new JumperTokenType("LONG");
    IElementType DOUBLE = new JumperTokenType("DOUBLE");
    IElementType STRING = new JumperTokenType("STRING");
    IElementType IDENT = new JumperTokenType("IDENT");

    // keywords
    IElementType DYN = new JumperTokenType("dyn");
    IElementType KW_INT = new JumperTokenType("int");
    IElementType KW_LONG = new JumperTokenType("long");
    IElementType KW_DOUBLE = new JumperTokenType("double");
    IElementType KW_BOOLEAN = new JumperTokenType("boolean");
    IElementType KW_STRING = new JumperTokenType("String");
    IElementType VOID = new JumperTokenType("void");
    IElementType CLASS = new JumperTokenType("class");
    IElementType NEW = new JumperTokenType("new");
    IElementType RETURN = new JumperTokenType("return");
    IElementType IF = new JumperTokenType("if");
    IElementType ELSE = new JumperTokenType("else");
    IElementType WHILE = new JumperTokenType("while");
    IElementType FOR = new JumperTokenType("for");
    IElementType DO = new JumperTokenType("do");
    IElementType BREAK = new JumperTokenType("break");
    IElementType CONTINUE = new JumperTokenType("continue");
    IElementType TRUE = new JumperTokenType("true");
    IElementType FALSE = new JumperTokenType("false");
    IElementType NULL = new JumperTokenType("null");
    IElementType IMPORT = new JumperTokenType("import");
    IElementType THIS = new JumperTokenType("this");
    IElementType SUPER = new JumperTokenType("super");
    IElementType EXTENDS = new JumperTokenType("extends");
    IElementType STATIC = new JumperTokenType("static");
    IElementType TRY = new JumperTokenType("try");
    IElementType CATCH = new JumperTokenType("catch");
    IElementType FINALLY = new JumperTokenType("finally");
    IElementType THROW = new JumperTokenType("throw");
    IElementType SWITCH = new JumperTokenType("switch");
    IElementType CASE = new JumperTokenType("case");
    IElementType DEFAULT = new JumperTokenType("default");

    // punctuation
    IElementType LPAREN = new JumperTokenType("(");
    IElementType RPAREN = new JumperTokenType(")");
    IElementType LBRACE = new JumperTokenType("{");
    IElementType RBRACE = new JumperTokenType("}");
    IElementType LBRACKET = new JumperTokenType("[");
    IElementType RBRACKET = new JumperTokenType("]");
    IElementType COMMA = new JumperTokenType(",");
    IElementType DOT = new JumperTokenType(".");
    IElementType SEMI = new JumperTokenType(";");
    IElementType COLON = new JumperTokenType(":");
    IElementType QUESTION = new JumperTokenType("?");
    IElementType ARROW = new JumperTokenType("->");

    // operators
    IElementType PLUS = new JumperTokenType("+");
    IElementType MINUS = new JumperTokenType("-");
    IElementType STAR = new JumperTokenType("*");
    IElementType SLASH = new JumperTokenType("/");
    IElementType PERCENT = new JumperTokenType("%");
    IElementType PLUSPLUS = new JumperTokenType("++");
    IElementType MINUSMINUS = new JumperTokenType("--");
    IElementType EQ = new JumperTokenType("=");
    IElementType EQEQ = new JumperTokenType("==");
    IElementType NE = new JumperTokenType("!=");
    IElementType LT = new JumperTokenType("<");
    IElementType LE = new JumperTokenType("<=");
    IElementType GT = new JumperTokenType(">");
    IElementType GE = new JumperTokenType(">=");
    IElementType ANDAND = new JumperTokenType("&&");
    IElementType OROR = new JumperTokenType("||");
    IElementType NOT = new JumperTokenType("!");
    IElementType AMP = new JumperTokenType("&");
    IElementType PIPE = new JumperTokenType("|");
    IElementType CARET = new JumperTokenType("^");
    IElementType SHL = new JumperTokenType("<<");
    IElementType SHR = new JumperTokenType(">>");
    IElementType USHR = new JumperTokenType(">>>");
    IElementType PLUSEQ = new JumperTokenType("+=");
    IElementType MINUSEQ = new JumperTokenType("-=");
    IElementType STAREQ = new JumperTokenType("*=");
    IElementType SLASHEQ = new JumperTokenType("/=");
    IElementType PERCENTEQ = new JumperTokenType("%=");

    // what the language's lexer skips
    IElementType LINE_COMMENT = new JumperTokenType("LINE_COMMENT");
    IElementType BLOCK_COMMENT = new JumperTokenType("BLOCK_COMMENT");
    IElementType WHITE_SPACE = TokenType.WHITE_SPACE;
    IElementType BAD_CHARACTER = TokenType.BAD_CHARACTER;

    /** Reserved words, as the language's Lexer.KEYWORDS. */
    Map<String, IElementType> KEYWORD_MAP = Keywords.MAP;

    /** `dyn` .. `default`: the language's Parser.isKeyword - also valid as a member name and a table key. */
    TokenSet KEYWORDS = TokenSet.create(DYN, KW_INT, KW_LONG, KW_DOUBLE, KW_BOOLEAN, KW_STRING, VOID, CLASS, NEW, RETURN,
            IF, ELSE, WHILE, FOR, DO, BREAK, CONTINUE, TRUE, FALSE, NULL, IMPORT, THIS, SUPER, EXTENDS, STATIC, TRY, CATCH,
            FINALLY, THROW, SWITCH, CASE, DEFAULT);
    /** Types of a declaration: `typeKw` of the grammar, and `void` of a function. */
    TokenSet TYPE_KEYWORDS = TokenSet.create(DYN, KW_INT, KW_LONG, KW_DOUBLE, KW_BOOLEAN, KW_STRING);
    TokenSet COMMENTS = TokenSet.create(LINE_COMMENT, BLOCK_COMMENT);
    TokenSet WHITE_SPACES = TokenSet.create(WHITE_SPACE);
    TokenSet STRINGS = TokenSet.create(STRING);
    TokenSet NUMBERS = TokenSet.create(INT, LONG, DOUBLE);
    TokenSet LITERALS = TokenSet.create(INT, LONG, DOUBLE, STRING, TRUE, FALSE, NULL);
    TokenSet ASSIGN_OPS = TokenSet.create(EQ, PLUSEQ, MINUSEQ, STAREQ, SLASHEQ, PERCENTEQ);
    TokenSet OPERATORS = TokenSet.create(PLUS, MINUS, STAR, SLASH, PERCENT, PLUSPLUS, MINUSMINUS, EQ, EQEQ, NE, LT, LE, GT, GE,
            ANDAND, OROR, NOT, AMP, PIPE, CARET, SHL, SHR, USHR, PLUSEQ, MINUSEQ, STAREQ, SLASHEQ, PERCENTEQ, QUESTION, COLON, ARROW);

    final class Keywords {
        private Keywords() {}

        static final Map<String, IElementType> MAP = new HashMap<>();

        static {
            IElementType[] kws = {DYN, KW_INT, KW_LONG, KW_DOUBLE, KW_BOOLEAN, KW_STRING, VOID, CLASS, NEW, RETURN, IF, ELSE,
                    WHILE, FOR, DO, BREAK, CONTINUE, TRUE, FALSE, NULL, IMPORT, THIS, SUPER, EXTENDS, STATIC, TRY, CATCH,
                    FINALLY, THROW, SWITCH, CASE, DEFAULT};
            for (IElementType k : kws) MAP.put(k.getDebugName(), k);
        }
    }
}
