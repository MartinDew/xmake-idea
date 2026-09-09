/*!A Xmake integration in IntelliJ IDEA/Clion
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright (C) 2015-present, Xmake Open Source Community.
 */
package io.xmake.clion

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

/**
 * Supplies the CLion-only integrations the core plugin cannot depend on directly: the native
 * "Xmake Executable" run configuration type, CLion Custom Build Targets, and the Compilation
 * Database IntelliSense feed.
 *
 * The single implementation lives in the optional `xmake-idea.clion-debug` content module and is
 * contributed through the `io.xmake.clionSupport` extension point, mirroring [io.xmake.debug.XMakeDebugSupport].
 * On IDEA Community the extension list is empty and every caller degrades to a no-op.
 */
interface XMakeClionSupport {

    /**
     * Register the native "Xmake Executable" run configuration type (owned by the XMake plugin).
     * Idempotent. Returns `true` once the type is present.
     */
    fun registerExecutableRunConfigurationType(): Boolean

    /**
     * Replace the plugin-managed CLion Custom Build Targets (and their ready-to-run "Xmake
     * Executable" configurations) with the ones described by [spec]. Returns `true` if the sync
     * was scheduled/applied.
     */
    fun syncBuildTargets(spec: XMakeBuildTargetSpec): Boolean

    /**
     * Link (if needed) and refresh CLion's Compilation Database from the `compile_commands.json`
     * at [compileCommandsPath]. Returns `true` if the refresh was scheduled.
     */
    fun attachCompileCommands(project: Project, compileCommandsPath: String): Boolean

    companion object {
        private val EP_NAME = ExtensionPointName.create<XMakeClionSupport>("io.xmake.clionSupport")

        fun find(): XMakeClionSupport? = EP_NAME.extensionList.firstOrNull()

        fun isAvailable(): Boolean = EP_NAME.extensionList.isNotEmpty()
    }
}

/** The xmake targets to publish to CLion, resolved in one build-profile context. */
data class XMakeBuildTargetSpec(
    val project: Project,
    val xmakeBinary: String,
    val workingDirectory: String,
    val projectName: String,
    val targets: List<XMakeBuildTargetInfo>,
)

/** One xmake target and how CLion should build, clean, and (for binaries) run it. */
data class XMakeBuildTargetInfo(
    val name: String,
    /** Absolute local path to the built executable, or `null` for a non-runnable target. */
    val executablePath: String?,
    val buildArguments: List<String>,
    val cleanArguments: List<String>,
) {
    val isExecutable: Boolean get() = executablePath != null
}
