package me.padej.jumper.idea.editor;

import com.intellij.lang.cacheBuilder.DefaultWordsScanner;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.TokenSet;
import me.padej.jumper.idea.lang.lexer.JumperLexer;
import me.padej.jumper.idea.lang.lexer.JumperTokenTypes;
import me.padej.jumper.idea.lang.psi.JmpNamedElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Find usages of a declaration of a script - and, through the words index built with this scanner, of a Java
 * class or method a script uses: the IDE finds `server.world()` in .jmp files when asked about World.
 */
public final class JumperFindUsagesProvider implements FindUsagesProvider {
    @Override
    public @Nullable WordsScanner getWordsScanner() {
        return new DefaultWordsScanner(new JumperLexer(), TokenSet.create(JumperTokenTypes.IDENT), JumperTokenTypes.COMMENTS,
                JumperTokenTypes.STRINGS);
    }

    @Override
    public boolean canFindUsagesFor(@NotNull PsiElement element) {
        return element instanceof JmpNamedElement;
    }

    @Override
    public @Nullable String getHelpId(@NotNull PsiElement element) {
        return null;
    }

    @Override
    public @NotNull String getType(@NotNull PsiElement element) {
        return element instanceof JmpNamedElement n ? n.kindName() : "";
    }

    @Override
    public @NotNull String getDescriptiveName(@NotNull PsiElement element) {
        return element instanceof JmpNamedElement n && n.getName() != null ? n.getName() : "";
    }

    @Override
    public @NotNull String getNodeText(@NotNull PsiElement element, boolean useFullName) {
        return getDescriptiveName(element);
    }
}
