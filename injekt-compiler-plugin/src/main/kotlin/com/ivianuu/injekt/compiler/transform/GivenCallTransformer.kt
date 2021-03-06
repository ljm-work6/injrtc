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

package com.ivianuu.injekt.compiler.transform

import com.ivianuu.injekt.compiler.DeclarationStore
import com.ivianuu.injekt.compiler.InjektFqNames
import com.ivianuu.injekt.compiler.InjektWritableSlices
import com.ivianuu.injekt.compiler.SourcePosition
import com.ivianuu.injekt.compiler.resolution.*
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContextImpl
import org.jetbrains.kotlin.backend.common.ir.allParameters
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.pop
import org.jetbrains.kotlin.backend.common.push
import org.jetbrains.kotlin.cfg.index
import org.jetbrains.kotlin.descriptors.ClassConstructorDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.ParameterDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.ReceiverParameterDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.descriptors.VariableDescriptor
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.Scope
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irSet
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.declarations.impl.IrFactoryImpl
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.symbols.IrBindableSymbol
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.utils.addToStdlib.cast

private fun GivenGraph.Success.flattenResults(): List<CandidateResolutionResult.Success> {
    val allResults = mutableListOf<CandidateResolutionResult.Success>()
    fun CandidateResolutionResult.Success.visit() {
        if (!allResults.add(this)) return
        dependencyResults.forEach { it.value.visit() }
    }
    results.forEach { it.value.visit() }
    return allResults
}

