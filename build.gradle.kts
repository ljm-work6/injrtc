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

import com.ivianuu.injekt.gradle.InjektExtension
import com.ivianuu.injekt.gradle.setupForInjekt

buildscript {
    repositories {
        mavenLocal()
        maven("https://dl.bintray.com/ivianuu/maven")
        maven("https://dl.bintray.com/kotlin/kotlin-eap")
        google()
        jcenter()
        maven("https://oss.sonatype.org/content/repositories/snapshots")
        maven("https://plugins.gradle.org/m2")
    }
    dependencies {
        classpath(Deps.androidGradlePlugin)
        classpath(Deps.bintrayGradlePlugin)
        classpath(Deps.buildConfigGradlePlugin)
        classpath(Deps.Injekt.gradlePlugin)
        classpath(Deps.Kotlin.gradlePlugin)
        classpath(Deps.mavenGradlePlugin)
        classpath(Deps.shadowGradlePlugin)
        classpath(Deps.spotlessGradlePlugin)
    }
}

allprojects {
    repositories {
        mavenLocal()
        maven("https://dl.bintray.com/ivianuu/maven")
        maven("https://dl.bintray.com/kotlin/kotlin-eap")
        google()
        jcenter()
        maven("https://oss.sonatype.org/content/repositories/snapshots")
        maven("https://plugins.gradle.org/m2")
    }

    if (project.name != "injekt-compiler-plugin") {
        extensions.add<InjektExtension>("injekt", InjektExtension())
    }
    afterEvaluate {
        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
            kotlinOptions {
                useIR = true
                if (project.name != "injekt-compiler-plugin" &&
                        project.name != "injekt-gradle-plugin") {
                    val options = setupForInjekt()
                    options.forEach {
                        freeCompilerArgs += listOf(
                            "-P", "plugin:com.ivianuu.injekt:${it.key}=${it.value}"
                        )
                    }
                }
                if (configurations.findByName("kotlinCompilerPluginClasspath")
                                ?.dependencies
                                ?.any { it.group == "androidx.compose.compiler" } == true) {
                    freeCompilerArgs += listOf(
                            "-P", "plugin:androidx.compose.compiler.plugins.kotlin:suppressKotlinVersionCompatibilityCheck=true"
                    )
                }
            }
        }
    }
}