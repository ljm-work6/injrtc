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

package com.ivianuu.injekt.compiler.resolution

import com.ivianuu.injekt.compiler.InjektContext
import com.ivianuu.injekt.compiler.transform.toKotlinType
import org.jetbrains.kotlin.descriptors.SourceElement
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptorImpl
import org.jetbrains.kotlin.descriptors.findClassifierAcrossModuleDependencies
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.descriptorUtil.classId

data class AnnotationRef(
    val type: TypeRef,
    val arguments: Map<Name, ConstantValue<*>>
)

fun AnnotationRef.substitute(map: Map<ClassifierRef, TypeRef>): AnnotationRef {
    if (map.isEmpty()) return this
    return copy(type = type.substitute(map))
}

fun AnnotationDescriptor.toAnnotationRef(
    context: InjektContext,
    trace: BindingTrace?
) = AnnotationRef(
    type = type.toTypeRef(context, trace),
    arguments = allValueArguments
        .mapValues { it.value.toInjektConstantValue(context, trace) }
)

fun AnnotationRef.toAnnotationDescriptor(context: InjektContext) = AnnotationDescriptorImpl(
    type.toKotlinType(context),
    arguments.mapValues { it.value.toKotlinConstantValue(context) },
    SourceElement.NO_SOURCE
)

fun ConstantValue<*>.toKotlinConstantValue(
    context: InjektContext
): org.jetbrains.kotlin.resolve.constants.ConstantValue<*> = when (this) {
    is ArrayValue -> org.jetbrains.kotlin.resolve.constants.ArrayValue(
        value.map { it.toKotlinConstantValue(context) }
    ) { type.toKotlinType(context) }
    is BooleanValue -> org.jetbrains.kotlin.resolve.constants.BooleanValue(value)
    is ByteValue -> org.jetbrains.kotlin.resolve.constants.ByteValue(value)
    is CharValue -> org.jetbrains.kotlin.resolve.constants.CharValue(value)
    is DoubleValue -> org.jetbrains.kotlin.resolve.constants.DoubleValue(value)
    is EnumValue -> org.jetbrains.kotlin.resolve.constants.EnumValue(
        value.first.descriptor!!.classId!!, value.second
    )
    is FloatValue -> org.jetbrains.kotlin.resolve.constants.FloatValue(value)
    is IntValue -> org.jetbrains.kotlin.resolve.constants.IntValue(value)
    is KClassValue -> org.jetbrains.kotlin.resolve.constants.KClassValue.create(
        value.defaultType.toKotlinType(context))!!
    is LongValue -> org.jetbrains.kotlin.resolve.constants.LongValue(value)
    is ShortValue -> org.jetbrains.kotlin.resolve.constants.ShortValue(value)
    is StringValue -> org.jetbrains.kotlin.resolve.constants.StringValue(value)
    is UByteValue -> org.jetbrains.kotlin.resolve.constants.UByteValue(value)
    is UIntValue -> org.jetbrains.kotlin.resolve.constants.UIntValue(value)
    is ULongValue -> org.jetbrains.kotlin.resolve.constants.ULongValue(value)
    is UShortValue -> org.jetbrains.kotlin.resolve.constants.UShortValue(value)
}

