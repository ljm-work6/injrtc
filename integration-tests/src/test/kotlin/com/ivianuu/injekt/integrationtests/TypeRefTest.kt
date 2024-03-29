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

package com.ivianuu.injekt.integrationtests

import com.ivianuu.injekt.compiler.InjektContext
import com.ivianuu.injekt.compiler.asNameId
import com.ivianuu.injekt.compiler.resolution.AnnotationRef
import com.ivianuu.injekt.compiler.resolution.ClassifierRef
import com.ivianuu.injekt.compiler.resolution.STAR_PROJECTION_TYPE
import com.ivianuu.injekt.compiler.resolution.StringValue
import com.ivianuu.injekt.compiler.resolution.TypeRef
import com.ivianuu.injekt.compiler.resolution.copy
import com.ivianuu.injekt.compiler.resolution.getSubstitutionMap
import com.ivianuu.injekt.compiler.resolution.isAssignableTo
import com.ivianuu.injekt.compiler.resolution.isSubTypeOf
import com.ivianuu.injekt.compiler.resolution.toTypeRef
import com.ivianuu.injekt.compiler.resolution.typeWith
import com.ivianuu.injekt.test.codegen
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.findClassifierAcrossModuleDependencies
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension
import org.junit.Test

class TypeRefTest {

    @Test
    fun testSimpleTypeWithSameClassifierIsAssignable() = withAnalysisContext {
        stringType shouldBeAssignable stringType
    }

    @Test
    fun testSimpleTypeWithDifferentClassifierIsNotAssignable() = withAnalysisContext {
        stringType shouldNotBeAssignable intType
    }

    @Test
    fun testNonNullIsAssignableToNullable() = withAnalysisContext {
        stringType shouldBeAssignable stringType.nullable()
    }

    @Test
    fun testNullableIsNotAssignableToNonNullable() = withAnalysisContext {
        stringType.nullable() shouldNotBeAssignable stringType
    }

    @Test
    fun testMatchingGenericTypeIsAssignable() = withAnalysisContext {
        listType.typeWith(listOf(stringType)) shouldBeAssignable listType
    }

    @Test
    fun testNotMatchingGenericTypeIsNotAssignable() = withAnalysisContext {
        listType.typeWith(stringType) shouldNotBeAssignable listType.typeWith(intType)
    }

    @Test
    fun testAnyTypeIsAssignableToStarProjectedType() = withAnalysisContext {
        starProjectedType shouldBeAssignable stringType
    }

    @Test
    fun testStarProjectedTypeMatchesNullableType() = withAnalysisContext {
        starProjectedType shouldBeAssignable stringType.nullable()
    }

    @Test
    fun testStarProjectedTypeMatchesQualifiedType() = withAnalysisContext {
        starProjectedType shouldBeAssignable stringType.qualified(qualifier1())
    }

    @Test
    fun testRandomTypeIsNotSubTypeOfTypeAliasWithAnyExpandedType() = withAnalysisContext {
        stringType shouldNotBeAssignable typeAlias(anyType)
    }

    @Test
    fun testTypeAliasIsNotAssignableToOtherTypeAliasOfTheSameExpandedType() = withAnalysisContext {
        typeAlias(stringType) shouldNotBeAssignable typeAlias(stringType)
    }

    @Test
    fun testTypeAliasIsAssignableToOtherTypeAliasOfTheSameExpandedType() = withAnalysisContext {
        typeAlias(stringType) shouldNotBeAssignable typeAlias(stringType)
    }

    @Test
    fun testTypeAliasIsSubTypeOfExpandedType() = withAnalysisContext {
        typeAlias(stringType) shouldBeSubTypeOf stringType
    }

    @Test
    fun testNestedTypeAliasIsSubTypeOfExpandedType() = withAnalysisContext {
        typeAlias(typeAlias(stringType)) shouldBeSubTypeOf stringType
    }

