package me.padej.jumper.idea.lang.resolve;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import me.padej.jumper.idea.lang.psi.*;
import me.padej.jumper.idea.workspace.JumperContext;
import me.padej.jumper.idea.workspace.JumperWorkspace;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static me.padej.jumper.idea.lang.parser.JumperElementTypes.*;

/**
 * The names visible at a place, innermost first - the scoping of the language (lsp.Symbols, Parser.variable):
 * <ul>
 *   <li>a block (and the file) is a scope: its functions and classes exist from its start (hoisted), a module
 *       it imports (`import "m.jmp"`) too; a variable from the end of its declarator (`dyn x = x;` reads the outer
 *       x); a Java `import a.b.C;` from the import on;</li>
 *   <li>a function's parameters in its body, a lambda's in its body, `catch (e)` in its block, a `for`'s
 *       variables in the loop, a for-each variable in the body; the branches of a switch share one scope;</li>
 *   <li>in a class body: the class's members (and those of the script classes it extends) - after the method's
 *       own locals, before the variables outside the class;</li>
 *   <li>then what the file does not declare: the host's globals, the built-ins, `Policy` in a .jma.</li>
 * </ul>
 * Java classes by simple name (imports, java.lang) and packages are looked up by {@link JmpResolver} by name.
 * <p>
 * What a block declares is listed once per block, by name, and kept until the code changes: resolving a name looks
 * at the declarations of that name only, not at every statement of every block around it.
 */
public final class JmpScopes {
    private JmpScopes() {}

    public interface Processor {
        /** @return false to stop */
        boolean process(String name, PsiElement target);
    }

    /** Every name visible at `place`, innermost first (a name may come twice: the first one wins). */
    public static void processDeclarations(PsiElement place, Processor p) {
        processDeclarations(place, null, p);
    }

    /**
     * The declarations visible at `place`, innermost first; with a `name`, only those of that name (what resolving
     * a name needs - much less to look at than every name), else all of them.
     */
    public static void processDeclarations(PsiElement place, @Nullable String name, Processor p) {
        int offset = place.getTextRange().getStartOffset();
        PsiElement child = place;
        for (PsiElement parent = place.getParent(); parent != null; child = parent, parent = parent.getParent()) {
            if (parent instanceof JmpBlock || parent instanceof JumperFile) {
                if (!processBlock(parent, offset, name, p)) return;
                if (parent instanceof PsiFile) break;
            } else if (parent instanceof JmpFunction f) {
                if (child instanceof JmpBlock) for (JmpParameter prm : f.getParameters()) if (!named(prm, name, p)) return;
            } else if (parent instanceof JmpLambda l) {
                if (!(child instanceof JmpParameterList)) for (JmpParameter prm : l.getParameters()) if (!named(prm, name, p)) return;
            } else if (parent instanceof JmpClassBody body) {
                for (JmpNamedElement m : body.owner().allMembers()) if (!named(m, name, p)) return;
            } else if (parent instanceof JmpElement e) {
                if (e.is(CATCH_SECTION)) {
                    if (child instanceof JmpBlock) for (JmpParameter prm : e.children(JmpParameter.class)) if (!named(prm, name, p)) return;
                } else if (e.is(FOR_STATEMENT)) {
                    for (PsiElement c = e.getFirstChild(); c != null; c = c.getNextSibling()) {
                        if (c instanceof JmpElement d && d.is(VARIABLE_DECLARATION))
                            for (JmpVariable v : d.children(JmpVariable.class)) if (before(v, offset) && !named(v, name, p)) return;
                    }
                } else if (e.is(FOREACH_STATEMENT)) {
                    if (!(child instanceof JmpVariable) && !(child instanceof JmpTypeElement) && !JmpPsiUtil.isExpression(child))
                        for (JmpVariable v : e.children(JmpVariable.class)) if (!named(v, name, p)) return;
                } else if (e.is(SWITCH_STATEMENT)) {
                    for (PsiElement br = e.getFirstChild(); br != null; br = br.getNextSibling()) {
                        if (!(br instanceof JmpElement b) || !b.is(SWITCH_BRANCH)) continue;
                        for (PsiElement s = b.getFirstChild(); s != null; s = s.getNextSibling()) {
                            if (s instanceof JmpElement d && d.is(VARIABLE_DECLARATION))
                                for (JmpVariable v : d.children(JmpVariable.class)) if (before(v, offset) && !named(v, name, p)) return;
                        }
                    }
                }
            }
        }
        PsiFile file = place.getContainingFile();
        if (file instanceof JumperFile jf) processOutside(jf, place, name, p);
    }

