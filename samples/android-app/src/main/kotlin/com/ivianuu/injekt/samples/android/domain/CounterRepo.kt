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

package com.ivianuu.injekt.samples.android.domain

import com.ivianuu.injekt.Given
import com.ivianuu.injekt.scope.AppGivenScope
import com.ivianuu.injekt.samples.android.data.CounterStorage
import com.ivianuu.injekt.scope.Scoped
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

@Scoped<AppGivenScope>
@Given
class CounterRepo(@Given private val storage: CounterStorage) {
    val counterState: Flow<Int>
        get() = storage.counterState

    suspend fun inc() {
        storage.updateCounter(counterState.first() + 1)
    }

    suspend fun dec() {
        storage.updateCounter(counterState.first() - 1)
    }
}
