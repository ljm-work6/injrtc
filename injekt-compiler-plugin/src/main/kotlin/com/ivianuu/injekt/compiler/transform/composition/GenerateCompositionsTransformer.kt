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

package com.ivianuu.injekt.compiler.transform.composition

import com.ivianuu.injekt.compiler.CompositionSymbols
import com.ivianuu.injekt.compiler.InjektFqNames
import com.ivianuu.injekt.compiler.InjektNameConventions
import com.ivianuu.injekt.compiler.NameProvider
import com.ivianuu.injekt.compiler.addMetadataIfNotLocal
import com.ivianuu.injekt.compiler.buildClass
import com.ivianuu.injekt.compiler.child
import com.ivianuu.injekt.compiler.getIrClass
import com.ivianuu.injekt.compiler.transform.AbstractInjektTransformer
import com.ivianuu.injekt.compiler.transform.InjektDeclarationIrBuilder
import com.ivianuu.injekt.compiler.transform.InjektDeclarationStore
import com.ivianuu.injekt.compiler.withNoArgAnnotations
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.addChild
import org.jetbrains.kotlin.backend.common.ir.createImplicitParameterDeclarationWithWrappedDescriptor
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionReferenceImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.constants.ArrayValue
import org.jetbrains.kotlin.resolve.constants.KClassValue
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

