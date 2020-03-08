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

import junit.framework.Assert.assertTrue
import org.junit.Test

class BindingContextTest {

    @Test
    fun testBindAliasBoth() {
        val component = Component {
            single { TestDep1() }
                .bindAlias<Any>(qualifier = TestQualifier1)
        }

        val declared = component.get<TestDep1>()
        val aliased = component.get<Any>(qualifier = TestQualifier1)
        assertTrue(declared === aliased)
    }

    @Test
    fun testBindAliasTypeOnly() {
        val component = Component {
            single { TestDep1() }
                .bindAlias<Any>()
        }

        val declared = component.get<TestDep1>()
        val aliased = component.get<Any>()
        assertTrue(declared === aliased)
    }

    @Test
    fun testBindAliasNameOnly() {
        val component = Component {
            single { TestDep1() }
                .bindAlias(qualifier = TestQualifier1)
        }

        val declared = component.get<TestDep1>()
        val aliased = component.get<TestDep1>(qualifier = TestQualifier1)
        assertTrue(declared === aliased)
    }
}
