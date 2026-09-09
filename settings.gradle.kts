// Auto-provisions the JDK requested by the jvmToolchain(...) setting, so local builds use the same
// JDK as CI regardless of the installed Java version.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "xmake-idea"

include(":clion-debug")
