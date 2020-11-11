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
import com.ivianuu.injekt.Binding
import com.ivianuu.injekt.Adapter
import com.ivianuu.injekt.FunBinding

@Adapter
annotation class ActivityViewModelBinding {
    companion object {
        @Binding
        fun <VM : ViewModel> viewModel(getViewModel: getViewModel<VM, ActivityViewModelStoreOwner>): VM =
            getViewModel()
    }
}

@Adapter
annotation class FragmentViewModelBinding {
    companion object {
        @Binding
        fun <VM : ViewModel> viewModel(getViewModel: getViewModel<VM, FragmentViewModelStoreOwner>): VM =
            getViewModel()
    }
}

@FunBinding
inline fun <reified VM : ViewModel, VMSO : ViewModelStoreOwner> getViewModel(
    viewModelStoreOwner: VMSO,
    noinline viewModelFactory: () -> VM
): VM {
    return ViewModelProvider(
        viewModelStoreOwner,
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel?> create(modelClass: Class<T>): T =
                viewModelFactory() as T
        }
    )[VM::class.java]
}
