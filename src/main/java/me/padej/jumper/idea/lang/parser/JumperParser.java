package me.padej.jumper.idea.lang.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import me.padej.jumper.idea.lang.lexer.JumperTokenTypes;
import org.jetbrains.annotations.NotNull;

import static me.padej.jumper.idea.lang.lexer.JumperTokenTypes.*;
import static me.padej.jumper.idea.lang.parser.JumperElementTypes.*;

/**
 * The grammar of {@code lang/.../parser/Parser.java}, as a tree: the same recursive descent, the same
 * lookaheads, the same messages - but nothing is resolved while parsing, so where the language asks "is this
 * name a class?" (`Point p = ...`, `(Point p) -> ...`) the tree reads two names in a row as a type and a name:
 * in the language two names in a row are an error anyway, and the annotator tells what the first one is.
 *
 * <p>Tolerant, as {@code Parser.tolerant}: an error is recorded and parsing goes on. A statement that breaks is
 * skipped to its end by the language's rule (its `;` outside braces, or its closing `}`); a broken list or
 * parenthesized part is skipped to its closing bracket, so one mistake is one error.
 */
public final class JumperParser implements PsiParser {
    @Override
    public @NotNull ASTNode parse(@NotNull IElementType root, @NotNull PsiBuilder builder) {
        PsiBuilder.Marker file = builder.mark();
        new Impl(builder).program();
        file.done(root);
        return builder.getTreeBuilt();
    }

    private static final TokenSet PREFIX_OPS = TokenSet.create(NOT, MINUS, PLUS, PLUSPLUS, MINUSMINUS);
    private static final TokenSet[] BINARY_LEVELS = {
            TokenSet.create(OROR), TokenSet.create(ANDAND), TokenSet.create(PIPE), TokenSet.create(CARET), TokenSet.create(AMP),
            TokenSet.create(EQEQ, NE), TokenSet.create(LT, LE, GT, GE), TokenSet.create(SHL, SHR, USHR),
            TokenSet.create(PLUS, MINUS), TokenSet.create(STAR, SLASH, PERCENT)};
    private static final TokenSet TABLE_KEY_TOKENS = TokenSet.create(IDENT, STRING, INT, LONG, DOUBLE);
    private static final TokenSet AFTER_BLOCK_CONTINUES = TokenSet.create(ELSE, CATCH, FINALLY, SEMI, RPAREN, COMMA, DOT, LPAREN,
            LBRACKET, RBRACKET);
    private static final TokenSet OPENERS = TokenSet.create(LPAREN, LBRACKET, LBRACE);
    private static final TokenSet CLOSERS = TokenSet.create(RPAREN, RBRACKET, RBRACE);

    private static final class Impl {
        private final PsiBuilder b;

        Impl(PsiBuilder b) {
            this.b = b;
        }

        // ------------------------------------------------------------------ helpers

        private boolean at(IElementType t) {
            return b.getTokenType() == t;
        }

        private IElementType la(int k) {
            return k == 0 ? b.getTokenType() : b.lookAhead(k);
        }

        private boolean eof() {
            return b.eof();
        }

        private boolean consume(IElementType t) {
            if (!at(t)) return false;
            b.advanceLexer();
            return true;
        }

        private String tokenText() {
            String s = b.getTokenText();
            return s == null ? "" : s;
        }

        /** As Parser.expect: "Expected <what> but got '<token>'" at the current token. */
        private boolean expect(IElementType t, String what) {
            if (consume(t)) return true;
            b.error("Expected " + what + " but got '" + tokenText() + "'");
            return false;
        }

        private static boolean isKeyword(IElementType t) {
            return t != null && KEYWORDS.contains(t);
        }

        private static boolean isTypeKeyword(IElementType t) {
            return t != null && TYPE_KEYWORDS.contains(t);
        }

        /** A type at k followed by a name: `int x`, `dyn x`, `Point p`, `Random r` (two names in a row). */
        private boolean typeThenName(int k) {
            IElementType t = la(k);
            return (isTypeKeyword(t) || t == IDENT) && la(k + 1) == IDENT;
        }

