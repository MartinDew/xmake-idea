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

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.jetbrains.cidr.ArchitectureType
import com.jetbrains.cidr.execution.TrivialRunParameters
import com.jetbrains.cidr.execution.debugger.CidrLocalDebugProcess
import io.xmake.debug.DapDriverDetector
import io.xmake.debug.XMakeDebugLaunch
import io.xmake.debug.XMakeDebugSupport
import io.xmake.debug.clion.utils.Logger
import java.io.File

/**
 * Debugs xmake targets with CLion's own in-process CIDR debugger ([CidrLocalDebugProcess]) rather
 * than the platform DAP client: the debuggee is launched directly and the DAP driver
 * (`lldb-dap` / `gdb -i dap`) is attached through CLion's [XMakeDapDriverConfiguration], so
 * disassembly, registers, and memory views work without a separate external adapter setup.
 */
class ClionDebugSupport : XMakeDebugSupport {

    override fun createProcessStarter(
        launch: XMakeDebugLaunch,
        environment: ExecutionEnvironment,
    ): XDebugProcessStarter {
        Logger.i(
            TAG,
            "Creating CIDR debug process: project=${environment.project.name}, " +
                "driver=${launch.driver.displayName}, target=${launch.executablePath}",
        )
        return object : XDebugProcessStarter() {
            override fun start(session: XDebugSession): XDebugProcess = startDebugProcess(launch, session)
        }
    }

    private fun startDebugProcess(launch: XMakeDebugLaunch, session: XDebugSession): XDebugProcess {
        val project = session.project
        val driverName = when (launch.driver.type) {
            DapDriverDetector.DapDriverType.GDB_DAP -> GDB_DAP
            else -> LLDB_DAP
        }
        val workingDirectory = launch.workingDirectory.ifBlank { project.basePath }
            ?: File(launch.executablePath).absoluteFile.parent

        val driverConfiguration = XMakeDapDriverConfiguration(
            project,
            launch.driver.path,
            driverName,
            launch.launchConfiguration,
            launch.environment,
        )
        val inferiorCommandLine = GeneralCommandLine(launch.executablePath)
            .withWorkDirectory(workingDirectory)
            .withEnvironment(launch.environment)
            .withParameters(launch.arguments)

        val parameters = TrivialRunParameters(driverConfiguration, inferiorCommandLine, ArchitectureType.UNKNOWN)
        val consoleBuilder = TextConsoleBuilderFactory.getInstance().createBuilder(project)
        return CidrLocalDebugProcess(parameters, session, consoleBuilder).also { it.start() }
    }

    private companion object {
        const val TAG = "ClionDebugSupport"
        const val GDB_DAP = "gdb-dap"
        const val LLDB_DAP = "lldb-dap"
    }
}
