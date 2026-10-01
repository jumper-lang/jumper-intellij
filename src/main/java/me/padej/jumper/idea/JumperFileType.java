package me.padej.jumper.idea;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

/**
 * The three kinds of Jumper files, one language (as ScalaFileType and SbtFileType share Scala):
 * <ul>
 *   <li>.jmp - a script: runs under its host's access policy, with the host's globals and the server's classes;</li>
 *   <li>.jmc - a config: a subset of the language (no loops, functions, classes, imports, Java);</li>
 *   <li>.jma - an access policy: a script that sees only {@code Policy}.</li>
 * </ul>
 */
public final class JumperFileType extends LanguageFileType {
    public enum Kind { SCRIPT, CONFIG, POLICY }

    public static final JumperFileType SCRIPT = new JumperFileType(Kind.SCRIPT, "Jumper", "Jumper script", "jmp", JumperIcons.SCRIPT);
    public static final JumperFileType CONFIG = new JumperFileType(Kind.CONFIG, "Jumper Config", "Jumper config", "jmc", JumperIcons.CONFIG);
    public static final JumperFileType POLICY = new JumperFileType(Kind.POLICY, "Jumper Access Policy", "Jumper access policy", "jma", JumperIcons.POLICY);

    private final Kind kind;
    private final String name, description, extension;
    private final Icon icon;

    private JumperFileType(Kind kind, String name, String description, String extension, Icon icon) {
        super(JumperLanguage.INSTANCE, kind != Kind.SCRIPT);   // the script type is the language's own
        this.kind = kind;
        this.name = name;
        this.description = description;
        this.extension = extension;
        this.icon = icon;
    }

    public Kind kind() {
        return kind;
    }

    /** The kind of a file by its extension; a file of another type (or no file: a fragment) is a script. */
    public static Kind kindOf(@Nullable VirtualFile file) {
        if (file == null) return Kind.SCRIPT;
        String ext = file.getExtension();
        if ("jmc".equals(ext)) return Kind.CONFIG;
        if ("jma".equals(ext)) return Kind.POLICY;
        return Kind.SCRIPT;
    }

    @Override
    public @NotNull String getName() {
        return name;
    }

    /** Shown in Settings | File Types; must differ between the three (the default is the language's name). */
    @Override
    public @NotNull String getDisplayName() {
        return name;
    }

    @Override
    public @NotNull String getDescription() {
        return description;
    }

    @Override
    public @NotNull String getDefaultExtension() {
        return extension;
    }

    @Override
    public Icon getIcon() {
        return icon;
    }
}
