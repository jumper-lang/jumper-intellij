package me.padej.jumper.idea.workspace;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A jar (a host rebuilt), a policy or a descriptor changed, or a Jumper file came or went: contexts are read
 * again (the language server does the same after a save and on changed watched files).
 */
public final class JumperFileListener implements BulkFileListener {
    private final Project project;

    public JumperFileListener(Project project) {
        this.project = project;
    }

    @Override
    public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        boolean relevant = false;
        for (VFileEvent e : events) {
            String path = e.getPath();
            boolean content = e instanceof VFileContentChangeEvent;
            if (path.endsWith(".jar") || path.endsWith(".jma") || path.endsWith(".jmc")
                    || !content && (path.endsWith(".jmp"))) {
                relevant = true;
                break;
            }
        }
        if (!relevant) return;
        if (project.isDisposed()) return;
        JumperHostLibraries.getInstance(project).refresh();
    }
}
