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

import android.app.Activity
import android.content.Context
import com.ivianuu.injekt.*
import com.ivianuu.injekt.constant.constant
import com.ivianuu.injekt.eager.createEagerInstances

/**
 * Activity name
 */
object ForActivity

/**
 * Returns a [Component] with convenient configurations
 */
fun <T : Activity> T.activityComponent(
    block: (Component.() -> Unit)? = null
): Component = component {
    getClosestComponentOrNull()?.let { dependencies(it) }
    modules(activityModule())
    block?.invoke(this)
    createEagerInstances()
}

/**
 * Returns the closest [Component] or null
 */
fun Activity.getClosestComponentOrNull(): Component? =
    getApplicationComponentOrNull()

/**
 * Returns the closest [Component]
 */
fun Activity.getClosestComponent(): Component =
    getClosestComponentOrNull() ?: error("No close component found for $this")

/**
 * Returns the application [Component] or null
 */
fun Activity.getApplicationComponentOrNull(): Component? = (application as? InjektTrait)?.component

/**
 * Returns the parent [Component] or throws
 */
fun Activity.getApplicationComponent(): Component =
    getApplicationComponentOrNull() ?: error("No application component found for $this")

/**
 * Returns a [Module] with convenient bindings
 */
fun <T : Activity> T.activityModule(): Module = module {
    constant(this@activityModule) apply {
        bindType<Activity>()
        bindAlias<Context>(ForActivity)
        bindType<Context>()
    }

    factory { resources } bindName ForActivity
}