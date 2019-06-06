/*
 * Copyright 2018 Manuel Wrage
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

internal abstract class AbsMapBinding<K, V, M : Map<K, *>>(private val keysByKey: Map<K, Key>) :
    Binding<M>() {
    lateinit var bindingsByKey: Map<K, Binding<out V>>
    final override fun attach(component: Component) {
        bindingsByKey = keysByKey
            .mapValues { component.getBinding<V>(it.value) }
    }
}

internal class MapBinding<K, V>(keysByKey: Map<K, Key>) :
    AbsMapBinding<K, V, Map<K, V>>(keysByKey) {
    override fun get(parameters: ParametersDefinition?): Map<K, V> = bindingsByKey
        .mapValues { it.value.get() }
}

internal class LazyMapBinding<K, V>(keysByKey: Map<K, Key>) :
    AbsMapBinding<K, V, Map<K, Lazy<V>>>(keysByKey) {
    override fun get(parameters: ParametersDefinition?): Map<K, Lazy<V>> = bindingsByKey
        .mapValues { (_, binding) ->
            lazy(LazyThreadSafetyMode.NONE) { binding.get() }
        }
}

internal class ProviderMapBinding<K, V>(keysByKey: Map<K, Key>) :
    AbsMapBinding<K, V, Map<K, Provider<out V>>>(keysByKey) {
    override fun get(parameters: ParametersDefinition?): Map<K, Provider<out V>> = bindingsByKey
        .mapValues { (_, binding) ->
            provider { binding.get(it) }
        }
}

internal abstract class AbsSetBinding<E, S : Set<*>>(private val keys: Set<Key>) : Binding<S>() {
    lateinit var bindings: Set<Binding<out E>>
    final override fun attach(component: Component) {
        bindings = keys.map { component.getBinding<E>(it) }.toSet()
    }
}

internal class SetBinding<E>(keys: Set<Key>) : AbsSetBinding<E, Set<E>>(keys) {
    override fun get(parameters: ParametersDefinition?): Set<E> = bindings
        .map { it.get() }
        .toSet()
}

internal class LazySetBinding<E>(keys: Set<Key>) : AbsSetBinding<E, Set<Lazy<E>>>(keys) {
    override fun get(parameters: ParametersDefinition?): Set<Lazy<E>> = bindings
        .map { binding -> lazy(LazyThreadSafetyMode.NONE) { binding.get() } }
        .toSet()
}

internal class ProviderSetBinding<E>(keys: Set<Key>) :
    AbsSetBinding<E, Set<Provider<out E>>>(keys) {
    override fun get(parameters: ParametersDefinition?): Set<Provider<E>> = bindings
        .map { binding -> provider { binding.get(it) } }
        .toSet()
}