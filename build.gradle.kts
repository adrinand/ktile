@file:OptIn(ExperimentalComposeLibrary::class)

import org.jetbrains.compose.ExperimentalComposeLibrary
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

group = "com.adrinand"
version = "1.0.0"

val isLinux = System.getProperty("os.name").lowercase().contains("linux")

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kover)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(libs.jna.platform)
    implementation(libs.jnativehook)
    implementation(libs.kotlinx.serialization.json)

    // Kotest (unit tests)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)

    // ─── Compose UI Tests ───
    // Desktop runtime for tests
    testImplementation(compose.desktop.currentOs)

    // Core UI test API (provides `androidx.compose.ui.test` package)
    testImplementation(compose.uiTest)

    // JUnit4 integration (provides `createComposeRule`)
    testImplementation(compose.desktop.uiTestJUnit4)

    // JUnit4 runtime (for @Test, @Rule)
    testImplementation("junit:junit:4.13.2")

    // Allows JUnit4 tests to run on the JUnit5 platform
    testImplementation("org.junit.vintage:junit-vintage-engine:5.10.1")
}

val rustDir = layout.projectDirectory.dir("lib/ktile-hotkey")
val rustReleaseSo = rustDir.file("target/release/libktile_hotkey.so")
val rustLibsDir = layout.buildDirectory.dir("rust-libs")

val buildRustRelease by tasks.registering(Exec::class) {
    group = "rust"
    description = "Build the Rust hotkey library in release mode"
    workingDir = rustDir.asFile
    commandLine("cargo", "build", "--release")
    inputs.dir(rustDir.dir("src"))
    outputs.file(rustReleaseSo)
    onlyIf { isLinux }
}

val copyRustLib by tasks.registering(Copy::class) {
    group = "rust"
    description = "Copy the Rust hotkey shared library to build/rust-libs"
    dependsOn(buildRustRelease)
    from(rustReleaseSo)
    into(rustLibsDir)
    onlyIf { isLinux }
}

tasks.named("processResources") {
    dependsOn(copyRustLib)
}

afterEvaluate {
    tasks.named("prepareAppResources") {
        dependsOn(copyRustLib)
    }
}

compose.desktop {
    application {
        mainClass = "com.adrinand.ktile.MainKt"
        jvmArgs +=
            listOf(
                "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
                "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED",
                "-Djna.library.path=${rustLibsDir.get().asFile.absolutePath}",
            )
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "ktile"
            packageVersion = version.toString()
            description = "A keyboard-driven tiling window manager"
            copyright = "© 2026 adrinand"
            vendor = "adrinand"
            appResourcesRootDir.set(rustLibsDir)

            macOS {
                bundleID = "com.adrinand.ktile"
                iconFile.set(project.file("src/main/resources/ktile.icns"))
            }

            linux {
                iconFile.set(project.file("src/main/resources/ktile.png"))
            }
        }
    }
}

detekt {
    config.from("detekt.yml")
    autoCorrect = true
    ignoreFailures = false
}

kover {
    reports {
        filters {
            excludes {
                // App bootstrap and OS-specific integration code: not unit-testable headless,
                // covered by functional tests (core.screen) and per-OS integration tests (providers, window, tray).
                classes(
                    "com.adrinand.ktile.MainKt*",
                    "com.adrinand.ktile.ComposableSingletons*",
                    "com.adrinand.ktile.core.screen.*",
                    "com.adrinand.ktile.core.hotkey.LinuxEvdevHotkeyProvider*",
                    "com.adrinand.ktile.core.hotkey.JNativeHookProvider*",
                    "com.adrinand.ktile.core.hotkey.KtileHotkeyNative*",
                    "com.adrinand.ktile.core.hotkey.InputDevicePermissionChecker",
                    "com.adrinand.ktile.ui.KTileWindowKt*",
                    "com.adrinand.ktile.ui.KTileTrayKt*",
                    "com.adrinand.ktile.ui.GlobalHotkeyRegistration*",
                )
            }
        }
        verify {
            rule {
                minBound(90)
            }
        }
    }
}

ktlint {
    filter {
        exclude { element -> element.file.path.contains("generated/") }
    }
    outputToConsole.set(true)
    ignoreFailures.set(false)
}

tasks {
    withType<Test> {
        useJUnitPlatform()
        testLogging.events("passed", "skipped", "failed", "standardOut", "standardError")
        outputs.upToDateWhen { false }
        ignoreFailures = false
        dependsOn(copyRustLib)
        systemProperty("jna.library.path", rustLibsDir.get().asFile.absolutePath)
    }

    named("check") {
        dependsOn("ktlintCheck", "detekt", "test", "koverVerify")
    }
}

tasks.register("printVersion") {
    group = "help"
    description = "Prints the project version"
    doLast {
        println(project.version)
    }
}

tasks.register<Exec>("installGnomeExtension") {
    group = "distribution"
    description = "Installs the GNOME Shell extension for native Wayland support"
    commandLine(
        "bash",
        "-c",
        """
        install -d "${System.getProperty("user.home")}/.local/share/gnome-shell/extensions/ktile@adrinand"
        cp -r extensions/gnome/ktile@adrinand/* "${System.getProperty("user.home")}/.local/share/gnome-shell/extensions/ktile@adrinand/"
        echo "GNOME Shell extension installed. Log Out and enable it using: gnome-extensions enable ktile@adrinand"
        """.trimIndent(),
    )
}

tasks.register<Exec>("installKdeScript") {
    group = "distribution"
    description = "Installs the KDE KWin script for native Wayland support"
    commandLine(
        "bash",
        "-c",
        """
        install -d "${System.getProperty("user.home")}/.local/share/kwin/scripts/ktile.kwin"
        cp -r extensions/kde/ktile.kwin/* "${System.getProperty("user.home")}/.local/share/kwin/scripts/ktile.kwin/"
        echo "KDE KWin script installed. Enable it in KWin script settings."
        """.trimIndent(),
    )
}