    /** What the file does not declare but a script sees: host globals, built-ins. */
    public static boolean processOutside(JumperFile file, PsiElement context, Processor p) {
        return processOutside(file, context, null, p);
    }

    private static boolean processOutside(JumperFile file, PsiElement context, @Nullable String name, Processor p) {
        JumperContext ctx = JumperWorkspace.contextFor(file);
        if (name != null) {
            String type = ctx.globals().get(name);
            if (type != null && !p.process(name, new JmpSynthetic(JmpSynthetic.Kind.HOST_GLOBAL, name, type, context))) return false;
            List<String> sig = JmpBuiltins.SIGNATURES.get(name);
            if (sig != null && JmpBuiltins.NAMES.contains(name) && !p.process(name, new JmpSynthetic(JmpSynthetic.Kind.BUILTIN, name, sig.get(0), context)))
                return false;
        } else {
            for (Map.Entry<String, String> g : ctx.globals().entrySet())
                if (!p.process(g.getKey(), new JmpSynthetic(JmpSynthetic.Kind.HOST_GLOBAL, g.getKey(), g.getValue(), context))) return false;
            for (String b : JmpBuiltins.NAMES)
                if (!p.process(b, new JmpSynthetic(JmpSynthetic.Kind.BUILTIN, b, JmpBuiltins.SIGNATURES.get(b).get(0), context))) return false;
        }
        if (file.isPolicy() && (name == null || name.equals("Policy"))) {
            PsiClass policy = JmpJava.findClass("me.padej.jumper.security.Policy", context);
            if (policy != null && !p.process("Policy", policy)) return false;
        }
        return true;
    }

    private static boolean before(JmpVariable v, int offset) {
        return v.getTextRange().getEndOffset() <= offset;
    }

    private static boolean named(JmpNamedElement e, @Nullable String name, Processor p) {
        String n = e.getName();
        return n == null || name != null && !name.equals(n) || p.process(n, e);
    }

    // ------------------------------------------------------------------ what a block declares

    /**
     * A declaration of a block: a function or a class (visible in the whole block), a variable (after its
     * declarator), a Java import (after it), or a module import (its names, in the whole block).
     */
    private record Decl(@Nullable String name, PsiElement element, int visibleFrom, int order) {}

    /** The declarations of a block in order, and by name; the module imports apply to every name. */
    private record BlockIndex(List<Decl> all, Map<String, List<Decl>> byName, List<Decl> modules) {}

    private static final Key<CachedValue<BlockIndex>> BLOCK_INDEX = Key.create("jumper.blockDeclarations");

    private static BlockIndex index(PsiElement block) {
        return CachedValuesManager.getCachedValue(block, BLOCK_INDEX,
                () -> CachedValueProvider.Result.create(computeIndex(block), PsiModificationTracker.MODIFICATION_COUNT));
    }