    @Test
    fun testSameComposabilityIsAssignable() = withAnalysisContext {
        composableFunction(0) shouldBeAssignable composableFunction(0)
    }

    @Test
    fun testComposableTypeAliasIsSubTypeOfComposableFunctionUpperBound() = withAnalysisContext {
        typeAlias(composableFunction(0)) shouldBeAssignable typeParameter(composableFunction(0))
    }

    @Test
    fun testSameQualifiersIsAssignable() = withAnalysisContext {
        stringType.qualified(qualifier1()) shouldBeAssignable stringType.qualified(qualifier1())
    }

    @Test
    fun testDifferentQualifiersIsNotAssignable() = withAnalysisContext {
        stringType.qualified(qualifier1()) shouldNotBeAssignable stringType.qualified(qualifier2("a"))
    }

    @Test
    fun testSameQualifiersWithSameArgsIsAssignable() = withAnalysisContext {
        stringType.qualified(qualifier2("a")) shouldBeAssignable
                stringType.qualified(qualifier2("a"))
    }

    @Test
    fun testSameQualifiersWithDifferentArgsIsAssignable() = withAnalysisContext {
        stringType.qualified(qualifier2("a")) shouldNotBeAssignable
                stringType.qualified(qualifier2("b"))
    }

    @Test
    fun testSubTypeOfTypeParameterWithNullableAnyUpperBound() = withAnalysisContext {
        stringType shouldBeAssignable typeParameter()
    }

    @Test
    fun testComposableSubTypeOfTypeParameterWithNullableAnyUpperBound() = withAnalysisContext {
        composableFunction(0) shouldBeAssignable typeParameter()
    }

    @Test
    fun testComposableIsNotSubTypeOfNonComposable() = withAnalysisContext {
        composableFunction(0) shouldNotBeAssignable typeParameter(function(0))
    }

    @Test
    fun testSubTypeOfTypeParameterWithNonNullAnyUpperBound() = withAnalysisContext {
        stringType shouldBeAssignable typeParameter(nullable = false)
    }

    @Test
    fun testNullableSubTypeOfTypeParameterWithNonNullAnyUpperBound() = withAnalysisContext {
        stringType.nullable() shouldNotBeAssignable typeParameter(nullable = false)
    }

    @Test
    fun testSubTypeOfTypeParameterWithUpperBound() = withAnalysisContext {
        subType(stringType) shouldBeSubTypeOf typeParameter(stringType)
    }

    @Test
    fun testSubTypeOfTypeParameterWithNullableUpperBound() = withAnalysisContext {
        subType(stringType) shouldBeSubTypeOf typeParameter(stringType.nullable())
    }

    @Test
    fun testQualifiedSubTypeOfQualifiedTypeParameter() = withAnalysisContext {
        stringType.qualified(qualifier1()) shouldBeAssignable
                typeParameter(nullable = false).qualified(qualifier1())
    }

    @Test
    fun testNestedQualifiedSubTypeOfNestedQualifiedTypeParameter() = withAnalysisContext {
        listType.typeWith(stringType.qualified(qualifier1())) shouldBeAssignable
                listType.typeWith(typeParameter(nullable = false).qualified(qualifier1()))
    }

    @Test
    fun testUnqualifiedSubTypeOfTypeParameterWithQualifiedUpperBound() = withAnalysisContext {
        stringType shouldNotBeAssignable
                typeParameter(anyNType.qualified(qualifier1()))
    }

    @Test
    fun testNestedUnqualifiedSubTypeOfNestedTypeParameterWithQualifiedUpperBound() =
        withAnalysisContext {
            listType.typeWith(stringType) shouldNotBeAssignable
                    listType.typeWith(typeParameter(anyNType.qualified(qualifier1())))
        }

    @Test
    fun testNestedQualifiedSubTypeOfNestedTypeParameterWithQualifiedUpperBound() =
        withAnalysisContext {
            listType.typeWith(stringType.qualified(qualifier1())) shouldBeAssignable
                    listType.typeWith(typeParameter(anyNType.qualified(qualifier1())))
        }

