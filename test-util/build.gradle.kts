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

plugins {
    kotlin("jvm")
    kotlin("kapt")
}

apply(from = "https://raw.githubusercontent.com/IVIanuu/gradle-scripts/master/java-8.gradle")
apply(from = "https://raw.githubusercontent.com/IVIanuu/gradle-scripts/master/kt-compiler-args.gradle")

dependencies {
    api(Deps.AndroidX.Compose.compiler)

    api(project(":injekt-compiler-plugin"))
    api(project(":injekt-core"))

    api(Deps.Coroutines.core)
    api(Deps.Coroutines.test)

    api(Deps.Kotlin.compilerEmbeddable)
    api(Deps.kotlinCompileTesting)

    api(Deps.kotestAssertions)

    api(Deps.junit)
    api(Deps.AndroidX.Test.core)
    api(Deps.AndroidX.Test.junit)
    api(Deps.roboelectric)

    // todo remove compile testing deps
    api("com.squareup.okio:okio:2.10.0")
}
