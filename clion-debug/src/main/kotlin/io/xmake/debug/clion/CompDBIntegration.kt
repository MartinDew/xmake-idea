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
package io.xmake.debug.clion

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import com.jetbrains.cidr.cpp.compdb.CompDBManager
import com.jetbrains.cidr.cpp.compdb.settings.CompDBProjectSettings
import com.jetbrains.cidr.cpp.compdb.settings.CompDBSettings
import io.xmake.debug.clion.utils.Logger
import java.io.File

/**
 * Feeds CLion IntelliSense from xmake's generated `compile_commands.json` by linking it into
 * CLion's Compilation Database external system (`com.intellij.clion-compdb`) and refreshing it.
 *
 * This is CLion-only; reached from root only through [io.xmake.clion.XMakeClionSupport] via
 * [ClionSupport], never imported directly (see the CLion boundary rule in CLAUDE.md).
 */
internal object CompDBIntegration {

    private const val TAG = "CompDBIntegration"

    /** Link (if needed) and refresh the compilation database at [compileCommandsPath]. Returns
     * true if the attach was scheduled. The actual link+refresh runs on the EDT — `linkProject`
     * mutates external-system settings and fires listeners, which must not happen on an arbitrary
     * background thread (this is called from xmake process-termination callbacks). */
    fun attachCompileCommands(project: Project, compileCommandsPath: String): Boolean {
        val file = File(compileCommandsPath)
        if (!file.isFile) {
            Logger.w(TAG, "compile_commands.json not found at $compileCommandsPath")
            return false
        }
        val path = file.absolutePath
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            try {
                val systemSettings = CompDBSettings.getInstance(project)
                if (systemSettings.getLinkedProjectSettings(path) == null) {
                    val projectSettings = CompDBProjectSettings.`default`()
                    projectSettings.externalProjectPath = path
                    systemSettings.linkProject(projectSettings)
                    Logger.i(TAG, "Linked compilation database: $path")
                }
                val systemId = CompDBManager().systemId
                ExternalSystemUtil.refreshProject(
                    path,
                    ImportSpecBuilder(project, systemId).use(ProgressExecutionMode.IN_BACKGROUND_ASYNC),
                )
                Logger.i(TAG, "Refreshed compilation database: $path")
            } catch (t: Throwable) {
                Logger.e(TAG, "Failed to attach compilation database: ${t.message}")
            }
        }
        return true
    }
}
