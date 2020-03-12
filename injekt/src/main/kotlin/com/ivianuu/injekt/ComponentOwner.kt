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
 * Holds a [Component] and allows for shorter syntax and lazy construction of a component
 *
 * Example:
 *
 * ```
 * class MainActivity : Activity(), ComponentOwner {
 *
 *     override val component = Component { ... }
 *
 *     private val dep1: Dependency1 by getLazy()
 *     private val dep2: Dependency2 by getLazy()
 *
 * }
 * ```
 *
 */
interface ComponentOwner {
    /**
     * The [Component] which will be used to retrieve dependencies
     */
    val component: Component

    /**
     * @see Component.get
     */
    fun <T> get(
        key: Key<T>,
        parameters: Parameters = emptyParameters()
    ): T = component.get(key, parameters)

    /**
     * Lazy version of [get]
     *
     * @param key the key of the instance
     * @param parameters optional parameters to construct the instance
     * @return the instance

     * @see Component.get
     */
    fun <T> getLazy(
        key: Key<T>,
        parameters: () -> Parameters = { emptyParameters() }
    ): kotlin.Lazy<T> = lazy(LazyThreadSafetyMode.NONE) { component.get(key, parameters()) }
}

/**
 * @see Component.get
 */
inline fun <reified T> ComponentOwner.get(
    qualifier: Qualifier = Qualifier.None,
    parameters: Parameters = emptyParameters()
): T = get(keyOf(qualifier = qualifier), parameters)

/**
 * Lazy version of [get]
 *
 * @see Component.get
 */
inline fun <reified T> ComponentOwner.getLazy(
    qualifier: Qualifier = Qualifier.None,
    noinline parameters: () -> Parameters = { emptyParameters() }
): kotlin.Lazy<T> = getLazy(key = keyOf(qualifier), parameters = parameters)