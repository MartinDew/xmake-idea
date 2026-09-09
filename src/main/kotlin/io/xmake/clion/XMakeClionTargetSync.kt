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

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import io.xmake.project.profile.XMakeBuildProfile
import io.xmake.project.target.discoverXMakeBuildTargets
import io.xmake.project.target.resolveXMakeLocalProfileContext
import io.xmake.project.target.resolveXMakeTargetPath
import io.xmake.project.xmakeSettings
import io.xmake.run.command.DEFAULT_BUILD_TARGET
import io.xmake.run.target.activeOrSingleXMakeBuildProfile
import io.xmake.utils.SystemUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

/**
 * Keeps CLion's native "Xmake Executable" run configurations and its Compilation Database in sync
 * with the **active** build profile: one run config per executable target, each with the target's
 * resolved (profile-specific) executable path and a profile-aware build step.
 *
 * No-ops on IDEA Community and for remote (WSL/SSH) profiles. Requests are conflated and debounced
 * so bursts of toolkit / profile / target-list changes collapse into one sync.
 */
@OptIn(FlowPreview::class)
@Service(Service.Level.PROJECT)
class XMakeClionTargetSync(
    private val project: Project,
    scope: CoroutineScope,
) {
    private val requests = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            requests.consumeAsFlow()
                .debounce(SYNC_DEBOUNCE)
                .collect { runSync() }
        }
    }

    fun requestSync() {
        if (project.isDisposed || !XMakeClionSupport.isAvailable() || !SystemUtils.isXMakeProject(project)) return
        requests.trySend(Unit)
    }

    private suspend fun runSync() {
        val support = XMakeClionSupport.find() ?: return
        val profile = project.activeOrSingleXMakeBuildProfile ?: return
        val context = try {
            project.resolveXMakeLocalProfileContext(profile)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.debug("Failed to resolve XMake profile context for CLion (profile ${profile.id})", error)
            null
        } ?: return

        val targetNames = try {
            project.discoverXMakeBuildTargets(profile)
                .filter { it.isNotBlank() && it != DEFAULT_BUILD_TARGET }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.debug("Failed to discover XMake targets for CLion (profile ${profile.id})", error)
            emptyList()
        }

        val executableTargets = targetNames.mapNotNull { name ->
            project.resolveXMakeTargetPath(profile, name)?.let { path -> XMakeExecutableTarget(name, path) }
        }
        if (executableTargets.isNotEmpty()) {
            support.syncExecutableRunConfigurations(XMakeExecutableTargetSpec(project, executableTargets))
        }
        refreshCompileCommands(support, context.workingDirectory)
    }

    private fun refreshCompileCommands(support: XMakeClionSupport, workingDirectory: String) {
        val file = resolveCompileCommandsFile(workingDirectory) ?: return
        if (file.isFile) {
            support.attachCompileCommands(project, file.absolutePath)
        }
    }

    private fun resolveCompileCommandsFile(workingDirectory: String): File? {
        val base = workingDirectory.ifBlank { project.basePath ?: return null }
        val configured = project.xmakeSettings.state.compileCommandsPath.trim()
        return when {
            configured.isEmpty() -> File(base, "compile_commands.json")
            configured.endsWith(".json") -> File(base).resolve(configured)
            else -> File(base).resolve(configured).resolve("compile_commands.json")
        }
    }

    companion object {
        private val Log = logger<XMakeClionTargetSync>()
        private val SYNC_DEBOUNCE = 400.milliseconds

        fun getInstance(project: Project): XMakeClionTargetSync =
            project.getService(XMakeClionTargetSync::class.java)
    }
}
