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

package com.ivianuu.injekt

/**
 * Creates instances of type [T]
 */
typealias Definition<T> = Component.(Parameters) -> T

internal class DefinitionBinding<T>(
    private val definition: Definition<T>
) : UnlinkedBinding<T>() {
    override fun link(component: Component): LinkedBinding<T> = Linked(component, definition)
    private class Linked<T>(
        private val component: Component,
        private val definition: Definition<T>
    ) : LinkedBinding<T>() {
        override fun invoke(parameters: Parameters): T =
            definition(component, parameters)
    }
}
