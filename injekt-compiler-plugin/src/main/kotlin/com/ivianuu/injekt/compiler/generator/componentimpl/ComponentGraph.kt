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

package com.ivianuu.injekt.compiler.generator.componentimpl

import com.ivianuu.injekt.Assisted
import com.ivianuu.injekt.Binding
import com.ivianuu.injekt.compiler.generator.Callable
import com.ivianuu.injekt.compiler.generator.DeclarationStore
import com.ivianuu.injekt.compiler.generator.ModuleDescriptor
import com.ivianuu.injekt.compiler.generator.TypeRef
import com.ivianuu.injekt.compiler.generator.asNameId
import com.ivianuu.injekt.compiler.generator.getInjectConstructor
import com.ivianuu.injekt.compiler.generator.getSubstitutionMap
import com.ivianuu.injekt.compiler.generator.isAssignable
import com.ivianuu.injekt.compiler.generator.render
import com.ivianuu.injekt.compiler.generator.substitute
import org.jetbrains.kotlin.backend.common.pop
import org.jetbrains.kotlin.backend.common.push
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

@Binding
class BindingGraph(
    @Assisted private val owner: ComponentImpl,
    collectionsFactory: (ComponentImpl, BindingCollections?) -> BindingCollections,
    private val declarationStore: DeclarationStore,
    private val componentImplFactory: (
        TypeRef,
        Name,
        ComponentImpl?,
    ) -> ComponentImpl,
) {

    private val parent = owner.parent?.graph

    private val componentType = owner.componentType

    private val moduleBindingCallables = mutableListOf<CallableWithReceiver>()

    private val collections: BindingCollections = collectionsFactory(owner, parent?.collections)

    val resolvedBindings = mutableMapOf<TypeRef, BindingNode>()

    private val chain = mutableListOf<BindingNode>()

    init {
        fun ModuleDescriptor.collectBindings(
            parentCallable: Callable?,
            parentAccessExpression: ComponentExpression,
        ) {
            val thisAccessExpression: ComponentExpression = {
                parentAccessExpression()
                if (parentCallable != null) {
                    emit(".")
                    emit("${parentCallable!!.name}")
                    if (parentCallable.isCall) emit("()")
                }
            }

            for (callable in callables) {
                if (callable.contributionKind == null) continue
                when (callable.contributionKind) {
                    Callable.ContributionKind.BINDING -> moduleBindingCallables += CallableWithReceiver(
                        callable,
                        thisAccessExpression,
                        emptyMap()
                    )
                    Callable.ContributionKind.MAP_ENTRIES -> {
                        collections.addMapEntries(
                            CallableWithReceiver(
                                callable,
                                thisAccessExpression,
                                emptyMap()
                            )
                        )
                    }
                    Callable.ContributionKind.SET_ELEMENTS -> {
                        collections.addSetElements(
                            CallableWithReceiver(
                                callable,
                                thisAccessExpression,
                                emptyMap()
                            )
                        )
                    }
                    Callable.ContributionKind.MODULE -> {
                        declarationStore.moduleForType(callable.type)
                            .collectBindings(callable, thisAccessExpression)
                    }
                }.let {}
            }
        }

        declarationStore.moduleForType(componentType)
            .collectBindings(null) { emit("this@${owner.name}") }
    }

    fun checkRequests(requests: List<BindingRequest>) {
        requests.forEach { check(it) }
    }

    private fun check(binding: BindingNode) {
        if (binding in chain) {
            val relevantSubchain = chain.subList(
                chain.indexOf(binding), chain.lastIndex
            )
            if (relevantSubchain.any {
                    it is ProviderBindingNode ||
                            it.type.classifier.fqName.asString().startsWith("kotlin.Function")
                }) return
            error(
                "Circular dependency ${relevantSubchain.map { it.type.render() }} already contains ${binding.type.render()} $chain"
            )
        }
        chain.push(binding)
        binding
            .dependencies
            .forEach { check(it) }
        (binding as? ChildImplBindingNode)?.childComponentImpl?.initialize()
        chain.pop()
    }

    private fun check(request: BindingRequest) {
        check(getBinding(request))
    }

    fun getBinding(request: BindingRequest): BindingNode {
        var binding = getBindingOrNull(request.type)
        if (binding != null) return binding

        if (request.type.isMarkedNullable) {
            binding = NullBindingNode(request.type, owner)
            resolvedBindings[request.type] = binding
            return binding
        }

        error(
            buildString {
                var indendation = ""
                fun indent() {
                    indendation = "$indendation    "
                }
                appendLine("No binding found for '${request.type.render()}' in '${componentType.render()}':")
                /*chain.push(ChainElement.Binding(type))
                chain.forEachIndexed { index, element ->
                    if (index == 0) {
                        appendLine("${indendation}runReader call '${element}'")
                    } else {
                        when (element) {
                            is ChainElement.Call -> {
                                val lastElement = chain.getOrNull(index - 1)
                                if (lastElement is ChainElement.Binding) {
                                    appendLine("${indendation}binding by '${element}'")
                                } else {
                                    appendLine("${indendation}calls reader '${element}'")
                                }
                            }
                            is ChainElement.Binding -> appendLine("${indendation}requires binding '${element}'")
                        }

                    }
                    indent()
                }
                chain.pop()*/
                // todo
            }
        )
    }

    private fun getBindingOrNull(type: TypeRef): BindingNode? {
        var binding = resolvedBindings[type]
        if (binding != null) return binding

        val bindings = bindingsForType(type)

        if (bindings.size > 1) {
            val mostSpecific = bindings.getExact(type)
            if (mostSpecific != null) {
                binding = mostSpecific
            } else {
                error(
                    "Multiple bindings found for '${type.render()}' at:\n${
                    bindings
                        .joinToString("\n") { "    '${it.origin.orUnknown()}'" }
                    }"
                )
            }
        } else {
            binding = bindings.singleOrNull()
        }

        binding?.let {
            resolvedBindings[type] = it
            return it
        }

        if (type.isBinding || type.typeArguments.lastOrNull()?.isBinding == true) {
            val bindingType = if (type.isBinding) type else type.typeArguments.last()
            binding = declarationStore.callableForDescriptor(
                declarationStore.classDescriptorForFqName(bindingType.classifier.fqName)
                    .getInjectConstructor()!!
            )
                .let { callable ->
                    val substitutionMap = type.getSubstitutionMap(callable.type)
                    CallableBindingNode(
                        type = type,
                        rawType = callable.type.substitute(substitutionMap),
                        owner = owner,
                        dependencies = callable.valueParameters
                            .filterNot { it.isAssisted }
                            .map {
                                BindingRequest(
                                    it.type.substitute(substitutionMap),
                                    callable.fqName.child(it.name)
                                )
                            },
                        valueParameters = callable.valueParameters.map {
                            it.copy(
                                type = it.type.substitute(substitutionMap)
                            )
                        },
                        origin = callable.fqName,
                        targetComponent = callable.targetComponent,
                        receiver = null,
                        callable = callable
                    )
                }

            binding.let {
                resolvedBindings[type] = it
                return it
            }
        }

        parent?.getBindingOrNull(type)?.let {
            resolvedBindings[type] = it
            return it
        }

        return null
    }

    private fun bindingsForType(type: TypeRef): List<BindingNode> = buildList<BindingNode> {
        if (type == componentType) {
            this += SelfBindingNode(
                type = type,
                component = owner
            )
        }

        if (type.isFunction && type.typeArguments.last().isChildComponent) {
            // todo check if the arguments match the constructor arguments of the child component
            val childComponentType = type.typeArguments.last()
            val existingComponents = mutableSetOf<TypeRef>()
            var currentComponent: ComponentImpl? = owner
            while (currentComponent != null) {
                existingComponents += currentComponent.componentType
                currentComponent = currentComponent.parent
            }
            if (childComponentType !in existingComponents) {
                val componentImpl = componentImplFactory(
                    childComponentType,
                    owner.contextTreeNameProvider("C").asNameId(),
                    owner
                )
                this += ChildImplBindingNode(
                    type = type,
                    owner = owner,
                    origin = null,
                    childComponentImpl = componentImpl
                )
            }
        }

        if (type.isFunctionAlias) {
            val callable = declarationStore.functionForAlias(type)
            val substitutionMap = callable.typeParameters
                .zip(type.typeArguments)
                .toMap()
            this += FunctionAliasBindingNode(
                type = type,
                rawType = callable.type,
                owner = owner,
                dependencies = callable.valueParameters
                    .filterNot { it.isAssisted }
                    .map {
                        BindingRequest(
                            it.type.substitute(substitutionMap),
                            callable.fqName.child(it.name)
                        )
                    },
                valueParameters = callable.valueParameters.map {
                    it.copy(
                        type = it.type.substitute(substitutionMap)
                    )
                },
                origin = callable.fqName,
                targetComponent = callable.targetComponent,
                receiver = null,
                callable = callable
            )
        }

        if (type.isFunction && type.typeArguments.size == 1 &&
                !type.typeArguments.last().isChildComponent) {
            this += ProviderBindingNode(
                type = type,
                owner = owner,
                dependencies = listOf(
                    BindingRequest(
                        type.typeArguments.single(),
                        FqName.ROOT // todo
                    )
                ),
                FqName.ROOT // todo
            )
        }

        this += moduleBindingCallables
            .filter { type.isAssignable(it.callable.type) }
            .map { (callable, receiver) ->
                val substitutionMap = type.getSubstitutionMap(callable.type)
                CallableBindingNode(
                    type = type,
                    rawType = callable.type,
                    owner = owner,
                    dependencies = callable.valueParameters
                        .filterNot { it.isAssisted }
                        .map {
                            BindingRequest(
                                it.type.substitute(substitutionMap),
                                callable.fqName.child(it.name)
                            )
                        },
                    valueParameters = callable.valueParameters.map {
                        it.copy(
                            type = it.type.substitute(substitutionMap)
                        )
                    },
                    origin = callable.fqName,
                    targetComponent = callable.targetComponent,
                    receiver = receiver,
                    callable = callable
                )
            }

        this += collections.getNodes(type)
    }

    private fun List<BindingNode>.getExact(requested: TypeRef): BindingNode? =
        singleOrNull { it.rawType == requested }
}

