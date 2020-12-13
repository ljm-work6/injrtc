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

package com.ivianuu.injekt

@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.LOCAL_VARIABLE,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.TYPE
)
annotation class Given

val given: Nothing = error("Must be compiled with the injekt compiler")

inline fun <T> givenOrElse(defaultValue: () -> T): T = defaultValue()

@Suppress("NOTHING_TO_INLINE")
inline fun <T> given(value: T = given): T = value

inline fun <A, R> withGiven(a: A, block: (@Given A) -> R) = block(a)

class GivenTuple2<A, B>(val a: A, val b: B) {
    companion object {
        //@Given fun <A> @Given GivenTuple2<A, *>.a(): A = a
        //@Given fun <B> @Given GivenTuple2<*, B>.b(): B = b
    }
}

inline fun <A, B, R> withGiven(a: A, b: B, block: @Given GivenTuple2<A, B>.() -> R) =
    block(GivenTuple2(a, b))

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
annotation class GivenMap

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
annotation class GivenSet

@Target(AnnotationTarget.ANNOTATION_CLASS)
annotation class Qualifier