        /** Skips to `close` at this nesting level and consumes it; stops before a `;` or `}` of an enclosing level. */
        private void skipTo(IElementType close) {
            int depth = 0;
            while (!eof()) {
                IElementType t = b.getTokenType();
                if (depth == 0 && t == close) { b.advanceLexer(); return; }
                if (depth == 0 && (t == SEMI || t == RBRACE)) return;
                if (OPENERS.contains(t)) depth++;
                else if (CLOSERS.contains(t)) depth--;
                b.advanceLexer();
            }
        }

        /** A broken `for (...)` header: to its `)` - its own `;` do not end it; a brace at this level does. */
        private void skipHeaderRest() {
            int depth = 0;
            while (!eof()) {
                IElementType t = b.getTokenType();
                if (depth == 0 && (t == LBRACE || t == RBRACE)) return;
                if (t == LPAREN) depth++;
                else if (t == RPAREN && depth-- == 0) { b.advanceLexer(); return; }
                b.advanceLexer();
            }
        }

        /**
         * After an error in a statement (Parser.statementOrRecover / statementEnd): the tokens up to its `;`
         * outside braces, or its closing `}` when nothing that continues the statement follows it. A `}` that
         * closes an enclosing block stops it.
         */
        private void skipStatementRest(int startIndex) {
            if (b.rawTokenIndex() == startIndex && !eof()) b.advanceLexer();   // always make progress
            int depth = 0;
            while (!eof()) {
                IElementType t = b.getTokenType();
                if (t == LBRACE) depth++;
                else if (t == RBRACE) {
                    if (depth == 0) return;
                    b.advanceLexer();
                    if (--depth == 0 && !AFTER_BLOCK_CONTINUES.contains(b.getTokenType())) return;
                    continue;
                } else if (t == SEMI && depth == 0) {
                    b.advanceLexer();
                    return;
                }
                b.advanceLexer();
            }
        }

        // ------------------------------------------------------------------ statements

        void program() {
            while (!eof()) statementOrRecover();
        }

        private void statementOrRecover() {
            int start = b.rawTokenIndex();
            if (!statement()) skipStatementRest(start);
        }

        /** One statement; false when it broke off (its error is recorded). */
        private boolean statement() {
            IElementType t = b.getTokenType();
            if (t == null) {
                b.error("Expected a statement");
                return false;
            }
            if (t == IMPORT) return importStatement();
            if (t == LBRACE) return block();
            if (t == IF) return ifStatement();
            if (t == WHILE) return whileStatement();
            if (t == DO) return doWhileStatement();
            if (t == FOR) return forStatement();
            if (t == RETURN) return simple(RETURN_STATEMENT, true);
            if (t == BREAK) return simple(BREAK_STATEMENT, false);
            if (t == CONTINUE) return simple(CONTINUE_STATEMENT, false);
            if (t == THROW) return simple(THROW_STATEMENT, true);
            if (t == SEMI) {
                PsiBuilder.Marker m = b.mark();
                b.advanceLexer();
                m.done(EMPTY_STATEMENT);
                return true;
            }
            if (t == VOID) return function(b.mark(), null);
            if (t == JumperTokenTypes.CLASS) return classDeclaration();
            if (t == TRY) return tryStatement();
            if (t == SWITCH) return switchStatement();
            if (t == IDENT && "function".equals(tokenText()) && (la(1) == IDENT || la(1) == LPAREN)) {
                b.error("'function' is not a keyword: declare functions as 'dyn name(...)', 'void name(...)' or 'int name(...)', lambdas as '(x) -> ...'");
                return false;
            }
            if (t == IDENT && "var".equals(tokenText()) && la(1) == IDENT) {
                b.error("'var' is not a keyword in Jumper: a dynamically typed variable is 'dyn name = ...' (a typed one: int, long, double, boolean, String or a class name)");
                return false;
            }
            if (typeThenName(0)) {
                if (la(2) == LPAREN) return function(b.mark(), null);
                return variableDeclaration(b.mark());
            }
            return expressionStatement();
        }

        private boolean expressionStatement() {
            PsiBuilder.Marker m = b.mark();
            if (expression() == null) {
                m.done(EXPRESSION_STATEMENT);
                return false;
            }
            boolean ok = expect(SEMI, "';'");
            m.done(EXPRESSION_STATEMENT);
            return ok;
        }

