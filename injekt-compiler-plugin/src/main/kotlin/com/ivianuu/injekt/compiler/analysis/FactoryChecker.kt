package com.ivianuu.injekt.compiler.analysis

import com.ivianuu.injekt.compiler.InjektErrors
import com.ivianuu.injekt.compiler.InjektFqNames
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.callUtil.isCallableReference
import org.jetbrains.kotlin.resolve.calls.checkers.CallChecker
import org.jetbrains.kotlin.resolve.calls.checkers.CallCheckerContext
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.checkers.DeclarationChecker
import org.jetbrains.kotlin.resolve.checkers.DeclarationCheckerContext
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.scopes.utils.parentsWithSelf
import org.jetbrains.kotlin.types.typeUtil.isTypeParameter
import org.jetbrains.kotlin.types.typeUtil.supertypes

class FactoryChecker : CallChecker, DeclarationChecker {

    override fun check(
        declaration: KtDeclaration,
        descriptor: DeclarationDescriptor,
        context: DeclarationCheckerContext
    ) {
        if (descriptor is FunctionDescriptor && (descriptor.annotations.hasAnnotation(InjektFqNames.Factory) ||
                    descriptor.annotations.hasAnnotation(InjektFqNames.ChildFactory))
        ) {
            checkFactoriesLastStatementIsCreate(
                declaration as KtFunction,
                descriptor,
                context
            )

            val annotationsSize = descriptor.annotations
                .filter {
                    it.fqName == InjektFqNames.ChildFactory ||
                            it.fqName == InjektFqNames.Factory ||
                            it.fqName == InjektFqNames.Module
                }
                .size

            if (annotationsSize > 1) {
                context.trace.report(InjektErrors.EITHER_MODULE_OR_FACTORY.on(declaration))
            }

            if (descriptor.isSuspend) {
                context.trace.report(
                    InjektErrors.CANNOT_BE_SUSPEND
                        .on(declaration)
                )
            }

            if (!descriptor.isInline) {
                if (descriptor.typeParameters.isNotEmpty()) {
                    context.trace.report(
                        InjektErrors.FACTORY_WITH_TYPE_PARAMETERS_MUST_BE_INLINE
                            .on(declaration)
                    )
                }
                descriptor.valueParameters.forEach { valueParameter ->
                    if (valueParameter.type.annotations.hasAnnotation(InjektFqNames.Module)) {
                        context.trace.report(
                            InjektErrors.MODULE_PARAMETER_WITHOUT_INLINE
                                .on(valueParameter.findPsi() ?: declaration)
                        )
                    }
                }
            }
        }
    }

    override fun check(
        resolvedCall: ResolvedCall<*>,
        reportOn: PsiElement,
        context: CallCheckerContext
    ) {
        val resultingDescriptor = resolvedCall.resultingDescriptor

        when (resultingDescriptor.fqNameSafe.asString()) {
            "com.ivianuu.injekt.childFactory" -> {
                val referencedFunction = resolvedCall
                    .valueArgumentsByIndex!!.singleOrNull()
                    ?.arguments
                    ?.single()
                    ?.getArgumentExpression()
                    ?.let { it as? KtCallableReferenceExpression }
                    ?.callableReference
                    ?.getResolvedCall(context.trace.bindingContext)
                    ?.resultingDescriptor

                if (referencedFunction?.annotations?.hasAnnotation(InjektFqNames.ChildFactory) != true) {
                    context.trace.report(InjektErrors.NOT_A_CHILD_FACTORY.on(reportOn))
                }
            }
            "com.ivianuu.injekt.createImpl" -> {
                checkCreateImplInvocation(resolvedCall, reportOn, context)
            }
        }

        if (resultingDescriptor is FunctionDescriptor &&
            resultingDescriptor.annotations.hasAnnotation(InjektFqNames.Factory) &&
            resultingDescriptor.isInline
        ) {
            if (resolvedCall.typeArguments.any { it.value.isTypeParameter() }) {
                context.trace.report(
                    InjektErrors.INLINE_FACTORY_CALL_MUST_HAVE_CONCRETE_TYPE
                        .on(reportOn)
                )
            }
            // todo we need to detect whether this factory is a @ImplFactory
            //resolvedCall.getReturnType().constructor.declarationDescriptor?.let {
            //    if (it is ClassDescriptor) checkImplType(it, context, reportOn)
            //}
        }

        if (resultingDescriptor.annotations.hasAnnotation(InjektFqNames.ChildFactory) &&
            !resolvedCall.call.isCallableReference()
        ) {
            context.trace.report(InjektErrors.CANNOT_INVOKE_CHILD_FACTORIES.on(reportOn))
        }
    }

