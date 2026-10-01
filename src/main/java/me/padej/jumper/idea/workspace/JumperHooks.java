package me.padej.jumper.idea.workspace;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import me.padej.jumper.idea.lang.resolve.JmpJava;
import com.intellij.psi.PsiElement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Functions a script declares for its host to call - `void onJoin(dyn p) { ... }` - as the host descriptor names
 * them (the language's workspace.Hooks): an interface (each of its methods is a function a script may
 * implement), or a table `{ onJoin: "a.Class#method" }`.
 */
public final class JumperHooks {
    private JumperHooks() {}

    /** The class and the methods a script function of that name implements; `methods` empty when only a class is named. */
    public record Hook(String name, PsiClass owner, List<PsiMethod> methods) {
        /** The overload taking `arity` arguments, else the first; null if only a class is named. */
        public PsiMethod method(int arity) {
            for (PsiMethod m : methods) if (m.getParameterList().getParametersCount() == arity) return m;
            return methods.isEmpty() ? null : methods.get(0);
        }
    }

    /** The hook `name` in the context of a file, or null when its host does not call such a function. */
    public static Hook find(JumperContext ctx, String name, PsiElement context) {
        String spec = ctx.hooks().get(name);
        if (spec != null) {
            int hash = spec.indexOf('#');
            PsiClass c = JmpJava.findClass((hash < 0 ? spec : spec.substring(0, hash)).strip(), context);
            if (c == null) return null;
            String m = hash < 0 ? "" : spec.substring(hash + 1).replaceAll("\\(.*", "").strip();
            return new Hook(name, c, m.isEmpty() ? List.of() : named(c, m));
        }
        for (String type : ctx.hookTypes()) {
            PsiClass c = JmpJava.findClass(type.strip(), context);
            if (c == null) continue;
            List<PsiMethod> ms = named(c, name);
            if (!ms.isEmpty()) return new Hook(name, c, ms);
        }
        return null;
    }

    /** Every hook name the host calls: the interfaces' methods, then the table's names. */
    public static List<String> names(JumperContext ctx, PsiElement context) {
        List<String> out = new ArrayList<>();
        for (String type : ctx.hookTypes()) {
            PsiClass c = JmpJava.findClass(type.strip(), context);
            if (c == null) continue;
            List<PsiMethod> ms = new ArrayList<>(List.of(c.getAllMethods()));
            ms.sort(Comparator.comparing(PsiMethod::getName));
            for (PsiMethod m : ms) {
                if (m.hasModifierProperty(PsiModifier.STATIC) || JmpJava.isObjectMethod(m)) continue;
                if (!out.contains(m.getName())) out.add(m.getName());
            }
        }
        for (String n : ctx.hooks().keySet()) if (!out.contains(n)) out.add(n);
        return out;
    }

    /** `void onJoin(ScriptPlayer player)`. */
    public static String signature(PsiMethod m) {
        StringBuilder sb = new StringBuilder(m.getReturnType() == null ? "" : m.getReturnType().getPresentableText() + " ")
                .append(m.getName()).append('(');
        PsiParameter[] ps = m.getParameterList().getParameters();
        for (int i = 0; i < ps.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(ps[i].getType().getPresentableText()).append(' ').append(ps[i].getName());
        }
        return sb.append(')').toString();
    }

    private static List<PsiMethod> named(PsiClass c, String name) {
        List<PsiMethod> out = new ArrayList<>();
        for (PsiMethod m : c.findMethodsByName(name, true))
            if (!m.hasModifierProperty(PsiModifier.STATIC) && !JmpJava.isObjectMethod(m)) out.add(m);
        out.sort(Comparator.comparingInt(m -> m.getParameterList().getParametersCount()));
        return out;
    }
}
