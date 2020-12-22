package com.ivianuu.injekt.samples.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.ivianuu.injekt.Given
import com.ivianuu.injekt.GivenSetElement
import com.ivianuu.injekt.Qualifier
import com.ivianuu.injekt.Unqualified
import com.ivianuu.injekt.component.ApplicationScoped
import com.ivianuu.injekt.component.Storage
import com.ivianuu.injekt.component.memo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlin.reflect.KClass

typealias KeyUiBinding = Pair<KClass<*>, @Composable () -> Unit>

inline fun <reified K : Any, reified T : @Composable () -> Unit> keyUiBinding():
        @GivenSetElement (@Given T) -> KeyUiBinding = { K::class to it }

typealias ActionChannel<A> = Channel<A>

@Given fun <A> ActionChannel(@Given storage: Storage<ApplicationScoped>): ActionChannel<A> =
    storage.memo("action_channel") { Channel() }

typealias Dispatch<A> = (A) -> Unit

@Given val <A> @Given ActionChannel<A>.dispatch: Dispatch<A>
    get() = { action: A -> offer(action) }

typealias Actions<A> = Flow<A>

@Given inline val <A> @Given ActionChannel<A>.actions: Actions<A>
    get() = consumeAsFlow()

@Target(AnnotationTarget.TYPE)
@Qualifier
annotation class UiState

@Given @Composable
fun <T> uiState(@Given stateFactory: (CoroutineScope) -> StateFlow<@Unqualified T>): @UiState T {
    val scope = rememberCoroutineScope()
    return remember { stateFactory(scope) }.collectAsState().value
}