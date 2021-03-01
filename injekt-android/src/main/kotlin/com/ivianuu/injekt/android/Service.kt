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

@file:Suppress("NOTHING_TO_INLINE")

package com.ivianuu.injekt.android

import android.app.Service
import android.content.Context
import android.content.res.Resources
import com.ivianuu.injekt.Given
import com.ivianuu.injekt.component.AppComponent
import com.ivianuu.injekt.component.Component
import com.ivianuu.injekt.component.ComponentElementBinding
import com.ivianuu.injekt.component.element
import com.ivianuu.injekt.component.get

typealias ServiceComponent = Component

@ComponentElementBinding<AppComponent>
@Given
fun serviceComponentFactory(
    @Given parent: AppComponent,
    @Given builderFactory: () -> Component.Builder<ServiceComponent>,
): (Service) -> ServiceComponent = { service ->
    builderFactory()
        .dependency(parent)
        .element { service }
        .build()
}

fun Service.createServiceComponent(): ServiceComponent =
    application.appComponent.get<(Service) -> ServiceComponent>()(this)

@Given
val @Given ServiceComponent.appComponentFromService: AppComponent
    get() = get()

@Given
inline val @Given ServiceComponent.service: Service
    get() = get()

typealias ServiceContext = Context

@Given
inline val @Given Service.serviceContext: ServiceContext
    get() = this

typealias ServiceResources = Resources

@Given
inline val @Given Service.serviceResources: ServiceResources
    get() = resources
