// The Jumper plugin for IntelliJ IDEA - one module, Java. It carries the language itself: the lexer, the parser and
// the rules of the language and of its language server are ported from https://github.com/jumper-lang/jumper (lang/),
// so the plugin needs nothing of that repository to build or to run.
// Build: gradlew buildPlugin (build/distributions/*.zip); try it: gradlew runIde. The file icons are
// src/main/resources/icons/<name>.svg (light theme) and <name>_dark.svg (dark).
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

group = "me.padej.jumper"
version = "0.11.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create("IC", "2025.1")
        // PSI of Java: script names resolve to Java classes, methods and fields; decompiled classes are its
        bundledPlugin("com.intellij.java")
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "251"
            untilBuild = provider { null }   // no upper bound: 2025.2 and later too
        }
        changeNotes = """
            0.11.0: the language in the IDE - lexer, parser and PSI of Jumper; highlighting, the language's
            diagnostics and access policy, completion, navigation into Java, hooks, usages, rename, structure,
            folding, formatting, parameter info.
        """.trimIndent()
    }
}

// JetBrains Marketplace: gradlew publishPlugin with PUBLISH_TOKEN set (a token of the vendor's account; the release
// workflow does it on a tag, .github/workflows/release.yml). The first upload is done by hand on plugins.jetbrains.com.
intellijPlatform {
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}
