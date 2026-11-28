package co.asssie.qrlibre.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ListenerConnectionTest {

    @Before
    fun reset() {
        // The signal is process-local. Reset it the same way the service does
        // when its binding goes away, so a test never inherits another's state.
        ListenerConnection.onListenerDisconnected()
    }

    @Test
    fun startsDisconnectedWithoutASavedState() {
        assertFalse(ListenerConnection.connected.value)
    }

    @Test
    fun connectAndDisconnectUpdateTheSignal() {
        ListenerConnection.onListenerConnected()
        assertTrue(ListenerConnection.connected.value)

        ListenerConnection.onListenerDisconnected()
        assertFalse(ListenerConnection.connected.value)
    }

    @Test
    fun repeatedConnectKeepsTheSignalOn() {
        ListenerConnection.onListenerConnected()
        ListenerConnection.onListenerConnected()

        assertTrue(ListenerConnection.connected.value)
    }

    @Test
    fun repeatedDisconnectClearsTheRetainedSignal() {
        ListenerConnection.onListenerConnected()
        ListenerConnection.onListenerDisconnected()
        ListenerConnection.onListenerDisconnected()

        assertFalse(ListenerConnection.connected.value)
    }
}