        /** return/throw [expression]; and break;/continue; */
        private boolean simple(IElementType type, boolean withValue) {
            PsiBuilder.Marker m = b.mark();
            boolean isReturn = at(RETURN);
            b.advanceLexer();
            boolean ok = true;
            if (withValue && !(isReturn && at(SEMI))) ok = expression() != null;
            if (ok) ok = expect(SEMI, "';'");
            m.done(type);
            return ok;
        }

        private boolean importStatement() {
            PsiBuilder.Marker m = b.mark();
            b.advanceLexer();
            if (at(STRING)) {   // import "file.jmp";
                PsiBuilder.Marker lit = b.mark();
                b.advanceLexer();
                lit.done(LITERAL_EXPRESSION);
                boolean ok = expect(SEMI, "';'");
                m.done(MODULE_IMPORT);
                return ok;
            }
            boolean ok = qualifiedReference("class name", "identifier");
            if (ok) ok = expect(SEMI, "';'");
            m.done(IMPORT_STATEMENT);
            return ok;
        }

        /** a.b.C as nested reference expressions (import, extends). */
        private boolean qualifiedReference(String firstWhat, String nextWhat) {
            PsiBuilder.Marker r = b.mark();
            if (!expect(IDENT, firstWhat)) {
                r.drop();
                return false;
            }
            r.done(REFERENCE_EXPRESSION);
            while (at(DOT)) {
                r = r.precede();
                b.advanceLexer();
                boolean ok = expect(IDENT, nextWhat);
                r.done(REFERENCE_EXPRESSION);
                if (!ok) return false;
            }
            return true;
        }

        boolean block() {
            PsiBuilder.Marker m = b.mark();
            if (!expect(LBRACE, "'{'")) {
                m.drop();
                return false;
            }
            while (!at(RBRACE) && !eof()) statementOrRecover();
            boolean ok = expect(RBRACE, "'}'");
            m.done(BLOCK);
            return ok;
        }

        /** The body of if/while/for: a block or one statement (in its own scope). */
        private boolean body() {
            if (at(LBRACE)) return block();
            int start = b.rawTokenIndex();
            if (statement()) return true;
            skipStatementRest(start);
            return true;   // recovered: what follows is parsed as usual
        }

        /** `( expression )` of if/while/switch; a broken one is skipped to its `)`. */
        private void condition() {
            if (!expect(LPAREN, "'('")) return;
            if (expression() == null) { skipTo(RPAREN); return; }
            if (!expect(RPAREN, "')'")) skipTo(RPAREN);
        }

        private boolean ifStatement() {
            PsiBuilder.Marker m = b.mark();
            b.advanceLexer();
            condition();
            boolean ok = body();
            if (consume(ELSE)) ok = body();
            m.done(IF_STATEMENT);
            return ok;
        }

        private boolean whileStatement() {
            PsiBuilder.Marker m = b.mark();
            b.advanceLexer();
            condition();
            boolean ok = body();
            m.done(WHILE_STATEMENT);
            return ok;
        }

        private boolean doWhileStatement() {
            PsiBuilder.Marker m = b.mark();
            b.advanceLexer();
            body();
            boolean ok = expect(WHILE, "while");
            if (ok) {
                condition();
                ok = expect(SEMI, "';'");
            }
            m.done(DO_WHILE_STATEMENT);
            return ok;
        }

        /** for (T x : xs) body | for (init; cond; update) body. */
        private boolean forStatement() {
            PsiBuilder.Marker m = b.mark();
            b.advanceLexer();
            if (!expect(LPAREN, "'('")) {
                m.done(FOR_STATEMENT);
                return false;
            }
            if (typeThenName(0) && la(2) == COLON) {
                typeElement();
                PsiBuilder.Marker v = b.mark();
                b.advanceLexer();
                v.done(VARIABLE);
                b.advanceLexer();   // ':'
                if (expression() == null || !expect(RPAREN, "')'")) skipTo(RPAREN);
                body();
                m.done(FOREACH_STATEMENT);
                return true;
            }
            boolean header = true;
            if (!consume(SEMI)) {
                if (typeThenName(0)) header = variableDeclaration(b.mark());
                else header = expressionStatement();
            }
            if (header && !at(SEMI)) header = expression() != null;
            if (header) header = expect(SEMI, "';'");
            if (header && !at(RPAREN)) header = expression() != null;
            if (header) header = expect(RPAREN, "')'");
            if (!header) skipHeaderRest();
            body();
            m.done(FOR_STATEMENT);
            return true;
        }