fun org.jetbrains.kotlin.resolve.constants.ConstantValue<*>.toInjektConstantValue(
    context: InjektContext,
    trace: BindingTrace?
): ConstantValue<*> = when (this) {
    is org.jetbrains.kotlin.resolve.constants.ArrayValue -> ArrayValue(
        value = value.map { it.toInjektConstantValue(context, trace) },
        type = getType(context.module).toTypeRef(context, trace)
    )
    is org.jetbrains.kotlin.resolve.constants.BooleanValue -> BooleanValue(
        value, getType(context.module).toTypeRef(context, trace))
    is org.jetbrains.kotlin.resolve.constants.ByteValue -> ByteValue(
        value, getType(context.module).toTypeRef(context, trace))
    is org.jetbrains.kotlin.resolve.constants.CharValue -> CharValue(
        value, getType(context.module).toTypeRef(context, trace))
    is org.jetbrains.kotlin.resolve.constants.DoubleValue -> DoubleValue(
        value, getType(context.module).toTypeRef(context, trace))
    is org.jetbrains.kotlin.resolve.constants.EnumValue -> EnumValue(
        context.module.findClassifierAcrossModuleDependencies(
            enumClassId
        )!!.toClassifierRef(context, trace) to enumEntryName,
        getType(context.module).toTypeRef(context, trace)
    )
    is org.jetbrains.kotlin.resolve.constants.FloatValue -> FloatValue(
        value, getType(context.module).toTypeRef(context, trace))
    is org.jetbrains.kotlin.resolve.constants.IntValue -> IntValue(
        value, getType(context.module).toTypeRef(context, trace))
    is org.jetbrains.kotlin.resolve.constants.KClassValue -> KClassValue(
        getArgumentType(context.module).toTypeRef(context, trace).classifier,
        getType(context.module).toTypeRef(context, trace)
    )
    is org.jetbrains.kotlin.resolve.constants.LongValue -> LongValue(
        value, getType(context.module).toTypeRef(context, trace))
    is org.jetbrains.kotlin.resolve.constants.ShortValue -> ShortValue(
        value, getType(context.module).toTypeRef(context, trace))
    is org.jetbrains.kotlin.resolve.constants.StringValue -> StringValue(
        value, getType(context.module).toTypeRef(context, trace))
    is org.jetbrains.kotlin.resolve.constants.UByteValue -> UByteValue(
        value, getType(context.module).toTypeRef(context, trace))
    is org.jetbrains.kotlin.resolve.constants.UIntValue -> UIntValue(
        value, getType(context.module).toTypeRef(context, trace))
    is org.jetbrains.kotlin.resolve.constants.ULongValue -> ULongValue(
        value, getType(context.module).toTypeRef(context, trace))
    is org.jetbrains.kotlin.resolve.constants.UShortValue -> UShortValue(
        value, getType(context.module).toTypeRef(context, trace))
    else -> error("Unexpected constant value $this")
}


sealed class ConstantValue<T> {
    abstract val value: T
    abstract val type: TypeRef
}

data class ArrayValue(
    override val value: List<ConstantValue<*>>,
    override val type: TypeRef
) : ConstantValue<List<ConstantValue<*>>>()

data class BooleanValue(
    override val value: Boolean,
    override val type: TypeRef
) : ConstantValue<Boolean>()

data class ByteValue(
    override val value: Byte,
    override val type: TypeRef
) : ConstantValue<Byte>()

data class CharValue(
    override val value: Char,
    override val type: TypeRef
) : ConstantValue<Char>()

data class DoubleValue(
    override val value: Double,
    override val type: TypeRef
) : ConstantValue<Double>()

data class EnumValue(
    override val value: Pair<ClassifierRef, Name>,
    override val type: TypeRef
) : ConstantValue<Pair<ClassifierRef, Name>>()

data class FloatValue(
    override val value: Float,
    override val type: TypeRef
) : ConstantValue<Float>()

data class IntValue(
    override val value: Int,
    override val type: TypeRef
) : ConstantValue<Int>()

data class KClassValue(
    override val value: ClassifierRef,
    override val type: TypeRef
) : ConstantValue<ClassifierRef>()

data class LongValue(
    override val value: Long,
    override val type: TypeRef
) : ConstantValue<Long>()

data class ShortValue(
    override val value: Short,
    override val type: TypeRef
) : ConstantValue<Short>()

data class StringValue(
    override val value: String,
    override val type: TypeRef
) : ConstantValue<String>()

data class UByteValue(
    override val value: Byte,
    override val type: TypeRef
) : ConstantValue<Byte>()

data class UIntValue(
    override val value: Int,
    override val type: TypeRef
) : ConstantValue<Int>()

data class ULongValue(
    override val value: Long,
    override val type: TypeRef
) : ConstantValue<Long>()

data class UShortValue(
    override val value: Short,
    override val type: TypeRef
) : ConstantValue<Short>()
