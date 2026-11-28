package co.asssie.qrlibre.pairing

/**
 * What the speaker screen shows about the link to the capture phone.
 *
 * [IDLE] means the receiver is not running: the app is in the background, the
 * role is capture or there is no peer. [DISCONNECTED] means it is supposed to
 * run but the link dropped and a retry is pending.
 */
enum class SpeakerConnection {
    IDLE,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
}

/** The only inputs that move [SpeakerConnection]. */
sealed interface SpeakerEvent {
    /** A scan or a retry is starting. */
    data object Searching : SpeakerEvent

    /** GATT is up and the notification subscription was written. */
    data object Linked : SpeakerEvent

    /** The link dropped. */
    data object Lost : SpeakerEvent

    /** The app left the foreground or the pairing was cleared. */
    data object Stopped : SpeakerEvent
}

/**
 * The connection reducer, kept pure so the state machine is testable without
 * Bluetooth. [Lost] is ignored while [SpeakerConnection.IDLE]: a late GATT
 * callback from a closed link must not undo an explicit stop.
 */
fun SpeakerConnection.reduce(event: SpeakerEvent): SpeakerConnection = when (event) {
    SpeakerEvent.Searching -> SpeakerConnection.CONNECTING
    SpeakerEvent.Linked -> SpeakerConnection.CONNECTED
    SpeakerEvent.Lost ->
        if (this == SpeakerConnection.IDLE) SpeakerConnection.IDLE else SpeakerConnection.DISCONNECTED

    SpeakerEvent.Stopped -> SpeakerConnection.IDLE
}

/**
 * The speaker surface: the link state plus the last verified message.
 *
 * [lastMessage] is written only after `verifyFrame` accepted the frame, so an
 * unauthenticated payload is never exposed or spoken. [droppedFrames] counts
 * frames the receiver refused; it is a diagnostic, not a history.
 */
data class SpeakerUiState(
    val connection: SpeakerConnection = SpeakerConnection.IDLE,
    val lastMessage: CaptureMessage? = null,
    val droppedFrames: Int = 0,
)
