package me.padej.jumper.idea.workspace;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A jar (a host rebuilt) or a policy changed, or a Jumper file came or went: contexts are read again (the language
 * server does the same after a save and on changed watched files), what was computed from them - resolved names, the
 * types of `dyn` values, the editor's checks - is computed again, and the host jars are searched again.
 * <p>
 * Called in the write action that applied the events: it only marks things stale, the work is done later and in the
 * background (JumperHostLibraries).
 */
public final class JumperFileListener implements BulkFileListener {
    private final Project project;

    public JumperFileListener(Project project) {
        this.project = project;
    }

    @Override
    public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        boolean contexts = false, files = false;
        for (VFileEvent e : events) {
            String path = e.getPath();
            if (path.endsWith(".jar") || path.endsWith(".jma")) {
                contexts = true;
                break;
            }
            // a script or a config that comes, goes or moves: the folders whose jars are libraries
            if (!(e instanceof VFileContentChangeEvent) && (path.endsWith(".jmp") || path.endsWith(".jmc"))) files = true;
        }
        if (!contexts && !files || project.isDisposed()) return;
        JumperHostLibraries.getInstance(project).refresh();
        if (contexts) {
            PsiManager.getInstance(project).dropPsiCaches();
            DaemonCodeAnalyzer.getInstance(project).restart();
        }
    }
}
