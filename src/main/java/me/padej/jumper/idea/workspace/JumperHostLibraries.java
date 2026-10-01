package me.padej.jumper.idea.workspace;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.AdditionalLibraryRootsListener;
import com.intellij.openapi.roots.JavaSyntheticLibrary;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.openapi.vfs.VfsUtilCore;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The host jars of a project (see JumperLibraryRootsProvider): found once, again when a jar, a policy or a Jumper
 * file of the project changes (JumperFileListener); when they are not the same any more the IDE is told.
 */
@Service(Service.Level.PROJECT)
public final class JumperHostLibraries {
    private static final int MAX_FILES = 2000, MAX_DEPTH = 10;

    private final Project project;
    private volatile List<Path> jars;
    private volatile Collection<SyntheticLibrary> libraries;
    private final AtomicBoolean started = new AtomicBoolean(), pending = new AtomicBoolean();

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
        if (!pending.compareAndSet(false, true)) return;   // one search at a time is enough, it sees the latest state
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            pending.set(false);
            if (project.isDisposed()) return;
            List<Path> found;
            try {
                // only the walk over the project needs the read lock; the jars are read without it (reading zips
                // under the lock kept write actions, and so the UI, waiting)
                List<Path> files = ReadAction.nonBlocking(this::findFiles).expireWith(project).executeSynchronously();
                found = jarsOf(files);
            } catch (ProcessCanceledException e) {
                return;
            }
            publish(found);
        });
    }

    private synchronized void publish(List<Path> found) {
        List<Path> old = jars;
        if (found.equals(old)) return;
        Collection<SyntheticLibrary> oldLibs = libraries;
        Collection<SyntheticLibrary> newLibs = toLibraries(found);
        jars = found;
        libraries = newLibs;
        if (roots(oldLibs).isEmpty() && roots(newLibs).isEmpty()) return;
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) return;
            ApplicationManager.getApplication().runWriteAction(() ->
                    AdditionalLibraryRootsListener.fireAdditionalLibraryChanged(project, "Jumper host jars",
                            roots(oldLibs), roots(newLibs), "Jumper"));
        });
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

    /** The project's Jumper files. Only ever runs in the background (see libraries), in a read action. */
    private List<Path> findFiles() {
        Set<Path> dirs = new LinkedHashSet<>();
        int[] files = {0};
        ProjectFileIndex index = ProjectFileIndex.getInstance(project);
        for (VirtualFile root : ProjectRootManager.getInstance(project).getContentRoots()) {
            VfsUtilCore.visitChildrenRecursively(root, new VirtualFileVisitor<Void>(VirtualFileVisitor.limit(MAX_DEPTH)) {
                @Override
                public boolean visitFile(VirtualFile file) {
                    ProgressManager.checkCanceled();   // a write action is waiting: give way, the search restarts
                    if (files[0] >= MAX_FILES) return false;
                    if (file.isDirectory()) return !index.isExcluded(file) && !file.getName().startsWith(".")
                            && !file.getName().equals("node_modules");
                    String ext = file.getExtension();
                    if ("jmp".equals(ext) || "jmc".equals(ext) || "jma".equals(ext)) {
                        files[0]++;
                        Path p = JumperWorkspace.pathOf(file);
                        if (p != null) dirs.add(p);
                    }
                    return true;
                }
            });
        }
        return new ArrayList<>(dirs);
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
