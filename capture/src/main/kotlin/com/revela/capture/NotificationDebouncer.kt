package com.revela.capture

/**
 * Suppresses duplicate/updated notifications from the same conversation
 * (§5.2). A conversation is keyed by (package, notification key); a repeat
 * within [windowMs] is treated as the same event. Pure logic, unit-tested.
 */
class NotificationDebouncer(
    private val windowMs: Long = 10_000L,
    private val maxEntries: Int = 512,
) {
    private val lastSeen = LinkedHashMap<String, Long>(64, 0.75f, true)

    /** True if this notification should be recorded (not a debounced repeat). */
    @Synchronized
    fun shouldRecord(packageName: String, notificationKey: String, ts: Long): Boolean {
        val key = "$packageName|$notificationKey"
        val previous = lastSeen[key]
        lastSeen[key] = ts
        if (lastSeen.size > maxEntries) {
            val oldest = lastSeen.keys.iterator()
            if (oldest.hasNext()) {
                oldest.next()
                oldest.remove()
            }
        }
        return previous == null || ts - previous > windowMs
    }
}