        private boolean tryStatement() {
            PsiBuilder.Marker m = b.mark();
            b.advanceLexer();
            block();
            boolean any = false;
            if (at(CATCH)) {
                any = true;
                PsiBuilder.Marker c = b.mark();
                b.advanceLexer();
                if (expect(LPAREN, "'('")) {
                    PsiBuilder.Marker p = b.mark();
                    if (expect(IDENT, "exception variable")) p.done(PARAMETER); else p.drop();
                    if (!expect(RPAREN, "')'")) skipTo(RPAREN);
                }
                block();
                c.done(CATCH_SECTION);
            }
            if (at(FINALLY)) {
                any = true;
                PsiBuilder.Marker f = b.mark();
                b.advanceLexer();
                block();
                f.done(FINALLY_SECTION);
            }
            if (!any) b.error("'try' without 'catch' or 'finally'");
            m.done(TRY_STATEMENT);
            return true;
        }

        /** `case a, b` or `default`; false if neither is there. */
        private boolean caseLabel() {
            PsiBuilder.Marker m = b.mark();
            if (consume(DEFAULT)) {
                m.done(CASE_LABEL);
                return true;
            }
            if (!expect(CASE, "'case' or 'default'")) {
                m.drop();
                return false;
            }
            do {
                if (expression() == null) break;
            } while (consume(COMMA));
            m.done(CASE_LABEL);
            return true;
        }

        private void arrow() {
            if (at(COLON)) {
                b.error("switch uses arrows: 'case 1 -> ...' (form with ':' and fallthrough is not supported)");
                b.advanceLexer();
                return;
            }
            expect(ARROW, "'->'");
        }

        /** `switch (x) {` of both forms; false if broken before the `{`. */
        private boolean switchHead() {
            b.advanceLexer();
            condition();
            return expect(LBRACE, "'{'");
        }

        private boolean switchStatement() {
            PsiBuilder.Marker m = b.mark();
            if (!switchHead()) {
                m.done(SWITCH_STATEMENT);
                return false;
            }
            boolean any = false;
            while (!at(RBRACE) && !eof()) {
                PsiBuilder.Marker br = b.mark();
                int start = b.rawTokenIndex();
                if (!caseLabel()) {
                    skipStatementRest(start);
                    br.done(SWITCH_BRANCH);
                    continue;
                }
                any = true;
                arrow();
                if (at(LBRACE)) block(); else statementOrRecover();
                br.done(SWITCH_BRANCH);
            }
            if (!any && at(RBRACE)) b.error("Empty switch");
            boolean ok = expect(RBRACE, "'}'");
            m.done(SWITCH_STATEMENT);
            return ok;
        }

        // ------------------------------------------------------------------ declarations

        /** A type: `dyn`, `int` ... `String`, `void`, or a class name (a reference). */
        private void typeElement() {
            PsiBuilder.Marker t = b.mark();
            if (isTypeKeyword(b.getTokenType()) || at(VOID)) {
                b.advanceLexer();
            } else if (at(IDENT)) {
                PsiBuilder.Marker r = b.mark();
                b.advanceLexer();
                r.done(REFERENCE_EXPRESSION);
            }
            t.done(TYPE_ELEMENT);
        }

        /** `T a [= e], b;` from the type on; `m` starts before it (and before `static` in a class). */
        private boolean variableDeclaration(PsiBuilder.Marker m) {
            typeElement();
            boolean ok = true;
            do {
                PsiBuilder.Marker d = b.mark();
                if (!expect(IDENT, "variable name")) {
                    d.drop();
                    ok = false;
                    break;
                }
                if (consume(EQ) && expression() == null) ok = false;
                d.done(VARIABLE);
                if (!ok) break;
            } while (consume(COMMA));
            if (ok) ok = expect(SEMI, "';'");
            m.done(VARIABLE_DECLARATION);
            return ok;
        }

