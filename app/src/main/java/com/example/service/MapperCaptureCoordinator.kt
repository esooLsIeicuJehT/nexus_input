package com.example.service

import android.graphics.Bitmap
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException

/** Recycles results cancelled before delivery, including a late Android screenshot callback. */
internal suspend fun awaitMapperBitmap(request: ((Result<Bitmap>) -> Unit) -> Unit): Bitmap =
    suspendCancellableCoroutine { continuation ->
        request { result ->
            result.fold(onSuccess = { bitmap ->
                continuation.resume(bitmap) { _, unused, _ -> unused.recycle() }
            },onFailure = { error -> if(continuation.isActive) continuation.resumeWithException(error) })
        }
    }

/** Orders actual service operations; a bridge acknowledgement is never image or touch evidence. */
internal class MapperCaptureCoordinator(
    private val releaseContacts: suspend () -> Unit,
    private val removeOwnedWindows: () -> Unit,
    private val awaitFrame: suspend () -> Unit,
    private val captureBitmap: suspend () -> Bitmap,
    private val stopMapping: () -> Unit,
    private val openMapper: (Bitmap) -> Unit
) {
    suspend fun capture() {
        releaseContacts()
        removeOwnedWindows()
        awaitFrame()
        awaitFrame()
        val bitmap = captureBitmap()
        try {
            stopMapping()
            openMapper(bitmap)
        } finally {
            bitmap.recycle()
        }
    }
}