    private static BlockIndex computeIndex(PsiElement block) {
        List<Decl> all = new ArrayList<>();
        for (PsiElement c = block.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c instanceof JmpFunction || c instanceof JmpClass) {
                all.add(new Decl(((JmpNamedElement) c).getName(), c, -1, all.size()));
            } else if (c instanceof JmpElement e && e.is(VARIABLE_DECLARATION)) {
                for (JmpVariable v : e.children(JmpVariable.class))
                    all.add(new Decl(v.getName(), v, v.getTextRange().getEndOffset(), all.size()));
            } else if (c instanceof JmpModuleImport mi) {
                all.add(new Decl(null, mi, -1, all.size()));
            } else if (c instanceof JmpImportStatement imp && imp.getSimpleName() != null) {
                all.add(new Decl(imp.getSimpleName(), imp, imp.getTextRange().getEndOffset(), all.size()));
            }
        }
        Map<String, List<Decl>> byName = new HashMap<>();
        List<Decl> modules = new ArrayList<>();
        for (Decl d : all) {
            if (d.name() != null) byName.computeIfAbsent(d.name(), k -> new ArrayList<>(1)).add(d);
            else if (d.element() instanceof JmpModuleImport) modules.add(d);
        }
        return new BlockIndex(List.copyOf(all), byName, List.copyOf(modules));
    }

    /** The declarations of a block (or the file) seen at `offset` - of one name, or all. */
    private static boolean processBlock(PsiElement block, int offset, @Nullable String name, Processor p) {
        BlockIndex idx = index(block);
        List<Decl> decls;
        if (name == null) decls = idx.all();
        else {
            List<Decl> named = idx.byName().getOrDefault(name, List.of());
            if (idx.modules().isEmpty()) decls = named;
            else {   // in the order of the block: of two, the first wins
                decls = new ArrayList<>(named);
                decls.addAll(idx.modules());
                decls.sort((a, b) -> Integer.compare(a.order(), b.order()));
            }
        }
        for (Decl d : decls) {
            if (d.element() instanceof JmpModuleImport mi) {
                JumperFile m = mi.resolveModule();
                if (m == null || m == block.getContainingFile()) continue;
                BlockIndex mod = index(m);
                for (Decl md : name == null ? mod.all() : mod.byName().getOrDefault(name, List.of()))
                    if (md.element() instanceof JmpNamedElement n && !named(n, name, p)) return false;
            } else if (d.visibleFrom() > offset) {
                continue;   // a variable, an import: not yet
            } else if (d.element() instanceof JmpImportStatement imp) {
                PsiClass cls = importedClass(imp);
                if (cls != null && !p.process(d.name(), cls)) return false;
            } else if (d.element() instanceof JmpNamedElement n && !named(n, name, p)) {
                return false;
            }
        }
        return true;
    }

    /** The class an `import a.b.C;` names, or null. */
    public static PsiClass importedClass(JmpImportStatement imp) {
        String fq = imp.getQualifiedName();
        return fq == null ? null : JmpJava.findClass(fq, imp);
    }

    /** Every Java import of the file by simple name (Parser.fileImports: a type sees imports further down too). */
    public static PsiClass fileImport(JumperFile file, String simple) {
        String fq = fileImports(file).get(simple);
        return fq == null ? null : JmpJava.findClass(fq, file);
    }

    /** Simple name -> qualified name of every `import a.b.C;` of the file, anywhere in it (kept until the file changes). */
    public static Map<String, String> fileImports(JumperFile file) {
        return com.intellij.psi.util.CachedValuesManager.getCachedValue(file, () -> {
            Map<String, String> out = new java.util.LinkedHashMap<>();
            com.intellij.psi.util.PsiTreeUtil.processElements(file, e -> {
                if (e instanceof JmpImportStatement i && i.getSimpleName() != null && i.getQualifiedName() != null)
                    out.putIfAbsent(i.getSimpleName(), i.getQualifiedName());
                return true;
            });
            return com.intellij.psi.util.CachedValueProvider.Result.create(out, com.intellij.psi.util.PsiModificationTracker.MODIFICATION_COUNT);
        });
    }

    /** The names visible at a place, once each (for completion). */
    public static Set<String> names(PsiElement place) {
        Set<String> out = new HashSet<>();
        processDeclarations(place, (n, t) -> { out.add(n); return true; });
        return out;
    }
}
