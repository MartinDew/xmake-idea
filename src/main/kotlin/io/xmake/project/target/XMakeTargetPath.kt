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
package io.xmake.project.target

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import io.xmake.project.profile.XMakeBuildProfile
import io.xmake.run.command.withProfileCommands
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val Log = Logger.getInstance("io.xmake.project.target.XMakeTargetPath")

private val TARGET_PATH_MARKER = Regex("__begin__([\\s\\S]*?)__end__")

/** The resolved local command context of a build profile: the xmake binary and its working directory. */
data class XMakeLocalProfileContext(
    val xmakeBinary: String,
    val workingDirectory: String,
)

/**
 * Resolves a build profile's local xmake binary + working directory, or `null` when the profile
 * has no usable toolkit or targets a remote (WSL/SSH) host.
 */
internal suspend fun Project.resolveXMakeLocalProfileContext(
    profile: XMakeBuildProfile,
): XMakeLocalProfileContext? = withContext(Dispatchers.IO) {
    try {
        withProfileCommands(profile) { _ ->
            val command = createConfigure()
            if (command.toolkit.requiresBackend) return@withProfileCommands null
            XMakeLocalProfileContext(command.toolkit.path, command.workingDirectory)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Log.debug("Failed to resolve XMake profile context for ${profile.id}", error)
        null
    }
}

/**
 * Resolves the built executable path of [targetName] in one build profile's command context by
 * running `xmake l scripts/targetpath.lua <target>` and reading the absolute path printed between
 * the `__begin__` / `__end__` markers.
 *
 * Returns `null` when the profile has no usable local toolkit, the script is missing, or the
 * target produces no binary (libraries, phony targets). Remote (WSL/SSH) profiles always return
 * `null` — CLion can only run a local filesystem executable.
 */
internal suspend fun Project.resolveXMakeTargetPath(
    profile: XMakeBuildProfile,
    targetName: String,
): String? = withContext(Dispatchers.IO) {
    try {
        withProfileCommands(profile) { executionService ->
            val command = createTargetPathQuery(targetName)
            if (command.toolkit.requiresBackend) return@withProfileCommands null
            val output = executionService.captureStandardOutput(command).trim()
            val path = TARGET_PATH_MARKER.find(output)
                ?.groupValues
                ?.get(1)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: return@withProfileCommands null
            if (File(path).isAbsolute) path else File(command.workingDirectory, path).absolutePath
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Log.debug("Failed to resolve XMake target path for '$targetName' in profile ${profile.id}", error)
        null
    }
}
