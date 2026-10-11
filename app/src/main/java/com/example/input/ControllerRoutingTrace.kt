package com.example.input

import android.os.SystemClock

data class ControllerRouteObservation(
    val uptimeMs: Long,
    val profileId: String,
    val input: String,
    val result: String,
    val backend: String?
)

/** Bounded observations, retained after leaving the game so a report can locate a failure. */
object ControllerRoutingTrace {
    private val observations = ArrayDeque<ControllerRouteObservation>()

    @Synchronized
    fun record(profileId: String, input: String, result: String, backend: String? = null) {
        observations.addLast(ControllerRouteObservation(SystemClock.uptimeMillis(), profileId, input, result, backend))
        while (observations.size > 48) observations.removeFirst()
    }

    @Synchronized
    fun snapshot(): List<ControllerRouteObservation> = observations.toList()

    @Synchronized
    fun clear() = observations.clear()
}