private fun FqName?.orUnknown(): String = this?.asString() ?: "unknown origin"

@Binding
class BindingCollections(
    @Assisted private val owner: ComponentImpl,
    @Assisted private val parent: BindingCollections?,
) {

    private val thisMapEntries = mutableMapOf<TypeRef, MutableList<CallableWithReceiver>>()
    private val thisSetElements = mutableMapOf<TypeRef, MutableList<CallableWithReceiver>>()

    fun addMapEntries(entries: CallableWithReceiver) {
        thisMapEntries.getOrPut(entries.callable.type) { mutableListOf() } += entries
    }

    fun addSetElements(elements: CallableWithReceiver) {
        thisSetElements.getOrPut(elements.callable.type) { mutableListOf() } += elements
    }

    private val mapEntriesByType = mutableMapOf<TypeRef, List<CallableWithReceiver>>()
    private fun getMapEntries(type: TypeRef): List<CallableWithReceiver> {
        return mapEntriesByType.getOrPut(type) {
            (parent?.getMapEntries(type) ?: emptyList()) +
                (thisMapEntries[type] ?: emptyList())
        }
    }

    private val setElementsByType = mutableMapOf<TypeRef, List<CallableWithReceiver>>()
    private fun getSetElements(type: TypeRef): List<CallableWithReceiver> {
        return setElementsByType.getOrPut(type) {
            (parent?.getSetElements(type) ?: emptyList()) +
                (thisSetElements[type] ?: emptyList())
        }
    }

    fun getNodes(type: TypeRef): List<BindingNode> {
        return listOfNotNull(
            getMapEntries(type)
                .takeIf { it.isNotEmpty() }
                ?.let { entries ->
                    MapBindingNode(
                        type = type,
                        owner = owner,
                        dependencies = entries.flatMap { (entry, _, substitutionMap) ->
                            entry.valueParameters
                                .filterNot { it.isAssisted }
                                .map {
                                    BindingRequest(
                                        it.type.substitute(substitutionMap),
                                        entry.fqName.child(it.name)
                                    )
                                }
                        },
                        entries = entries
                    )
                },
            getSetElements(type)
                .takeIf { it.isNotEmpty() }
                ?.let { elements ->
                    SetBindingNode(
                        type = type,
                        owner = owner,
                        dependencies = elements.flatMap { (element, _, substitutionMap) ->
                            element.valueParameters
                                .filterNot { it.isAssisted }
                                .map {
                                    BindingRequest(
                                        it.type.substitute(substitutionMap),
                                        element.fqName.child(it.name)
                                    )
                                }
                        },
                        elements = elements
                    )
                }
        )
    }
}
