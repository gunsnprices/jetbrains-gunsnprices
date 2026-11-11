import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

java {
    toolchain {
        // IntelliJ Platform 2024.3 (since-build 243) runs on Java 21.
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing", "-Xlint:-serial"))
}

dependencies {
    // The title parser: https://central.sonatype.com/artifact/com.gunsnprices/gunsnprices (code MIT, data CC BY 4.0)
    implementation("com.gunsnprices:gunsnprices:0.1.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")

    // IntelliJ Platform Gradle Plugin Dependencies Extension:
    // https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        // Build against the lowest supported version, as JetBrains recommends.
        intellijIdeaCommunity("2024.3.7")
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        // id, name, vendor and description are in src/main/resources/META-INF/plugin.xml;
        // version comes from gradle.properties and change notes from CHANGELOG.md.
        ideaVersion {
            sinceBuild = "243"
            // No upper bound: JetBrains' current advice for plugins that use only stable APIs.
            untilBuild = provider { null }
        }
    }

    pluginVerification {
        ides {
            // The since-build: the IDE the plugin is built against (IntelliJ IDEA Community 2024.3.7).
            current()
            // The latest release. Pass -PverifyIdePath=/path/to/IDE to use an IDE that is already on disk instead.
            val localIde = providers.gradleProperty("verifyIdePath")
            if (localIde.isPresent) {
                local(file(localIde.get()))
            } else {
                create(IntelliJPlatformType.IntellijIdea, "2026.3")
            }
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}

changelog {
    repositoryUrl = providers.gradleProperty("pluginRepositoryUrl")
}
