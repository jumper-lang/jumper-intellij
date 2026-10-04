package me.padej.jumper.idea.lang.lexer;

import com.intellij.psi.tree.IElementType;
import me.padej.jumper.idea.JumperLanguage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/** A token of Jumper; its debug name is how the language writes it (`dyn`, `+=`) or the token kind (IDENT). */
public final class JumperTokenType extends IElementType {
    private final String text;

    public JumperTokenType(@NotNull @NonNls String debugName) {
        super(debugName, JumperLanguage.INSTANCE);
        this.text = debugName;
    }

    /** How the language writes it (`dyn`, `+=`), or the token kind (IDENT). */
    public String text() {
        return text;
    }

    @Override
    public String toString() {
        return "JumperToken." + super.toString();
    }
}
