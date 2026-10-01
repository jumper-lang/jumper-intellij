package me.padej.jumper.idea;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import me.padej.jumper.idea.lang.psi.JmpClass;
import me.padej.jumper.idea.lang.psi.JmpFunction;
import me.padej.jumper.idea.lang.psi.JmpNamedElement;
import me.padej.jumper.idea.lang.psi.JmpParameter;
import me.padej.jumper.idea.lang.psi.JmpVariable;

import java.util.List;

/**
 * The plugin in a light IDE (no JDK: what needs Java is covered by the language's own tests): the language's
 * diagnostics with their messages, the scoping of names, completion, rename.
 */
public class JumperHighlightingTest extends BasePlatformTestCase {
    private void check(String code) {
        myFixture.configureByText(JumperFileType.SCRIPT, code);
        myFixture.checkHighlighting(true, false, false);
    }

    public void testParserChecks() {
        check("""
                dyn a = 1;
                dyn <error descr="Variable 'a' is already declared in this scope">a</error> = 2;
                void f(x, <error descr="Variable 'x' is already declared in this scope">x</error>) { <error descr="Cannot return a value from a void function">return</error> 1; }
                class C { C(x) {} int v; int <error descr="Field 'v' is already declared in C">v</error>; }
                dyn c = <error descr="Constructor C expects 1 argument, got 2">new</error> C(1, 2);
                dyn t = <error descr="'this' is only valid inside a class">this</error>;
                dyn q = true <error descr="Cannot apply '+' to boolean">+</error> 1;
                int <error descr="Cannot assign string to int">n</error> = "s";
                <error descr="'break' outside of a loop">break</error>;
                dyn u = <error descr="Undefined variable 'nope'">nope</error>;
                while (true) { break; }
                """);
    }

    public void testLexicalErrors() {
        check("""
                dyn a = <error descr="Hex literal without digits: 0x">0x</error>;
                dyn b = <error descr="Exponent without digits: 1e">1e</error>;
                dyn s = "a<error descr="Bad escape \\q">\\q</error>";
                int m = -2147483648;
                """);
    }

    public void testConfig() {
        myFixture.configureByText(JumperFileType.CONFIG, """
                int port = 25565;
                <error descr="A loop is not allowed in a config (.jmc)">while</error> (true) { }
                dyn <error descr="A function is not allowed in a config (.jmc)">f</error>() { return 1; }
                """);
        myFixture.checkHighlighting(true, false, false);
    }

    private PsiElement resolveAtCaret(String code) {
        myFixture.configureByText(JumperFileType.SCRIPT, code);
        PsiReference ref = myFixture.getFile().findReferenceAt(myFixture.getCaretOffset());
        assertNotNull("no reference at the caret", ref);
        return ref.resolve();
    }

    public void testScopes() {
        // a variable is visible after its declarator: `dyn x = x;` reads the outer x
        PsiElement t = resolveAtCaret("dyn x = 1;\nvoid f() { dyn x = <caret>x; }");
        assertTrue(t instanceof JmpVariable v && v.getTextOffset() < 10);
        // functions are hoisted
        assertTrue(resolveAtCaret("<caret>g();\nvoid g() { }") instanceof JmpFunction);
        // a parameter shadows, a member of the class is found from a method
        assertTrue(resolveAtCaret("dyn y = 1;\nvoid f(y) { println(<caret>y); }") instanceof JmpParameter);
        PsiElement field = resolveAtCaret("class P { int n; int get() { return <caret>n; } }");
        assertTrue(field instanceof JmpVariable v && v.isField());
        assertTrue(resolveAtCaret("class P { }\nP p = new <caret>P();") instanceof JmpClass);
        // lambda parameters
        assertTrue(resolveAtCaret("dyn f = (a, b) -> <caret>a + b;") instanceof JmpParameter);
    }

    public void testCompletion() {
        myFixture.configureByText(JumperFileType.SCRIPT, "dyn visits = 1;\nvoid f() { vi<caret> }");
        myFixture.completeBasic();
        List<String> items = myFixture.getLookupElementStrings();
        assertTrue(items == null ? "completed in place" : String.valueOf(items),
                items == null ? myFixture.getEditor().getDocument().getText().contains("visits }") : items.contains("visits"));
    }

    public void testRename() {
        myFixture.configureByText(JumperFileType.SCRIPT, "void f() { dyn <caret>count = 1; count++; println(count); }");
        myFixture.renameElementAtCaret("total");
        myFixture.checkResult("void f() { dyn total = 1; total++; println(total); }");
    }

    public void testStructure() {
        myFixture.configureByText(JumperFileType.SCRIPT, "class P { int x; void m() { } }\nvoid f() { }\ndyn v = 1;");
        List<String> names = new java.util.ArrayList<>();
        for (PsiElement c : myFixture.getFile().getChildren())
            if (c instanceof JmpNamedElement n) names.add(n.getName());
        assertEquals(List.of("P", "f"), names);
    }
}
