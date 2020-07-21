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

import com.ivianuu.injekt.compiler.InjektFqNames
import com.ivianuu.injekt.compiler.NameProvider
import com.ivianuu.injekt.compiler.addMetadataIfNotLocal
import com.ivianuu.injekt.compiler.asNameId
import com.ivianuu.injekt.compiler.canUseImplicits
import com.ivianuu.injekt.compiler.copy
import com.ivianuu.injekt.compiler.distinctedType
import com.ivianuu.injekt.compiler.flatMapFix
import com.ivianuu.injekt.compiler.getJoinedName
import com.ivianuu.injekt.compiler.getReaderConstructor
import com.ivianuu.injekt.compiler.getValueArgumentSafe
import com.ivianuu.injekt.compiler.isExternalDeclaration
import com.ivianuu.injekt.compiler.isMarkedAsImplicit
import com.ivianuu.injekt.compiler.jvmNameAnnotation
import com.ivianuu.injekt.compiler.readableName
import com.ivianuu.injekt.compiler.remapTypeParameters
import com.ivianuu.injekt.compiler.substitute
import com.ivianuu.injekt.compiler.thisOfClass
import com.ivianuu.injekt.compiler.tmpFunction
import com.ivianuu.injekt.compiler.typeArguments
import com.ivianuu.injekt.compiler.uniqueFqName
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.ScopeWithIr
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.addChild
import org.jetbrains.kotlin.backend.common.ir.copyTo
import org.jetbrains.kotlin.backend.common.ir.copyTypeParametersFrom
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.pop
import org.jetbrains.kotlin.backend.common.push
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.PropertyGetterDescriptor
import org.jetbrains.kotlin.descriptors.PropertySetterDescriptor
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irExprBody
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irSetField
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationContainer
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithName
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrTypeParametersContainer
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrDelegatingConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrDelegatingConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructedClass
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.copyTypeAndValueArgumentsFrom
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.file
import org.jetbrains.kotlin.ir.util.findAnnotation
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.getPackageFragment
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.hasDefaultValue
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