    private fun checkFactoriesLastStatementIsCreate(
        element: KtFunction,
        descriptor: FunctionDescriptor,
        context: DeclarationCheckerContext
    ) {
        fun reportLastStatementMustBeCreate() {
            context.trace.report(InjektErrors.LAST_STATEMENT_MUST_BE_CREATE.on(element))
        }

        val statements = element.bodyBlockExpression?.statements
            ?: listOfNotNull(element.bodyExpression)

        val returnedExpression = when (val lastStatement = statements.lastOrNull()) {
            is KtReturnExpression -> lastStatement.returnedExpression
            is KtCallExpression -> lastStatement
            else -> null
        }

        if (returnedExpression !is KtCallExpression) {
            reportLastStatementMustBeCreate()
            return
        }

        val resolvedCall = returnedExpression.getResolvedCall(context.trace.bindingContext)
        if (resolvedCall == null) {
            reportLastStatementMustBeCreate()
            return
        }

        if (resolvedCall.resultingDescriptor.fqNameSafe.asString() != "com.ivianuu.injekt.createImpl" &&
            descriptor.annotations.hasAnnotation(InjektFqNames.ChildFactory)
        ) {
            context.trace.report(InjektErrors.CREATE_INSTANCE_IN_CHILD_FACTORY.on(element))
        }

        if (resolvedCall.resultingDescriptor.fqNameSafe.asString() != "com.ivianuu.injekt.createImpl" &&
            resolvedCall.resultingDescriptor.fqNameSafe.asString() != "com.ivianuu.injekt.createInstance"
        ) {
            reportLastStatementMustBeCreate()
        }
    }

    private fun checkCreateImplInvocation(
        resolvedCall: ResolvedCall<*>,
        reportOn: PsiElement,
        context: CallCheckerContext
    ) {
        val type =
            resolvedCall.typeArguments.values.singleOrNull()

        type?.constructor?.declarationDescriptor?.let {
            if (it is ClassDescriptor) checkImplType(it, context, reportOn)
        }

        val enclosingModuleFunction = findEnclosingModuleFunctionContext(context) {
            it.annotations.hasAnnotation(InjektFqNames.Factory) ||
                    it.annotations.hasAnnotation(InjektFqNames.ChildFactory)
        }

        when {
            enclosingModuleFunction != null -> {
                if (context.scope.parentsWithSelf.any {
                        it.isScopeForDefaultParameterValuesOf(
                            enclosingModuleFunction
                        )
                    }) {
                    context.trace.report(
                        Errors.UNSUPPORTED.on(
                            reportOn,
                            "createImpl function calls in a context of default parameter value"
                        )
                    )
                }
            }
            resolvedCall.call.isCallableReference() -> {
                // do nothing: we can get callable reference to suspend function outside suspend context
            }
            else -> {
                context.trace.report(
                    InjektErrors.CREATE_IMPl_WITHOUT_FACTORY.on(reportOn)
                )
            }
        }
    }

    private fun checkImplType(
        clazz: ClassDescriptor,
        context: CallCheckerContext,
        reportOn: PsiElement
    ) {
        if (clazz.modality != Modality.ABSTRACT) {
            context.trace.report(
                InjektErrors.FACTORY_IMPL_MUST_BE_ABSTRACT
                    .on(reportOn)
            )
        }

        if (clazz.kind == ClassKind.CLASS && clazz.constructors.none { it.valueParameters.isEmpty() }) {
            context.trace.report(
                InjektErrors.IMPL_SUPER_TYPE_MUST_HAVE_EMPTY_CONSTRUCTOR
                    .on(reportOn)
            )
        }

        clazz.forEachDeclarationInThisAndSuperTypes { declaration ->
            when (declaration) {
                is FunctionDescriptor -> {
                    if (declaration.typeParameters.isNotEmpty()) {
                        context.trace.report(
                            InjektErrors.PROVISION_FUNCTION_CANNOT_HAVE_TYPE_PARAMETERS
                                .on(reportOn)
                        )
                    }
                    if (declaration.valueParameters.isNotEmpty()) {
                        context.trace.report(
                            InjektErrors.PROVISION_FUNCTION_CANNOT_HAVE_VALUE_PARAMETERS
                                .on(reportOn)
                        )
                    }
                    if (declaration.isSuspend) {
                        context.trace.report(
                            InjektErrors.PROVISION_FUNCTION_CANNOT_BE_SUSPEND
                                .on(reportOn)
                        )
                    }
                }
                is PropertyDescriptor -> {
                    if (declaration.isVar) {
                        context.trace.report(
                            InjektErrors.IMPL_CANNOT_CONTAIN_VARS
                                .on(reportOn)
                        )
                    }
                }
            }
        }
    }

    private fun ClassDescriptor.forEachDeclarationInThisAndSuperTypes(block: (DeclarationDescriptor) -> Unit) {
        unsubstitutedMemberScope
            .getContributedDescriptors()
            .filter {
                it !is CallableMemberDescriptor ||
                        it.dispatchReceiverParameter?.type != builtIns.anyType
            }
            .forEach(block)
        defaultType.supertypes()
            .mapNotNull { it.constructor.declarationDescriptor as? ClassDescriptor }
            .forEach { it.forEachDeclarationInThisAndSuperTypes(block) }
    }
}