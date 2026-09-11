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
package io.xmake.clion.run

import com.intellij.execution.ExecutionException
import com.intellij.execution.runners.ExecutionEnvironment
import com.jetbrains.cidr.cpp.execution.CLionLauncher
import com.jetbrains.cidr.cpp.toolchains.CPPEnvironment
import com.jetbrains.cidr.cpp.toolchains.CPPToolchains
import io.xmake.clion.XMakeBuiltTarget
import io.xmake.clion.XMakeClionLaunchBridge
import java.io.File
import java.nio.file.Path

internal class XMakeClionLauncher(
    environment: ExecutionEnvironment,
    private val xmakeConfiguration: XMakeClionRunConfiguration,
) : CLionLauncher(environment, xmakeConfiguration) {

    override fun getRunFileAndEnvironment(): Pair<File, CPPEnvironment> {
        val built = builtTarget()
        val toolchain = CPPToolchains.getInstance().defaultToolchain
            ?: throw ExecutionException(
                "CLion has no default toolchain. Configure one in Settings | Build, Execution, Deployment | Toolchains.",
            )
        return built.executable to CPPEnvironment(toolchain)
    }

    override fun getDefaultWorkingDir(executable: Path): String = builtTarget().workingDirectory

    private fun builtTarget(): XMakeBuiltTarget =
        XMakeClionLaunchBridge.resolveBuilt(project, executionEnvironment.executionTarget, xmakeConfiguration.runTarget)
}
