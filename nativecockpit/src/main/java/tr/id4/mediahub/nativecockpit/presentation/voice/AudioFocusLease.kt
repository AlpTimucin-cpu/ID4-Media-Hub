package tr.id4.mediahub.nativecockpit.presentation.voice

/**
 * Pure Kotlin lifecycle guard for a single short navigation utterance.
 * Android AudioFocus and third-party media behavior remain OS responsibilities.
 */
internal class AudioFocusLease(
    private val requestFocus: () -> Boolean,
    private val abandonFocus: () -> Unit
) {
    private var held = false
    private var closed = false

    @Synchronized
    fun acquire(): Boolean {
        if (closed) return false
        if (held) return true
        held = runCatching(requestFocus).getOrDefault(false)
        return held
    }

    /** An old queued focus event must never stop a released/closed utterance. */
    @Synchronized
    fun shouldStopAfterLoss(): Boolean = held && !closed

    @Synchronized
    fun release() {
        if (held) runCatching(abandonFocus)
        held = false
    }

    @Synchronized
    fun close() {
        if (closed) return
        closed = true
        release()
    }
}
