/*
 * Copyright 2019 Manuel Wrage
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

import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import org.junit.Test

class ReflectionTest {

    @Test
    fun testCreatesViaReflection() {
        val component = Component()
        component.get<ReflectionDep>()
    }

    @Test
    fun testUsesParams() {
        val component = Component()
        component.get<ReflectionDepWithParam> { parametersOf(1) }
    }

    @Test
    fun testUsesNamedParams() {
        val component = Component {
            modules(
                Module {
                    factory(PackageName) { "com.ivianuu.injekt" }
                }
            )
        }

        component.get<ReflectionDepWithNamedParam>()
    }

    @Test
    fun testUsesScope() {
        val testScopeComponent = Component {
            scopes(TestScopeOne)
        }

        val component = Component {
            scopes(OtherScope)
            dependencies(testScopeComponent)
        }

        component.get<ReflectionDepWithScope>()

        assertTrue(keyOf<ReflectionDepWithScope>() in testScopeComponent.allBindings)
        assertFalse(keyOf<ReflectionDepWithScope>() in component.allBindings)
    }

    @Test
    fun testUsesAnnotatedConstructor() {
        val component = Component()
        assertTrue(component.get<ReflectionDepWithAnnotatedConstructor>().arg is TestDep2)
    }

    @Test
    fun testResolvesParameterizedDeps() {
        val component = Component {
            modules(
                Module {
                    map<String, Memoized<String>>()
                }
            )
        }

        component.get<ReflectionDepWithParameterizedDep>()
    }
}

@Name
annotation class PackageName {
    companion object
}

@Factory
class ReflectionDep

@Factory
class ReflectionDepWithParam(@Param val value: Int)

@Factory
class ReflectionDepWithNamedParam(@PackageName val packageName: String)

@Factory
class ReflectionDepWithAnnotatedConstructor {

    val arg: Any?

    constructor(testDep1: TestDep1) {
        arg = testDep1
    }

    @InjektConstructor
    constructor(testDep2: TestDep2) {
        arg = testDep2
    }
}

interface Memoized<T>

@Factory
class ReflectionDepWithParameterizedDep(
    private val mapOfStrings: Map<String, Memoized<String>>
)

@TestScopeOne
@Factory
class ReflectionDepWithScope

@Scope
annotation class OtherScope {
    companion object
}