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
 * "Xmake Executable" run configuration type and the Compilation Database IntelliSense feed.
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
     * Create or refresh one ready-to-run "Xmake Executable" run configuration per executable target
     * in [spec], each with its resolved executable path pre-filled and a build step that builds the
     * target for the active XMake build profile. Returns `true` if the sync was scheduled/applied.
     */
    fun syncExecutableRunConfigurations(spec: XMakeExecutableTargetSpec): Boolean

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

/** The xmake executable targets to expose in CLion as native run configurations. */
data class XMakeExecutableTargetSpec(
    val project: Project,
    val targets: List<XMakeExecutableTarget>,
)

/** One runnable xmake target and its resolved local executable path. */
data class XMakeExecutableTarget(
    val name: String,
    val executablePath: String,
)
