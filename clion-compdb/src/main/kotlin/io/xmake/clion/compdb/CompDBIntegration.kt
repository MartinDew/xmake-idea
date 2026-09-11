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
package io.xmake.clion.compdb

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import com.jetbrains.cidr.cpp.compdb.CompDBManager
import com.jetbrains.cidr.cpp.compdb.settings.CompDBProjectSettings
import com.jetbrains.cidr.cpp.compdb.settings.CompDBSettings
import java.io.File

/**
 * Feeds CLion IntelliSense from xmake's generated `compile_commands.json` by linking it into
 * CLion's Compilation Database external system (`com.intellij.clion-compdb`) and refreshing it.
 * Lives in its own content module so debugging never depends on the compdb plugin being enabled.
 */
internal object CompDBIntegration {

    private val LOG = logger<CompDBIntegration>()

    /** Link (if needed) and refresh the compilation database at [compileCommandsPath]. Returns
     * true if the attach was scheduled. The actual link+refresh runs on the EDT — `linkProject`
     * mutates external-system settings and fires listeners, which must not happen on an arbitrary
     * background thread (this is called from xmake process-termination callbacks). */
    fun attachCompileCommands(project: Project, compileCommandsPath: String): Boolean {
        val file = File(compileCommandsPath)
        if (!file.isFile) {
            LOG.warn("compile_commands.json not found at $compileCommandsPath")
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
                    LOG.info("Linked compilation database: $path")
                }
                val systemId = CompDBManager().systemId
                ExternalSystemUtil.refreshProject(
                    path,
                    ImportSpecBuilder(project, systemId).use(ProgressExecutionMode.IN_BACKGROUND_ASYNC),
                )
                LOG.info("Refreshed compilation database: $path")
            } catch (t: Throwable) {
                LOG.warn("Failed to attach compilation database: $path", t)
            }
        }
        return true
    }
}
