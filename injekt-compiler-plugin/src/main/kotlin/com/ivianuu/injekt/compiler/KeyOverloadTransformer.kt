package com.ivianuu.injekt.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.copyTypeArgumentsFrom
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.toKotlinType
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.referenceFunction
import org.jetbrains.kotlin.ir.util.substitute
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.psi2ir.findFirstFunction
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.types.typeUtil.isSubtypeOf

class KeyOverloadTransformer(pluginContext: IrPluginContext) :
    AbstractInjektTransformer(pluginContext) {

    private val key = getClass(InjektClassNames.Key)
    private val qualifier = getClass(InjektClassNames.Qualifier)

    private lateinit var moduleFragment: IrModuleFragment

    override fun visitModuleFragment(declaration: IrModuleFragment): IrModuleFragment {
        moduleFragment = declaration
        return super.visitModuleFragment(declaration)
    }

    override fun visitCall(expression: IrCall): IrExpression {
        super.visitCall(expression)

        val callee = expression.symbol.owner

        if (!callee.annotations.hasAnnotation(InjektClassNames.KeyOverloadStub)) return expression

        var keyOverloadFunction: IrFunction? = null

        moduleFragment.transformChildrenVoid(object : IrElementTransformerVoid() {
            override fun visitFunction(declaration: IrFunction): IrStatement {
                if (keyOverloadFunction != null) return declaration
                val otherFunction = declaration.symbol.owner
                if (otherFunction.annotations.hasAnnotation(InjektClassNames.KeyOverload) &&
                    (otherFunction.extensionReceiverParameter
                        ?: otherFunction.dispatchReceiverParameter)?.type == callee.extensionReceiverParameter?.type &&
                    otherFunction.typeParameters.size == callee.typeParameters.size &&
                    otherFunction.valueParameters.size == callee.valueParameters.size &&
                    otherFunction.valueParameters.all { otherValueParameter ->
                        val calleeValueParameter = callee.valueParameters[otherValueParameter.index]
                        if (otherValueParameter.type.toKotlinType().constructor.declarationDescriptor == key) {
                            calleeValueParameter.type.toKotlinType()
                                .isSubtypeOf(qualifier.defaultType)
                        } else {
                            otherValueParameter.name == calleeValueParameter.name
                        }
                    }
                ) {
                    keyOverloadFunction = otherFunction
                    return declaration
                }
                return super.visitFunction(declaration)
            }
        })

        if (keyOverloadFunction == null) {
            fun MemberScope.findKeyOverloadFunction() = try {
                findFirstFunction(callee.name.asString()) { otherFunction ->
                    otherFunction.annotations.hasAnnotation(InjektClassNames.KeyOverload)
                            && (otherFunction.extensionReceiverParameter
                        ?: otherFunction.dispatchReceiverParameter)?.type == callee.extensionReceiverParameter?.type?.toKotlinType() &&
                            otherFunction.typeParameters.size == callee.typeParameters.size &&
                            otherFunction.valueParameters.size == callee.valueParameters.size &&
                            otherFunction.valueParameters.all { otherValueParameter ->
                                val calleeValueParameter =
                                    callee.valueParameters[otherValueParameter.index]
                                if (otherValueParameter.type.constructor.declarationDescriptor == key) {
                                    calleeValueParameter.type.toKotlinType()
                                        .isSubtypeOf(qualifier.defaultType)
                                } else {
                                    otherValueParameter.name == calleeValueParameter.name
                                }
                            }
                }
            } catch (e: Exception) {
                null
            }

            keyOverloadFunction =
                ((callee.descriptor.containingDeclaration as PackageFragmentDescriptor)
                    .let { pluginContext.moduleDescriptor.getPackage(it.fqName).memberScope }
                    .findKeyOverloadFunction()
                    ?: (callee.descriptor.extensionReceiverParameter?.value?.type?.constructor?.declarationDescriptor as? ClassDescriptor)
                        ?.unsubstitutedMemberScope?.findKeyOverloadFunction())
                    ?.let { symbolTable.referenceFunction(it) }
                    ?.ensureBound()
                    ?.owner
        }

        checkNotNull(keyOverloadFunction) {
            "Couldn't find @KeyOverload function for ${callee.dump()}"
        }

        val keyOf = injektPackage.memberScope
            .findFirstFunction("keyOf") { it.valueParameters.size == 1 }

        val builder = DeclarationIrBuilder(pluginContext, expression.symbol)

        return builder.irCall(keyOverloadFunction!!).apply {
            if (keyOverloadFunction!!.dispatchReceiverParameter != null) {
                dispatchReceiver = expression.extensionReceiver
            } else {
                extensionReceiver = expression.extensionReceiver
            }

            copyTypeArgumentsFrom(expression)

            keyOverloadFunction!!.valueParameters.forEach { valueParameter ->
                if (valueParameter.type.toKotlinType().constructor.declarationDescriptor == key) {
                    val keyType = valueParameter.type
                        .substitute(
                            keyOverloadFunction!!.typeParameters
                                .associate { it.symbol to expression.getTypeArgument(it.index)!! }
                        )
                    putValueArgument(
                        valueParameter.index,
                        builder.irCall(
                            callee = symbolTable.referenceSimpleFunction(keyOf),
                            type = keyType
                        ).apply {
                            putTypeArgument(
                                0,
                                (keyType as IrSimpleType).arguments.single().typeOrNull!!
                            )
                            putValueArgument(0, expression.getValueArgument(valueParameter.index))
                        }
                    )
                } else {
                    putValueArgument(
                        valueParameter.index,
                        expression.getValueArgument(valueParameter.index)
                    )
                }
            }
        }
    }

}