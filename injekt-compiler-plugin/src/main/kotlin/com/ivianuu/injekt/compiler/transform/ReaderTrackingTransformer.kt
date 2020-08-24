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

import com.ivianuu.injekt.compiler.canUseReaders
import com.ivianuu.injekt.compiler.getContext
import com.ivianuu.injekt.compiler.getReaderConstructor
import com.ivianuu.injekt.compiler.irClassReference
import com.ivianuu.injekt.compiler.isMarkedAsReader
import com.ivianuu.injekt.compiler.isReaderLambdaInvoke
import com.ivianuu.injekt.compiler.recordLookup
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.irBoolean
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithName
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrGetField
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrSetField
import org.jetbrains.kotlin.ir.expressions.IrSetVariable
import org.jetbrains.kotlin.ir.expressions.IrWhen
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.file
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.utils.addIfNotNull

class ReaderTrackingTransformer(
    injektContext: InjektContext,
    private val indexer: Indexer,
    private val readerContextParamTransformer: ReaderContextParamTransformer
) : AbstractInjektTransformer(injektContext) {

    private val newIndexBuilders = mutableListOf<NewIndexBuilder>()

    private data class NewIndexBuilder(
        val path: List<String>,
        val originatingDeclaration: IrDeclarationWithName,
        val classBuilder: IrClass.() -> Unit
    )

    private sealed class Scope {
        abstract val file: IrFile
        abstract val fqName: FqName
        abstract val invocationContext: IrClass

        class Reader(
            val declaration: IrDeclaration,
            override val invocationContext: IrClass
        ) : Scope() {
            override val file: IrFile
                get() = declaration.file
            override val fqName: FqName
                get() = declaration.descriptor.fqNameSafe
        }

        class RunReader(
            val call: IrCall,
            override val file: IrFile,
            override val fqName: FqName
        ) : Scope() {

            override val invocationContext = call.getValueArgument(0)!!
                .type
                .lambdaContext!!

            fun isBlock(function: IrFunction): Boolean =
                call.getValueArgument(0).let {
                    it is IrFunctionExpression &&
                            it.function == function
                }

        }
    }

    private var currentReaderScope: Scope? = null

    private inline fun <R> inScope(scope: Scope, block: () -> R): R {
        val previousScope = currentReaderScope
        currentReaderScope = scope
        val result = block()
        currentReaderScope = previousScope
        return result
    }

    override fun lower() {
        injektContext.module.transformChildrenVoid(object : IrElementTransformerVoidWithContext() {

            override fun visitValueParameterNew(declaration: IrValueParameter): IrStatement {
                val defaultValue = declaration.defaultValue
                if (defaultValue != null && defaultValue.expression.type.isTransformedReaderLambda()) {
                    newIndexBuilders += defaultValue.expression
                        .collectReaderLambdaContextsInExpression()
                        .map { subContext ->
                            readerImplIndexBuilder(
                                declaration.type.lambdaContext!!,
                                subContext
                            )
                        }
                }
                return super.visitValueParameterNew(declaration)
            }

            override fun visitFieldNew(declaration: IrField): IrStatement {
                val initializer = declaration.initializer
                if (initializer != null && initializer.expression.type.isTransformedReaderLambda()) {
                    newIndexBuilders += initializer.expression
                        .collectReaderLambdaContextsInExpression()
                        .map { subContext ->
                            readerImplIndexBuilder(
                                declaration.type.lambdaContext!!,
                                subContext
                            )
                        }
                }
                return super.visitFieldNew(declaration)
            }

            override fun visitVariable(declaration: IrVariable): IrStatement {
                val initializer = declaration.initializer
                if (initializer != null && initializer.type.isTransformedReaderLambda()) {
                    newIndexBuilders += initializer
                        .collectReaderLambdaContextsInExpression()
                        .map { subContext ->
                            readerImplIndexBuilder(
                                declaration.type.lambdaContext!!,
                                subContext
                            )
                        }
                }
                return super.visitVariable(declaration)
            }

            override fun visitSetField(expression: IrSetField): IrExpression {
                if (expression.symbol.owner.type.isTransformedReaderLambda()) {
                    newIndexBuilders += expression.value
                        .collectReaderLambdaContextsInExpression()
                        .map { subContext ->
                            readerImplIndexBuilder(
                                expression.symbol.owner.type.lambdaContext!!,
                                subContext
                            )
                        }
                }
                return super.visitSetField(expression)
            }

            override fun visitSetVariable(expression: IrSetVariable): IrExpression {
                if (expression.symbol.owner.type.isTransformedReaderLambda()) {
                    newIndexBuilders += expression.value
                        .collectReaderLambdaContextsInExpression()
                        .map { subContext ->
                            readerImplIndexBuilder(
                                expression.symbol.owner.type.lambdaContext!!,
                                subContext
                            )
                        }
                }
                return super.visitSetVariable(expression)
            }

            override fun visitWhen(expression: IrWhen): IrExpression {
                val result = super.visitWhen(expression) as IrWhen
                if (expression.type.isTransformedReaderLambda()) {
                    newIndexBuilders += expression.branches
                        .flatMap { it.result.collectReaderLambdaContextsInExpression() }
                        .map { subContext ->
                            readerImplIndexBuilder(
                                expression.type.lambdaContext!!,
                                subContext
                            )
                        }
                }
                return result
            }

            override fun visitClassNew(declaration: IrClass): IrStatement {
                return if (declaration.canUseReaders(injektContext)) {
                    inScope(
                        Scope.Reader(
                            declaration,
                            declaration.getReaderConstructor(injektContext)!!
                                .getContext()!!
                        )
                    ) {
                        super.visitClassNew(declaration)
                    }
                } else super.visitClassNew(declaration)
            }

            override fun visitFunctionNew(declaration: IrFunction): IrStatement {
                if (declaration.returnType.isTransformedReaderLambda()) {
                    val lastBodyStatement =
                        declaration.body?.statements?.lastOrNull() as? IrExpression
                    if (lastBodyStatement != null && lastBodyStatement.type.isTransformedReaderLambda()) {
                        newIndexBuilders += lastBodyStatement
                            .collectReaderLambdaContextsInExpression()
                            .map { subContext ->
                                readerImplIndexBuilder(
                                    declaration.returnType.lambdaContext!!,
                                    subContext
                                )
                            }
                    }

                    if (declaration is IrSimpleFunction) {
                        val field = declaration.correspondingPropertySymbol?.owner?.backingField
                        if (field != null && field.type.isTransformedReaderLambda()) {
                            newIndexBuilders += readerImplIndexBuilder(
                                declaration.returnType.lambdaContext!!,
                                field.type.lambdaContext!!
                            )
                        }
                    }
                }

                return if (declaration.canUseReaders(injektContext) &&
                    currentReaderScope.let {
                        it == null || it !is Scope.RunReader || !it.isBlock(declaration)
                    }
                ) {
                    inScope(
                        Scope.Reader(
                            declaration,
                            declaration.getContext()!!
                        )
                    ) {
                        super.visitFunctionNew(declaration)
                    }
                } else super.visitFunctionNew(declaration)
            }

            override fun visitCall(expression: IrCall): IrExpression {
                return if (expression.symbol.descriptor.fqNameSafe.asString() == "com.ivianuu.injekt.runReader") {
                    inScope(
                        Scope.RunReader(
                            expression,
                            currentFile,
                            currentScope!!.scope.scopeOwner.fqNameSafe
                        )
                    ) {
                        val blockExpression = expression.getValueArgument(0)!!
                        blockExpression.transformChildrenVoid()
                        visitPossibleReaderCall(
                            // we fake the invoke call here because otherwise
                            // the call would get recorded in the parent scope
                            DeclarationIrBuilder(injektContext, expression.symbol).run {
                                irCall(
                                    blockExpression.type.classOrNull!!.owner
                                        .functions
                                        .first { it.name.asString() == "invoke" }
                                ).apply {
                                    dispatchReceiver = blockExpression
                                }
                            }
                        )
                        expression
                    }
                } else {
                    visitPossibleReaderCall(expression)
                    super.visitCall(expression)
                }
            }
        })

        injektContext.module.transformChildrenVoid(object : IrElementTransformerVoidWithContext() {

            override fun visitFunctionAccess(expression: IrFunctionAccessExpression): IrExpression {
                val transformedCallee = readerContextParamTransformer
                    .getTransformedFunction(expression.symbol.owner)

                newIndexBuilders += (0 until expression.valueArgumentsCount)
                    .mapNotNull { index ->
                        expression.getValueArgument(index)
                            ?.let { index to it }
                    }
                    .map { transformedCallee.valueParameters[it.first] to it.second }
                    .filter { it.first.type.isTransformedReaderLambda() }
                    .flatMap { (parameter, argument) ->
                        argument.collectReaderLambdaContextsInExpression()
                            .map { parameter.type.lambdaContext!! to it }
                    }
                    .map { (superContext, subContext) ->
                        readerImplIndexBuilder(
                            superContext,
                            subContext
                        )
                    }

                return super.visitFunctionAccess(expression)
            }

            override fun visitFunctionNew(declaration: IrFunction): IrStatement {
                if (declaration is IrSimpleFunction &&
                    declaration.isMarkedAsReader(injektContext) &&
                    declaration.overriddenSymbols.isNotEmpty()
                ) {
                    newIndexBuilders += readerImplIndexBuilder(
                        declaration.overriddenSymbols
                            .single()
                            .owner
                            .getContext()!!,
                        declaration
                            .getContext()!!
                    )
                }

                return super.visitFunctionNew(declaration)
            }

        })

        newIndexBuilders.forEach {
            indexer.index(
                originatingDeclaration = it.originatingDeclaration,
                path = it.path,
                classBuilder = it.classBuilder
            )
        }
    }

    private fun visitPossibleReaderCall(call: IrFunctionAccessExpression) {
        newIndexBuilders += listOfNotNull(
            when {
                call.isReaderLambdaInvoke(injektContext) -> {
                    val lambdaContext = call.dispatchReceiver!!.type.lambdaContext!!
                    val scope = currentReaderScope!!
                    readerCallIndexBuilder(
                        lambdaContext,
                        scope.invocationContext,
                        true
                    )
                }
                call.symbol.owner.canUseReaders(injektContext) -> {
                    readerCallIndexBuilder(
                        call.symbol.owner.getContext()!!,
                        currentReaderScope!!.invocationContext,
                        false
                    )
                }
                else -> null
            }
        )
    }

    private fun readerCallIndexBuilder(
        calleeContext: IrClass,
        callingContext: IrClass,
        isLambda: Boolean
    ): NewIndexBuilder {
        return NewIndexBuilder(
            listOf(
                DeclarationGraph.READER_CALL_PATH,
                callingContext.descriptor.fqNameSafe.asString()
            ),
            callingContext
        ) {
            recordLookup(this, calleeContext)
            recordLookup(this, callingContext)
            annotations += DeclarationIrBuilder(injektContext, callingContext.symbol).run {
                irCall(injektContext.injektSymbols.readerCall.constructors.single()).apply {
                    putValueArgument(
                        0,
                        irClassReference(calleeContext)
                    )
                    putValueArgument(
                        1,
                        irBoolean(isLambda)
                    )
                }
            }
        }
    }

    private fun readerImplIndexBuilder(
        superContext: IrClass,
        subContext: IrClass
    ) = NewIndexBuilder(
        listOf(
            DeclarationGraph.READER_IMPL_PATH,
            superContext.descriptor.fqNameSafe.asString()
        ),
        subContext
    ) {
        recordLookup(this, superContext)
        recordLookup(this, subContext)
        annotations += DeclarationIrBuilder(injektContext, subContext.symbol).run {
            irCall(injektContext.injektSymbols.readerImpl.constructors.single()).apply {
                putValueArgument(
                    0,
                    irClassReference(subContext)
                )
            }
        }
    }

    private fun IrExpression.collectReaderLambdaContextsInExpression(): Set<IrClass> {
        val contexts = mutableSetOf<IrClass>()

        if (type.isTransformedReaderLambda()) {
            contexts.addIfNotNull(type.lambdaContext)
        }

        when (this) {
            is IrGetField -> {
                if (symbol.owner.type.isTransformedReaderLambda()) {
                    contexts.addIfNotNull(symbol.owner.type.lambdaContext)
                }
            }
            is IrGetValue -> {
                if (symbol.owner.type.isTransformedReaderLambda()) {
                    contexts.addIfNotNull(symbol.owner.type.lambdaContext)
                }
            }
            is IrFunctionExpression -> {
                contexts.addIfNotNull(function.getContext())
            }
            is IrCall -> {
                contexts.addIfNotNull(symbol.owner.getContext())
            }
        }

        return contexts
    }

}