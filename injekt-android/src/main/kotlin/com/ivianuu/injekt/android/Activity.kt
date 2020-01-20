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

package com.ivianuu.injekt.android

import android.app.Activity
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import com.ivianuu.injekt.Component
import com.ivianuu.injekt.ComponentBuilder
import com.ivianuu.injekt.InjektTrait
import com.ivianuu.injekt.Module
import com.ivianuu.injekt.Name
import com.ivianuu.injekt.Scope
import com.ivianuu.injekt.typeOf

@Scope
annotation class ActivityScope {
    companion object
}

@Name
annotation class ForActivity {
    companion object
}

fun <T : Activity> T.ActivityComponent(
    block: (ComponentBuilder.() -> Unit)? = null
): Component = Component {
    scopes(ActivityScope)
    getClosestComponentOrNull()?.let { dependencies(it) }
    modules(ActivityModule())
    block?.invoke(this)
}

fun Activity.getClosestComponentOrNull(): Component? =
    getApplicationComponentOrNull()

fun Activity.getClosestComponent(): Component =
    getClosestComponentOrNull() ?: error("No close Component found for $this")

fun Activity.getApplicationComponentOrNull(): Component? = (application as? InjektTrait)?.component

fun Activity.getApplicationComponent(): Component =
    getApplicationComponentOrNull() ?: error("No application Component found for $this")

fun <T : Activity> T.ActivityModule(): Module = Module {
    instance(instance = this@ActivityModule, type = typeOf(this@ActivityModule)).apply {
        bindType<Activity>()
        bindAlias<Context>(name = ForActivity, override = true)
        bindType<Context>(override = true)

        if (this@ActivityModule is ComponentActivity) bindType<ComponentActivity>()
        if (this@ActivityModule is FragmentActivity) bindType<FragmentActivity>()
        if (this@ActivityModule is AppCompatActivity) bindType<AppCompatActivity>()
        if (this@ActivityModule is LifecycleOwner) {
            bindType<LifecycleOwner>()
            bindAlias<LifecycleOwner>(name = ForActivity)
        }
        if (this@ActivityModule is ViewModelStoreOwner) {
            bindType<ViewModelStoreOwner>()
            bindAlias<ViewModelStoreOwner>(name = ForActivity)
        }
        if (this@ActivityModule is SavedStateRegistryOwner) {
            bindType<SavedStateRegistryOwner>()
            bindAlias<SavedStateRegistryOwner>(name = ForActivity)
        }
    }

    factory(override = true) { resources!! }
        .bindName(ForActivity)

    (this@ActivityModule as? LifecycleOwner)?.let {
        factory(override = true) { lifecycle }
            .bindName(ForActivity)
    }

    (this@ActivityModule as? ViewModelStoreOwner)?.let {
        factory(override = true) { viewModelStore }
            .bindName(ForActivity)
    }

    (this@ActivityModule as? SavedStateRegistryOwner)?.let {
        factory(override = true) { savedStateRegistry }
            .bindName(ForActivity)
    }

    (this@ActivityModule as? FragmentActivity)?.let {
        factory(override = true) { supportFragmentManager }
            .bindName(ForActivity)
    }

    withBinding<Component>(name = ActivityScope) {
        bindName(name = ForActivity)
    }
}