        /** `T name(params) { body }`; `m` starts before the type (or `static`), `ctorName` for a constructor. */
        private boolean function(PsiBuilder.Marker m, String ctorName) {
            if (ctorName == null) typeElement();
            if (!expect(IDENT, ctorName == null ? "function name" : "method name")) {
                m.done(FUNCTION);
                return false;
            }
            parameterList();
            boolean ok = block();
            m.done(FUNCTION);
            return ok;
        }

        private void parameterList() {
            PsiBuilder.Marker m = b.mark();
            if (!expect(LPAREN, "'('")) {
                m.done(PARAMETER_LIST);
                return;
            }
            if (!at(RPAREN)) {
                do {
                    if (!parameter()) break;
                } while (consume(COMMA));
            }
            if (!expect(RPAREN, "')'")) skipTo(RPAREN);
            m.done(PARAMETER_LIST);
        }

        /** `[T] name`. */
        private boolean parameter() {
            PsiBuilder.Marker p = b.mark();
            if (typeThenName(0)) typeElement();
            if (!expect(IDENT, "parameter name")) {
                p.drop();
                return false;
            }
            p.done(PARAMETER);
            return true;
        }

        private boolean classDeclaration() {
            PsiBuilder.Marker m = b.mark();
            b.advanceLexer();
            String name = at(IDENT) ? tokenText() : null;
            if (!expect(IDENT, "class name")) {
                m.done(CLASS_DECLARATION);
                return false;
            }
            if (at(EXTENDS)) {
                PsiBuilder.Marker e = b.mark();
                b.advanceLexer();
                qualifiedReference("superclass name", "superclass name");
                e.done(EXTENDS_CLAUSE);
            }
            PsiBuilder.Marker body = b.mark();
            if (!expect(LBRACE, "'{'")) {
                body.drop();
                m.done(CLASS_DECLARATION);
                return false;
            }
            while (!at(RBRACE) && !eof()) member(name);
            boolean ok = expect(RBRACE, "'}'");
            body.done(CLASS_BODY);
            m.done(CLASS_DECLARATION);
            return ok;
        }

        /** A member of a class body: a field, a method, the constructor (Parser.classDecl). */
        private void member(String className) {
            if (at(SEMI)) {
                PsiBuilder.Marker e = b.mark();
                b.advanceLexer();
                e.done(EMPTY_STATEMENT);
                return;
            }
            int start = b.rawTokenIndex();
            PsiBuilder.Marker m = b.mark();
            boolean isStatic = consume(STATIC);
            IElementType t = b.getTokenType();
            boolean typed = isTypeKeyword(t) || t == VOID || (t == IDENT && la(1) == IDENT);
            if (typed && la(1) == IDENT && la(2) == LPAREN) {
                if (!function(m, null)) skipStatementRest(start);
                return;
            }
            if (t != VOID && typeThenName(0)) {
                if (!variableDeclaration(m)) skipStatementRest(start);
                return;
            }
            if (!isStatic && t == IDENT && la(1) == LPAREN) {
                if (!tokenText().equals(className)) b.error("Method '" + tokenText() + "' needs a return type: void, dyn, int, ...");
                if (!function(m, tokenText())) skipStatementRest(start);
                return;
            }
            b.error(isStatic ? "Expected a static field or method in class " + className
                    : "Expected field, method or constructor in class " + className);
            skipStatementRest(start);
            m.drop();
        }

        // ------------------------------------------------------------------ expressions

        /** The expression's node, or null when it broke off (the error is recorded). */
        PsiBuilder.Marker expression() {
            return assignment();
        }

        private PsiBuilder.Marker assignment() {
            PsiBuilder.Marker left = ternary();
            if (left == null) return null;
            if (ASSIGN_OPS.contains(b.getTokenType())) {
                PsiBuilder.Marker a = left.precede();
                b.advanceLexer();
                PsiBuilder.Marker right = assignment();
                a.done(ASSIGNMENT_EXPRESSION);
                return right == null ? null : a;
            }
            return left;
        }

        private PsiBuilder.Marker ternary() {
            PsiBuilder.Marker cond = binary(0);
            if (cond == null || !at(QUESTION)) return cond;
            PsiBuilder.Marker c = cond.precede();
            b.advanceLexer();
            boolean ok = assignment() != null && expect(COLON, "':'") && assignment() != null;
            c.done(CONDITIONAL_EXPRESSION);
            return ok ? c : null;
        }

