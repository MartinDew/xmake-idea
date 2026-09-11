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

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

/** Bridges xmake's generated compile database into CLion's own IntelliSense, without coupling the core plugin to CLion. */
interface XMakeClionSupport {

    /** Link (if needed) and refresh CLion's Compilation Database from [compileCommandsPath]. */
    fun attachCompileCommands(project: Project, compileCommandsPath: String): Boolean

    companion object {
        private val EP_NAME = ExtensionPointName.create<XMakeClionSupport>("io.xmake.clionSupport")

        internal fun find(): XMakeClionSupport? = EP_NAME.extensionList.firstOrNull()

        internal fun isAvailable(): Boolean = EP_NAME.extensionList.isNotEmpty()
    }
}