class GenerateCompositionsTransformer(
    pluginContext: IrPluginContext,
    private val declarationStore: InjektDeclarationStore,
    private val compositionAggregateGenerator: CompositionAggregateGenerator
) : AbstractInjektTransformer(pluginContext) {

    private val compositionSymbols = CompositionSymbols(pluginContext)

    override fun visitModuleFragment(declaration: IrModuleFragment): IrModuleFragment {
        val generateCompositionsCalls = mutableListOf<Pair<IrCall, IrFile>>()

        declaration.transformChildrenVoid(object : IrElementTransformerVoidWithContext() {
            override fun visitCall(expression: IrCall): IrExpression {
                if (expression.symbol.descriptor.fqNameSafe.asString() ==
                    "com.ivianuu.injekt.composition.generateCompositions"
                ) {
                    generateCompositionsCalls += expression to currentFile
                }
                return super.visitCall(expression)
            }
        })

        if (generateCompositionsCalls.isEmpty()) return super.visitModuleFragment(declaration)

        val compositionsPackage =
            pluginContext.moduleDescriptor.getPackage(InjektFqNames.CompositionsPackage)

        val allModules = mutableMapOf<IrClassSymbol, MutableList<IrFunctionSymbol>>()
        val allFactories = mutableMapOf<IrClassSymbol, MutableList<IrFunctionSymbol>>()

        compositionAggregateGenerator.compositionElements
            .forEach { (compositionType, elements) ->
                elements.forEach {
                    if (it.owner.hasAnnotation(InjektFqNames.CompositionFactory)) {
                        allFactories.getOrPut(compositionType) { mutableListOf() } += it
                    } else if (it.owner.hasAnnotation(InjektFqNames.Module)) {
                        allModules.getOrPut(compositionType) { mutableListOf() } += it
                    }
                }
            }

        compositionsPackage
            .memberScope
            .getContributedDescriptors()
            .filterIsInstance<ClassDescriptor>()
            .map {
                val x = it.name.asString().split("___")
                FqName(x[0].replace("__", ".")) to FqName(x[1].replace("__", "."))
            }
            .groupBy { it.first }
            .mapValues { it.value.map { it.second } }
            .mapKeys { pluginContext.referenceClass(it.key)!! }
            .mapValues {
                it.value.map {
                    pluginContext.referenceFunctions(it).firstOrNull()
                        ?: error("could not find for $it")
                }
            }
            .forEach { (compositionType, elements) ->
                elements.forEach {
                    if (it.owner.hasAnnotation(InjektFqNames.CompositionFactory)) {
                        allFactories.getOrPut(compositionType) { mutableListOf() } += it
                    } else if (it.owner.hasAnnotation(InjektFqNames.Module)) {
                        allModules.getOrPut(compositionType) { mutableListOf() } += it
                    }
                }
            }

        val graph = CompositionFactoryGraph(
            pluginContext,
            allFactories,
            allModules
        )

        val factoryImpls = mutableMapOf<IrClassSymbol, IrFunctionSymbol>()

        generateCompositionsCalls.forEach { (call, file) ->
            val nameProvider = NameProvider()

            val processedFactories = mutableSetOf<CompositionFactory>()

            while (true) {
                val factoriesToProcess = graph.compositionFactories
                    .filter { it !in processedFactories }
                    .filter { factory ->
                        factory.children.all { it in processedFactories }
                    }

                if (factoriesToProcess.isEmpty()) {
                    break
                }

                factoriesToProcess.forEach { factory ->
                    val modules = factory.modules

                    val entryPoints = modules
                        .flatMap {
                            it.owner.getAnnotation(InjektFqNames.AstEntryPoints)
                                ?.getValueArgument(0)
                                ?.let { it as IrVarargImpl }
                                ?.elements
                                ?.map { it as IrClassReference }
                                ?.map { it.classType.classOrNull!! }
                                ?: it.owner.descriptor
                                    .annotations
                                    .findAnnotation(InjektFqNames.AstEntryPoints)
                                    ?.allValueArguments
                                    ?.values
                                    ?.single()
                                    ?.let { it as ArrayValue }
                                    ?.value
                                    ?.filterIsInstance<KClassValue>()
                                    ?.map { it.getIrClass(pluginContext).symbol }
                                    .let { it ?: emptyList() }
                        }
                        .map { it.defaultType }
                        .distinct()

                    val factoryType = compositionFactoryType(
                        nameProvider.allocateForGroup(
                            InjektNameConventions.getCompositionFactoryTypeNameForCall(
                                file,
                                call,
                                factory.factoryFunction
                            )
                        ),
                        factory.compositionType.defaultType,
                        entryPoints
                    )
                    file.addChild(factoryType)

                    val factoryFunctionImpl = compositionFactoryImpl(
                        nameProvider.allocateForGroup(
                            InjektNameConventions.getCompositionFactoryImplNameForCall(
                                file,
                                call,
                                factory.factoryFunction,
                                factory.parents.isNotEmpty()
                            )
                        ),
                        factory.parents.isNotEmpty(),
                        factoryType.symbol,
                        factory.factoryFunction,
                        factory.children.map {
                            it.compositionType to factoryImpls.getValue(it.compositionType)
                        },
                        factory.modules
                    )

                    processedFactories += factory

                    factoryImpls[factory.compositionType] = factoryFunctionImpl.symbol

                    file.addChild(factoryFunctionImpl)
                }
            }
        }

        declaration.transformChildrenVoid(object : IrElementTransformerVoidWithContext() {
            override fun visitCall(expression: IrCall): IrExpression {
                if (generateCompositionsCalls.none { it.first == expression }) return super.visitCall(
                    expression
                )
                return DeclarationIrBuilder(pluginContext, expression.symbol).run {
                    irBlock {
                        factoryImpls.forEach { (compositionType, factoryFunctionImpl) ->
                            if (factoryFunctionImpl.owner.hasAnnotation(InjektFqNames.ChildFactory)) return@forEach
                            +irCall(
                                compositionSymbols.compositionFactories
                                    .functions
                                    .single { it.owner.name.asString() == "register" }
                            ).apply {
                                dispatchReceiver =
                                    irGetObject(compositionSymbols.compositionFactories)

                                putValueArgument(
                                    0,
                                    IrClassReferenceImpl(
                                        UNDEFINED_OFFSET,
                                        UNDEFINED_OFFSET,
                                        irBuiltIns.kClassClass.typeWith(compositionType.defaultType),
                                        compositionType,
                                        compositionType.defaultType
                                    )
                                )

                                putValueArgument(
                                    1,
                                    IrFunctionReferenceImpl(
                                        UNDEFINED_OFFSET,
                                        UNDEFINED_OFFSET,
                                        irBuiltIns.function(factoryFunctionImpl.owner.valueParameters.size)
                                            .typeWith(
                                                factoryFunctionImpl.owner.valueParameters
                                                    .map { it.type } + factoryFunctionImpl.owner.returnType
                                            ),
                                        factoryFunctionImpl,
                                        0,
                                        null
                                    )
                                )
                            }
                        }
                    }
                }
            }
        })

        return super.visitModuleFragment(declaration)
    }

    private fun compositionFactoryType(
        name: Name,
        compositionType: IrType,
        entryPoints: List<IrType>
    ) = buildClass {
        this.name = name
        kind = ClassKind.INTERFACE
    }.apply {
        createImplicitParameterDeclarationWithWrappedDescriptor()
        addMetadataIfNotLocal()

        superTypes += compositionType
        entryPoints.forEach { superTypes += it }
    }

    private fun compositionFactoryImpl(
        name: Name,
        childFactory: Boolean,
        factoryType: IrClassSymbol,
        factory: IrFunctionSymbol,
        childFactories: List<Pair<IrClassSymbol, IrFunctionSymbol>>,
        modules: Set<IrFunctionSymbol>
    ) = buildFun {
        this.name = name
        returnType = factoryType.owner.defaultType
    }.apply {
        annotations += InjektDeclarationIrBuilder(pluginContext, symbol)
            .noArgSingleConstructorCall(
                if (childFactory) symbols.childFactory else symbols.factory
            )

        addMetadataIfNotLocal()

        factory.owner.valueParameters.forEach {
            addValueParameter(
                it.name.asString(),
                it.type
            )
        }

        body = DeclarationIrBuilder(pluginContext, symbol).run {
            irBlockBody {
                val factoryModule = declarationStore.getModuleFunctionForFactory(factory.owner)

                +irCall(factoryModule).apply {
                    valueParameters.forEach {
                        putValueArgument(it.index, irGet(it))
                    }
                }

                childFactories.forEach { (compositionType, childFactory) ->
                    +irCall(
                        pluginContext.referenceFunctions(
                            InjektFqNames.InjektPackage
                                .child("childFactory")
                        ).single()
                    ).apply {
                        putValueArgument(
                            0,
                            IrFunctionReferenceImpl(
                                UNDEFINED_OFFSET,
                                UNDEFINED_OFFSET,
                                irBuiltIns.function(childFactory.owner.valueParameters.size)
                                    .typeWith(
                                        childFactory.owner.valueParameters
                                            .map { it.type } + childFactory.owner.returnType
                                    ),
                                childFactory,
                                0,
                                null
                            )
                        )
                    }

                    +irCall(
                        pluginContext.referenceFunctions(
                            InjektFqNames.InjektPackage
                                .child("alias")
                        ).single()
                    ).apply {
                        val functionType = irBuiltIns.function(
                            childFactory.owner
                                .valueParameters
                                .size
                        ).typeWith(childFactory.owner
                            .valueParameters
                            .map { it.type } + childFactory.owner.returnType
                        ).withNoArgAnnotations(
                            pluginContext,
                            listOf(InjektFqNames.ChildFactory)
                        )
                        val aliasFunctionType = irBuiltIns.function(
                            childFactory.owner
                                .valueParameters
                                .size
                        ).typeWith(childFactory.owner
                            .valueParameters
                            .map { it.type } + compositionType.defaultType
                        ).withNoArgAnnotations(
                            pluginContext,
                            listOf(InjektFqNames.ChildFactory)
                        )
                        putTypeArgument(0, functionType)
                        putTypeArgument(1, aliasFunctionType)
                    }
                }

                modules.forEach { +irCall(it) }

                +irReturn(
                    irCall(
                        pluginContext.referenceFunctions(
                            InjektFqNames.InjektPackage
                                .child("create")
                        ).single()
                    ).apply {
                        putTypeArgument(0, factoryType.defaultType)
                    }
                )
            }
        }
    }

}
