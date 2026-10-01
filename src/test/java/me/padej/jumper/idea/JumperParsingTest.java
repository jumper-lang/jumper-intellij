package me.padej.jumper.idea;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.testFramework.ParsingTestCase;
import me.padej.jumper.idea.lang.parser.JumperParserDefinition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

/**
 * The parser against the language: every test script of the language and of its example parses without
 * an error and the tree covers the text; broken code gives the language's messages, one per mistake; mutated
 * files never break the parser.
 */
public class JumperParsingTest extends ParsingTestCase {
    public JumperParsingTest() {
        super("", "jmp", new JumperParserDefinition());
    }

    @Override
    protected String getTestDataPath() {
        return ".";
    }

    /**
     * The language's own test scripts and the scripts of its embedding example, as they were when the parser was
     * ported (jumper-lang/jumper: lang/src/test/resources/scripts, example/server): a copy in
     * src/test/resources/corpus. A new construct of the language comes with its script here.
     */
    private static final Path CORPUS = Path.of("src", "test", "resources", "corpus");

    private static List<Path> corpus() throws IOException {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> s = Files.walk(CORPUS)) {
            s.filter(f -> f.toString().matches(".*\\.(jmp|jmc|jma)")).sorted().forEach(out::add);
        }
        return out;
    }

    private List<String> errors(String name, String text) {
        PsiFile f = createFile(name, text);
        List<String> out = new ArrayList<>();
        StringBuilder leaves = new StringBuilder();
        f.accept(new PsiRecursiveElementWalkingVisitor() {
            @Override
            public void visitElement(PsiElement e) {
                if (e instanceof PsiErrorElement err) out.add(err.getErrorDescription());
                if (e.getFirstChild() == null) leaves.append(e.getText());
                super.visitElement(e);
            }
        });
        assertEquals("the tree covers the text", text, leaves.toString());
        return out;
    }

    public void testLanguageCorpusParses() throws IOException {
        List<Path> files = corpus();
        assertFalse("no corpus found under " + CORPUS.toAbsolutePath(), files.isEmpty());
        for (Path p : files) {
            String text = Files.readString(p).replace("\r\n", "\n");
            // welcome.jmp of the example carries deliberate mistakes for the tools to show
            if (p.getFileName().toString().equals("welcome.jmp")) continue;
            assertEquals(p.toString(), List.of(), errors(p.getFileName().toString(), text));
        }
    }

    public void testMessagesOfTheLanguage() {
        assertEquals(List.of("Unexpected token ';'"), errors("a.jmp", "dyn a = 1 +;\ndyn b = 2;"));
        assertEquals(List.of("Expected ';' but got 'dyn'"), errors("a.jmp", "dyn a = 1\ndyn b = 2;"));
        assertEquals(List.of("'var' is not a keyword in Jumper: a dynamically typed variable is 'dyn name = ...' (a typed one: int, long, double, boolean, String or a class name)"),
                errors("a.jmp", "var x = 1;"));
        assertEquals(List.of("switch uses arrows: 'case 1 -> ...' (form with ':' and fallthrough is not supported)"),
                errors("a.jmp", "switch (x) { case 1: y(); }"));
        assertEquals(List.of("Method 'm' needs a return type: void, dyn, int, ..."), errors("a.jmp", "class C { m() { } }"));
        assertEquals(List.of("'try' without 'catch' or 'finally'"), errors("a.jmp", "try { }\nx();"));
        assertEquals(List.of("Unexpected type keyword 'int'"), errors("a.jmp", "dyn x = (int) y;"));
    }

    public void testOneErrorPerMistake() {
        // a broken statement is skipped to its end: what follows parses as usual
        assertEquals(1, errors("a.jmp", "dyn a = (1 + ;\nvoid f() { return; }\nclass P { int x; }").size());
        assertEquals(1, errors("a.jmp", "for (int i = 0; i < ; i++) { println(i); }\ndyn z = 1;").size());
        assertEquals(1, errors("a.jmp", "f(1, , 2);\ng();").size());
    }

    public void testMutationsNeverBreakTheParser() throws IOException {
        String[] bits = {"(", ")", "{", "}", "[", "]", ";", ",", ".", "->", "=", "\"", "'", "/*", "class ", "dyn ", "new ", "switch ", "case ", ":", "?"};
        for (Path p : corpus()) {
            String src = Files.readString(p).replace("\r\n", "\n");
            Random rnd = new Random(p.getFileName().toString().hashCode());
            for (int i = 0; i < 50; i++) {
                StringBuilder sb = new StringBuilder(src);
                for (int j = 0; j < 1 + rnd.nextInt(4) && sb.length() > 0; j++) {
                    int at = rnd.nextInt(sb.length());
                    if (rnd.nextBoolean()) sb.delete(at, Math.min(sb.length(), at + 1 + rnd.nextInt(8)));
                    else sb.insert(at, bits[rnd.nextInt(bits.length)]);
                }
                errors("m.jmp", sb.toString());   // no exception, the tree covers the text
            }
        }
    }
}
