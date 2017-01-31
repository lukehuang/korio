package com.soywiz.korio.async

import java.util.*
import java.util.concurrent.CancellationException
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.suspendCoroutine

class Promise<T : Any?> {
	class Deferred<T : Any?> {
		val promise = Promise<T>()
		val onCancel = promise.onCancel
		fun resolve(value: T): Unit = run { promise.complete(value, null) }
		fun reject(error: Throwable): Unit = run { promise.complete(null, error) }
		fun toContinuation(ctx: CoroutineContext = CoroutineCancelContext()): CancellableContinuation<T> {
			val deferred = this
			val cc = CancellableContinuation(object : Continuation<T> {
				override val context: CoroutineContext = ctx
				override fun resume(value: T) = deferred.resolve(value)
				override fun resumeWithException(exception: Throwable) = deferred.reject(exception)
			})
			onCancel {
				cc.cancel()
			}
			cc.onCancel {
				promise.cancel()
				cc.cancel()
			}
			return cc
		}
	}

	companion object {
		fun <T> resolved(value: T) = Promise<T>().complete(value, null)
		fun <T> rejected(error: Throwable) = Promise<T>().complete(null, error)
	}

	private var value: T? = null
	private var error: Throwable? = null
	private var done: Boolean = false
	private val resolvedHandlers = LinkedList<(T) -> Unit>()
	private val rejectedHandlers = LinkedList<(Throwable) -> Unit>()

	private fun flush() {
		if (!done) return
		if (error != null) {
			while (rejectedHandlers.isNotEmpty()) {
				val handler = rejectedHandlers.removeFirst()
				EventLoop.queue { handler(error ?: RuntimeException()) }
			}
		} else {
			while (resolvedHandlers.isNotEmpty()) {
				val handler = resolvedHandlers.removeFirst()
				EventLoop.queue { handler(value as T) }
			}
		}
	}

	internal fun complete(value: T?, error: Throwable?): Promise<T> {
		if (!this.done) {
			this.value = value
			this.error = error
			this.done = true

			if (error != null && this.rejectedHandlers.isEmpty() && error !is CancellationException) {
				System.err.println("## Not handled Promise exception:")
				error.printStackTrace()
			}

			flush()
		}
		return this
	}

	fun then(resolved: (T) -> Unit) {
		resolvedHandlers += resolved
		flush()
	}

	fun then(resolved: (T) -> Unit, rejected: (Throwable) -> Unit) {
		resolvedHandlers += resolved
		rejectedHandlers += rejected
		flush()
	}

	fun then(c: Continuation<T>) {
		this.then(
			resolved = { c.resume(it) },
			rejected = { c.resumeWithException(it) }
		)
	}

	private val onCancel = Signal<Unit>()

	fun cancel() {
		onCancel()
		complete(null, CancellationException())
	}

	suspend fun await(): T = suspendCoroutine(this::then)
}
