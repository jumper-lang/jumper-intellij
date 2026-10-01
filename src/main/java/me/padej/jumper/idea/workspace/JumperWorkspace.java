package me.padej.jumper.idea.workspace;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import me.padej.jumper.idea.JumperFileType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Finds the context of a Jumper file with no project to go by (the language's workspace.Workspace, the same
 * rules): a server folder where the host - a plugin jar - put scripts, policies and configs wherever it wanted.
 *
 * <ol>
 * <li><b>Hosts describe themselves.</b> From the file's folder up (at most {@value #MAX_UP} levels, not above
 *     the user's home), every jar in a folder or in its direct subfolders is probed for
 *     {@value HostDescriptor#DESCRIPTOR}. The host whose globs match the file owns it; of two, the more specific glob wins.</li>
 * <li><b>By convention</b>, when no descriptor claims it: the policy of a script is the nearest {@code *.jma} up
 *     from it (of several in one folder - the one named like a folder on the way); its folder is the root.</li>
 * <li><b>Classpath:</b> the jars under the root, minus the jars of other hosts.</li>
 * </ol>
 *
 * Read from the disk, cached for {@value #TTL_MS} ms (as the language server re-reads contexts), jar probes by size and time.
 */
public final class JumperWorkspace {
    static final int MAX_UP = 8, MAX_DEPTH = 8, MAX_JARS = 4096, MAX_DIR_ENTRIES = 512;
    static final long TTL_MS = 3000;

    private JumperWorkspace() {}

    private record Probe(long size, long mtime, String descriptor) {}

    private record Cached(long at, JumperContext ctx) {}

    private static final Map<Path, Probe> PROBES = new ConcurrentHashMap<>();
    private static final Map<Path, Cached> CONTEXTS = new ConcurrentHashMap<>();

    /** The context of a file of the editor (of the original file when this is a copy made for completion). */
    public static JumperContext contextFor(PsiFile file) {
        PsiFile original = file.getOriginalFile();
        VirtualFile vf = original.getVirtualFile();
        if (vf == null) vf = file.getViewProvider().getVirtualFile();
        JumperFileType.Kind kind = JumperFileType.kindOf(vf);
        Path path = pathOf(vf);
        return path == null ? JumperContext.empty(kind) : contextFor(path);
    }

    public static Path pathOf(VirtualFile vf) {
        if (vf == null || !vf.isInLocalFileSystem()) return null;
        try {
            return Path.of(vf.getPath());
        } catch (InvalidPathException e) {
            return null;
        }
    }

    public static JumperContext contextFor(Path file) {
        Path f = file.toAbsolutePath().normalize();
        long now = System.currentTimeMillis();
        Cached c = CONTEXTS.get(f);
        if (c != null && now - c.at < TTL_MS) return c.ctx;
        JumperContext ctx;
        try {
            ctx = compute(f);
        } catch (RuntimeException e) {
            ctx = new JumperContext(kindOf(f), f, f.getParent(), null, null, Map.of(), List.of(), List.of("context: " + e));
        }
        if (CONTEXTS.size() > 4096) CONTEXTS.clear();
        CONTEXTS.put(f, new Cached(now, ctx));
        return ctx;
    }

    /** Forget what was read: a jar or a policy changed. */
    public static void invalidate() {
        CONTEXTS.clear();
        CLASSPATHS.clear();
    }

    private record CachedPath(long at, List<Path> jars) {}

    /** The jars under a root, by root and owner: a walk of the server folder is not repeated for every file. */
    private static final Map<String, CachedPath> CLASSPATHS = new ConcurrentHashMap<>();
    private static final long CLASSPATH_TTL_MS = 30_000;

    static JumperFileType.Kind kindOf(Path f) {
        String n = f.getFileName().toString();
        if (n.endsWith(".jmc")) return JumperFileType.Kind.CONFIG;
        if (n.endsWith(".jma")) return JumperFileType.Kind.POLICY;
        return JumperFileType.Kind.SCRIPT;
    }

    private static JumperContext compute(Path f) {
        JumperFileType.Kind kind = kindOf(f);
        List<String> notes = new ArrayList<>();
        List<HostDescriptor> hosts = findHosts(f.getParent(), notes);
        HostDescriptor owner = null;
        int best = -1;
        for (HostDescriptor h : hosts) {
            String glob = switch (kind) {
                case SCRIPT -> h.match(h.scripts(), f);
                case CONFIG -> h.match(h.configs(), f);
                case POLICY -> f.equals(h.access()) ? "(access)" : null;
            };
            if (glob == null) continue;
            int spec = glob.equals("(access)") ? Integer.MAX_VALUE : Glob.specificity(glob);
            if (owner != null) {
                notes.add("claimed by two hosts: " + owner.name() + " and " + h.name() + " - the more specific pattern wins");
                if (spec <= best) continue;
            }
            owner = h;
            best = spec;
        }
        for (HostDescriptor h : hosts) for (String p : h.problems()) notes.add(h.name() + " (" + h.jar().getFileName() + "): " + p);
        if (owner != null) {
            Path access = kind == JumperFileType.Kind.SCRIPT ? owner.access() : null;
            if (access != null && !Files.isRegularFile(access))
                notes.add(owner.name() + " names the policy " + owner.root().relativize(access) + ", which does not exist");
            Map<String, String> globals = kind == JumperFileType.Kind.SCRIPT ? owner.globals() : Map.of();
            return new JumperContext(kind, f, owner.root(), owner, access, globals, classpath(owner.root(), hosts, owner), List.copyOf(notes));
        }
        if (kind == JumperFileType.Kind.SCRIPT) {
            Path access = conventionPolicy(f, notes);
            Path root = access != null ? access.getParent() : f.getParent();
            return new JumperContext(kind, f, root, null, access, Map.of(), access != null ? classpath(root, hosts, null) : List.of(),
                    List.copyOf(notes));
        }
        return new JumperContext(kind, f, f.getParent(), null, null, Map.of(), List.of(), List.copyOf(notes));
    }

    // ------------------------------------------------------------------ hosts

    private static List<HostDescriptor> findHosts(Path from, List<String> notes) {
        Map<Path, HostDescriptor> found = new LinkedHashMap<>();
        Path home = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
        Path d = from;
        for (int up = 0; d != null && up <= MAX_UP; up++, d = d.getParent()) {
            for (Path jar : jarsNear(d)) {
                if (found.containsKey(jar)) continue;
                String text = probe(jar);
                if (text != null) found.put(jar, HostDescriptor.parse(jar, text));
            }
            if (d.equals(home)) break;
        }
        return new ArrayList<>(found.values());
    }

    private static List<Path> jarsNear(Path d) {
        List<Path> out = new ArrayList<>();
        List<Path> dirs = new ArrayList<>();
        try (Stream<Path> s = Files.list(d)) {
            s.limit(MAX_DIR_ENTRIES).forEach(p -> {
                if (Files.isDirectory(p)) dirs.add(p);
                else if (isJar(p)) out.add(p.toAbsolutePath().normalize());
            });
        } catch (IOException | SecurityException | java.io.UncheckedIOException e) {
            return out;
        }
        for (Path sub : dirs) {
            try (Stream<Path> s = Files.list(sub)) {
                s.limit(MAX_DIR_ENTRIES).filter(JumperWorkspace::isJar).forEach(p -> out.add(p.toAbsolutePath().normalize()));
            } catch (IOException | SecurityException | java.io.UncheckedIOException e) {
                // an unreadable folder: skipped
            }
        }
        return out;
    }

    /** A jar with classes: not `name-sources.jar` (a source of go-to-definition, next to its jar). */
    static boolean isJar(Path p) {
        String n = p.getFileName().toString();
        return n.endsWith(".jar") && !n.endsWith("-sources.jar") && Files.isRegularFile(p);
    }

    /** The descriptor text of a jar, or null; a jar that is not a zip is not a host. */
    static String probe(Path jar) {
        try {
            var a = Files.readAttributes(jar, java.nio.file.attribute.BasicFileAttributes.class);
            Probe p = PROBES.get(jar);
            if (p != null && p.size == a.size() && p.mtime == a.lastModifiedTime().toMillis()) return p.descriptor;
            String text = null;
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(jar.toFile())) {
                var e = zip.getEntry(HostDescriptor.DESCRIPTOR);
                if (e != null) try (var in = zip.getInputStream(e)) {
                    text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
            }
            PROBES.put(jar, new Probe(a.size(), a.lastModifiedTime().toMillis(), text));
            return text;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ convention

    private static Path conventionPolicy(Path file, List<String> notes) {
        Path home = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
        Path d = file.getParent();
        for (int up = 0; d != null && up <= MAX_UP; up++, d = d.getParent()) {
            List<Path> jmas = new ArrayList<>();
            try (Stream<Path> s = Files.list(d)) {
                s.limit(MAX_DIR_ENTRIES).filter(p -> p.getFileName().toString().endsWith(".jma") && Files.isRegularFile(p)).sorted().forEach(jmas::add);
            } catch (IOException | SecurityException | java.io.UncheckedIOException e) {
                jmas = List.of();
            }
            if (jmas.size() == 1) return jmas.get(0);
            if (jmas.size() > 1) {
                for (Path seg : d.relativize(file.getParent())) {   // scripts/<name>/a.jmp -> <name>.jma
                    for (Path jma : jmas) {
                        String n = jma.getFileName().toString();
                        if (n.substring(0, n.length() - 4).equals(seg.toString())) return jma;
                    }
                }
                List<String> names = new ArrayList<>();
                for (Path j : jmas) names.add(j.getFileName().toString());
                notes.add("several policies in " + d + " (" + String.join(", ", names) + ") and none is named after a folder of "
                        + "the script: checked without a policy. A host descriptor (" + HostDescriptor.DESCRIPTOR + ") would say which");
                return null;
            }
            if (d.equals(home)) break;
        }
        notes.add("no access policy (.jma) found above the script: checked without one");
        return null;
    }

    // ------------------------------------------------------------------ classpath

    static List<Path> classpath(Path root, List<HostDescriptor> hosts, HostDescriptor owner) {
        String key = root + "|" + (owner == null ? "" : owner.jar());
        long now = System.currentTimeMillis();
        CachedPath c = CLASSPATHS.get(key);
        if (c != null && now - c.at < CLASSPATH_TTL_MS) return c.jars;
        List<Path> jars = walkClasspath(root, hosts, owner);
        CLASSPATHS.put(key, new CachedPath(now, jars));
        return jars;
    }

    private static List<Path> walkClasspath(Path root, List<HostDescriptor> hosts, HostDescriptor owner) {
        Set<Path> otherHosts = new HashSet<>();
        for (HostDescriptor h : hosts) if (h != owner) otherHosts.add(h.jar().toAbsolutePath().normalize());
        List<Path> out = new ArrayList<>();
        try (Stream<Path> s = Files.walk(root, MAX_DEPTH)) {
            s.filter(JumperWorkspace::isJar).map(p -> p.toAbsolutePath().normalize())
                    .filter(p -> !otherHosts.contains(p)).limit(MAX_JARS).sorted().forEach(out::add);
        } catch (IOException | java.io.UncheckedIOException | SecurityException e) {
            // an unreadable part of the tree: what was found so far
        }
        if (owner != null) {
            Path own = owner.jar().toAbsolutePath().normalize();
            if (!out.contains(own)) out.add(own);
        }
        return List.copyOf(out);
    }

    /** `name.jar` -> `name-sources.jar` next to it, if there is one. */
    public static Path sourcesOf(Path jar) {
        String n = jar.getFileName().toString();
        Path s = jar.resolveSibling(n.substring(0, n.length() - 4) + "-sources.jar");
        return Files.isRegularFile(s) ? s : null;
    }
}
