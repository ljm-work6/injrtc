package com.ivianuu.injekt.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.ivianuu.injekt.Given
import com.ivianuu.injekt.merge.Effect
import kotlin.reflect.KClass

@Effect(ActivityComponent::class)
annotation class GivenActivityViewModel {
    companion object {
        @Given
        inline fun <reified VM : S, reified S : ViewModel> viewModel(
            viewModelStoreOwner: FragmentViewModelStoreOwner,
            noinline viewModelFactory: () -> VM,
        ): S = getViewModel(viewModelStoreOwner, VM::class, viewModelFactory)
    }
}

@Effect(FragmentComponent::class)
annotation class GivenFragmentViewModel {
    companion object {
        @Given
        inline fun <reified VM : S, reified S : ViewModel> viewModel(
            viewModelStoreOwner: ActivityViewModelStoreOwner,
            noinline viewModelFactory: () -> VM,
        ): S = getViewModel(viewModelStoreOwner, VM::class, viewModelFactory)
    }
}

@PublishedApi
internal fun <VM : S, S : ViewModel> getViewModel(
    viewModelStoreOwner: ViewModelStoreOwner,
    viewModelClass: KClass<S>,
    viewModelFactory: () -> S,
): S {
    return ViewModelProvider(
        viewModelStoreOwner,
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel?> create(modelClass: Class<T>): T {
                return viewModelFactory() as T
            }
        }
    )[viewModelClass.java]
}
