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

package com.ivianuu.injekt.comparison.injekt

import com.ivianuu.injekt.ApplicationComponent
import com.ivianuu.injekt.Module
import com.ivianuu.injekt.comparison.fibonacci.Fib1
import com.ivianuu.injekt.comparison.fibonacci.Fib2
import com.ivianuu.injekt.comparison.fibonacci.Fib3
import com.ivianuu.injekt.comparison.fibonacci.Fib4
import com.ivianuu.injekt.comparison.fibonacci.Fib5
import com.ivianuu.injekt.comparison.fibonacci.Fib6
import com.ivianuu.injekt.comparison.fibonacci.Fib7
import com.ivianuu.injekt.comparison.fibonacci.Fib8
import com.ivianuu.injekt.composition.CompositionFactory
import com.ivianuu.injekt.create
import com.ivianuu.injekt.get
import com.ivianuu.injekt.transient

@CompositionFactory
fun createApplicationComponent(): ApplicationComponent = create()
