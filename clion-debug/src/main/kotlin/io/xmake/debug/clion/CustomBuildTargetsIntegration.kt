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
 *
 * @author      ruki
 * @file        CustomBuildTargetsIntegration.kt
 *
 */
package io.xmake.debug.clion

import com.intellij.openapi.application.ApplicationManager
import io.xmake.clion.XMakeBuildTargetSpec
import io.xmake.debug.clion.utils.Logger

/**
 * Registers each xmake target with CLion's Custom Build Targets subsystem
 * (`com.jetbrains.cidr.cpp.execution.external.build`), so the native "Build Target" action works
 * and gutter Run/Debug markers appear on `main()`, then creates a ready-to-run "Xmake Executable"
 * configuration per binary target.
 *
 * CLion-only; lives in the optional `xmake-idea.clion-debug` module.
 */
object CustomBuildTargetsIntegration {

    private const val TAG = "CustomBuildTargetsIntegration"

    /** Project-tool group all xmake build/clean tools live under (replaced on each sync). */
    private const val TOOL_GROUP = "XMake"

    /** Whether CLion's Custom Build Targets subsystem is present in this IDE. */
    @JvmStatic
    fun isAvailable(): Boolean = try {
        Class.forName("com.jetbrains.cidr.cpp.execution.external.build.CLionExternalBuildManager")
        true
    } catch (t: Throwable) {
        false
    }

    /**
     * Replace the plugin-managed CLion build targets with the ones in [spec]. Returns true if the
     * sync was scheduled/applied.
     */
    @JvmStatic
    fun syncBuildTargets(spec: XMakeBuildTargetSpec): Boolean {
        val targets = spec.targets.filter { it.name.isNotBlank() }
        if (targets.isEmpty()) {
            Logger.d(TAG, "No xmake targets to register")
            return false
        }
        Logger.i(TAG, "Registering ${targets.size} xmake target(s) with CLion: " + targets.joinToString { it.name })

        // setTools / setTargets mutate project-level persistent state and fire listeners, so run on
        // the EDT (this is invoked from background sync coroutines).
        ApplicationManager.getApplication().invokeLater {
            if (spec.project.isDisposed) return@invokeLater
            try {
                CLionBuildTargetRegistrar.register(
                    spec.project,
                    spec.xmakeBinary,
                    spec.workingDirectory,
                    spec.projectName,
                    TOOL_GROUP,
                    targets,
                )
                Logger.i(TAG, "Installed ${targets.size} CLion custom build target(s)")
            } catch (t: Throwable) {
                Logger.e(TAG, "Failed to register custom build targets: ${t.message}")
                t.printStackTrace()
            }
        }
        return true
    }
}