        private PsiBuilder.Marker binary(int level) {
            if (level == BINARY_LEVELS.length) return unary();
            PsiBuilder.Marker left = binary(level + 1);
            while (left != null && BINARY_LEVELS[level].contains(b.getTokenType())) {
                PsiBuilder.Marker bin = left.precede();
                b.advanceLexer();
                PsiBuilder.Marker right = binary(level + 1);
                bin.done(BINARY_EXPRESSION);
                left = right == null ? null : bin;
            }
            return left;
        }

        private PsiBuilder.Marker unary() {
            if (PREFIX_OPS.contains(b.getTokenType())) {
                PsiBuilder.Marker m = b.mark();
                b.advanceLexer();
                PsiBuilder.Marker operand = unary();
                m.done(PREFIX_EXPRESSION);
                return operand == null ? null : m;
            }
            return postfix();
        }

        private PsiBuilder.Marker postfix() {
            PsiBuilder.Marker e = primary();
            while (e != null) {
                if (at(DOT)) {
                    PsiBuilder.Marker r = e.precede();
                    b.advanceLexer();
                    // a keyword is a valid member name after a dot: cfg.groups.default, t.new, X.class
                    boolean ok = true;
                    if (isKeyword(b.getTokenType())) b.advanceLexer();
                    else ok = expect(IDENT, "member name");
                    r.done(REFERENCE_EXPRESSION);
                    e = ok ? r : null;
                } else if (at(LPAREN)) {
                    PsiBuilder.Marker c = e.precede();
                    argumentList();
                    c.done(CALL_EXPRESSION);
                    e = c;
                } else if (at(LBRACKET)) {
                    PsiBuilder.Marker ix = e.precede();
                    b.advanceLexer();
                    if (expression() == null || !expect(RBRACKET, "']'")) skipTo(RBRACKET);
                    ix.done(INDEX_EXPRESSION);
                    e = ix;
                } else if (at(PLUSPLUS) || at(MINUSMINUS)) {
                    PsiBuilder.Marker p = e.precede();
                    b.advanceLexer();
                    p.done(POSTFIX_EXPRESSION);
                    e = p;
                } else break;
            }
            return e;
        }

        private void argumentList() {
            PsiBuilder.Marker m = b.mark();
            b.advanceLexer();   // '('
            boolean ok = true;
            if (!at(RPAREN)) {
                do {
                    if (expression() == null) { ok = false; break; }
                } while (consume(COMMA));
            }
            if (!ok || !expect(RPAREN, "')'")) skipTo(RPAREN);
            m.done(ARGUMENT_LIST);
        }

        private PsiBuilder.Marker done(PsiBuilder.Marker m, IElementType type) {
            m.done(type);
            return m;
        }

