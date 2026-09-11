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
package io.xmake.debug.clion.native

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.util.SystemInfo
import com.jetbrains.cidr.execution.Installer
import com.jetbrains.cidr.execution.RunParameters
import com.jetbrains.cidr.execution.TrivialInstaller
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriverConfiguration
import com.jetbrains.cidr.execution.debugger.backend.gdb.GDBDriverConfiguration
import com.jetbrains.cidr.execution.debugger.backend.lldb.LLDBDriverConfiguration
import io.xmake.debug.DapDriverDetector
import io.xmake.debug.XMakeDebugLaunch
import io.xmake.debug.clion.utils.Logger

/**
 * Drives CLion's own native GDB/LLDB engine ([com.jetbrains.cidr.execution.debugger.CidrLocalDebugProcess])
 * directly — no external DAP driver process, unlike [io.xmake.debug.clion.dap.XMakeDapLaunchArguments].
 */
internal class XMakeRunParameters(private val launch: XMakeDebugLaunch) : RunParameters() {

    override fun getInstaller(): Installer = TrivialInstaller(
        GeneralCommandLine(launch.executablePath)
            .withParameters(launch.arguments)
            .withEnvironment(launch.environment)
            .withWorkDirectory(launch.workingDirectory.ifBlank { null }),
    )

    override fun getDebuggerDriverConfiguration(): DebuggerDriverConfiguration =
        when (launch.driver.type) {
            DapDriverDetector.DapDriverType.GDB_DAP -> object : GDBDriverConfiguration() {
                override fun getGDBExecutablePath(): String = launch.driver.path
            }

            else -> if (SystemInfo.isWindows) {
                // CLion rejects a custom LLDB path on Windows ("Custom LLDB is not supported on
                // Windows") — only the bundled LLDB works there, so leave it unconfigured.
                Logger.i(TAG, "Using CLion's bundled LLDB on Windows instead of ${launch.driver.path}")
                LLDBDriverConfiguration()
            } else {
                LLDBDriverConfiguration().apply { customLLDBPath = launch.driver.path }
            }
        }

    override fun getArchitectureId(): String? = null

    private companion object {
        const val TAG = "XMakeRunParameters"
    }
}
