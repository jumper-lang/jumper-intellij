package me.padej.jumper.idea;

import com.intellij.openapi.util.IconLoader;

import javax.swing.Icon;

/**
 * Icons of the plugin. The svg files are icon/jetbrains/ of the repository, copied into the jar by the build
 * (see build.gradle.kts): `name.svg` is the light theme's, `name_dark.svg` the dark one's - IconLoader picks
 * the dark file by itself. Until the light icons exist the build puts the dark one in their place.
 */
public final class JumperIcons {
    private JumperIcons() {}

    public static final Icon SCRIPT = load("/icons/jmp.svg");
    public static final Icon CONFIG = load("/icons/jmc.svg");
    public static final Icon POLICY = load("/icons/jma.svg");

    private static Icon load(String path) {
        return IconLoader.getIcon(path, JumperIcons.class);
    }
}
