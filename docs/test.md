# Injekt: Next gen DI framework powered by a Kotlin compiler plugin

Injekt is a compile-time checked DI framework for Kotlin developers.
Injekt is highly experimental and the api is unstable.

Minimal example:
```kotlin
// declare givens
@Given val foo = Foo()
@Given fun bar(@Given foo: Foo) = Bar(foo)

fun main() {
    // retrieve instance
    val bar = given<Bar>()
    println("Got $bar")
}
```