class GivenCallTransformer(
    private val declarationStore: DeclarationStore,
    private val pluginContext: IrPluginContext
) : IrElementTransformerVoid() {

    private inner class GraphContext(val graph: GivenGraph.Success) {
        private val scopeContexts = mutableMapOf<ResolutionScope, ScopeContext>()
        val statements = mutableListOf<IrStatement>()

        var parameterIndex = 0

        private val graphContextParents = buildList<ResolutionScope> {
            var current: ResolutionScope? = graph.scope.parent
            while (current != null) {
                this += current
                current = current.parent
            }
        }

        private fun ResolutionScope.mapScopeIfNeeded() =
            if (this in graphContextParents) graph.scope else this

        fun existingScopeContext(scope: ResolutionScope): ScopeContext =
            scopeContexts[scope.mapScopeIfNeeded()]
                ?: error("No existing scope context found for ${scope.mapScopeIfNeeded()}")

        fun createScopeContext(scope: ResolutionScope, irScope: Scope): ScopeContext {
            val finalScope = scope.mapScopeIfNeeded()
            check(scopeContexts[finalScope] == null) {
                "Cannot create scope context twice"
            }
            return scopeContexts.getOrPut(finalScope) {
                ScopeContext(this, finalScope, irScope)
            }.also {
                check(it.irScope === irScope)
            }
        }
    }

    private inner class ScopeContext(
        val graphContext: GraphContext,
        val scope: ResolutionScope,
        val irScope: Scope
    ) {
        val symbol = irScope.scopeOwnerSymbol
        private val initializingExpressions = mutableMapOf<GivenNode, GivenExpression>()
        val functionWrappedExpressions = mutableMapOf<CandidateResolutionResult.Success, ScopeContext.() -> IrExpression>()
        val cachedExpressions = mutableMapOf<CandidateResolutionResult.Success, ScopeContext.() -> IrExpression>()
        val statements = if (scope == graphContext.graph.scope) graphContext.statements else mutableListOf()

        fun expressionFor(result: CandidateResolutionResult.Success): IrExpression {
            val scopeContext = graphContext.existingScopeContext(result.outerMostScope)
            return scopeContext.expressionForImpl(result)
        }

        private fun expressionForImpl(result: CandidateResolutionResult.Success): IrExpression {
            initializingExpressions[result.candidate]?.run { return get() }
            val expression = GivenExpression(result)
            initializingExpressions[result.candidate] = expression
            val irExpression = expression.run { get() }
            initializingExpressions -= result.candidate
            return irExpression
        }
    }

    private fun IrFunctionAccessExpression.fillGivens(
        context: ScopeContext,
        results: Map<GivenRequest, CandidateResolutionResult.Success>
    ) {
        var nonReceiverIndex = 0
        results
            .forEach { (request, result) ->
                val expression = context.expressionFor(result)
                when(request.parameterName.asString()) {
                    "_dispatchReceiver" -> dispatchReceiver = expression
                    "_extensionReceiver" -> extensionReceiver = expression
                    else -> putValueArgument(
                        nonReceiverIndex++,
                        expression
                    )
                }
            }
    }

    private inner class GivenExpression(private val result: CandidateResolutionResult.Success) {
        private var block: IrBlock? = null
        private var tmpVariable: IrVariable? = null
        private var finalExpression: IrExpression? = null

        private var initializing = false

        fun ScopeContext.get(): IrExpression {
            if (initializing) {
                if (block == null) {
                    val resultType = result.candidate.type.toIrType(pluginContext, declarationStore)
                    block = DeclarationIrBuilder(pluginContext, symbol)
                        .irBlock(resultType = resultType) {
                            tmpVariable = irTemporary(
                                value = irNull(),
                                isMutable = true,
                                irType = resultType.makeNullable()
                            )
                        } as IrBlock
                }
                return DeclarationIrBuilder(pluginContext, symbol)
                    .irGet(tmpVariable!!)
            }

            finalExpression?.let { return it }

            initializing = true

            val rawExpression = cacheExpressionIfNeeded(result) {
                wrapExpressionInFunctionIfNeeded(result) {
                    when (result.candidate) {
                        is CallableGivenNode -> callableExpression(result, result.candidate.cast())
                        is ProviderGivenNode -> providerExpression(result, result.candidate.cast())
                        is SetGivenNode -> setExpression(result, result.candidate.cast())
                    }
                }
            }

            initializing = false

            finalExpression = if (block == null) rawExpression else {
                with(DeclarationIrBuilder(pluginContext, symbol)) {
                    block!!.statements += irSet(tmpVariable!!.symbol, rawExpression)
                    block!!.statements += irGet(tmpVariable!!)
                }
                block!!
            }

            return finalExpression!!
        }
    }

    private fun CandidateResolutionResult.Success.shouldWrap(): Boolean =
        dependencyResults.isNotEmpty() && !hasCircularDependency &&
                !shouldCache() && usages > 1

    private fun ScopeContext.wrapExpressionInFunctionIfNeeded(
        result: CandidateResolutionResult.Success,
        rawExpressionProvider: () -> IrExpression
    ): IrExpression = if (!result.shouldWrap()) rawExpressionProvider()
    else functionWrappedExpressions.getOrPut(result) {
        val function = IrFactoryImpl.buildFun {
            origin = IrDeclarationOrigin.DEFINED
            name = Name.special("<anonymous>")
            returnType = result.candidate.type.toIrType(pluginContext, declarationStore)
            visibility = DescriptorVisibilities.LOCAL
            isSuspend = scope.callContext == CallContext.SUSPEND
        }.apply {
            parent = irScope.getLocalDeclarationParent()
            if (result.candidate.callContext == CallContext.COMPOSABLE) {
                annotations += DeclarationIrBuilder(pluginContext, symbol)
                    .irCallConstructor(
                        pluginContext.referenceConstructors(InjektFqNames.Composable)
                            .single(),
                        emptyList()
                    )
            }
            this.body = DeclarationIrBuilder(pluginContext, symbol).run {
                irBlockBody {
                    +irReturn(rawExpressionProvider())
                }
            }
            statements += this
        }

        val expression: ScopeContext.() -> IrExpression = {
            DeclarationIrBuilder(pluginContext, symbol)
                .irCall(function)
        }
        expression
    }()

    private fun CandidateResolutionResult.Success.shouldCache(): Boolean =
        candidate.cache && !hasCircularDependency && usages > 1

    private fun ScopeContext.cacheExpressionIfNeeded(
        result: CandidateResolutionResult.Success,
        rawExpressionProvider: () -> IrExpression
    ): IrExpression {
        if (!result.shouldCache()) return rawExpressionProvider()
        return cachedExpressions.getOrPut(result) {
            val variable = irScope.createTemporaryVariable(rawExpressionProvider())
            statements += variable
            val expression: ScopeContext.() -> IrExpression = {
                DeclarationIrBuilder(pluginContext, symbol)
                    .irGet(variable)
            }
            expression
        }()
    }

    private fun ScopeContext.objectExpression(type: TypeRef): IrExpression =
        DeclarationIrBuilder(pluginContext, symbol)
            .irGetObject(pluginContext.referenceClass(type.classifier.fqName)!!)

    private fun ScopeContext.providerExpression(
        result: CandidateResolutionResult.Success,
        given: ProviderGivenNode
    ): IrExpression = DeclarationIrBuilder(pluginContext, symbol)
        .irLambda(
            given.type.toIrType(pluginContext, declarationStore),
            parameterNameProvider = { "p${graphContext.parameterIndex++}" }
        ) { function ->
            given.parameterDescriptors.zip(function.valueParameters)
                .forEach { parameterMap[it.first] = it.second }
            val dependencyScopeContext = graphContext.createScopeContext(given.dependencyScope, scope)
            val expression = with(dependencyScopeContext) {
                expressionFor(result.dependencyResults.values.single())!!
            }

            if (dependencyScopeContext.statements.isEmpty()) expression
            else {
                irBlock {
                    dependencyScopeContext.statements.forEach { +it }
                    +expression
                }
            }
        }

    private val mutableSetOf = pluginContext.referenceFunctions(
        FqName("kotlin.collections.mutableSetOf")
    ).single { it.owner.valueParameters.isEmpty() }

    private val setAddAll = mutableSetOf.owner.returnType
        .classOrNull!!
        .owner
        .functions
        .single { it.name.asString() == "add" }

    private val emptySet = pluginContext.referenceFunctions(
        FqName("kotlin.collections.emptySet")
    ).single()

    private fun ScopeContext.setExpression(
        result: CandidateResolutionResult.Success,
        given: SetGivenNode
    ): IrExpression {
        val elementType = given.type.fullyExpandedType.arguments.single()

        if (given.dependencies.isEmpty()) {
            return DeclarationIrBuilder(pluginContext, symbol)
                .irCall(emptySet)
                .apply { putTypeArgument(0, elementType.toIrType(pluginContext, declarationStore)) }
        }

        return DeclarationIrBuilder(pluginContext, symbol).irBlock {
            val tmpSet = irTemporary(
                irCall(mutableSetOf)
                    .apply { putTypeArgument(0, elementType.toIrType(pluginContext, declarationStore)) }
            )

            result.dependencyResults
                .forEach { dependencyResult ->
                    +irCall(setAddAll).apply {
                        dispatchReceiver = irGet(tmpSet)
                        putValueArgument(0, expressionFor(dependencyResult.value))
                    }
                }

            +irGet(tmpSet)
        }
    }

    private fun ScopeContext.callableExpression(
        result: CandidateResolutionResult.Success,
        given: CallableGivenNode
    ): IrExpression = when (given.callable.callable) {
        is ClassConstructorDescriptor -> classExpression(
            result,
            given,
            given.callable.callable
        )
        is PropertyDescriptor -> propertyExpression(
            result,
            given,
            given.callable.callable
        )
        is FunctionDescriptor -> functionExpression(
            result,
            given,
            given.callable.callable
        )
        is ReceiverParameterDescriptor -> if (given.type.classifier.isObject) objectExpression(given.type)
        else parameterExpression(given.callable.callable)
        is ValueParameterDescriptor -> parameterExpression(given.callable.callable)
        is VariableDescriptor -> variableExpression(given.callable.callable)
        else -> error("Unsupported callable $given")
    }

    private fun ScopeContext.classExpression(
        result: CandidateResolutionResult.Success,
        given: CallableGivenNode,
        descriptor: ClassConstructorDescriptor
    ): IrExpression = if (descriptor.constructedClass.kind == ClassKind.OBJECT) {
        val clazz =
            pluginContext.symbolTable.referenceClass(descriptor.constructedClass.original)
                .bind()
        DeclarationIrBuilder(pluginContext, symbol)
            .irGetObject(clazz)
    } else {
        val constructor =
            pluginContext.symbolTable.referenceConstructor(descriptor.original).bind()
                .owner
        DeclarationIrBuilder(pluginContext, symbol)
            .irCall(constructor.symbol)
            .apply {
                fillTypeParameters(given.callable)
                fillGivens(this@classExpression, result.dependencyResults)
            }
    }

    private fun ScopeContext.propertyExpression(
        result: CandidateResolutionResult.Success,
        given: CallableGivenNode,
        descriptor: PropertyDescriptor
    ): IrExpression {
        val property = pluginContext.symbolTable.referenceProperty(descriptor.original).bind()
        val getter = property.owner.getter!!
        return DeclarationIrBuilder(pluginContext, symbol)
            .irCall(getter.symbol)
            .apply {
                fillTypeParameters(given.callable)
                fillGivens(this@propertyExpression, result.dependencyResults)
            }
    }

    private fun ScopeContext.functionExpression(
        result: CandidateResolutionResult.Success,
        given: CallableGivenNode,
        descriptor: FunctionDescriptor
    ): IrExpression {
        val function = descriptor.irFunction()
        return DeclarationIrBuilder(pluginContext, symbol)
            .irCall(function.symbol)
            .apply {
                fillTypeParameters(given.callable)
                fillGivens(this@functionExpression, result.dependencyResults)
            }
    }

    private fun ScopeContext.parameterExpression(descriptor: ParameterDescriptor): IrExpression =
        when (val containingDeclaration = descriptor.containingDeclaration) {
            is ClassDescriptor -> receiverAccessors.last {
                descriptor.type.constructor.declarationDescriptor == it.first.descriptor
            }.second()
            is ClassConstructorDescriptor -> DeclarationIrBuilder(pluginContext, symbol)
                .irGet(
                    containingDeclaration.irConstructor()
                        .allParameters
                        .single { it.name == descriptor.name }
                )
            is FunctionDescriptor -> DeclarationIrBuilder(pluginContext, symbol)
                .irGet(
                    parameterMap[descriptor] ?: containingDeclaration.irFunction()
                        .let { function ->
                            function.allParameters
                                .filter { it != function.dispatchReceiverParameter }
                        }
                        .single { it.index == descriptor.index() }
                )
            else -> error("Unexpected parent $descriptor $containingDeclaration")
        }

    private fun IrFunctionAccessExpression.fillTypeParameters(callable: CallableRef) {
        callable
            .typeArguments
            .values
            .forEachIndexed { index, typeArgument ->
                putTypeArgument(index, typeArgument.toIrType(pluginContext, declarationStore))
            }
    }

    private fun ScopeContext.variableExpression(descriptor: VariableDescriptor): IrExpression =
        DeclarationIrBuilder(pluginContext, symbol)
            .irGet(variables.single { it.descriptor == descriptor })

    private fun ClassConstructorDescriptor.irConstructor() =
        pluginContext.symbolTable.referenceConstructor(original).bind().owner

    private fun FunctionDescriptor.irFunction() =
        pluginContext.symbolTable.referenceSimpleFunction(original)
            .bind()
            .owner

    private fun <T : IrBindableSymbol<*, *>> T.bind(): T {
        (pluginContext as IrPluginContextImpl).linker.run {
            getDeclaration(this@bind)
            postProcess()
        }
        return this
    }

    private val receiverAccessors = mutableListOf<Pair<IrClass, () -> IrExpression>>()

    private val variables = mutableListOf<IrVariable>()

    private val parameterMap = mutableMapOf<ParameterDescriptor, IrValueParameter>()

    private val fileStack = mutableListOf<IrFile>()
    override fun visitFile(declaration: IrFile): IrFile {
        fileStack.push(declaration)
        return super.visitFile(declaration)
            .also { fileStack.pop() }
    }

    override fun visitClass(declaration: IrClass): IrStatement {
        receiverAccessors.push(
            declaration to {
                DeclarationIrBuilder(pluginContext, declaration.symbol)
                    .irGet(declaration.thisReceiver!!)
            }
        )
        val result = super.visitClass(declaration)
        receiverAccessors.pop()
        return result
    }

    override fun visitFunction(declaration: IrFunction): IrStatement {
        val dispatchReceiver = declaration.dispatchReceiverParameter?.type?.classOrNull?.owner
        if (dispatchReceiver != null) {
            receiverAccessors.push(
                dispatchReceiver to {
                    DeclarationIrBuilder(pluginContext, declaration.symbol)
                        .irGet(declaration.dispatchReceiverParameter!!)
                }
            )
        }
        val extensionReceiver = declaration.extensionReceiverParameter?.type?.classOrNull?.owner
        if (extensionReceiver != null) {
            receiverAccessors.push(
                extensionReceiver to {
                    DeclarationIrBuilder(pluginContext, declaration.symbol)
                        .irGet(declaration.extensionReceiverParameter!!)
                }
            )
        }
        val result = super.visitFunction(declaration)
        if (dispatchReceiver != null) receiverAccessors.pop()
        if (extensionReceiver != null) receiverAccessors.pop()
        return result
    }

    override fun visitVariable(declaration: IrVariable): IrStatement =
        super.visitVariable(declaration)
            .also { variables += declaration }

    override fun visitFunctionAccess(expression: IrFunctionAccessExpression): IrExpression {
        val result = super.visitFunctionAccess(expression) as IrFunctionAccessExpression
        val graph = pluginContext.bindingContext[
                InjektWritableSlices.GIVEN_GRAPH,
                SourcePosition(
                    fileStack.last().fileEntry.name,
                    result.startOffset,
                    result.endOffset
                )
        ] ?: return result
        val graphContext = GraphContext(graph)
        return DeclarationIrBuilder(pluginContext, result.symbol)
            .irBlock {
                try {
                    graphContext
                        .createScopeContext(graph.scope, scope)
                        .run { result.fillGivens(this, graph.results) }
                } catch (e: Throwable) {
                    throw RuntimeException("Wtf ${expression.dump()}", e)
                }
                graphContext.statements.forEach { +it }
                +result
            }
    }

}
