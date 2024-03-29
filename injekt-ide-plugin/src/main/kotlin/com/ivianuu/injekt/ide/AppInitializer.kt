/*
 * Copyright 2020 Manuel Wrage
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ivianuu.injekt.ide

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.ivianuu.injekt.compiler.analysis.GivenCallResolutionInterceptorExtension
import com.ivianuu.injekt.compiler.analysis.InjektDiagnosticSuppressor
import com.ivianuu.injekt.compiler.analysis.InjektStorageComponentContainerContributor
import com.ivianuu.injekt.compiler.analysis.InjektTypeResolutionInterceptorExtension
import org.jetbrains.kotlin.extensions.StorageComponentContainerContributor
import org.jetbrains.kotlin.extensions.internal.CandidateInterceptor
import org.jetbrains.kotlin.extensions.internal.TypeResolutionInterceptor
import org.jetbrains.kotlin.resolve.diagnostics.DiagnosticSuppressor

@Suppress("UnstableApiUsage")
class AppInitializer : ApplicationInitializedListener {
    override fun componentsInitialized() {
        val app = ApplicationManager.getApplication()
        app
            .messageBus.connect(app)
            .subscribe(
                ProjectManager.TOPIC,
                object : ProjectManagerListener {
                    override fun projectOpened(project: Project) {
                        StorageComponentContainerContributor.registerExtension(
                            project,
                            InjektStorageComponentContainerContributor { latestBindingContext = it }
                        )
                        CandidateInterceptor.registerExtension(
                            project,
                            GivenCallResolutionInterceptorExtension()
                        )
                        TypeResolutionInterceptor.registerExtension(
                            project,
                            InjektTypeResolutionInterceptorExtension()
                        )

                        @Suppress("DEPRECATION")
                        Extensions.getRootArea().getExtensionPoint(DiagnosticSuppressor.EP_NAME)
                            .registerExtension(InjektDiagnosticSuppressor())
                    }
                }
            )
    }
}
