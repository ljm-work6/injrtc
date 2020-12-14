package com.ivianuu.injekt.samples.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.ivianuu.injekt.Given
import com.ivianuu.injekt.component.ApplicationScoped
import com.ivianuu.injekt.component.Storage
import com.ivianuu.injekt.component.memo
import com.ivianuu.injekt.given
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlin.reflect.KClass

typealias KeyUiBinding = Pair<KClass<*>, @Composable () -> Unit>

inline fun <reified K : Any> keyUiBinding(noinline content: @Composable () -> Unit): KeyUiBinding =
    K::class to content

inline fun <reified K : Any, S> keyUiWithStateBinding(
    noinline stateFactory: (CoroutineScope) -> StateFlow<S> = given,
    noinline content: @Composable (@Given S) -> Unit,
) = keyUiBinding<K> {
    val coroutineScope = rememberCoroutineScope()
    val state = remember { stateFactory(coroutineScope) }.collectAsState().value
    content(state)
}

typealias ActionChannel<A> = Channel<A>

@Given fun <A> ActionChannel(storage: Storage<ApplicationScoped> = given): ActionChannel<A> =
    storage.memo("action_channel") { Channel() }

typealias Dispatch<A> = (A) -> Unit

@Given val <A> @Given ActionChannel<A>.dispatch: Dispatch<A>
    get() = { action: A -> offer(action) }

typealias Actions<A> = Flow<A>

@Given inline val <A> @Given ActionChannel<A>.actions: Actions<A>
    get() = consumeAsFlow()
