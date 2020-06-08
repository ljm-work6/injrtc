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

package com.ivianuu.injekt.lol

import com.ivianuu.injekt.InstanceFactory
import com.ivianuu.injekt.Scope
import com.ivianuu.injekt.create
import com.ivianuu.injekt.scope
import com.ivianuu.injekt.scoped
import com.ivianuu.injekt.transient

@Scope
annotation class TestScope

class Foo
class Bar(foo: Foo)

@TestScope
class MyClass(foo: Foo, bar: Bar)

@InstanceFactory
fun createBar(): MyClass {
    scope<TestScope>()
    scoped<Foo>()
    transient<Bar>()
    return create()
}

fun invoke() = createBar()