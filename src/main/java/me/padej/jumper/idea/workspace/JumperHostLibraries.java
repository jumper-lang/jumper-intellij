package me.padej.jumper.idea.workspace;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.AdditionalLibraryRootsListener;
import com.intellij.openapi.roots.JavaSyntheticLibrary;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.concurrency.AppExecutorUtil;
import me.padej.jumper.idea.JumperFileType;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The host jars of a project (see JumperLibraryRootsProvider): found once, again when a jar, a policy or a Jumper
 * file of the project changes (JumperFileListener); when they are not the same any more the IDE is told.
 * <p>
 * Nothing of it runs on the UI thread or while the IDE builds its indexes: the Jumper files are looked up in the
 * file type index (once the indexes are ready, in a read action that gives way to writes), the jars are read after,
 * with no lock held.
 */
@Service(Service.Level.PROJECT)
public final class JumperHostLibraries {
    private static final int MAX_FILES = 2000;
    private static final List<FileType> FILE_TYPES = List.of(JumperFileType.SCRIPT, JumperFileType.CONFIG, JumperFileType.POLICY);

    private final Project project;
    private volatile List<Path> jars;
    private volatile Collection<SyntheticLibrary> libraries;
    private final AtomicBoolean started = new AtomicBoolean();
    /** Searches in the order they were asked for: an older one finishing late does not undo a newer one. */
    private final AtomicLong asked = new AtomicLong();
    private long published;

    public JumperHostLibraries(Project project) {
        this.project = project;
    }

    public static JumperHostLibraries getInstance(Project project) {
        return project.getService(JumperHostLibraries.class);
    }

    /**
     * What the provider hands to the IDE. It is called while the IDE builds its file index, so it must not look at the
     * project itself (that asks the index again: endless recursion): it only returns what was found before, and the
     * first time starts the search in the background; when that is done the IDE is told the libraries changed.
     */
    public Collection<SyntheticLibrary> libraries() {
        Collection<SyntheticLibrary> l = libraries;
        if (l == null) {
            if (started.compareAndSet(false, true)) scheduleSearch();
            return List.of();
        }
        return l;
    }

    /** Something changed on disk: find the jars again (in the background) and, if they differ, tell the IDE. */
    public void refresh() {
        JumperWorkspace.invalidate();
        started.set(true);
        scheduleSearch();
    }

    private void scheduleSearch() {
        long n = asked.incrementAndGet();
        // a newer request cancels the one still waiting (coalesceBy); the jars are read once the read action is over
        ReadAction.nonBlocking(this::findFiles)
                .inSmartMode(project)
                .expireWith(project)
                .coalesceBy(this)
                .submit(AppExecutorUtil.getAppExecutorService())
                .onSuccess(files -> AppExecutorUtil.getAppExecutorService().execute(() -> {
                    if (!project.isDisposed()) publish(n, jarsOf(files));
                }));
    }

    private synchronized void publish(long n, List<Path> found) {
        if (n < published) return;
        published = n;
        List<Path> old = jars;
        if (found.equals(old)) return;
        Collection<SyntheticLibrary> oldLibs = libraries;
        Collection<SyntheticLibrary> newLibs = toLibraries(found);
        jars = found;
        libraries = newLibs;
        if (roots(oldLibs).isEmpty() && roots(newLibs).isEmpty()) return;
        // the platform's way to say that the roots of an AdditionalLibraryRootsProvider changed (in a write action)
        ApplicationManager.getApplication().invokeLater(() -> ApplicationManager.getApplication().runWriteAction(() ->
                AdditionalLibraryRootsListener.fireAdditionalLibraryChanged(project, "Jumper host jars",
                        roots(oldLibs), roots(newLibs), "Jumper")), project.getDisposed());
    }

    private static Collection<VirtualFile> roots(Collection<SyntheticLibrary> libs) {
        List<VirtualFile> out = new ArrayList<>();
        if (libs == null) return out;
        for (SyntheticLibrary l : libs) {
            out.addAll(l.getBinaryRoots());
            out.addAll(l.getSourceRoots());
        }
        return out;
    }

    /** The project's Jumper files (not in excluded, hidden or node_modules folders), from the file type index. */
    private List<Path> findFiles() {
        Set<Path> out = new LinkedHashSet<>();
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        String basePath = project.getBasePath();
        Path base = basePath == null ? null : JumperWorkspace.pathOf(basePath);
        for (FileType type : FILE_TYPES) {
            FileTypeIndex.processFiles(type, file -> {
                ProgressManager.checkCanceled();   // a write action is waiting: give way, the search restarts
                Path p = JumperWorkspace.pathOf(file);
                if (p != null && !hidden(base, p)) out.add(p);
                return out.size() < MAX_FILES;
            }, scope);
        }
        return new ArrayList<>(out);
    }

    /** In a hidden folder (`.gradle`, `.idea`) or node_modules of the project. */
    private static boolean hidden(Path base, Path p) {
        if (base == null || p.getParent() == null || !p.startsWith(base)) return false;
        for (Path part : base.relativize(p.getParent())) {
            String n = part.toString();
            if (n.startsWith(".") || n.equals("node_modules")) return true;
        }
        return false;
    }

    /** The classpath of every context of these files: plain file reading, no read action. */
    private static List<Path> jarsOf(List<Path> files) {
        Set<Path> seenDirs = new HashSet<>();
        Set<Path> out = new LinkedHashSet<>();
        for (Path f : files) {
            if (!seenDirs.add(f.getParent())) continue;   // one context per folder is enough for its jars
            out.addAll(JumperWorkspace.contextFor(f).classpath());
        }
        return new ArrayList<>(out);
    }

    private static Collection<SyntheticLibrary> toLibraries(List<Path> jars) {
        if (jars.isEmpty()) return List.of();
        List<VirtualFile> binaries = new ArrayList<>(), sources = new ArrayList<>();
        for (Path jar : jars) {
            VirtualFile root = jarRoot(jar);
            if (root != null) binaries.add(root);
            Path src = JumperWorkspace.sourcesOf(jar);
            VirtualFile srcRoot = src == null ? null : jarRoot(src);
            if (srcRoot != null) sources.add(srcRoot);
        }
        if (binaries.isEmpty()) return List.of();
        // a Java library, not a plain SyntheticLibrary: only then are its jars package roots (JvmPackageRootData), and
        // a class of a jar can resolve the classes it names by their short names (ScriptPlayer in ScriptEvents)
        return List.of(new JavaSyntheticLibrary("Jumper host jars", sources, binaries, Set.of()));
    }

    private static VirtualFile jarRoot(Path jar) {
        VirtualFile local = LocalFileSystem.getInstance().findFileByNioFile(jar);
        return local == null ? null : JarFileSystem.getInstance().getJarRootForLocalFile(local);
    }
}
