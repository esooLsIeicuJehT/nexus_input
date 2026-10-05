package com.example.input

/** Completion is recorded only by actual Accessibility gesture callbacks or rejected dispatches. */
internal class GestureLedger {
    private val ids=java.util.concurrent.atomic.AtomicLong()
    private val pending=java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()
    val count: Int get()=pending.size
    fun begin(): Long = ids.incrementAndGet().also { pending.add(it) }
    fun complete(id: Long): Boolean = pending.remove(id)
    fun awaitIdle(timeoutMillis: Long): Boolean {
        require(timeoutMillis>=0)
        val deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while(pending.isNotEmpty() && System.nanoTime()<deadline) Thread.sleep(5)
        return pending.isEmpty()
    }
}
