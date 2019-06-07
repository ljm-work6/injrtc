/*
 * Copyright 2018 Manuel Wrage
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

import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import com.ivianuu.injekt.Component
import com.ivianuu.injekt.ComponentBuilder
import com.ivianuu.injekt.InjektTrait
import com.ivianuu.injekt.Module
import com.ivianuu.injekt.Name
import com.ivianuu.injekt.Scope
import com.ivianuu.injekt.bindAlias
import com.ivianuu.injekt.bindName
import com.ivianuu.injekt.bindType
import com.ivianuu.injekt.component

import com.ivianuu.injekt.instance
import com.ivianuu.injekt.module
import com.ivianuu.injekt.provide
import com.ivianuu.injekt.scopes

@Scope
annotation class FragmentScope

@Scope
annotation class ChildFragmentScope

@Name(ForFragment.Companion::class)
annotation class ForFragment {
    companion object
}

@Name(ForChildFragment.Companion::class)
annotation class ForChildFragment {
    companion object
}

fun <T : Fragment> T.fragmentComponent(block: (ComponentBuilder.() -> Unit)? = null): Component =
    component {
        scopes<FragmentScope>()
        getClosestComponentOrNull()?.let { dependencies(it) }
        modules(fragmentModule())
        block?.invoke(this)
    }

fun <T : Fragment> T.childFragmentComponent(block: (ComponentBuilder.() -> Unit)? = null): Component =
    component {
        scopes<ChildViewScope>()
        getClosestComponentOrNull()?.let { dependencies(it) }
        modules(childFragmentModule())
        block?.invoke(this)
    }

fun Fragment.getClosestComponentOrNull(): Component? {
    return getParentFragmentComponentOrNull()
        ?: getActivityComponentOrNull()
        ?: getApplicationComponentOrNull()
}

fun Fragment.getClosestComponent(): Component =
    getClosestComponentOrNull() ?: error("No close component found for $this")

fun Fragment.getParentFragmentComponentOrNull(): Component? =
    (parentFragment as? InjektTrait)?.component

fun Fragment.getParentFragmentComponent(): Component =
    getParentFragmentComponentOrNull() ?: error("No parent fragment component found for $this")

fun Fragment.getActivityComponentOrNull(): Component? =
    (activity as? InjektTrait)?.component

fun Fragment.getActivityComponent(): Component =
    getActivityComponentOrNull() ?: error("No activity component found for $this")

fun Fragment.getApplicationComponentOrNull(): Component? =
    (activity?.application as? InjektTrait)?.component

fun Fragment.getApplicationComponent(): Component =
    getApplicationComponentOrNull() ?: error("No application component found for $this")

fun <T : Fragment> T.fragmentModule(): Module = module {
    include(internalFragmentModule(ForFragment))
}

fun <T : Fragment> T.childFragmentModule(): Module = module {
    include(internalFragmentModule(ForChildFragment))
}

private fun <T : Fragment> T.internalFragmentModule(name: Any) = module {
    instance(this@internalFragmentModule, override = true).apply {
        bindType<Fragment>()
        bindAlias<Fragment>(name)
        bindType<LifecycleOwner>()
        bindAlias<LifecycleOwner>(name)
        bindType<ViewModelStoreOwner>()
        bindAlias<ViewModelStoreOwner>(name)
        bindType<SavedStateRegistryOwner>()
        bindAlias<SavedStateRegistryOwner>(name)
    }

    provide(override = true) { requireContext() } bindName name
    provide(override = true) { resources } bindName name
    provide(override = true) { lifecycle } bindName name
    provide(override = true) { viewModelStore } bindName name
    provide(override = true) { savedStateRegistry } bindName name
    provide(override = true) { childFragmentManager } bindName name
}