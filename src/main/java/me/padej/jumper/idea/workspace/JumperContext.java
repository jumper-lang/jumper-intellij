package me.padej.jumper.idea.workspace;

import me.padej.jumper.idea.JumperFileType;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * What surrounds one Jumper file (the language's workspace.FileContext): its kind, the host that owns it, the
 * policy it runs under, the names the host defines, the jars whose classes it can name.
 *
 * @param globals   names the host defines for its scripts -> type (a Java class name, "function", "dyn")
 * @param jars      the jars the file can name classes of: the server's and its host's, not other hosts' - read from
 *                  the disk when asked (a walk of the server folder), see {@link #classpath()}
 * @param notes     how the context was found when it is not certain - shown as hints, not errors
 */
public record JumperContext(JumperFileType.Kind kind, Path file, Path root, HostDescriptor host, Path access,
                            Map<String, String> globals, Supplier<List<Path>> jars, List<String> notes) {
    public static JumperContext empty(JumperFileType.Kind kind) {
        return new JumperContext(kind, null, null, null, null, Map.of(), List::of, List.of());
    }

    /** The jars of the server the file can name classes of. Walks the server folder: call it off the UI thread. */
    public List<Path> classpath() {
        return jars.get();
    }

    /** Interfaces whose methods a script of its host implements; empty for other files. */
    public List<String> hookTypes() {
        return kind == JumperFileType.Kind.SCRIPT && host != null ? host.hookTypes() : List.of();
    }

    /** Functions of a script its host calls one by one: name -> "a.Class#method"; empty for other files. */
    public Map<String, String> hooks() {
        return kind == JumperFileType.Kind.SCRIPT && host != null ? host.hooks() : Map.of();
    }

    public String hostName() {
        return host != null ? host.name() : "the host";
    }
}
