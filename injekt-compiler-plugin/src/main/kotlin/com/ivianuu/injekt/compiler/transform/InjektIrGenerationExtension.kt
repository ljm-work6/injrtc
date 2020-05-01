package com.ivianuu.injekt.compiler.transform

import com.ivianuu.injekt.compiler.InjektSymbols
import com.ivianuu.injekt.compiler.generateSymbols
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.resolve.DelegatingBindingTrace

class InjektIrGenerationExtension(private val project: Project) : IrGenerationExtension {

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        fun ModuleLoweringPass.visitModuleAndGenerateSymbols() {
            generateSymbols(pluginContext)
            lower(moduleFragment)
            generateSymbols(pluginContext)
        }

        val declarationStore = InjektDeclarationStore(
            pluginContext,
            moduleFragment,
            InjektSymbols(pluginContext)
        )

        val bindingTrace = DelegatingBindingTrace(
            pluginContext.bindingContext, "trace in InjektIrGenerationExtension"
        )

        // write qualifiers of expression to the irTrace
        QualifiedMetadataTransformer(pluginContext, bindingTrace).visitModuleAndGenerateSymbols()

        // generate a provider for each annotated class
        ClassProviderTransformer(pluginContext, bindingTrace).visitModuleAndGenerateSymbols()

        // move the module block of @Factory createImplementation { ... } to a function
        FactoryBlockTransformer(pluginContext, bindingTrace).visitModuleAndGenerateSymbols()

        val moduleTransformer = ModuleTransformer(pluginContext, bindingTrace, declarationStore)
            .also { declarationStore.moduleTransformer = it }
        val factoryTransformer = FactoryTransformer(pluginContext, bindingTrace, declarationStore)
            .also { declarationStore.factoryTransformer = it }

        // transform @Module functions to their ast representation
        moduleTransformer.visitModuleAndGenerateSymbols()

        // create implementations for factories
        factoryTransformer.visitModuleAndGenerateSymbols()
    }

}
