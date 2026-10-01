package me.padej.jumper.idea.lang.lexer;

import com.intellij.psi.tree.IElementType;
import me.padej.jumper.idea.JumperLanguage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/** A token of Jumper; its debug name is how the language writes it (`dyn`, `+=`) or the token kind (IDENT). */
public final class JumperTokenType extends IElementType {
    public JumperTokenType(@NotNull @NonNls String debugName) {
        super(debugName, JumperLanguage.INSTANCE);
    }

    @Override
    public String toString() {
        return "JumperToken." + super.toString();
    }
}
