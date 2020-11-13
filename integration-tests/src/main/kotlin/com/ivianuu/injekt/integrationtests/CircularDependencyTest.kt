package com.ivianuu.injekt.integrationtests

import com.ivianuu.injekt.test.assertInternalError
import com.ivianuu.injekt.test.codegen
import org.junit.Test

class CircularDependencyTest {

    @Test
    fun testCircularDependencyFails() = codegen(
        """
            @Binding class A(b: B)
            @Binding class B(a: A)

            @Component
            abstract class MyComponent {
                abstract val b: B
            }
        """
    ) {
        assertInternalError("circular")
    }

    @Test
    fun testProviderBreaksCircularDependency() = codegen(
        """
            @Binding class A(b: B)
            @Binding(MyComponent::class) class B(a: () -> A)
            
            @Component
            abstract class MyComponent {
                abstract val b: B
            }
        """
    )

    @Test
    fun testIrrelevantProviderInChainDoesNotBreakCircularDependecy() = codegen(
        """
            @Binding class A(b: () -> B)
            @Binding class B(b: C)
            @Binding class C(b: B)
            
            @Component
            abstract class MyComponent {
                abstract val c: C
            }
        """
    ) {
        assertInternalError("circular")
    }

    @Test
    fun testAssistedBreaksCircularDependency() = codegen(
        """
            @Binding class A(b: B)
            @Binding(MyComponent::class) class B(a: (B) -> A)
            
            @Component
            abstract class MyComponent {
                abstract val b: B
            }
        """
    )

    @Test
    fun testFunBindingBreaksCircularDependency() = codegen(
        """
            @FunBinding
            fun A(b: B) {
            }
            
            @FunBinding
            fun B(a: A) {
            }
            
            @Component
            abstract class MyComponent {
                abstract val b: B
            }
        """
    )

}
