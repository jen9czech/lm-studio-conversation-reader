package cz.jan.conversationreader

import org.junit.Assert.*
import org.junit.Test

class AppUpdatesTest {
    @Test fun comparesReleaseVersionsNumerically() {
        assertTrue(isNewerVersion("v0.10.12", "0.10.9"))
        assertTrue(isNewerVersion("v1.0.0", "0.99.99"))
        assertFalse(isNewerVersion("v0.10.11", "0.10.12"))
        assertFalse(isNewerVersion("v0.10.12", "0.10.12"))
        assertFalse(isNewerVersion("v0.10.13-beta", "0.10.12"))
    }
}
