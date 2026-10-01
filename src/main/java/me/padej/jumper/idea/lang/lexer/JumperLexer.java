package me.padej.jumper.idea.lang.lexer;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static me.padej.jumper.idea.lang.lexer.JumperTokenTypes.*;

/**
 * The language's lexer ({@code lang/.../lexer/Lexer.java}) as an editor lexer: the same words, numbers,
 * strings and operators, read by the same rules, plus the white space and comments that the language skips.
 *
 * <p>It never fails: what the language's lexer rejects is still one token here - `0x` is a number, a string
 * without its closing quote ends at the end of its line, an unclosed comment at the end of the file, a stray
 * character is a BAD_CHARACTER - and {@link JumperLiterals} tells what is wrong with it (the annotator shows
 * the language's message). No state between tokens: a comment is one token, a string one line at most.
 */
public final class JumperLexer extends LexerBase {
    private CharSequence buf;
    private int end;
    private int start, pos;
    private IElementType type;

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
        this.buf = buffer;
        this.end = endOffset;
        this.pos = startOffset;
        advance();
    }

    @Override
    public int getState() {
        return 0;
    }

    @Override
    public @Nullable IElementType getTokenType() {
        return type;
    }

    @Override
    public int getTokenStart() {
        return start;
    }

    @Override
    public int getTokenEnd() {
        return pos;
    }

    @Override
    public @NotNull CharSequence getBufferSequence() {
        return buf;
    }

    @Override
    public int getBufferEnd() {
        return end;
    }

    @Override
    public void advance() {
        start = pos;
        if (pos >= end) {
            type = null;
            return;
        }
        type = next();
    }

    private char peek(int off) {
        return pos + off < end ? buf.charAt(pos + off) : '\0';
    }

    private IElementType next() {
        char c = buf.charAt(pos);
        if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
            while (pos < end && isSpace(buf.charAt(pos))) pos++;
            return WHITE_SPACE;
        }
        if (c == '/' && peek(1) == '/') {
            while (pos < end && buf.charAt(pos) != '\n') pos++;
            return LINE_COMMENT;
        }
        if (c == '/' && peek(1) == '*') {
            pos += 2;
            while (pos < end && !(buf.charAt(pos) == '*' && peek(1) == '/')) pos++;
            pos = Math.min(end, pos + 2);   // an unclosed comment runs to the end of the file
            return BLOCK_COMMENT;
        }
        if (Character.isLetter(c) || c == '_') {
            while (pos < end && (Character.isLetterOrDigit(buf.charAt(pos)) || buf.charAt(pos) == '_')) pos++;
            IElementType kw = KEYWORD_MAP.get(buf.subSequence(start, pos).toString());
            return kw != null ? kw : IDENT;
        }
        if (Character.isDigit(c)) return number();
        if (c == '"' || c == '\'') return string(c);
        return operator();
    }

    private static boolean isSpace(char c) {
        return c == ' ' || c == '\t' || c == '\r' || c == '\n';
    }

    /** As Lexer.readNumber: hex, decimal with a fraction and an exponent, suffixes L and d. */
    private IElementType number() {
        if (buf.charAt(pos) == '0' && (peek(1) == 'x' || peek(1) == 'X')) {
            pos += 2;
            while (pos < end && (Character.digit(buf.charAt(pos), 16) >= 0 || buf.charAt(pos) == '_')) pos++;
            if (pos < end && (buf.charAt(pos) == 'L' || buf.charAt(pos) == 'l')) { pos++; return LONG; }
            return INT;
        }
        digits();
        boolean isDouble = false;
        if (peek(0) == '.' && Character.isDigit(peek(1))) {
            isDouble = true;
            pos++;
            digits();
        }
        if (peek(0) == 'e' || peek(0) == 'E') {
            isDouble = true;
            pos++;
            if (peek(0) == '+' || peek(0) == '-') pos++;
            while (pos < end && Character.isDigit(buf.charAt(pos))) pos++;
        }
        char s = peek(0);
        if (isDouble) {
            if (s == 'd' || s == 'D') pos++;
            return DOUBLE;
        }
        if (s == 'L' || s == 'l') { pos++; return LONG; }
        if (s == 'd' || s == 'D') { pos++; return DOUBLE; }
        return INT;
    }

    private void digits() {
        while (pos < end && (Character.isDigit(buf.charAt(pos)) || buf.charAt(pos) == '_')) pos++;
    }

    /** From the quote to the same quote, a backslash escaping the next character; a line break ends it too. */
    private IElementType string(char quote) {
        pos++;
        while (pos < end) {
            char ch = buf.charAt(pos);
            if (ch == '\n') break;
            pos++;
            if (ch == quote) break;
            if (ch == '\\' && pos < end && buf.charAt(pos) != '\n') pos++;
        }
        return STRING;
    }

    /** As Lexer.readOperator: the longest operator. */
    private IElementType operator() {
        char ch = buf.charAt(pos++);
        switch (ch) {
            case '(': return LPAREN;
            case ')': return RPAREN;
            case '{': return LBRACE;
            case '}': return RBRACE;
            case '[': return LBRACKET;
            case ']': return RBRACKET;
            case ',': return COMMA;
            case '.': return DOT;
            case ';': return SEMI;
            case ':': return COLON;
            case '?': return QUESTION;
            case '^': return CARET;
            case '+': return match('+') ? PLUSPLUS : match('=') ? PLUSEQ : PLUS;
            case '-': return match('-') ? MINUSMINUS : match('=') ? MINUSEQ : match('>') ? ARROW : MINUS;
            case '*': return match('=') ? STAREQ : STAR;
            case '/': return match('=') ? SLASHEQ : SLASH;
            case '%': return match('=') ? PERCENTEQ : PERCENT;
            case '=': return match('=') ? EQEQ : EQ;
            case '!': return match('=') ? NE : NOT;
            case '<': return match('=') ? LE : match('<') ? SHL : LT;
            case '>': return match('=') ? GE : match('>') ? (match('>') ? USHR : SHR) : GT;
            case '&': return match('&') ? ANDAND : AMP;
            case '|': return match('|') ? OROR : PIPE;
            default: return BAD_CHARACTER;
        }
    }

    private boolean match(char c) {
        if (pos < end && buf.charAt(pos) == c) { pos++; return true; }
        return false;
    }
}
