package com.ivianuu.injekt.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.descriptors.PackageViewDescriptor
import org.jetbrains.kotlin.descriptors.findTypeAliasAcrossModuleDependencies
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeAliasSymbol
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class InjektSymbols(val pluginContext: IrPluginContext) {

    val injektPackage = getPackage(InjektFqNames.InjektPackage)
    val internalPackage = getPackage(InjektFqNames.InternalPackage)

    val injektAst = pluginContext.referenceClass(InjektFqNames.InjektAst)!!

    private fun IrClassSymbol.childClass(name: Name) = owner.declarations
        .filterIsInstance<IrClass>()
        .single { it.name == name }
        .symbol

    val astAlias = injektAst.childClass(InjektFqNames.AstAlias.shortName())
    val astAssisted = pluginContext.referenceClass(InjektFqNames.AstAssisted)!!
    val astBinding = injektAst.childClass(InjektFqNames.AstBinding.shortName())
    val astChildFactory = injektAst.childClass(InjektFqNames.AstChildFactory.shortName())
    val astDependency = injektAst.childClass(InjektFqNames.AstDependency.shortName())
    val astInline = injektAst.childClass(InjektFqNames.AstInline.shortName())
    val astImplFactory = injektAst.childClass(InjektFqNames.AstImplFactory.shortName())
    val astInstanceFactory = injektAst.childClass(InjektFqNames.AstInstanceFactory.shortName())
    val astMap = injektAst.childClass(InjektFqNames.AstMap.shortName())
    val astMapEntry = astMap.childClass(InjektFqNames.AstMapEntry.shortName())
    val astMapClassKey = astMap.childClass(InjektFqNames.AstMapClassKey.shortName())
    val astMapTypeParameterClassKey =
        astMap.childClass(InjektFqNames.AstMapTypeParameterClassKey.shortName())
    val astMapIntKey = astMap.childClass(InjektFqNames.AstMapIntKey.shortName())
    val astMapLongKey = astMap.childClass(InjektFqNames.AstMapLongKey.shortName())
    val astMapStringKey = astMap.childClass(InjektFqNames.AstMapStringKey.shortName())
    val astModule = injektAst.childClass(InjektFqNames.AstModule.shortName())
    val astPath = injektAst.childClass(InjektFqNames.AstPath.shortName())
    val astClassPath = astPath.childClass(InjektFqNames.AstClassPath.shortName())
    val astPropertyPath = astPath.childClass(InjektFqNames.AstPropertyPath.shortName())
    val astTypeParameterPath = astPath.childClass(InjektFqNames.AstTypeParameterPath.shortName())
    val astValueParameterPath = astPath.childClass(InjektFqNames.AstValueParameterPath.shortName())
    val astScope = injektAst.childClass(InjektFqNames.AstScope.shortName())
    val astScoped = injektAst.childClass(InjektFqNames.AstScoped.shortName())
    val astSet = injektAst.childClass(InjektFqNames.AstSet.shortName())
    val astSetElement = astSet.childClass(InjektFqNames.AstSetElement.shortName())
    val astTyped = injektAst.childClass(InjektFqNames.AstTyped.shortName())

    val assisted = pluginContext.referenceClass(InjektFqNames.Assisted)!!
    val assistedParameters = pluginContext.referenceClass(InjektFqNames.AssistedParameters)!!

    val childFactory = pluginContext.referenceClass(InjektFqNames.ChildFactory)!!
    val doubleCheck = pluginContext.referenceClass(InjektFqNames.DoubleCheck)!!
    val factory = pluginContext.referenceClass(InjektFqNames.Factory)!!

    val instanceProvider = pluginContext.referenceClass(InjektFqNames.InstanceProvider)!!

    val lazy = pluginContext.referenceClass(InjektFqNames.Lazy)!!

    val mapDsl = pluginContext.referenceClass(InjektFqNames.MapDsl)!!
    val mapProvider = pluginContext.referenceClass(InjektFqNames.MapProvider)!!

    val module = pluginContext.referenceClass(InjektFqNames.Module)!!

    val provider = pluginContext.referenceClass(InjektFqNames.Provider)!!
    val providerDefinition = getTypeAlias(InjektFqNames.ProviderDefinition)
    val providerDsl = pluginContext.referenceClass(InjektFqNames.ProviderDsl)!!
    val providerOfLazy = pluginContext.referenceClass(InjektFqNames.ProviderOfLazy)!!

    val setDsl = pluginContext.referenceClass(InjektFqNames.SetDsl)!!
    val setProvider = pluginContext.referenceClass(InjektFqNames.SetProvider)!!

    val transient = pluginContext.referenceClass(InjektFqNames.Transient)!!

    fun getTypeAlias(fqName: FqName): IrTypeAliasSymbol =
        pluginContext.symbolTable.referenceTypeAlias(
            pluginContext.moduleDescriptor.findTypeAliasAcrossModuleDependencies(
                ClassId.topLevel(
                    fqName
                )
            )
                ?: error("No class found for $fqName")
        )

    fun getPackage(fqName: FqName): PackageViewDescriptor =
        pluginContext.moduleDescriptor.getPackage(fqName)
}