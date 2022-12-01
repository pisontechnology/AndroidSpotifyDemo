package com.example.spotifyvulcancontrol.util

import androidx.compose.runtime.*
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.badoo.reaktive.disposable.Disposable
import com.badoo.reaktive.disposable.DisposableWrapper
import com.badoo.reaktive.observable.Observable
import com.badoo.reaktive.observable.observable
import com.badoo.reaktive.observable.subscribe
import com.badoo.reaktive.scheduler.Scheduler
import com.badoo.reaktive.scheduler.ioScheduler
import com.badoo.reaktive.scheduler.mainScheduler
import com.badoo.reaktive.utils.reaktiveUncaughtErrorHandler
import java.util.concurrent.Executor

fun <T> Observable<T>.toLiveData(
    onError: (error: Throwable) -> Unit = reaktiveUncaughtErrorHandler,
    onNextFactory: (data: MutableLiveData<T>) -> (value: T) -> Unit = { (it::setValue) }
): LiveData<T> = object : MutableLiveData<T>() {

    val observable = this@toLiveData
    var disposable: Disposable? = null

    override fun onActive() {
        super.onActive()
        disposable = observable.subscribe(
            onNext = onNextFactory(this),
            onError = onError
        )
    }

    override fun onInactive() {
        super.onInactive()
        disposable?.dispose()
    }

}

@Composable
private inline fun <T, S> S.asMutableState(
    initial: T,
    crossinline subscribe: S.((T) -> Unit) -> Disposable
): MutableState<T> {
    val state = remember { mutableStateOf(initial) }
    DisposableEffect(this) {
        val disposable = subscribe {
            state.value = it
        }
        onDispose { disposable.dispose() }
    }
    return state
}

// Copied from the rxJava/compose interop code, altered to work with Reaktive
@Composable
private inline fun <T, S> S.asState(
    initial: T,
    crossinline subscribe: S.((T) -> Unit) -> Disposable
): State<T> {
    return asMutableState(initial = initial, subscribe = subscribe)
}

@Composable
fun <R, T : R> Observable<T>.subscribeAsState(initial: R): State<R> =
    asState(initial) { subscribe(onNext = it) }

@Composable
fun <R, T : R> Observable<T>.subscribeAsMutableState(initial: R): MutableState<R> =
    asMutableState(initial) { subscribe(onNext = it) }

fun <T> LiveData<T>.toObservable(owner: LifecycleOwner): Observable<T> =
    observable { emitter ->
        val observer = Observer<T>{ emitter.onNext(it) }.also{ observe(owner, it) }
        val disposable = disposableOf { removeObserver(observer) }

        emitter.setDisposable(disposable)
    }

private fun disposableOf(dispose: () -> Unit) = DisposableWrapper().also { wrapper ->
    wrapper.set(object : Disposable {

        override val isDisposed: Boolean
            get() = wrapper.isDisposed

        override fun dispose() = dispose.invoke()

    })
}

// to make accessible by Java
val mainSchedulerHook = mainScheduler
val ioSchedulerHook = ioScheduler

fun Scheduler.Executor.toJavaExecutor(): Executor {
    return Executor {
        submit(0) { it.run() }
    }
}