    @Test
    fun testQualifiedTypeIsSubTypeOfTypeParameterWithQualifiedUpperBound() = withAnalysisContext {
        val sTypeParameter = typeParameter(listType.typeWith(stringType))
        val tTypeParameter = typeParameter(sTypeParameter.qualified(qualifier1()))
        listType.typeWith(stringType)
            .qualified(qualifier1()) shouldBeSubTypeOf tTypeParameter
    }

    @Test
    fun testQualifiedTypeAliasIsSubTypeOfTypeParameterWithSameQualifiers() = withAnalysisContext {
        typeAlias(
            function(0)
                .copy(isMarkedComposable = true)
                .qualified(qualifier1())
        ) shouldBeSubTypeOf typeParameter(
            function(0)
                .copy(isMarkedComposable = true)
                .qualified(qualifier1())
        )
    }

    @Test
    fun testQualifiedTypeAliasIsNotSubTypeOfTypeParameterWithOtherQualifiers() = withAnalysisContext {
        typeAlias(
            function(0)
                .copy(isMarkedComposable = true)
                .qualified(qualifier1())
        ) shouldNotBeSubTypeOf typeParameter(
                function(0)
                    .copy(isMarkedComposable = true)
                    .qualified(qualifier2(""))
            )
    }

    @Test
    fun testTypeAliasIsNotSubTypeOfTypeParameterWithOtherTypeAliasUpperBound() = withAnalysisContext {
        val typeAlias1 = typeAlias(function(0).typeWith(stringType))
        val typeAlias2 = typeAlias(function(0).typeWith(intType))
        val typeParameter = typeParameter(typeAlias1)
        typeAlias2 shouldNotBeSubTypeOf typeParameter
    }

    @Test
    fun testTypeAliasIsSubTypeOfOtherTypeAlias() = withAnalysisContext {
        val typeAlias1 = typeAlias(function(0).typeWith(stringType))
        val typeAlias2 = typeAlias(typeAlias1)
        typeAlias2 shouldBeSubTypeOf typeAlias1
    }

    @Test
    fun testTypeAliasIsSubTypeOfTypeParameterWithTypeAliasUpperBound() = withAnalysisContext {
        val superTypeAlias = typeAlias(function(0))
        val typeParameterS = typeParameter(superTypeAlias)
        val typeParameterT = typeParameter(typeParameterS.qualified(qualifier1()))
        val subTypeAlias = typeAlias(superTypeAlias)
        subTypeAlias.qualified(qualifier1()) shouldBeSubTypeOf typeParameterT
    }

    @Test
    fun testGetSubstitutionMap() = withAnalysisContext {
        val superType = typeParameter()
        val map = getSubstitutionMap(context, listOf(stringType to superType))
        stringType shouldBe map[superType.classifier]
    }

    @Test
    fun testGetSubstitutionMapWithExtraTypeParameter() = withAnalysisContext {
        val typeParameterU = typeParameter()
        val typeParameterS = typeParameter(listType.typeWith(typeParameterU))
        val typeParameterT = typeParameter(typeParameterS)
        val substitutionType = listType.typeWith(stringType)
        val map = getSubstitutionMap(context, listOf(substitutionType to typeParameterT))
        map[typeParameterT.classifier] shouldBe substitutionType
        map[typeParameterS.classifier] shouldBe substitutionType
        map[typeParameterU.classifier] shouldBe stringType
    }

    @Test
    fun testGetSubstitutionMapWithNestedGenerics() = withAnalysisContext {
        val superType = typeParameter()
        val map = getSubstitutionMap(context, listOf(listType.typeWith(stringType) to listType.typeWith(superType)))
        stringType shouldBe map[superType.classifier]
    }

