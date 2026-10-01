package me.padej.jumper.idea;

import com.intellij.lang.Language;
import com.intellij.lang.jvm.JvmLanguage;

/**
 * The Jumper language: scripts (.jmp), configs (.jmc) and access policies (.jma) are one language - the
 * parser is the same, the kind of a file is its extension (see {@link JumperFileType}).
 * Like ScalaLanguage: a JVM language, so the platform treats its references to Java as JVM code.
 */
public final class JumperLanguage extends Language implements JvmLanguage {
    public static final JumperLanguage INSTANCE = new JumperLanguage();

    private JumperLanguage() {
        super("Jumper");
    }

    @Override
    public String getDisplayName() {
        return "Jumper";
    }
}
