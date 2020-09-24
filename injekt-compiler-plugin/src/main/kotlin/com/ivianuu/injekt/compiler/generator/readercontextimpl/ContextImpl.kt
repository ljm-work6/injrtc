package com.ivianuu.injekt.compiler.generator.readercontextimpl

import com.ivianuu.injekt.Reader
import com.ivianuu.injekt.compiler.UniqueNameProvider
import com.ivianuu.injekt.compiler.generator.CodeBuilder
import com.ivianuu.injekt.compiler.generator.DeclarationStore
import com.ivianuu.injekt.compiler.generator.TypeRef
import com.ivianuu.injekt.compiler.generator.render
import com.ivianuu.injekt.compiler.generator.substitute
import com.ivianuu.injekt.compiler.generator.typeWith
import com.ivianuu.injekt.compiler.irtransform.asNameId
import com.ivianuu.injekt.given
import org.jetbrains.kotlin.name.Name

@Reader
class ContextFactoryImpl(
    val name: Name,
    val factoryType: TypeRef,
    val inputTypes: List<TypeRef>,
    val contextType: TypeRef,
    val parent: ContextImpl?
) : ContextMember {

    val contextTreeNameProvider: UniqueNameProvider =
        parent?.factoryImpl?.contextTreeNameProvider ?: UniqueNameProvider()

    val context = ContextImpl(
        this,
        contextType,
        contextTreeNameProvider("C").asNameId(),
        inputTypes
    )

    fun initialize() {
        parent?.members?.add(this)
        parent?.children?.add(this)
        context.initialize()
    }

    override fun CodeBuilder.emit() {
        if (parent == null) {
            emit("object ")
        } else {
            emit("private inner class ")
        }
        emit(name)
        emit(" : ${factoryType.render()} ")
        braced {
            emit("override fun create(")
            inputTypes.forEachIndexed { index, inputType ->
                emit("p$index: ${inputType.render()}")
                if (index != inputTypes.lastIndex) emit(", ")
            }
            emit("): ${contextType.render()} ")
            braced {
                emit("return ${context.name}")
                //if (inputTypes.isNotEmpty()) {
                    emit("(")
                    inputTypes.forEachIndexed { index, _ ->
                        emit("p$index")
                        if (index != inputTypes.lastIndex) emit(", ")
                    }
                    emit(")")
                //}
                emitLine()
            }
            with(context) { emit() }
        }
    }
}

@Reader
class ContextImpl(
    val factoryImpl: ContextFactoryImpl,
    val contextId: TypeRef,
    val name: Name,
    val inputTypes: List<TypeRef>
) {

    val statements = GivenStatements(this)
    val graph = GivensGraph(this)

    val children = mutableListOf<ContextFactoryImpl>()

    val members = mutableListOf<ContextMember>()

    val superTypes = mutableListOf<TypeRef>()

    fun initialize() {
        val declarationStore = given<DeclarationStore>()
        val entryPoints = declarationStore.getRunReaderContexts(contextId.classifier.fqName)
            .map { declarationStore.getReaderContextByFqName(it)!! }
            .map { entryPoint ->
                // this is really naive and probably error prone
                entryPoint.copy(
                    type =
                    if (factoryImpl.factoryType.classifier.typeParameters.size ==
                        entryPoint.type.classifier.typeParameters.size &&
                        factoryImpl.factoryType.classifier.typeParameters.zip(
                            entryPoint.type.classifier.typeParameters
                        ).all { it.first.fqName.shortName() == it.second.fqName.shortName() }
                    ) {
                        entryPoint.type.typeWith(factoryImpl.factoryType.typeArguments.map { it })
                    } else entryPoint.type
                )
            }

        graph.checkEntryPoints(entryPoints)

        (entryPoints + graph.resolvedGivens.flatMap { it.value.contexts })
            .filterNot { it.type in superTypes }
            .forEach { context ->
                superTypes += context.type
                val substitutionMap = context.type.classifier.typeParameters
                    .zip(context.type.typeArguments)
                    .toMap()
                context.givenTypes
                    .map { it to it.substitute(substitutionMap) }
                    .forEach { (originalType, substitutedType) ->
                        statements.getGivenStatement(
                            graph.getGiven(substitutedType),
                            originalType
                        )
                    }
            }
    }

    fun CodeBuilder.emit() {
        emitLine("@com.ivianuu.injekt.internal.ContextImplMarker")
        emit("private ")
        if (factoryImpl.parent != null) emit("inner ")
        emit("class $name")
        if (inputTypes.isNotEmpty()) {
            emit("(")
            inputTypes.forEachIndexed { index, inputType ->
                emit("private val p$index: ${inputType.render()}")
                if (index != inputTypes.lastIndex) emit(", ")
            }
            emit(")")
        }

        emit(" : ${contextId.render()}")
        if (superTypes.isNotEmpty()) {
            emit(", ")
            superTypes.forEachIndexed { index, superType ->
                emit(superType.render())
                if (index != superTypes.lastIndex) emit(", ")
            }
        }
        emitSpace()
        braced {
            val renderedMembers = mutableSetOf<ContextMember>()
            var currentMembers: List<ContextMember> = members.toList()
            while (currentMembers.isNotEmpty()) {
                renderedMembers += currentMembers
                currentMembers.forEach {
                    with(it) { emit() }
                    emitLine()
                }
                currentMembers = members.filterNot { it in renderedMembers }
            }
        }
    }
}
