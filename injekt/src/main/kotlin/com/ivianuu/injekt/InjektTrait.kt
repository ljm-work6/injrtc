/*
 * Copyright 2019 Manuel Wrage
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
 * Holds a [Component] and allows for shorter syntax
 *
 * Example:
 *
 * ```
 * class MainActivity : Activity(), InjektTrait {
 *
 *     override val component = component { ... }
 *
 *     private val dep1: Dependency1 by inject()
 *     private val dep2: Dependency2 by inject()
 *
 * }
 * ```
 *
 */
interface InjektTrait {
    val component: Component

    fun <T> InjektTrait.get(
        type: Type<T>,
        name: Any? = null,
        parameters: ParametersDefinition? = null
    ): T = component.get(type, name, parameters)

    fun <T> InjektTrait.inject(
        type: Type<T>,
        name: Any? = null,
        parameters: ParametersDefinition? = null
    ): kotlin.Lazy<T> = lazy(LazyThreadSafetyMode.NONE) { component.get(type, name, parameters) }

}

inline fun <reified T> InjektTrait.get(
    name: Any? = null,
    noinline parameters: ParametersDefinition? = null
): T = get(type = typeOf(), name = name, parameters = parameters)

inline fun <reified T> InjektTrait.inject(
    name: Any? = null,
    noinline parameters: ParametersDefinition? = null
): kotlin.Lazy<T> = inject(type = typeOf(), name = name, parameters = parameters)