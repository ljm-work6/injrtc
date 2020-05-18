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

package com.ivianuu.injekt.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.ivianuu.injekt.BindingAdapter
import com.ivianuu.injekt.Module
import com.ivianuu.injekt.Provider
import com.ivianuu.injekt.Qualifier
import com.ivianuu.injekt.classOf
import com.ivianuu.injekt.transient

@BindingAdapter(ActivityComponent::class)
annotation class ActivityViewModel {
    companion object {
        @Module
        inline fun <T : ViewModel> bind() {
            activityViewModel<T>()
        }
    }
}

@Module
inline fun <T : ViewModel> activityViewModel() {
    baseViewModel<T, @ForActivity ViewModelStoreOwner>()
}

@Module
inline fun <T : ViewModel, S : ViewModelStoreOwner> baseViewModel() {
    transient<@UnscopedViewModel T>()
    val clazz = classOf<T>()
    transient {
        val viewModelStoreOwner = get<S>()
        val viewModelProvider = get<@Provider () -> @UnscopedViewModel T>()
        ViewModelProvider(
            viewModelStoreOwner,
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    viewModelProvider() as T
            }
        ).get(clazz.java)
    }
}

@Qualifier
private annotation class UnscopedViewModel
