plugins {
    id("org.jetbrains.kotlin.jvm") version "2.3.20"
    id("org.jetbrains.intellij.platform.module")
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    compileOnly(project(":"))

    intellijPlatform {
        clion(providers.gradleProperty("runIdeVersion"))
        bundledModule("intellij.cidr.debugger.core")
        bundledPlugin("com.intellij.nativeDebug")
        // Compilation Database ExternalSystem — feeds IntelliSense from xmake's generated
        // compile_commands.json (io.xmake.debug.clion.CompDBIntegration).
        bundledPlugin("com.intellij.clion-compdb")
    }
}