    @Test
    fun testGetSubstitutionMapWithQualifiers() = withAnalysisContext {
        val unqualifiedSuperType = typeParameter()
        val qualifiedSuperType = unqualifiedSuperType.qualified(qualifier1())
        val substitutionType = stringType.qualified(qualifier1())
        val map = getSubstitutionMap(context, listOf(substitutionType to qualifiedSuperType))
        stringType shouldBe map[unqualifiedSuperType.classifier]
    }

    @Test
    fun testGetSubstitutionMapWithGenericQualifierArguments() = withAnalysisContext {
        val typeParameter1 = typeParameter()
        val typeParameter2 = typeParameter()
        val qualifier = ClassifierRef(
            FqName("MyQualifier"),
            typeParameters = listOf(
                ClassifierRef(
                    fqName = FqName("MyQualifier.T")
                )
            )
        )
        val superType = typeParameter1.qualified(
            AnnotationRef(
                qualifier.defaultType.typeWith(typeParameter2),
                emptyMap()
            )
        )
        val substitutionType = stringType.qualified(
            AnnotationRef(
                qualifier.defaultType.typeWith(intType),
                emptyMap()
            )
        )
        val map = getSubstitutionMap(context, listOf(substitutionType to superType))
        map[typeParameter1.classifier] shouldBe stringType
        map[typeParameter2.classifier] shouldBe intType
    }

    @Test
    fun testGetSubstitutionMapPrefersInput() = withAnalysisContext {
        val typeParameter1 = typeParameter()
        val typeParameter2 = typeParameter(typeParameter1)
        val map = getSubstitutionMap(
            context,
            listOf(
                listType.typeWith(stringType) to listType.typeWith(typeParameter2),
                charSequenceType to typeParameter1
            )
        )
        map[typeParameter1.classifier] shouldBe charSequenceType
        map[typeParameter2.classifier] shouldBe stringType
    }

    @Test
    fun testGetSubstitutionMapWithSubClass() = withAnalysisContext {
        val classType = classType(listType.typeWith(stringType))
        val typeParameter = typeParameter()
        val map = getSubstitutionMap(context, listOf(classType to listType.typeWith(typeParameter)))
        map.shouldHaveSize(1)
        map.shouldContain(typeParameter.classifier, stringType)
    }

    @Test
    fun testSubTypeWithTypeParameterIsAssignableToSuperTypeWithOtherTypeParameterButSameSuperTypes() = withAnalysisContext {
        mutableListType.typeWith(typeParameter()) shouldBeAssignable listType.typeWith(typeParameter())
    }

    @Test
    fun testQualifiedSubTypeWithTypeParameterIsNotAssignableToSuperTypeWithOtherTypeParameterButSameSuperTypes() = withAnalysisContext {
        mutableListType.typeWith(typeParameter())
            .qualified(qualifier1()) shouldNotBeAssignable listType.typeWith(typeParameter())
    }

    @Test
    fun testComparableStackOverflowBug() = withAnalysisContext {
        floatType shouldNotBeSubTypeOf comparable.typeWith(intType)
    }

    private fun withAnalysisContext(
        block: AnalysisContext.() -> Unit,
    ) {
        codegen(
            """
            
        """,
            config = {
                compilerPlugins += object : ComponentRegistrar {
                    override fun registerProjectComponents(
                        project: MockProject,
                        configuration: CompilerConfiguration,
                    ) {
                        AnalysisHandlerExtension.registerExtension(
                            project,
                            object : AnalysisHandlerExtension {
                                override fun analysisCompleted(
                                    project: Project,
                                    module: ModuleDescriptor,
                                    bindingTrace: BindingTrace,
                                    files: Collection<KtFile>,
                                ): AnalysisResult? {
                                    block(AnalysisContext(module))
                                    return null
                                }
                            }
                        )
                    }
                }
            }
        )
    }

    class AnalysisContext(val module: ModuleDescriptor) {

        val context = InjektContext(module)

