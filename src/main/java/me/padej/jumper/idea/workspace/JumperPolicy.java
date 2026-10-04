package me.padej.jumper.idea.workspace;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An access policy (`*.jma`) as the language reads it (runtime.Access + security.Policy), with the classes of
 * the IDE's PSI in place of loaded ones. What the file says is read from its calls - `Policy.allowPackage("x")`,
 * `Policy.allowClass("x").denyMethod("m")` - never run. The rules and their order of strength are the
 * language's: an explicit class rule beats a package rule, the longest package wins, a member rule beats a
 * class rule and applies to overrides; escape routes open only by an explicit class rule; class loaders and
 * `java.lang.Class` beyond its safe methods never.
 */
public final class JumperPolicy {
    /** Classes no package rule opens - only an explicit class rule does (Access.ESCAPES). */
    static final String[] ESCAPES = {
            "java.lang.Class", "java.lang.ClassLoader", "java.lang.Runtime", "java.lang.ProcessBuilder",
            "java.lang.Process", "java.lang.ProcessHandle", "java.lang.System", "java.lang.Thread",
            "java.lang.ThreadGroup", "java.lang.Module", "java.lang.ModuleLayer", "java.lang.StackWalker",
            "java.lang.reflect.", "java.lang.invoke.", "java.lang.ref.", "sun.", "jdk.internal.", "jdk.",
            "java.security.", "javax.script.", "me.padej.jumper.",
            "java.util.Timer", "java.util.concurrent.Executors", "java.util.concurrent.ForkJoinPool",
            "java.util.concurrent.ForkJoinTask", "java.util.concurrent.CompletableFuture",
            "java.util.concurrent.ThreadPoolExecutor", "java.util.concurrent.ScheduledThreadPoolExecutor",
            "java.util.concurrent.Executor", "java.util.concurrent.ExecutorService",
            "java.util.ServiceLoader"
    };
    /** What a java.lang.Class can be asked under every policy (Access.CLASS_SAFE). */
    static final Set<String> CLASS_SAFE = Set.of(
            "getName", "getSimpleName", "getTypeName", "getCanonicalName", "getPackageName", "descriptorString",
            "isInstance", "isAssignableFrom", "isInterface", "isArray", "isPrimitive", "isEnum", "isRecord",
            "isAnnotation", "isSealed", "isSynthetic", "isAnonymousClass", "isLocalClass", "isMemberClass",
            "isHidden", "getModifiers", "cast", "getEnumConstants", "toString", "hashCode", "equals");

    private enum Kind { PACKAGE, CLASS, MEMBER }

    private record Rule(boolean allow, Kind kind, String target, String member) {}

    private final Path file;
    private final List<Rule> rules = new ArrayList<>();
    private boolean modules;
    private final List<String> problems = new ArrayList<>();

