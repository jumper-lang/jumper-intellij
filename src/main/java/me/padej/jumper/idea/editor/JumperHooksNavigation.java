package me.padej.jumper.idea.editor;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import me.padej.jumper.idea.lang.lexer.JumperTokenTypes;
import me.padej.jumper.idea.lang.psi.JmpFunction;
import me.padej.jumper.idea.lang.psi.JmpPsiUtil;
import me.padej.jumper.idea.lang.psi.JumperFile;
import me.padej.jumper.idea.workspace.JumperHooks;
import me.padej.jumper.idea.workspace.JumperWorkspace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * A function a script declares for its host to call (`void onJoin(dyn p)`) is an implementation of the host's
 * API method (workspace.Hooks): a gutter icon shows it, and Go to Declaration on its name opens the method -
 * as the language server's definition does on the declaration.
 */
public final class JumperHooksNavigation {
    private JumperHooksNavigation() {}

    /** The hook the function whose name is `leaf` implements, or null. */
    static @Nullable JumperHooks.Hook hookOf(PsiElement leaf) {
        if (leaf.getNode() == null || leaf.getNode().getElementType() != JumperTokenTypes.IDENT) return null;
        if (!(leaf.getParent() instanceof JmpFunction f) || f.getNameIdentifier() != leaf || !JmpPsiUtil.isTopLevel(f)) return null;
        if (!(f.getContainingFile() instanceof JumperFile file) || f.getName() == null) return null;
        return JumperHooks.find(JumperWorkspace.contextFor(file), f.getName(), f);
    }

    public static final class LineMarkers extends RelatedItemLineMarkerProvider {
        @Override
        protected void collectNavigationMarkers(@NotNull PsiElement element, @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
            JumperHooks.Hook h = hookOf(element);
            if (h == null) return;
            JmpFunction f = (JmpFunction) element.getParent();
            PsiMethod m = h.method(f.getParameters().size());
            PsiElement target = m != null ? m : h.owner();
            result.add(NavigationGutterIconBuilder.create(AllIcons.Gutter.ImplementingMethod)
                    .setTargets(target)
                    .setTooltipText("Implements " + h.owner().getName() + (m == null ? "" : "." + m.getName()) + " (called by the host)")
                    .createLineMarkerInfo(element));
        }
    }

    public static final class GotoHook implements GotoDeclarationHandler {
        @Override
        public PsiElement @Nullable [] getGotoDeclarationTargets(@Nullable PsiElement source, int offset, Editor editor) {
            if (source == null) return null;
            JumperHooks.Hook h = hookOf(source);
            if (h == null) return null;
            PsiMethod m = h.method(((JmpFunction) source.getParent()).getParameters().size());
            return new PsiElement[] {m != null ? m : h.owner()};
        }
    }
}