        val comparable = typeFor(StandardNames.FqNames.comparable)
        val anyType = typeFor(StandardNames.FqNames.any.toSafe())
        val anyNType = anyType.copy(isMarkedNullable = true)
        val floatType = typeFor(StandardNames.FqNames._float.toSafe())
        val intType = typeFor(StandardNames.FqNames._int.toSafe())
        val stringType = typeFor(StandardNames.FqNames.string.toSafe())
        val charSequenceType = typeFor(StandardNames.FqNames.charSequence.toSafe())
        val listType = typeFor(StandardNames.FqNames.list)
        val mutableListType = typeFor(StandardNames.FqNames.mutableList)
        val starProjectedType = STAR_PROJECTION_TYPE

        fun composableFunction(parameterCount: Int) = typeFor(
            FqName("kotlin.Function$parameterCount")
        ).copy(isMarkedComposable = true)

        fun function(parameterCount: Int) = typeFor(
            FqName("kotlin.Function$parameterCount")
        )

        fun qualifier1() = AnnotationRef(
            typeFor(FqName("com.ivianuu.injekt.test.Qualifier1")),
            emptyMap()
        )

        fun qualifier2(value: String) = AnnotationRef(
            typeFor(FqName("com.ivianuu.injekt.test.Qualifier2")),
            mapOf("value".asNameId() to StringValue(value, stringType))
        )

        private var id = 0

        fun subType(
            vararg superTypes: TypeRef,
            fqName: FqName = FqName("SubType${id}"),
        ) = ClassifierRef(
            fqName = fqName,
            superTypes = superTypes.toList()
        ).defaultType

        fun typeAlias(
            expandedType: TypeRef,
            fqName: FqName = FqName("Alias${id++}"),
        ) = ClassifierRef(
            fqName = fqName,
            superTypes = listOf(expandedType),
            isTypeAlias = true
        ).defaultType

        fun classType(
            vararg superTypes: TypeRef,
            fqName: FqName = FqName("ClassType${id++}"),
        ) = ClassifierRef(
            fqName = fqName,
            superTypes = superTypes.toList()
        ).defaultType

        fun typeParameter(
            fqName: FqName = FqName("TypeParameter${id++}"),
            nullable: Boolean = true,
        ): TypeRef =
            typeParameter(upperBounds = *emptyArray(), nullable = nullable, fqName = fqName)

        fun typeParameter(
            vararg upperBounds: TypeRef,
            nullable: Boolean = true,
            fqName: FqName = FqName("TypeParameter${id++}"),
        ) = ClassifierRef(
            fqName = fqName,
            superTypes = listOf(anyType.copy(isMarkedNullable = nullable)) + upperBounds,
            isTypeParameter = true
        ).defaultType

        fun typeFor(fqName: FqName) = module.findClassifierAcrossModuleDependencies(
            ClassId.topLevel(fqName)
        )!!.defaultType.toTypeRef(context, null)

        infix fun TypeRef.shouldBeAssignable(other: TypeRef) {
            if (!isAssignableTo(context, other)) {
                throw AssertionError("'$this' is not assignable to '$other'")
            }
        }

        infix fun TypeRef.shouldNotBeAssignable(other: TypeRef) {
            if (isAssignableTo(context, other)) {
                throw AssertionError("'$this' is assignable to '$other'")
            }
        }

        infix fun TypeRef.shouldBeSubTypeOf(other: TypeRef) {
            if (!isSubTypeOf(context, other)) {
                throw AssertionError("'$this' is not sub type of '$other'")
            }
        }

        infix fun TypeRef.shouldNotBeSubTypeOf(other: TypeRef) {
            if (isSubTypeOf(context, other)) {
                throw AssertionError("'$this' is sub type of '$other'")
            }
        }
    }

    fun TypeRef.nullable() = copy(isMarkedNullable = true)

    fun TypeRef.nonNull() = copy(isMarkedNullable = false)

    fun TypeRef.qualified(vararg qualifiers: AnnotationRef) =
        copy(qualifiers = qualifiers.toList())

    fun TypeRef.typeWith(vararg typeArguments: TypeRef) =
        copy(arguments = typeArguments.toList())

}