package me.padej.jumper.idea.lang.resolve;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Java as a script sees it (the language's workspace.Members, over the IDE's PSI): classes by name, public
 * methods and fields of a class or of its instances, properties (`obj.name` reads `getName()`/`isName()`).
 * Classes are looked up in the whole project - its libraries, its JDK and the host jars the plugin adds as
 * libraries (JumperLibraryRootsProvider); a decompiled class or a `-sources.jar` is then the platform's.
 * <p>
 * The members a script sees of a class are worked out once per class (a server API class has hundreds of methods
 * with its supertypes, and every name of a script asks) and kept until the code changes.
 */
public final class JmpJava {
    private JmpJava() {}

    public static GlobalSearchScope scope(Project project) {
        return GlobalSearchScope.allScope(project);
    }

    /** The Java PSI of the project, or null while indexes are being built (or where there is no Java support). */
    public static @Nullable JavaPsiFacade facade(Project project) {
        DumbService dumb = project.getService(DumbService.class);
        if (dumb == null || dumb.isDumb()) return null;
        return project.getService(JavaPsiFacade.class);
    }

    public static @Nullable PsiClass findClass(String fqn, PsiElement context) {
        if (fqn == null || fqn.isEmpty()) return null;
        Project project = context.getProject();
        JavaPsiFacade facade = facade(project);
        if (facade == null) return null;
        try {
            return facade.findClass(fqn.replace('$', '.'), scope(project));
        } catch (IndexNotReadyException e) {
            return null;
        }
    }

    public static @Nullable PsiPackage findPackage(String name, PsiElement context) {
        Project project = context.getProject();
        JavaPsiFacade facade = facade(project);
        if (facade == null) return null;
        try {
            PsiPackage p = facade.findPackage(name);
            return p != null && p.getDirectories(scope(project)).length > 0 ? p : null;
        } catch (IndexNotReadyException e) {
            return null;
        }
    }

    /** Is there a JDK to resolve against (java.lang.Object found)? Without one, a class name is not an error. */
    public static boolean available(PsiElement context) {
        return findClass("java.lang.Object", context) != null;
    }

    /** A simple name as the language finds it without an import: a class of java.lang. */
    public static @Nullable PsiClass javaLang(String name, PsiElement context) {
        if (name.isEmpty() || !Character.isUpperCase(name.charAt(0))) return null;
        return findClass("java.lang." + name, context);
    }

    public static boolean isObjectMethod(PsiMethod m) {
        PsiClass c = m.getContainingClass();
        return c != null && "java.lang.Object".equals(c.getQualifiedName());
    }

    public static boolean isPublic(PsiModifierListOwner m) {
        if (m.hasModifierProperty(PsiModifier.PUBLIC)) return true;
        return m instanceof PsiMember mm && mm.getContainingClass() != null && mm.getContainingClass().isInterface()
                && !m.hasModifierProperty(PsiModifier.PRIVATE);
    }

    public static boolean isStatic(PsiModifierListOwner m) {
        return m.hasModifierProperty(PsiModifier.STATIC);
    }

    /** What a script sees of a class, on an instance or on the class itself: worked out once (see the class comment). */
    private record Members(List<PsiMethod> methods, Map<String, List<PsiMethod>> byName, List<PsiField> fields,
                           Map<String, PsiField> fieldByName) {}

    private static final Key<CachedValue<Members>> INSTANCE_MEMBERS = Key.create("jumper.instanceMembers");
    private static final Key<CachedValue<Members>> STATIC_MEMBERS = Key.create("jumper.staticMembers");

    private static Members members(PsiClass c, boolean statics) {
        return CachedValuesManager.getCachedValue(c, statics ? STATIC_MEMBERS : INSTANCE_MEMBERS,
                () -> CachedValueProvider.Result.create(computeMembers(c, statics), PsiModificationTracker.MODIFICATION_COUNT));
    }

