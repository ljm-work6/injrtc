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

package com.ivianuu.injekt

import junit.framework.Assert.assertEquals
import org.junit.Test

class SingleBehaviorTest {

    @Test
    fun testSingleBehavior() {
        val componentA = Component {
            bind(
                Binding(
                    key = keyOf(),
                    behavior = SingleBehavior
                ) { TestDep1() }
            )
        }

        val componentB = Component { parents(componentA) }
        val componentC = Component { parents(componentB) }

        val depA = componentA.get<TestDep1>()
        val depA2 = componentA.get<TestDep1>()
        val depB = componentB.get<TestDep1>()
        val depC = componentC.get<TestDep1>()

        assertEquals(depA, depA2)
        assertEquals(depA, depB)
        assertEquals(depA, depC)
    }

    @Test
    fun testReusesSingleJitBindings() {
        val componentA = Component { scopes(TestScopeOne) }

        val componentB = Component {
            scopes(TestScopeTwo)
            parents(componentA)
        }
        val componentC = Component {
            scopes(TestScopeThree)
            parents(componentB)
        }

        val depA = componentA.get<SingleJitDep>()
        val depA2 = componentA.get<SingleJitDep>()
        val depB = componentB.get<SingleJitDep>()
        val depC = componentC.get<SingleJitDep>()

        assertEquals(depA, depA2)
        assertEquals(depA, depB)
        assertEquals(depA, depC)
    }
}
