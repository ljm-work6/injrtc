package com.ivianuu.injekt.compiler

import com.ivianuu.injekt.compiler.resolution.GivenGraph
import com.ivianuu.injekt.compiler.resolution.ResolutionResult
import com.ivianuu.injekt.compiler.resolution.render
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory0
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory1
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.rendering.DefaultErrorMessages
import org.jetbrains.kotlin.diagnostics.rendering.DiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.rendering.DiagnosticParameterRenderer
import org.jetbrains.kotlin.diagnostics.rendering.Renderers
import org.jetbrains.kotlin.diagnostics.rendering.RenderingContext
import org.jetbrains.kotlin.name.Name

interface InjektErrors {
    companion object {
        @JvmField
        val MAP = DiagnosticFactoryToRendererMap("Injekt")

        @JvmField
        val UNRESOLVED_GIVEN =
            DiagnosticFactory1.create<PsiElement, GivenGraph.Error>(Severity.ERROR)
                .also {
                    MAP.put(
                        it,
                        "{0}",
                        object : DiagnosticParameterRenderer<GivenGraph.Error> {
                            override fun render(
                                obj: GivenGraph.Error,
                                renderingContext: RenderingContext,
                            ): String = buildString {
                                var indent = 0
                                fun withIndent(block: () -> Unit) {
                                    indent++
                                    block()
                                    indent--
                                }

                                fun indent() = buildString {
                                    repeat(indent) { append("    ") }
                                }

                                fun ResolutionResult.Failure.print() {
                                    val any: Any = when (this) {
                                        is ResolutionResult.Failure.CandidateAmbiguity -> {
                                            appendLine("${indent()}ambiguous given arguments of type ${request.type.render()} " +
                                                    "for parameter ${request.parameterName} of function ${request.callableFqName}:")
                                            withIndent {
                                                candidateResults
                                                    .map { it.candidate }
                                                    .forEach { candidate ->
                                                        appendLine("${indent()}${candidate.callableFqName}")
                                                    }
                                            }
                                        }
                                        is ResolutionResult.Failure.CallContextMismatch -> {
                                            appendLine("${indent()} current call context is ${actualCallContext} but" +
                                                    " ${candidate.callableFqName} is ${candidate.callContext}")
                                        }
                                        is ResolutionResult.Failure.CircularDependency -> {
                                            appendLine("${indent()}circular")
                                        }
                                        is ResolutionResult.Failure.DivergentGiven -> {
                                            appendLine("${indent()}divergent given $request")
                                        }
                                        is ResolutionResult.Failure.CandidateFailures -> {
                                            appendLine("${indent()}given candidate of type ${request.type.render()} " +
                                                    "for parameter ${request.parameterName} of function ${request.callableFqName} has failures:")
                                            withIndent {
                                                candidateFailure
                                                    .failure.print()
                                            }
                                        }
                                        is ResolutionResult.Failure.NoCandidates -> {
                                            appendLine("${indent()}no given argument found of type " +
                                                    "${request.type.render()} for parameter ${request.parameterName} of function ${request.callableFqName}")
                                        }
                                    }
                                }

                                obj
                                    .failures
                                    .flatMap { it.value }
                                    .forEach { it.print() }
                            }
                        }
                    )
                }

        @JvmField
        val NON_GIVEN_PARAMETER_ON_GIVEN_DECLARATION =
            DiagnosticFactory1.create<PsiElement, Name>(Severity.ERROR)
                .also {
                    MAP.put(
                        it,
                        "non @Given parameter on @{0} declaration",
                        Renderers.TO_STRING
                    )
                }

        @JvmField
        val GIVEN_CLASS_WITH_GIVEN_CONSTRUCTOR =
            DiagnosticFactory0.create<PsiElement>(Severity.ERROR)
                .also { MAP.put(it, "class cannot be given and have a given constructor") }

        @JvmField
        val CLASS_WITH_MULTIPLE_GIVEN_CONSTRUCTORS =
            DiagnosticFactory0.create<PsiElement>(Severity.ERROR)
                .also { MAP.put(it, "class cannot have multiple given constructors") }

        @JvmField
        val GIVEN_FUN_WITHOUT_EXPLICIT_RETURN_TYPE =
            DiagnosticFactory0.create<PsiElement>(Severity.ERROR)
                .also { MAP.put(it, "@GivenFun annotated function must have explicit return type") }

        @JvmField
        val GIVEN_FUN_MUST_HAVE_UNIQUE_NAME =
            DiagnosticFactory0.create<PsiElement>(Severity.ERROR)
                .also { MAP.put(it, "@GivenFun must have a unique name") }

        @JvmField
        val GIVEN_FUN_AS_MEMBER =
            DiagnosticFactory0.create<PsiElement>(Severity.ERROR)
                .also { MAP.put(it, "@GivenFun must be top level") }

        @JvmField
        val NON_FOR_KEY_TYPE_PARAMETER_AS_FOR_KEY =
            DiagnosticFactory1.create<PsiElement, TypeParameterDescriptor>(Severity.ERROR)
                .also {
                    MAP.put(
                        it,
                        "Cannot use {0} as @ForKey type argument",
                        object : DiagnosticParameterRenderer<TypeParameterDescriptor> {
                            override fun render(
                                obj: TypeParameterDescriptor,
                                renderingContext: RenderingContext
                            ): String {
                                return obj.name.asString()
                            }
                        }
                    )
                }

        @JvmField
        val INTERCEPTOR_WITHOUT_FACTORY_PARAMETER =
            DiagnosticFactory0.create<PsiElement>(Severity.ERROR)
                .also {
                    MAP.put(
                        it,
                        "@Interceptor declaration must have one parameter which matches the return type." +
                                "E.g. intercept(factory: () -> Foo): Foo"
                    )
                }

        init {
            Errors.Initializer.initializeFactoryNamesAndDefaultErrorMessages(
                InjektErrors::class.java,
                InjektDefaultErrorMessages
            )
        }

        object InjektDefaultErrorMessages : DefaultErrorMessages.Extension {
            override fun getMap() = MAP
        }

    }
}
