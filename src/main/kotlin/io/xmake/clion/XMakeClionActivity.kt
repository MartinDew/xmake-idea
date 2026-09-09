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

import com.intellij.execution.ExecutionTargetListener
import com.intellij.execution.ExecutionTargetManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import io.xmake.project.profile.XMakeBuildProfileManager
import io.xmake.utils.SystemUtils
import io.xmake.utils.info.XMakeInfo
import io.xmake.utils.info.XMakeInfoManager

/**
 * Wires the CLion-only integrations when an xmake project opens in CLion: it registers the native
 * "Xmake Executable" run configuration type and re-publishes the active build profile's targets
 * whenever the target list, the profiles, or the active execution target changes.
 *
 * No-ops on IDEA Community, where [XMakeClionSupport] has no extension.
 */
class XMakeClionActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (!SystemUtils.isXMakeProject(project)) return
        val support = XMakeClionSupport.find() ?: return

        support.registerExecutableRunConfigurationType()

        val sync = XMakeClionTargetSync.getInstance(project)
        val connection = project.messageBus.connect()
        connection.subscribe(
            XMakeInfoManager.XMAKE_INFO_TOPIC,
            object : XMakeInfoManager.XMakeInfoListener {
                override fun onXMakeInfoUpdated(xmakeInfo: XMakeInfo) = sync.requestSync()
            },
        )
        connection.subscribe(
            XMakeBuildProfileManager.TOPIC,
            XMakeBuildProfileManager.Listener { sync.requestSync() },
        )
        connection.subscribe(
            ExecutionTargetManager.TOPIC,
            ExecutionTargetListener { sync.requestSync() },
        )

        sync.requestSync()
    }
}