        private PsiBuilder.Marker primary() {
            IElementType t = b.getTokenType();
            if (t == null) {
                b.error("Expected an expression");
                return null;
            }
            if (t == INT || t == LONG || t == DOUBLE || t == STRING || t == TRUE || t == FALSE || t == NULL) {
                PsiBuilder.Marker m = b.mark();
                b.advanceLexer();
                return done(m, LITERAL_EXPRESSION);
            }
            if (t == LBRACKET) return arrayLiteral();
            if (t == LBRACE) return tableLiteral();
            if (t == NEW) return newExpression();
            if (t == SWITCH) return switchExpression();
            if (t == THIS) {
                PsiBuilder.Marker m = b.mark();
                b.advanceLexer();
                return done(m, THIS_EXPRESSION);
            }
            if (t == SUPER) {
                PsiBuilder.Marker m = b.mark();
                b.advanceLexer();
                done(m, SUPER_EXPRESSION);
                if (!at(LPAREN) && !at(DOT)) {
                    b.error("Expected '(' or '.' after super but got '" + tokenText() + "'");
                    return null;
                }
                return m;
            }
            if (t == KW_INT || t == KW_LONG || t == KW_DOUBLE) {   // int(x): the built-in converters
                if (la(1) != LPAREN) {
                    b.error("Unexpected type keyword '" + tokenText() + "'");
                    return null;
                }
                PsiBuilder.Marker m = b.mark();
                b.advanceLexer();
                return done(m, REFERENCE_EXPRESSION);
            }
            if (t == KW_STRING) {   // String.valueOf(...): the class java.lang.String
                PsiBuilder.Marker m = b.mark();
                b.advanceLexer();
                return done(m, REFERENCE_EXPRESSION);
            }
            if (t == IDENT) {
                if (la(1) == ARROW) {   // x -> ...
                    PsiBuilder.Marker m = b.mark();
                    PsiBuilder.Marker params = b.mark();
                    PsiBuilder.Marker p = b.mark();
                    b.advanceLexer();
                    p.done(PARAMETER);
                    params.done(PARAMETER_LIST);
                    b.advanceLexer();   // '->'
                    boolean ok = lambdaBody();
                    m.done(LAMBDA_EXPRESSION);
                    return ok ? m : null;
                }
                PsiBuilder.Marker m = b.mark();
                b.advanceLexer();
                return done(m, REFERENCE_EXPRESSION);
            }
            if (t == LPAREN) {
                if (isLambdaAhead()) {
                    PsiBuilder.Marker m = b.mark();
                    parameterList();
                    boolean ok = expect(ARROW, "'->'") && lambdaBody();
                    m.done(LAMBDA_EXPRESSION);
                    return ok ? m : null;
                }
                PsiBuilder.Marker m = b.mark();
                b.advanceLexer();
                boolean ok = expression() != null;
                if (!ok || !expect(RPAREN, "')'")) skipTo(RPAREN);
                done(m, PARENTHESIZED_EXPRESSION);
                return ok ? m : null;   // `(int) x`: one error, the statement is skipped
            }
            b.error("Unexpected token '" + tokenText() + "'");
            return null;
        }

        private boolean lambdaBody() {
            if (at(LBRACE)) return block();
            return expression() != null;
        }

        /** Parser.isLambdaAhead: `( )` `->`, or `( [type] id, [type] id )` `->`. */
        private boolean isLambdaAhead() {
            int k = 1;
            if (la(k) == RPAREN) return la(k + 1) == ARROW;
            for (int guard = 0; guard < 256; guard++) {
                if (typeThenName(k)) k++;
                if (la(k) != IDENT) return false;
                k++;
                if (la(k) == COMMA) { k++; continue; }
                return la(k) == RPAREN && la(k + 1) == ARROW;
            }
            return false;
        }

        private PsiBuilder.Marker arrayLiteral() {
            PsiBuilder.Marker m = b.mark();
            b.advanceLexer();
            elements(RBRACKET, "']'");
            return done(m, ARRAY_LITERAL);
        }

        /** `a, b, c` up to the closing bracket (a trailing comma allowed), then the bracket. */
        private void elements(IElementType close, String what) {
            boolean ok = true;
            while (!at(close) && !eof()) {
                if (expression() == null) { ok = false; break; }
                if (!consume(COMMA)) break;
            }
            if (!ok || !expect(close, what)) skipTo(close);
        }

        /** Parser.tableEntryAhead: does a `key:` come first after the `{` (a table) or a plain element (an array)? */
        private boolean tableEntryAhead() {
            IElementType t = b.getTokenType();
            if (TABLE_KEY_TOKENS.contains(t) || isKeyword(t)) return la(1) == COLON;
            if (t == LBRACKET) {   // [expr]: v - a computed key; [a, b] - an array element
                int depth = 0;
                for (int k = 0; k < 10000; k++) {
                    IElementType u = la(k);
                    if (u == null) return false;
                    if (OPENERS.contains(u)) depth++;
                    else if (CLOSERS.contains(u) && --depth == 0) return la(k + 1) == COLON;
                }
            }
            return false;
        }

        private PsiBuilder.Marker tableLiteral() {
            PsiBuilder.Marker m = b.mark();
            b.advanceLexer();
            if (!at(RBRACE) && !tableEntryAhead()) {   // `{"ye", 123, isDev}` - elements without keys: an array
                elements(RBRACE, "'}'");
                return done(m, ARRAY_LITERAL);
            }
            boolean ok = true;
            while (!at(RBRACE) && !eof()) {
                if (!tableEntry()) { ok = false; break; }
                if (!consume(COMMA)) break;
            }
            if (!ok || !expect(RBRACE, "'}'")) skipTo(RBRACE);
            return done(m, TABLE_LITERAL);
        }

