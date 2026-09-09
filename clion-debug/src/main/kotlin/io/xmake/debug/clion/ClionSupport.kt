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

import com.intellij.openapi.project.Project
import io.xmake.clion.XMakeBuildTargetSpec
import io.xmake.clion.XMakeClionSupport
import io.xmake.debug.clion.utils.Logger

/**
 * CLion implementation of [XMakeClionSupport], contributed through the `io.xmake.clionSupport`
 * extension point from the optional `xmake-idea.clion-debug` module. Every method degrades to a
 * no-op when the matching CLion subsystem is not present in the running IDE.
 */
class ClionSupport : XMakeClionSupport {

    override fun registerExecutableRunConfigurationType(): Boolean {
        if (!XMakeRunConfigRegistrar.isAvailable()) {
            Logger.d(TAG, "External run configuration subsystem not available")
            return false
        }
        return XMakeRunConfigRegistrar.register()
    }

    override fun syncBuildTargets(spec: XMakeBuildTargetSpec): Boolean {
        if (!CustomBuildTargetsIntegration.isAvailable()) {
            Logger.d(TAG, "Custom Build Targets subsystem not available")
            return false
        }
        return CustomBuildTargetsIntegration.syncBuildTargets(spec)
    }

    override fun attachCompileCommands(project: Project, compileCommandsPath: String): Boolean {
        if (!CompDBIntegration.isAvailable()) {
            Logger.d(TAG, "Compilation Database subsystem not available")
            return false
        }
        return CompDBIntegration.attachCompileCommands(project, compileCommandsPath)
    }

    private companion object {
        const val TAG = "ClionSupport"
    }
}
