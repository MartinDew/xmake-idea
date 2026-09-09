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

import com.intellij.execution.BeforeRunTask
import com.intellij.execution.BeforeRunTaskProvider
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import io.xmake.project.console.xmakeConsoleService
import io.xmake.project.profile.XMakeBuildProfile
import io.xmake.run.command.XMakeConsoleOptions
import io.xmake.run.command.withProfileCommands
import io.xmake.run.target.activeOrSingleXMakeBuildProfile
import io.xmake.utils.SystemUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.swing.Icon

/**
 * Before-run step attached to every native "Xmake Executable" run configuration. It runs
 * `xmake config` + `xmake build <target>` for the **active** [XMakeBuildProfile] — using that
 * profile's toolkit, `xmake f` arguments, and isolated `XMAKE_CONFIGDIR` — so the binary CLion then
 * runs or debugs always matches the selected build profile. It replaces CLion's own
 * profile-blind "build the custom target" step (removed by `CLionBuildTargetRegistrar`).
 *
 * The target name is the run configuration's name (that is how the configs are generated).
 */
class XMakeExecutableBuildBeforeRunTask :
    BeforeRunTask<XMakeExecutableBuildBeforeRunTask>(PROVIDER_ID) {

    companion object {
        @JvmField
        val PROVIDER_ID: Key<XMakeExecutableBuildBeforeRunTask> =
            Key.create("XMake.ExecutableBuildBeforeRunTask")
    }
}

class XMakeExecutableBuildBeforeRunTaskProvider :
    BeforeRunTaskProvider<XMakeExecutableBuildBeforeRunTask>() {

    override fun getId(): Key<XMakeExecutableBuildBeforeRunTask> = XMakeExecutableBuildBeforeRunTask.PROVIDER_ID

    override fun getName(): String = "Build XMake target for the active profile"

    override fun getIcon(): Icon = AllIcons.Actions.Compile

    override fun getTaskIcon(task: XMakeExecutableBuildBeforeRunTask): Icon = AllIcons.Actions.Compile

    override fun isConfigurable(): Boolean = false

    override fun createTask(runConfiguration: RunConfiguration): XMakeExecutableBuildBeforeRunTask? =
        if (runConfiguration.type.id == SystemUtils.XMAKE_EXECUTABLE_CONFIG_TYPE_ID) {
            XMakeExecutableBuildBeforeRunTask().apply { isEnabled = true }
        } else {
            null
        }

    override fun executeTask(
        context: DataContext,
        configuration: RunConfiguration,
        environment: ExecutionEnvironment,
        task: XMakeExecutableBuildBeforeRunTask,
    ): Boolean {
        val project = configuration.project
        val profile = project.activeOrSingleXMakeBuildProfile ?: run {
            notifyError(project, "Select an XMake build profile before running this configuration.")
            return false
        }
        return try {
            runBlockingCancellable { buildTarget(project, profile, configuration.name) }
            true
        } catch (error: ProcessCanceledException) {
            throw error
        } catch (error: Exception) {
            notifyError(project, "XMake build failed: ${error.message}")
            false
        }
    }

    private suspend fun buildTarget(project: Project, profile: XMakeBuildProfile, target: String) {
        val console = project.xmakeConsoleService.awaitReady()
        withContext(Dispatchers.EDT) { console.clear() }
        project.withProfileCommands(profile) { execution ->
            execution.execute(
                console,
                createConfigure(),
                XMakeConsoleOptions(showConsole = true, showProblems = true),
            )
            execution.execute(
                console,
                createTargetBuild(target),
                XMakeConsoleOptions(showConsole = true, showProblems = true, showExitCode = true),
            )
        }
    }

    private fun notifyError(project: Project, message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("XMake.NotificationGroup")
            .createNotification("Xmake Executable", message, NotificationType.ERROR)
            .notify(project)
    }
}