    private JumperPolicy(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    /** What could not be read: a call with arguments that are not literals, an unknown call. */
    public List<String> problems() {
        return problems;
    }

    public boolean modulesAllowed() {
        return modules;
    }

    // ------------------------------------------------------------------ reading

    private record Cached(long stamp, long at, long size, long mtime, JumperPolicy policy) {}

    private static final Map<Path, Cached> CACHE = new ConcurrentHashMap<>();

    /** The policy of a context (its `access` file), or null when the file runs without one. */
    public static JumperPolicy of(JumperContext ctx) {
        return ctx == null || ctx.access() == null ? null : load(ctx.access());
    }

    /**
     * The policy in a file, read again only when it may have changed: the file system is not asked for every name the
     * editor checks, only after a change the IDE saw ({@link JumperWorkspace#MODIFICATIONS}) or a minute later - then
     * by size and time.
     */
    public static JumperPolicy load(Path jma) {
        long stamp = JumperWorkspace.MODIFICATIONS.getModificationCount(), now = System.currentTimeMillis();
        Cached c = CACHE.get(jma);
        if (c != null && c.stamp == stamp && now - c.at < JumperWorkspace.STALE_MS) return c.policy;
        try {
            var a = Files.readAttributes(jma, java.nio.file.attribute.BasicFileAttributes.class);
            JumperPolicy p = c != null && c.size == a.size() && c.mtime == a.lastModifiedTime().toMillis() ? c.policy
                    : parse(jma, Files.readString(jma));
            if (CACHE.size() > 256) CACHE.clear();
            CACHE.put(jma, new Cached(stamp, now, a.size(), a.lastModifiedTime().toMillis(), p));
            return p;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    public static JumperPolicy parse(Path file, CharSequence text) {
        JumperPolicy p = new JumperPolicy(file);
        for (List<JumperData.Call> chain : JumperData.calls(text, "Policy")) {
            String cls = null;
            for (int i = 0; i < chain.size(); i++) {
                JumperData.Call c = chain.get(i);
                if (c.args() == null) { p.problems.add(c.name() + ": only literal arguments are read"); break; }
                List<Object> a = c.args();
                if (i == 0) {
                    switch (c.name()) {
                        case "allowPackage" -> p.rule(true, Kind.PACKAGE, str(a, 0), null);
                        case "denyPackage" -> p.rule(false, Kind.PACKAGE, str(a, 0), null);
                        case "allowClass" -> { cls = str(a, 0); p.rule(true, Kind.CLASS, cls, null); }
                        case "denyClass" -> { cls = str(a, 0); p.rule(false, Kind.CLASS, cls, null); }
                        case "allowMethod", "allowField" -> p.rule(true, Kind.MEMBER, str(a, 0), str(a, 1));
                        case "denyMethod", "denyField" -> p.rule(false, Kind.MEMBER, str(a, 0), str(a, 1));
                        case "allowModules" -> p.modules = true;
                        case "maxTableSize" -> { }
                        default -> p.problems.add("Policy." + c.name() + ": no such rule");
                    }
                } else if (cls != null) {   // ClassRule: .allowMethod("m") / .denyMethod("m") ...
                    switch (c.name()) {
                        case "allowMethod", "allowField" -> p.rule(true, Kind.MEMBER, cls, str(a, 0));
                        case "denyMethod", "denyField" -> p.rule(false, Kind.MEMBER, cls, str(a, 0));
                        default -> p.problems.add("." + c.name() + ": no such rule of a class");
                    }
                }
            }
        }
        return p;
    }

    private void rule(boolean allow, Kind kind, String target, String member) {
        if (target == null || kind == Kind.MEMBER && member == null) return;
        rules.add(new Rule(allow, kind, target, member));
    }

    private static String str(List<Object> args, int i) {
        return i < args.size() && args.get(i) instanceof String s ? s : null;
    }

    // ------------------------------------------------------------------ checks

    private final Map<String, Boolean> classCache = new ConcurrentHashMap<>();

    /** Whether the script may see the class: import, new, static access, calls on its instances (Access.classAllowed). */
    public boolean classAllowed(PsiClass c) {
        String name = c.getQualifiedName();
        if (name == null) return true;
        if (InheritanceUtil.isInheritor(c, "java.lang.ClassLoader") || name.equals("java.lang.ClassLoader")) return false;
        return classCache.computeIfAbsent(name, this::classAllowedByName);
    }

    private boolean classAllowedByName(String name) {
        Boolean cls = null;
        for (Rule r : rules) if (r.kind == Kind.CLASS && r.target.equals(name)) cls = cls == null ? r.allow : (cls && r.allow);
        if (cls != null) return cls;
        if (isEscape(name)) return false;
        String best = null;
        boolean allow = false;
        for (Rule r : rules) {
            if (r.kind != Kind.PACKAGE || !name.startsWith(r.target + ".")) continue;
            if (best == null || r.target.length() > best.length() || (r.target.length() == best.length() && !r.allow)) {
                best = r.target;
                allow = r.allow;
            }
        }
        return best != null && allow;
    }

    private static boolean isEscape(String name) {
        for (String e : ESCAPES) if (e.endsWith(".") ? name.startsWith(e) : name.equals(e)) return true;
        return false;
    }

    /** Visible by name: allowed as a whole, or through an allowed member (Access.visible). */
    public boolean visible(PsiClass c) {
        if (classAllowed(c)) return true;
        String name = c.getQualifiedName();
        if (InheritanceUtil.isInheritor(c, "java.lang.ClassLoader")) return false;
        for (Rule r : rules) if (r.allow && r.kind == Kind.MEMBER && r.target.equals(name)) return true;
        return false;
    }

    /** Whether `member` declared in `declaring` may be used on an object of `runtime` (Access.memberAllowed). */
    public boolean memberAllowed(PsiClass declaring, PsiClass runtime, String member) {
        if (hardDenied(declaring, runtime, member)) return false;
        Boolean m = inheritedRule(runtime, member);
        if (m == null && runtime != declaring) m = inheritedRule(declaring, member);
        if (m != null) return m;
        if (member.equals("getClass") && !classAllowedByName("java.lang.Class")) return false;
        return classAllowed(declaring) || classAllowed(runtime);
    }

    /** Is a method callable on a receiver of class `receiver` (statics are judged by their own class) - Access.filter. */
    public boolean allowed(PsiMethod m, PsiClass receiver) {
        PsiClass declaring = m.getContainingClass();
        if (declaring == null) return true;
        PsiType rt = m.getReturnType();
        PsiClass rc = PsiUtil.resolveClassInType(rt);
        if (rc != null && (InheritanceUtil.isInheritor(rc, "java.lang.ClassLoader") || "java.lang.ClassLoader".equals(rc.getQualifiedName())))
            return false;
        PsiClass runtime = m.hasModifierProperty(PsiModifier.STATIC) ? declaring : receiver;
        return memberAllowed(declaring, runtime, m.getName());
    }

    /** A field read or written (Access.fieldAllowed). */
    public boolean allowed(PsiField f, PsiClass receiver) {
        PsiClass declaring = f.getContainingClass();
        if (declaring == null) return true;
        PsiClass t = PsiUtil.resolveClassInType(f.getType());
        if (t != null && InheritanceUtil.isInheritor(t, "java.lang.ClassLoader")) return false;
        return memberAllowed(declaring, f.hasModifierProperty(PsiModifier.STATIC) ? declaring : receiver, f.getName());
    }

    public boolean allowed(PsiMember m, PsiClass receiver) {
        if (m instanceof PsiMethod pm) return allowed(pm, receiver);
        if (m instanceof PsiField pf) return allowed(pf, receiver);
        return !(m instanceof PsiClass pc) || visible(pc);
    }

    static boolean hardDenied(PsiClass declaring, PsiClass runtime, String member) {
        if (isLoader(declaring) || isLoader(runtime)) return true;
        boolean isClass = "java.lang.Class".equals(declaring.getQualifiedName()) || "java.lang.Class".equals(runtime.getQualifiedName());
        return isClass && !CLASS_SAFE.contains(member);
    }

    private static boolean isLoader(PsiClass c) {
        return "java.lang.ClassLoader".equals(c.getQualifiedName()) || InheritanceUtil.isInheritor(c, "java.lang.ClassLoader");
    }

    /** The member rule of c or of its nearest supertypes that have one; a deny wins within one level. */
    private Boolean inheritedRule(PsiClass c, String member) {
        Boolean out = null;
        List<PsiClass> level = List.of(c);
        Set<String> seen = new HashSet<>();
        for (int depth = 0; !level.isEmpty() && out == null && depth < 32; depth++) {
            List<PsiClass> next = new ArrayList<>();
            for (PsiClass x : level) {
                String n = x.getQualifiedName();
                if (n == null || !seen.add(n)) continue;
                Boolean r = memberRule(n, member);
                if (r != null) out = out == null ? r : (out && r);
                for (PsiClass s : x.getSupers()) next.add(s);
            }
            level = next;
        }
        return out;
    }

    private Boolean memberRule(String cls, String member) {
        Boolean out = null;
        for (Rule r : rules) if (r.kind == Kind.MEMBER && r.member.equals(member) && r.target.equals(cls)) out = out == null ? r.allow : (out && r.allow);
        return out;
    }

    /** Package and class names the policy mentions: what completion inside a `.jma` string can offer is elsewhere. */
    public List<String> mentionedPackages() {
        List<String> out = new ArrayList<>();
        for (Rule r : rules) if (r.kind == Kind.PACKAGE && r.allow) out.add(r.target);
        return out;
    }
}