// todo dedup transform code
class ImplicitTransformer(pluginContext: IrPluginContext) :
    AbstractInjektTransformer(pluginContext) {

    private val transformedFunctions = mutableMapOf<IrFunction, IrFunction>()
    private val transformedClasses = mutableSetOf<IrClass>()

    private val readerSignatures = mutableSetOf<IrFunction>()

    private val globalNameProvider = NameProvider()

    fun getTransformedFunction(reader: IrFunction) =
        transformFunctionIfNeeded(reader)

    override fun lower() {
        module.transformChildrenVoid(object : IrElementTransformerVoid() {
            override fun visitCall(expression: IrCall): IrExpression {
                if (expression.symbol.owner.descriptor.fqNameSafe.asString() ==
                    "com.ivianuu.injekt.runReader"
                ) {
                    (expression.getValueArgument(0) as IrFunctionExpression)
                        .function.annotations += DeclarationIrBuilder(
                        pluginContext,
                        expression.symbol
                    ).irCall(symbols.reader.constructors.single())
                }
                return super.visitCall(expression)
            }
        })

        module.transformChildrenVoid(object : IrElementTransformerVoid() {
            override fun visitClass(declaration: IrClass): IrStatement =
                transformClassIfNeeded(super.visitClass(declaration) as IrClass)
        })

        module.transformChildrenVoid(object : IrElementTransformerVoid() {
            override fun visitFunction(declaration: IrFunction): IrStatement =
                transformFunctionIfNeeded(super.visitFunction(declaration) as IrFunction)
        })

        readerSignatures
            .filterNot { it.isExternalDeclaration() }
            .forEach { readerSignature ->
                val parent = readerSignature.parent as IrDeclarationContainer
                if (readerSignature !in parent.declarations) {
                    parent.addChild(readerSignature)
                }
            }
    }

    private fun transformClassIfNeeded(clazz: IrClass): IrClass {
        if (clazz in transformedClasses) return clazz

        val readerConstructor = clazz.getReaderConstructor()

        if (!clazz.isMarkedAsImplicit() && readerConstructor == null) return clazz

        if (readerConstructor == null) return clazz

        transformedClasses += clazz

        if (readerConstructor.valueParameters.any { it.hasAnnotation(InjektFqNames.Implicit) })
            return clazz

        if (clazz.isExternalDeclaration()) {
            val readerSignature = getReaderSignature(clazz)!!
            readerSignatures += readerSignature

            readerConstructor.copySignatureFrom(readerSignature) {
                it.remapTypeParameters(readerSignature, clazz)
            }

            return clazz
        }

        val givenCalls = mutableListOf<IrCall>()
        val defaultValueParameterByGivenCalls = mutableMapOf<IrCall, IrValueParameter>()
        val readerCalls = mutableListOf<IrFunctionAccessExpression>()

        clazz.transformChildrenVoid(object : IrElementTransformerVoid() {

            private val functionStack = mutableListOf<IrFunction>()
            private val valueParameterStack = mutableListOf<IrValueParameter>()

            override fun visitFunction(declaration: IrFunction): IrStatement {
                val isReader = declaration.canUseImplicits() && declaration != readerConstructor
                if (isReader) functionStack.push(declaration)
                return super.visitFunction(declaration)
                    .also { if (isReader) functionStack.pop() }
            }

            override fun visitValueParameter(declaration: IrValueParameter): IrStatement {
                valueParameterStack += declaration
                return super.visitValueParameter(declaration)
                    .also { valueParameterStack -= declaration }
            }

            override fun visitFunctionAccess(expression: IrFunctionAccessExpression): IrExpression {
                val result = super.visitFunctionAccess(expression) as IrFunctionAccessExpression
                if (functionStack.isNotEmpty()) return result
                if (result !is IrCall &&
                    result !is IrConstructorCall &&
                    result !is IrDelegatingConstructorCall
                ) return result
                if (expression.symbol.owner.canUseImplicits()) {
                    if (result is IrCall && result.symbol.owner.isGiven) {
                        givenCalls += result
                        valueParameterStack.lastOrNull()
                            ?.takeIf {
                                it.defaultValue is IrExpressionBody &&
                                        it.defaultValue!!.statements.single() == result
                            }
                            ?.let {
                                defaultValueParameterByGivenCalls[result] = it
                            }
                    } else {
                        readerCalls += result
                    }
                }
                return result
            }

        })

        val givenTypes = mutableMapOf<Any, IrType>()
        val callsByTypes = mutableMapOf<Any, IrFunctionAccessExpression>()

        givenCalls
            .forEach { givenCall ->
                val type = givenCall.getRealGivenType()
                givenTypes[type.distinctedType] = type
                callsByTypes[type.distinctedType] = givenCall
            }
        readerCalls.flatMapFix { readerCall ->
            val transformedCallee = transformFunctionIfNeeded(readerCall.symbol.owner)

            transformedCallee
                .valueParameters
                .filter { it.hasAnnotation(InjektFqNames.Implicit) }
                .filter { readerCall.getValueArgumentSafe(it.index) == null }
                .map { it.type }
                .map {
                    it
                        .remapTypeParameters(readerCall.symbol.owner, transformedCallee)
                        .substitute(
                            transformedCallee.typeParameters.map { it.symbol }
                                .zip(readerCall.typeArguments)
                                .toMap()
                        )
                }
                .map { readerCall to it }
        }.forEach { (call, type) ->
            givenTypes[type.distinctedType] = type
            callsByTypes[type.distinctedType] = call
        }

        val givenFields = mutableMapOf<Any, IrField>()
        val valueParametersByFields = mutableMapOf<IrField, IrValueParameter>()

        givenTypes.values.forEach { givenType ->
            val field = clazz.addField(
                fieldName = givenType.readableName(),
                fieldType = givenType
            )
            givenFields[givenType.distinctedType] = field

            val call = callsByTypes[givenType.distinctedType]
            val defaultValueParameter = defaultValueParameterByGivenCalls[call]

            val valueParameter = (defaultValueParameter
                ?.also { it.defaultValue = null }
                ?: readerConstructor.addValueParameter(
                    field.name.asString(),
                    field.type
                )).apply {
                annotations += DeclarationIrBuilder(pluginContext, symbol)
                    .irCall(symbols.implicit.constructors.single())
            }
            valueParametersByFields[field] = valueParameter
        }

        readerSignatures += createReaderSignature(clazz, readerConstructor) { it }

        readerConstructor.body = DeclarationIrBuilder(pluginContext, clazz.symbol).run {
            irBlockBody {
                readerConstructor.body?.statements?.forEach {
                    +it
                    if (it is IrDelegatingConstructorCall) {
                        valueParametersByFields.forEach { (field, valueParameter) ->
                            +irSetField(
                                irGet(clazz.thisReceiver!!),
                                field,
                                irGet(valueParameter)
                            )
                        }
                    }
                }
            }
        }

        rewriteCalls(
            owner = clazz,
            givenCalls = givenCalls,
            readerCalls = readerCalls
        ) { type, expression, scopes ->
            val finalType = (if (expression.symbol.owner.isGiven) {
                expression.getRealGivenType()
            } else type)
                .substitute(
                    transformFunctionIfNeeded(expression.symbol.owner)
                        .typeParameters
                        .map { it.symbol }
                        .zip(expression.typeArguments)
                        .toMap()
                ).distinctedType
            val field = givenFields[finalType]!!

            return@rewriteCalls if (scopes.none { it.irElement == readerConstructor }) {
                irGetField(
                    irGet(scopes.thisOfClass(clazz)!!),
                    field
                )
            } else {
                irGet(valueParametersByFields.getValue(field))
            }
        }

        return clazz
    }

    private fun transformFunctionIfNeeded(function: IrFunction): IrFunction {
        if (function is IrConstructor) {
            return if (function.canUseImplicits()) {
                transformClassIfNeeded(function.constructedClass)
                    .getReaderConstructor()!!
            } else function
        }

        transformedFunctions[function]?.let { return it }
        if (function in transformedFunctions.values) return function

        if (!function.canUseImplicits()) return function

        if (function.valueParameters.any { it.hasAnnotation(InjektFqNames.Implicit) }) {
            transformedFunctions[function] = function
            return function
        }

        if (function.isExternalDeclaration()) {
            val transformedFunction = function.copyAsReader()
            transformedFunctions[function] = transformedFunction

            if (!transformedFunction.isGiven) {
                val signature = getReaderSignature(transformedFunction)!!
                readerSignatures += signature
                transformedFunction.copySignatureFrom(signature) {
                    it.remapTypeParameters(signature, transformedFunction)
                }
            }

            return transformedFunction
        }

        val transformedFunction = function.copyAsReader()
        transformedFunctions[function] = transformedFunction

        val givenCalls = mutableListOf<IrCall>()
        val defaultValueParameterByGivenCalls = mutableMapOf<IrCall, IrValueParameter>()
        val readerCalls = mutableListOf<IrFunctionAccessExpression>()

        transformedFunction.transformChildrenVoid(object : IrElementTransformerVoid() {

            private val functionStack = mutableListOf(transformedFunction)
            private val valueParameterStack = mutableListOf<IrValueParameter>()

            override fun visitFunction(declaration: IrFunction): IrStatement {
                val isReader = declaration.canUseImplicits()
                if (isReader) functionStack.push(declaration)
                return super.visitFunction(declaration)
                    .also { if (isReader) functionStack.pop() }
            }

            override fun visitValueParameter(declaration: IrValueParameter): IrStatement {
                valueParameterStack += declaration
                return super.visitValueParameter(declaration)
                    .also { valueParameterStack -= declaration }
            }

            override fun visitFunctionAccess(expression: IrFunctionAccessExpression): IrExpression {
                val result = super.visitFunctionAccess(expression) as IrFunctionAccessExpression
                if (result !is IrCall &&
                    result !is IrConstructorCall &&
                    result !is IrDelegatingConstructorCall
                ) return result
                if (functionStack.lastOrNull() != transformedFunction) return result
                if (expression.symbol.owner.canUseImplicits()) {
                    if (result is IrCall && expression.symbol.owner.isGiven) {
                        givenCalls += result
                        valueParameterStack.lastOrNull()
                            ?.takeIf {
                                it.defaultValue is IrExpressionBody &&
                                        it.defaultValue!!.statements.single() == result
                            }
                            ?.let {
                                defaultValueParameterByGivenCalls[result] = it
                            }
                    } else {
                        readerCalls += result
                    }
                }
                return result
            }
        })

        val givenTypes = mutableMapOf<Any, IrType>()
        val callsByTypes = mutableMapOf<Any, IrFunctionAccessExpression>()

        givenCalls
            .forEach { givenCall ->
                val type = givenCall.getRealGivenType()
                    .remapTypeParameters(function, transformedFunction)
                givenTypes[type.distinctedType] = type
                callsByTypes[type.distinctedType] = givenCall
            }
        readerCalls.flatMapFix { readerCall ->
            val transformedCallee = transformFunctionIfNeeded(readerCall.symbol.owner)
            transformedCallee
                .valueParameters
                .filter { it.hasAnnotation(InjektFqNames.Implicit) }
                .filter { readerCall.getValueArgumentSafe(it.index) == null }
                .map { it.type }
                .map {
                    it
                        .remapTypeParameters(readerCall.symbol.owner, transformedCallee)
                        .substitute(
                            transformedCallee.typeParameters.map { it.symbol }
                                .zip(
                                    readerCall.typeArguments
                                        .map {
                                            it.remapTypeParameters(
                                                function, transformedFunction
                                            )
                                        }
                                )
                                .toMap()
                        )
                }
                .map { readerCall to it }
        }.forEach { (call, type) ->
            givenTypes[type.distinctedType] = type
            callsByTypes[type.distinctedType] = call
        }

        val givenValueParameters = mutableMapOf<Any, IrValueParameter>()

        givenTypes.values.forEach { givenType ->
            val call = callsByTypes[givenType.distinctedType]
            val defaultValueParameter = defaultValueParameterByGivenCalls[call]

            val valueParameter = (defaultValueParameter
                ?.also { it.defaultValue = null }
                ?: transformedFunction.addValueParameter(
                    name = givenType.readableName().asString(),
                    type = givenType
                )).apply {
                annotations += DeclarationIrBuilder(pluginContext, symbol)
                    .irCall(symbols.implicit.constructors.single())
            }
            givenValueParameters[givenType.distinctedType] = valueParameter
        }

        readerSignatures += createReaderSignature(
            transformedFunction,
            transformedFunction
        ) {
            it.remapTypeParameters(function, transformedFunction)
        }

        rewriteCalls(
            owner = transformedFunction,
            givenCalls = givenCalls,
            readerCalls = readerCalls
        ) { type, expression, _ ->
            val finalType = (if (expression.symbol.owner.isGiven) {
                expression.getRealGivenType()
            } else type).remapTypeParameters(function, transformedFunction)
                .substitute(
                    transformFunctionIfNeeded(expression.symbol.owner)
                        .typeParameters
                        .map { it.symbol }
                        .zip(
                            expression.typeArguments
                                .map { it.remapTypeParameters(function, transformedFunction) }
                        )
                        .toMap()
                )
                .distinctedType
            val valueParameter = givenValueParameters[finalType] ?: error(
                "Could not find for ${(finalType as? IrType)?.render() ?: finalType} expr ${expression.dump()}\nexisting ${
                    givenValueParameters.map {
                        it.value.render()
                    }
                }"
            )
            return@rewriteCalls irGet(valueParameter)
        }

        return transformedFunction
    }

    private fun IrFunction.copySignatureFrom(
        signature: IrFunction,
        remapType: (IrType) -> IrType
    ) {
        val implicitIndices = signature.getAnnotation(InjektFqNames.Implicits)!!
            .getValueArgument(0)
            .let { it as IrVarargImpl }
            .elements
            .map { it as IrConst<Int> }
            .map { it.value }

        valueParameters = signature.valueParameters.map {
            it.copyTo(
                this,
                type = remapType(it.type),
                varargElementType = it.varargElementType?.let(remapType)
            )
        }.onEach {
            if (it.index in implicitIndices) {
                it.annotations += DeclarationIrBuilder(pluginContext, it.symbol)
                    .irCall(symbols.implicit.constructors.single())
            }
        }
    }

    private fun IrFunctionAccessExpression.getRealGivenType(): IrType {
        if (!symbol.owner.isGiven) return type

        val arguments = (getValueArgument(0) as? IrVarargImpl)
            ?.elements
            ?.map { it as IrExpression } ?: emptyList()

        val lazy = getValueArgument(1)
            ?.let { it as IrConst<Boolean> }
            ?.value ?: false

        return when {
            arguments.isNotEmpty() -> pluginContext.tmpFunction(arguments.size)
                .typeWith(arguments.map { it.type } + type)
            lazy -> pluginContext.tmpFunction(0).typeWith(type)
            else -> type
        }
    }

    private fun createReaderSignature(
        owner: IrDeclarationWithName,
        function: IrFunction,
        remapType: (IrType) -> IrType
    ) = buildFun {
        this.name = globalNameProvider.allocateForGroup(
            getJoinedName(
                owner.getPackageFragment()!!.fqName,
                owner.descriptor.fqNameSafe
                    .parent()
                    .let {
                        if (owner.name.isSpecial) {
                            it.child(globalNameProvider.allocateForGroup("Lambda").asNameId())
                        } else {
                            it.child(owner.name.asString().asNameId())
                        }
                    }
            ).asString() + "_ReaderSignature"
        ).asNameId()
    }.apply {
        parent = owner.file
        addMetadataIfNotLocal()

        copyTypeParametersFrom(owner as IrTypeParametersContainer)

        annotations += DeclarationIrBuilder(pluginContext, symbol).run {
            irCall(symbols.name.constructors.single()).apply {
                putValueArgument(
                    0,
                    irString(owner.uniqueFqName())
                )
            }
        }

        annotations += DeclarationIrBuilder(pluginContext, symbol).run {
            irCall(symbols.implicits.constructors.single())
                .apply {
                    val intArray = pluginContext.referenceClass(
                        FqName("kotlin.IntArray")
                    )!!
                    putValueArgument(
                        0,
                        IrVarargImpl(
                            UNDEFINED_OFFSET,
                            UNDEFINED_OFFSET,
                            intArray.defaultType,
                            irBuiltIns.intType,
                            function.valueParameters
                                .filter { it.hasAnnotation(InjektFqNames.Implicit) }
                                .map { it.index }
                                .map { irInt(it) }
                        )
                    )
                }
        }

        returnType = remapType(function.returnType)
            .remapTypeParameters(owner, this)

        valueParameters = function.valueParameters.map {
            it.copyTo(
                this,
                type = remapType(it.type)
                    .remapTypeParameters(owner, this),
                varargElementType = it.varargElementType?.let(remapType)
                    ?.remapTypeParameters(owner, this),
                defaultValue = if (it.hasDefaultValue()) DeclarationIrBuilder(
                    pluginContext,
                    it.symbol
                ).run {
                    irExprBody(
                        irCall(
                            pluginContext.referenceFunctions(
                                FqName("com.ivianuu.injekt.internal.injektIntrinsic")
                            )
                                .single()
                        ).apply {
                            putTypeArgument(0, it.type)
                        }
                    )
                } else null
            )
        }

        body = DeclarationIrBuilder(pluginContext, symbol).run {
            irExprBody(
                irCall(
                    pluginContext.referenceFunctions(
                        FqName("com.ivianuu.injekt.internal.injektIntrinsic")
                    )
                        .single()
                ).apply {
                    putTypeArgument(0, returnType)
                }
            )
        }
    }

    private fun <T> rewriteCalls(
        owner: T,
        givenCalls: List<IrCall>,
        readerCalls: List<IrFunctionAccessExpression>,
        provider: IrBuilderWithScope.(IrType, IrFunctionAccessExpression, List<ScopeWithIr>) -> IrExpression
    ) where T : IrDeclaration, T : IrDeclarationParent {
        owner.transform(object : IrElementTransformerVoidWithContext() {
            override fun visitFunctionAccess(expression: IrFunctionAccessExpression): IrExpression {
                return when (val result =
                    super.visitFunctionAccess(expression) as IrFunctionAccessExpression) {
                    in givenCalls -> {
                        val rawExpression = provider(
                            DeclarationIrBuilder(pluginContext, result.symbol),
                            result.getTypeArgument(0)!!,
                            result,
                            allScopes
                        )

                        val arguments = (result.getValueArgument(0) as? IrVarargImpl)
                            ?.elements
                            ?.map { it as IrExpression } ?: emptyList()

                        val lazy = result.getValueArgument(1)
                            ?.let { it as IrConst<Boolean> }
                            ?.value ?: false

                        when {
                            arguments.isNotEmpty() -> DeclarationIrBuilder(
                                pluginContext,
                                result.symbol
                            ).irCall(
                                rawExpression.type.classOrNull!!
                                    .owner
                                    .functions
                                    .first { it.name.asString() == "invoke" }
                            ).apply {
                                dispatchReceiver = rawExpression
                                arguments.forEachIndexed { index, argument ->
                                    putValueArgument(index, argument)
                                }
                            }
                            lazy -> DeclarationIrBuilder(
                                pluginContext,
                                result.symbol
                            ).irCall(
                                rawExpression.type.classOrNull!!
                                    .owner
                                    .functions
                                    .first { it.name.asString() == "invoke" }
                            ).apply {
                                dispatchReceiver = rawExpression
                            }
                            else -> rawExpression
                        }
                    }
                    in readerCalls -> {
                        val transformedCallee = transformFunctionIfNeeded(result.symbol.owner)
                        fun IrFunctionAccessExpression.fillGivenParameters() {
                            transformedCallee.valueParameters.forEach { valueParameter ->
                                val valueArgument = getValueArgument(valueParameter.index)
                                if (valueParameter.hasAnnotation(InjektFqNames.Implicit) &&
                                    valueArgument == null
                                ) {
                                    putValueArgument(
                                        valueParameter.index,
                                        provider(
                                            DeclarationIrBuilder(pluginContext, result.symbol),
                                            valueParameter.type,
                                            result,
                                            allScopes
                                        )
                                    )
                                }
                            }
                        }
                        when (result) {
                            is IrConstructorCall -> {
                                IrConstructorCallImpl(
                                    result.startOffset,
                                    result.endOffset,
                                    transformedCallee.returnType,
                                    transformedCallee.symbol as IrConstructorSymbol,
                                    result.typeArgumentsCount,
                                    transformedCallee.typeParameters.size,
                                    transformedCallee.valueParameters.size,
                                    result.origin
                                ).apply {
                                    copyTypeAndValueArgumentsFrom(result)
                                    fillGivenParameters()
                                }
                            }
                            is IrDelegatingConstructorCall -> {
                                IrDelegatingConstructorCallImpl(
                                    result.startOffset,
                                    result.endOffset,
                                    result.type,
                                    transformedCallee.symbol as IrConstructorSymbol,
                                    result.typeArgumentsCount,
                                    transformedCallee.valueParameters.size
                                ).apply {
                                    copyTypeAndValueArgumentsFrom(result)
                                    fillGivenParameters()
                                }
                            }
                            else -> {
                                result as IrCall
                                IrCallImpl(
                                    result.startOffset,
                                    result.endOffset,
                                    transformedCallee.returnType,
                                    transformedCallee.symbol,
                                    result.origin,
                                    result.superQualifierSymbol
                                ).apply {
                                    copyTypeAndValueArgumentsFrom(result)
                                    fillGivenParameters()
                                }
                            }
                        }
                    }
                    else -> result
                }
            }
        }, null)
    }

    private val IrFunction.isGiven: Boolean
        get() = descriptor.fqNameSafe.asString() == "com.ivianuu.injekt.given"

    private fun IrFunction.copyAsReader(): IrFunction {
        return copy(pluginContext).apply {
            val descriptor = descriptor
            if (descriptor is PropertyGetterDescriptor &&
                annotations.findAnnotation(DescriptorUtils.JVM_NAME) == null
            ) {
                val name = JvmAbi.getterName(descriptor.correspondingProperty.name.identifier)
                annotations += DeclarationIrBuilder(pluginContext, symbol)
                    .jvmNameAnnotation(name, pluginContext)
                correspondingPropertySymbol?.owner?.getter = this
            }

            if (descriptor is PropertySetterDescriptor &&
                annotations.findAnnotation(DescriptorUtils.JVM_NAME) == null
            ) {
                val name = JvmAbi.setterName(descriptor.correspondingProperty.name.identifier)
                annotations += DeclarationIrBuilder(pluginContext, symbol)
                    .jvmNameAnnotation(name, pluginContext)
                correspondingPropertySymbol?.owner?.setter = this
            }
        }
    }

    private fun getReaderSignature(owner: IrDeclarationWithName): IrFunction? {
        val declaration = if (owner is IrConstructor)
            owner.constructedClass else owner

        return pluginContext.moduleDescriptor.getPackage(declaration.getPackageFragment()!!.fqName)
            .memberScope
            .getContributedDescriptors()
            .filterIsInstance<FunctionDescriptor>()
            .flatMapFix { pluginContext.referenceFunctions(it.fqNameSafe) }
            .map { it.owner }
            .filter { it.hasAnnotation(InjektFqNames.Name) }
            .singleOrNull { function ->
                function.getAnnotation(InjektFqNames.Name)!!
                    .getValueArgument(0)!!
                    .let { it as IrConst<String> }
                    .value == declaration.uniqueFqName()
            }
    }

}