package me.padej.jumper.idea;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import me.padej.jumper.idea.lang.psi.JmpClass;
import me.padej.jumper.idea.lang.psi.JmpFunction;
import me.padej.jumper.idea.lang.psi.JmpNamedElement;
import me.padej.jumper.idea.lang.psi.JmpParameter;
import me.padej.jumper.idea.lang.psi.JmpVariable;

import java.util.List;

/**
 * The plugin in a light IDE: the language's diagnostics with their messages, the scoping of names, completion,
 * rename; with a few Java classes of the project (there is no JDK) - the types of `dyn` values a for-each and Java
 * generics give.
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

    public void testModulesAndBlocks() {
        myFixture.addFileToProject("util.jmp", "void helper() { }\ndyn shared = 1;");
        PsiElement t = resolveAtCaret("import \"util.jmp\";\n<caret>helper();");
        assertTrue(String.valueOf(t), t instanceof JmpFunction f && "util.jmp".equals(f.getContainingFile().getName()));
        assertTrue(resolveAtCaret("import \"util\";\nvoid f() { println(<caret>shared); }") instanceof JmpVariable);
        // the file's own declaration before the module's of the same name: the first one wins
        PsiElement own = resolveAtCaret("void helper() { }\nimport \"util.jmp\";\n<caret>helper();");
        assertTrue(String.valueOf(own), own instanceof JmpFunction f && !"util.jmp".equals(f.getContainingFile().getName()));
        // an outer block's variable from a nested block; not before its declarator
        assertTrue(resolveAtCaret("void f() { dyn a = 1; { dyn b = <caret>a; } }") instanceof JmpVariable);
        assertNull(resolveAtCaret("void f() { println(<caret>later); dyn later = 1; }"));
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

    // ------------------------------------------------------------------ Java types of dyn values

    /** A server API and the few JDK types a for-each goes through (the light test project has no JDK). */
    private void javaApi() {
        myFixture.addFileToProject("java/lang/Object.java", "package java.lang; public class Object { public String toString() { return null; } }");
        myFixture.addFileToProject("java/lang/String.java", "package java.lang; public final class String { public int length() { return 0; } }");
        myFixture.addFileToProject("java/lang/Iterable.java", "package java.lang; public interface Iterable<T> { java.util.Iterator<T> iterator(); }");
        myFixture.addFileToProject("java/util/Iterator.java", "package java.util; public interface Iterator<E> { boolean hasNext(); E next(); }");
        myFixture.addFileToProject("java/util/Collection.java", "package java.util; public interface Collection<E> extends Iterable<E> { int size(); }");
        myFixture.addFileToProject("java/util/List.java", "package java.util; public interface List<E> extends Collection<E> { E get(int index); }");
        myFixture.addFileToProject("java/util/Map.java", "package java.util; public interface Map<K, V> { V get(Object key); }");
        myFixture.addFileToProject("api/ScriptPlayer.java", "package api; public interface ScriptPlayer { void heal(int hp); String name(); }");
        myFixture.addFileToProject("api/Server.java", """
                package api;
                import java.util.*;
                public interface Server {
                    List<ScriptPlayer> players();
                    Collection<? extends ScriptPlayer> online();
                    ScriptPlayer[] all();
                    Map<String, ScriptPlayer> byName();
                    Iterator<ScriptPlayer> cursor();
                    List<?> anything();
                    List raw();
                }""");
    }

    /** What the name at the caret resolves to, in a script that has `s`, a Server. */
    private PsiElement resolveWithApi(String body) {
        return resolveAtCaret("import api.Server;\nServer s = null;\n" + body);
    }

    private static void assertMethod(PsiElement t, String owner, String name) {
        assertTrue(String.valueOf(t), t instanceof PsiMethod m && m.getName().equals(name)
                && m.getContainingClass() != null && owner.equals(m.getContainingClass().getName()));
    }

    public void testForEachVariableHasTheElementType() {
        javaApi();
        assertMethod(resolveWithApi("for (dyn p : s.players()) p.<caret>heal(3);"), "ScriptPlayer", "heal");
        assertMethod(resolveWithApi("for (dyn p : s.online()) { p.<caret>heal(3); }"), "ScriptPlayer", "heal");
        assertMethod(resolveWithApi("for (dyn p : s.all()) p.<caret>heal(3);"), "ScriptPlayer", "heal");
        assertMethod(resolveWithApi("for (dyn p : s.cursor()) p.<caret>heal(3);"), "ScriptPlayer", "heal");
        // a Map gives its keys, a String its characters (strings)
        assertMethod(resolveWithApi("for (dyn k : s.byName()) k.<caret>length();"), "String", "length");
        assertMethod(resolveWithApi("for (dyn c : \"abc\") c.<caret>length();"), "String", "length");
        // through a dyn variable that holds the list
        assertMethod(resolveWithApi("dyn ps = s.players();\nfor (dyn p : ps) p.<caret>heal(3);"), "ScriptPlayer", "heal");
        // a declared type is the type
        assertMethod(resolveWithApi("import api.ScriptPlayer;\nfor (ScriptPlayer p : s.raw()) p.<caret>heal(3);"), "ScriptPlayer", "heal");
    }

    public void testForEachVariableWithoutAKnownElementType() {
        javaApi();
        // nothing said: `List<?>`, a raw List; a variable the loop gives another value
        assertNull(resolveWithApi("for (dyn p : s.anything()) p.<caret>heal(3);"));
        assertNull(resolveWithApi("for (dyn p : s.raw()) p.<caret>heal(3);"));
        assertNull(resolveWithApi("for (dyn p : s.players()) { p = 5; p.<caret>heal(3); }"));
    }

    public void testTypeArgumentsOfTheReceiver() {
        javaApi();
        assertMethod(resolveWithApi("s.players().get(0).<caret>heal(3);"), "ScriptPlayer", "heal");
        assertMethod(resolveWithApi("s.byName().get(\"x\").<caret>heal(3);"), "ScriptPlayer", "heal");
        assertMethod(resolveWithApi("dyn first = s.players().get(0);\nfirst.<caret>name();"), "ScriptPlayer", "name");
    }

    public void testCompletionOnAForEachVariable() {
        javaApi();
        myFixture.configureByText(JumperFileType.SCRIPT, "import api.Server;\nServer s = null;\nfor (dyn p : s.players()) p.<caret>");
        myFixture.completeBasic();
        List<String> items = myFixture.getLookupElementStrings();
        assertNotNull(items);
        assertTrue(String.valueOf(items), items.contains("heal") && items.contains("name"));
    }
}
