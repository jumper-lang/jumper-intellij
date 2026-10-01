package me.padej.jumper.idea.workspace;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * The server's jars as libraries of the project: what a script names (`import example.api.World;`, the type of a
 * host global) is found by the IDE's Java support - completion, navigation, a decompiled class or its
 * `-sources.jar`. The jars are the classpath of the contexts of the project's Jumper files (JumperWorkspace).
 */
public final class JumperLibraryRootsProvider extends AdditionalLibraryRootsProvider {
    @Override
    public @NotNull Collection<SyntheticLibrary> getAdditionalProjectLibraries(@NotNull Project project) {
        return JumperHostLibraries.getInstance(project).libraries();
    }

    @Override
    public @NotNull Collection<VirtualFile> getRootsToWatch(@NotNull Project project) {
        return List.of();
    }
}
