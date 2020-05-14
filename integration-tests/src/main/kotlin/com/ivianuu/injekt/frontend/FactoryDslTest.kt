package com.ivianuu.injekt.frontend

import com.ivianuu.injekt.assertCompileError
import com.ivianuu.injekt.assertOk
import com.ivianuu.injekt.codegen
import org.junit.Test

class FactoryDslTest {

    @Test
    fun testCreateImplInAFactory() =
        codegen(
            """
        @Factory fun factory() = createImpl<TestComponent>()
    """
        ) {
            assertOk()
        }

    @Test
    fun testCreateImplInAChildFactory() =
        codegen(
            """
        @ChildFactory fun factory() = createImpl<TestComponent>()
    """
        ) {
            assertOk()
        }

    @Test
    fun testCreateImplCannotBeCalledOutsideOfAFactory() =
        codegen(
            """
        fun nonFactory() = createImpl<TestComponent>()
    """
        ) {
            assertCompileError("factory")
        }

    @Test
    fun testFactoryWithCreateExpression() = codegen(
        """
        @Factory
        fun exampleFactory(): TestComponent = createImpl()
    """
    ) {
        assertOk()
    }

    @Test
    fun testFactoryWithReturnCreateExpression() =
        codegen(
            """
        @Factory
        fun exampleFactory(): TestComponent {
            return createImpl()
        }
    """
        ) {
            assertOk()
        }

    @Test
    fun testFactoryWithMultipleStatements() =
        codegen(
            """
        @Factory
        fun exampleFactory(): TestComponent {
            println()
            return createImpl()
        }
    """
        ) {
            assertOk()
        }

    @Test
    fun testFactoryWithoutCreate() = codegen(
        """
        @Factory
        fun exampleFactory() {
        }
    """
    ) {
        assertCompileError("statement")
    }

    @Test
    fun testFactoryWithAbstractClassWithZeroArgsConstructor() =
        codegen(
            """
        abstract class Impl
        @Factory
        fun factory(): Impl = createImpl()
    """
        ) {
            assertOk()
        }

    @Test
    fun testFactoryWithAbstractClassWithConstructorParams() =
        codegen(
            """
        abstract class Impl(p0: String)
        @Factory
        fun factory(): Impl = createImpl()
    """
        ) {
            assertCompileError("empty")
        }

    @Test
    fun testFactoryWithInterface() = codegen(
        """
        interface Impl
        @Factory
        fun factory(): Impl = createImpl()
    """
    ) {
        assertOk()
    }

    @Test
    fun testFactoryWithNormalClass() = codegen(
        """
        class Impl
        @Factory
        fun factory(): Impl = createImpl()
    """
    ) {
        assertCompileError("abstract")
    }

    @Test
    fun testFactoryWithTypeParametersAndWithoutInline() =
        codegen(
            """
        interface Impl
        @Factory
        fun <T> factory(): Impl = createImpl()
    """
        ) {
            assertCompileError("inline")
        }

    @Test
    fun testMutablePropertiesNotAllowedInFactoryImpls() =
        codegen(
            """
        interface Impl {
            var state: String
        }
        @Factory
        fun <T> factory(): Impl = createImpl()
    """
        ) {
            assertCompileError("mutable")
        }

    @Test
    fun testValueParametersNotAllowedInProvisionFunctions() =
        codegen(
            """
        interface Impl {
            fun provisionFunction(p0: String): String
        }
        @Factory
        fun factory(): Impl = createImpl()
    """
        ) {
            assertCompileError("parameters")
        }

    @Test
    fun testSuspendValueNotAllowedInProvisionFunctions() =
        codegen(
            """
        interface Impl {
            suspend fun provisionFunction(): String
        }
        @Factory
        fun factory(): Impl = createImpl()
    """
        ) {
            assertCompileError("suspend")
        }

    @Test
    fun testCannotInvokeChildFactories() = codegen(
        """
            @ChildFactory
            fun factory(): TestComponent = createImpl()
            
            fun invoke() = factory()
    """
    ) {
        assertCompileError("cannot invoke")
    }

    @Test
    fun testCanReferenceChildFactories() = codegen(
        """
            @ChildFactory
            fun factory(): TestComponent = createImpl()
            
            fun invoke() = ::factory
    """
    ) {
        assertOk()
    }

    @Test
    fun testCreateInstanceInFactory() = codegen(
        """
            @Factory
            fun factory(): Foo {
                transient<Foo>()
                return createInstance()
            }
            """
    ) {
        assertOk()
    }

    @Test
    fun testCreateInstanceInChildFactory() = codegen(
        """
            @ChildFactory
            fun factory(): TestComponent = createInstance()
            """
    ) {
        assertCompileError("childfactory")
    }

    @Test
    fun testFactoryAndModule() = codegen(
        """
        @ChildFactory
        @Factory 
        @Module
        fun factory(): TestComponent = createImpl()
        """
    ) {
        assertCompileError("only")
    }

    @Test
    fun testFactoryCannotBeSuspend() = codegen(
        """
        @Factory 
        suspend fun factory(): TestComponent = createImpl()
        """
    ) {
        assertCompileError("suspend")
    }

    @Test
    fun testChildFactoryCannotBeSuspend() = codegen(
        """
        @ChildFactory 
        suspend fun factory(): TestComponent = createImpl()
        """
    ) {
        assertCompileError("suspend")
    }

    @Test
    fun testNonInlineFactoryWithModuleParameter() = codegen(
        """ 
        @Factory
        fun factory(block: @Module () -> Unit): TestComponent {
            block()
            return createImpl()
        }
    """
    ) {
        assertCompileError("inline")
    }

    @Test
    fun testCallingInlineFactoryWithTypeParametersNotAllowed() =
        codegen(
            """ 
        @Factory
        inline fun <T> factory(): TestComponent {
            return createImpl()
        }

        fun <T> callerWithTypeParameters() {
            factory<T>()
        }
    """
        ) {
            assertCompileError("type param")
        }

}