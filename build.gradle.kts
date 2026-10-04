// The Jumper plugin for IntelliJ IDEA - one module, Java. It carries the language itself: the lexer, the parser and
// the rules of the language and of its language server are ported from https://github.com/jumper-lang/jumper (lang/),
// so the plugin needs nothing of that repository to build or to run.
// Build: gradlew buildPlugin (build/distributions/*.zip); try it: gradlew runIde. The file icons are
// src/main/resources/icons/<name>.svg (light theme) and <name>_dark.svg (dark).
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "me.padej.jumper"
version = "0.11.1"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2025.1")
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
            0.11.1: the variable of a for-each has the type of the elements (`for (dyn p : server.players())` -
            completion and Go to Declaration on `p.`), type arguments of Java generics are followed
            (`players().get(0)`); faster: the members of Java classes, the file's context and policy are read once
            and kept until they change, the host jars are found from the index in the background.
            <br>
            0.11.0: the language in the IDE - lexer, parser and PSI of Jumper; highlighting, the language's
            diagnostics and access policy, completion, navigation into Java, hooks, usages, rename, structure,
            folding, formatting, parameter info.
        """.trimIndent()
    }

    // gradlew verifyPlugin: the JetBrains Plugin Verifier - what Marketplace checks on upload (compatibility,
    // internal, deprecated and experimental API).
    // On CI: the IDE versions JetBrains recommends for sinceBuild/untilBuild - they include an EAP build, which comes
    // from download.jetbrains.com, and that answers HTTP 451 in some countries. So locally: released versions only,
    // the same majors (pinned; move them on when new ones are out).
    pluginVerification {
        ides {
            if (providers.environmentVariable("CI").isPresent) {
                recommended()
            } else {
                create(IntelliJPlatformType.IntellijIdeaCommunity, "2025.1.7.2")
                create(IntelliJPlatformType.IntellijIdeaCommunity, "2025.2.6.3")
                create(IntelliJPlatformType.IntellijIdea, "2025.3.6.1")
                create(IntelliJPlatformType.IntellijIdea, "2026.1.5")
                create(IntelliJPlatformType.IntellijIdea, "2026.2.3")
            }
        }
    }
}

// JetBrains Marketplace: gradlew publishPlugin with PUBLISH_TOKEN set (a token of the vendor's account; the release
// workflow does it on a tag, .github/workflows/release.yml). The first upload is done by hand on plugins.jetbrains.com.
intellijPlatform {
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}
