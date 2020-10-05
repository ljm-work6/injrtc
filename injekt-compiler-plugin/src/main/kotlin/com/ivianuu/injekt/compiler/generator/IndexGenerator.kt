package com.ivianuu.injekt.compiler.generator

import com.ivianuu.injekt.Binding
import com.ivianuu.injekt.compiler.InjektFqNames
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

@Binding(GenerationComponent::class)
class IndexGenerator(
    private val bindingContext: BindingContext,
    private val declarationStore: DeclarationStore,
    private val fileManager: FileManager,
    private val supportsMerge: SupportsMerge
) : Generator {

    override fun generate(files: List<KtFile>) {
        if (!supportsMerge) return
        files.forEach { file ->
            val indices = mutableListOf<Index>()
            file.accept(
                object : KtTreeVisitorVoid() {
                    var inModuleLikeScope = false

                    override fun visitClassOrObject(classOrObject: KtClassOrObject) {
                        val descriptor = classOrObject.descriptor<DeclarationDescriptor>(bindingContext)!!
                        val prevShouldIndexBindings = inModuleLikeScope
                        inModuleLikeScope = descriptor.hasAnnotation(InjektFqNames.Module) ||
                                descriptor.hasAnnotation(InjektFqNames.Component) ||
                                descriptor.hasAnnotation(InjektFqNames.ChildComponent) ||
                                descriptor.hasAnnotation(InjektFqNames.MergeComponent) ||
                                descriptor.hasAnnotation(InjektFqNames.MergeChildComponent)
                        super.visitClassOrObject(classOrObject)
                        inModuleLikeScope = prevShouldIndexBindings

                    }
                    override fun visitNamedDeclaration(declaration: KtNamedDeclaration) {
                        super.visitNamedDeclaration(declaration)
                        val descriptor = declaration.descriptor<DeclarationDescriptor>(bindingContext)
                            ?: return

                        if (((descriptor !is FunctionDescriptor || !inModuleLikeScope) &&
                                    (descriptor.hasAnnotation(InjektFqNames.Binding) ||
                                            descriptor.hasAnnotation(InjektFqNames.MapEntries) ||
                                            descriptor.hasAnnotation(InjektFqNames.SetElements))) ||
                            descriptor.hasAnnotation(InjektFqNames.MergeComponent) ||
                            descriptor.hasAnnotation(InjektFqNames.MergeChildComponent) ||
                            descriptor.hasAnnotation(InjektFqNames.MergeInto)) {
                            val index = Index(
                                descriptor.fqNameSafe,
                                when (descriptor) {
                                    is ClassDescriptor -> "class"
                                    is FunctionDescriptor -> "function"
                                    is PropertyDescriptor -> "property"
                                    else -> error("Unexpected declaration ${declaration.text}")
                                }
                            )
                            indices += index
                            declarationStore.addInternalIndex(index)
                        }
                    }
                }
            )

            if (indices.isNotEmpty()) {
                val fileName = file.packageFqName.pathSegments().joinToString("_") + "_${file.name}"
                fileManager.generateFile(
                    packageFqName = InjektFqNames.IndexPackage,
                    fileName = fileName,
                    code = buildCodeString {
                        emitLine("package ${InjektFqNames.IndexPackage}")
                        indices
                            .forEach { index ->
                                val indexName = index.fqName
                                    .pathSegments().joinToString("_") + "__${index.type}"
                                emitLine("val $indexName = Unit")
                        }
                    }
                )
            }
        }
    }
}