        private boolean tableEntry() {
            PsiBuilder.Marker e = b.mark();
            PsiBuilder.Marker k = b.mark();
            IElementType t = b.getTokenType();
            if (TABLE_KEY_TOKENS.contains(t) || isKeyword(t) && la(1) == COLON) {
                b.advanceLexer();
            } else if (t == LBRACKET) {
                b.advanceLexer();
                if (expression() == null || !expect(RBRACKET, "']'")) skipTo(RBRACKET);
            } else {
                b.error("Expected table key");
                k.drop();
                e.drop();
                return false;
            }
            k.done(TABLE_KEY);
            boolean ok = expect(COLON, "':'") && expression() != null;
            e.done(TABLE_ENTRY);
            return ok;
        }

        private PsiBuilder.Marker newExpression() {
            PsiBuilder.Marker m = b.mark();
            b.advanceLexer();
            if ((isTypeKeyword(b.getTokenType())) && la(1) == LBRACKET) {   // new int[n], new double[]{...}
                typeElement();
                newArrayRest();
                return done(m, NEW_ARRAY_EXPRESSION);
            }
            PsiBuilder.Marker r = b.mark();
            if (!expect(IDENT, "class name")) {
                r.drop();
                m.done(NEW_EXPRESSION);
                return null;
            }
            r.done(REFERENCE_EXPRESSION);
            while (true) {   // new registry.cat(...), new classes[i](...), new java.util.HashMap()
                if (at(DOT)) {
                    r = r.precede();
                    b.advanceLexer();
                    boolean ok = expect(IDENT, "member name");
                    r.done(REFERENCE_EXPRESSION);
                    if (!ok) { m.done(NEW_EXPRESSION); return null; }
                } else if (at(LBRACKET)) {
                    if (!indexThenCall()) {   // new Foo[n]: a Java array
                        newArrayRest();
                        return done(m, NEW_ARRAY_EXPRESSION);
                    }
                    r = r.precede();
                    b.advanceLexer();
                    if (expression() == null || !expect(RBRACKET, "']'")) skipTo(RBRACKET);
                    r.done(INDEX_EXPRESSION);
                } else break;
            }
            if (!at(LPAREN)) {
                b.error("Expected '(' but got '" + tokenText() + "'");
                m.done(NEW_EXPRESSION);
                return null;
            }
            argumentList();
            return done(m, NEW_EXPRESSION);
        }

        /** Parser.scanIndexThenCall: does `[...]` here look like indexing followed by a call? */
        private boolean indexThenCall() {
            int depth = 0;
            for (int k = 0; k < 10000; k++) {
                IElementType u = la(k);
                if (u == null || u == SEMI) return false;
                if (u == LBRACKET) depth++;
                else if (u == RBRACKET && --depth == 0) return la(k + 1) == LPAREN;
            }
            return false;
        }

        /** After `new T`: `[size]` or `[]{items}`. */
        private void newArrayRest() {
            b.advanceLexer();   // '['
            if (consume(RBRACKET)) {
                if (expect(LBRACE, "'{'")) elements(RBRACE, "'}'");
                return;
            }
            if (expression() == null || !expect(RBRACKET, "']'")) skipTo(RBRACKET);
        }

        private PsiBuilder.Marker switchExpression() {
            PsiBuilder.Marker m = b.mark();
            if (!switchHead()) {
                m.done(SWITCH_EXPRESSION);
                return null;
            }
            while (!at(RBRACE) && !eof()) {
                PsiBuilder.Marker br = b.mark();
                if (!caseLabel()) {
                    br.drop();
                    skipTo(RBRACE);
                    m.done(SWITCH_EXPRESSION);
                    return m;
                }
                arrow();
                if (at(LBRACE)) {
                    b.error("switch expression needs a value: 'case 1 -> \"one\";'");
                    block();
                } else if (expression() == null || !expect(SEMI, "';'")) {
                    skipTo(SEMI);
                }
                br.done(SWITCH_BRANCH);
            }
            expect(RBRACE, "'}'");
            return done(m, SWITCH_EXPRESSION);
        }
    }
}
