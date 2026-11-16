package app.merachride.wear

import com.google.android.gms.tasks.Task
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Awaits a Play services [Task] without the kotlinx-coroutines-play-services dependency. */
internal suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { c ->
    addOnCompleteListener { t ->
        val e = t.exception
        when {
            e != null -> c.resumeWithException(e)
            t.isCanceled -> c.cancel()
            else -> c.resume(t.result)
        }
    }
}

/** Awaits a [ListenableFuture] (Health Services) without the kotlinx-coroutines-guava dependency. */
internal suspend fun <T> ListenableFuture<T>.awaitFuture(): T = suspendCancellableCoroutine { c ->
    addListener({
        try {
            c.resume(get())
        } catch (e: ExecutionException) {
            c.resumeWithException(e.cause ?: e)
        } catch (e: Exception) {
            c.resumeWithException(e)
        }
    }, Runnable::run)
    c.invokeOnCancellation { cancel(false) }
}
