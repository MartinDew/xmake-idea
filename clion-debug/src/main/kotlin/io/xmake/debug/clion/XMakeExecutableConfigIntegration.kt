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

import com.intellij.execution.BeforeRunTask
import com.intellij.execution.RunManager
import com.intellij.execution.RunManagerEx
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.jetbrains.cidr.cpp.execution.external.run.CLionExternalRunConfiguration
import com.jetbrains.cidr.execution.ExecutableData
import io.xmake.clion.XMakeExecutableBuildBeforeRunTask
import io.xmake.clion.XMakeExecutableTargetSpec
import io.xmake.debug.clion.utils.Logger

/**
 * Creates and refreshes one native "Xmake Executable" run configuration
 * ([CLionExternalRunConfiguration], type [XMakeExecutableRunConfigurationType]) per executable xmake
 * target. Each config launches the target's resolved binary directly (native run + CLion debugger)
 * and builds it through the plugin's profile-aware [XMakeExecutableBuildBeforeRunTask] — it is
 * deliberately *not* bound to a CLion Custom Build Target, whose build ignores the selected profile.
 */
object XMakeExecutableConfigIntegration {

    private const val TAG = "XMakeExecutableConfigIntegration"

    /** Whether CLion's external run configuration subsystem is present in this IDE. */
    @JvmStatic
    fun isAvailable(): Boolean = try {
        Class.forName("com.jetbrains.cidr.cpp.execution.external.run.CLionExternalRunConfiguration")
        true
    } catch (t: Throwable) {
        false
    }

    /** Sync the "Xmake Executable" configs to [spec]. Returns `true` once the update is scheduled. */
    @JvmStatic
    fun syncExecutableRunConfigurations(spec: XMakeExecutableTargetSpec): Boolean {
        if (spec.targets.isEmpty()) return false
        ApplicationManager.getApplication().invokeLater {
            if (spec.project.isDisposed) return@invokeLater
            try {
                spec.targets.forEach { target ->
                    ensureRunConfiguration(spec.project, target.name, target.executablePath)
                }
                Logger.i(TAG, "Synced ${spec.targets.size} Xmake Executable run configuration(s)")
            } catch (t: Throwable) {
                Logger.e(TAG, "Failed to sync Xmake Executable run configurations: ${t.message}")
                t.printStackTrace()
            }
        }
        return true
    }

    private fun ensureRunConfiguration(project: Project, name: String, executablePath: String) {
        val type: ConfigurationType? =
            ConfigurationTypeUtil.findConfigurationType(XMakeExecutableRunConfigurationType::class.java)
        val factory = when (type) {
            null -> null
            else -> type.configurationFactories.firstOrNull()
        }
        if (factory == null) {
            Logger.w(TAG, "The Xmake Executable run configuration type is not registered")
            return
        }
        val runManager = RunManager.getInstance(project)

        val existing = runManager.allSettings.firstOrNull { settings ->
            settings.type.id == XMakeExecutableRunConfigurationType.ID && settings.name == name
        }
        val configuration = if (existing != null) {
            bindExecutable(existing.configuration, executablePath)
            existing.configuration
        } else {
            val settings = runManager.createConfiguration(name, factory)
            bindExecutable(settings.configuration, executablePath)
            runManager.addConfiguration(settings)
            settings.configuration
        }
        applyProfileBuildStep(project, configuration)
    }

    /** Rewrite the launch executable (mode-specific, so it is refreshed on every profile change). */
    private fun bindExecutable(configuration: RunConfiguration, executablePath: String) {
        (configuration as? CLionExternalRunConfiguration)?.executableData = ExecutableData(executablePath)
    }

    /**
     * Make the profile-aware XMake build the config's only before-run step, replacing CLion's own
     * "build the custom target" step so the binary is built exactly once, for the active profile.
     */
    private fun applyProfileBuildStep(project: Project, configuration: RunConfiguration) {
        val task = XMakeExecutableBuildBeforeRunTask().apply { isEnabled = true }
        RunManagerEx.getInstanceEx(project)
            .setBeforeRunTasks(configuration, listOf<BeforeRunTask<*>>(task))
    }
}