    private static Members computeMembers(PsiClass c, boolean statics) {
        Map<String, PsiMethod> bySignature = new LinkedHashMap<>();
        List<PsiMethod> all = new ArrayList<>(List.of(c.getAllMethods()));
        if (c.isInterface() && !statics) {   // an object behind an interface is still an Object (toString, equals...)
            PsiClass object = findClass(CommonClassNames.JAVA_LANG_OBJECT, c);
            if (object != null) all.addAll(List.of(object.getMethods()));
        }
        for (PsiMethod m : all) {
            if (m.isConstructor() || !isPublic(m)) continue;
            if (statics && !isStatic(m)) continue;
            bySignature.putIfAbsent(signatureKey(m), m);
        }
        List<PsiMethod> methods = new ArrayList<>(bySignature.values());
        methods.sort((a, b) -> a.getName().equals(b.getName())
                ? Integer.compare(a.getParameterList().getParametersCount(), b.getParameterList().getParametersCount())
                : a.getName().compareTo(b.getName()));
        Map<String, List<PsiMethod>> byName = new HashMap<>();
        for (PsiMethod m : methods) byName.computeIfAbsent(m.getName(), k -> new ArrayList<>()).add(m);
        byName.replaceAll((k, v) -> List.copyOf(v));
        List<PsiField> fields = new ArrayList<>();
        Map<String, PsiField> fieldByName = new HashMap<>();
        for (PsiField f : c.getAllFields()) {
            if (!isPublic(f) || statics && !isStatic(f)) continue;
            fields.add(f);
            fieldByName.putIfAbsent(f.getName(), f);
        }
        return new Members(List.copyOf(methods), byName, List.copyOf(fields), fieldByName);
    }

    /** Public methods callable on an instance (statics = false: all of them) or on the class (statics = true), by name. */
    public static List<PsiMethod> methods(PsiClass c, boolean statics) {
        return members(c, statics).methods();
    }

    private static String signatureKey(PsiMethod m) {
        StringBuilder sb = new StringBuilder(m.getName()).append('(');
        for (PsiParameter p : m.getParameterList().getParameters()) sb.append(p.getType().getCanonicalText()).append(',');
        return sb.toString();
    }

    public static List<PsiMethod> methodsNamed(PsiClass c, boolean statics, String name) {
        return members(c, statics).byName().getOrDefault(name, Collections.emptyList());
    }

    /** The overloads that take n arguments (a varargs one: n >= its fixed ones) - MemberCheck.arity. */
    public static List<PsiMethod> arity(List<PsiMethod> ms, int n) {
        List<PsiMethod> out = new ArrayList<>();
        for (PsiMethod m : ms) {
            int k = m.getParameterList().getParametersCount();
            if (k == n || m.isVarArgs() && n >= k - 1) out.add(m);
        }
        return out;
    }

    public static List<PsiField> fields(PsiClass c, boolean statics) {
        return members(c, statics).fields();
    }

    public static @Nullable PsiField field(PsiClass c, boolean statics, String name) {
        return members(c, statics).fieldByName().get(name);
    }

    /** `obj.name` read as a value when there is no field: `getName()` or `isName()` with no parameters. */
    public static @Nullable PsiMethod getter(PsiClass c, String name) {
        if (name.isEmpty()) return null;
        String cap = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        for (String n : new String[] {"get" + cap, "is" + cap}) {
            for (PsiMethod m : methodsNamed(c, false, n)) if (m.getParameterList().getParametersCount() == 0 && !isStatic(m)) return m;
        }
        return null;
    }

    /** A nested class `Outer.Inner` read through the outer class. */
    public static @Nullable PsiClass inner(PsiClass c, String name) {
        PsiClass k = c.findInnerClassByName(name, true);
        return k != null && isPublic(k) ? k : null;
    }

    /** The class of a Java type, when members can be looked up on it: not a primitive, an array or a type variable. */
    public static @Nullable PsiClass classOf(@Nullable PsiType t) {
        if (!(t instanceof PsiClassType ct)) return null;
        PsiClass c = ct.resolve();
        return c == null || c instanceof PsiTypeParameter ? null : c;
    }

    /** `int size()`, `static double max(double, double)` - Members.signature. */
    public static String signature(PsiMethod m) {
        StringBuilder sb = new StringBuilder();
        if (isStatic(m)) sb.append("static ");
        sb.append(m.getReturnType() == null ? "" : m.getReturnType().getPresentableText() + " ").append(m.getName()).append('(');
        PsiParameter[] ps = m.getParameterList().getParameters();
        for (int i = 0; i < ps.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(ps[i].getType().getPresentableText());
        }
        return sb.append(')').toString();
    }

    public static String signature(PsiField f) {
        return (isStatic(f) ? "static " : "") + f.getType().getPresentableText() + " " + f.getName();
    }
}
