package cz.jan.conversationreader

import org.junit.Assert.*
import org.junit.Test

class MetadataCacheTest {
    @Test fun unchangedFilesReuseMetadata() {
        assertTrue(reusableMetadata(100, 37000, 100, 37000))
    }
    @Test fun changedFilesInvalidateMetadata() {
        assertFalse(reusableMetadata(100, 37000, 101, 37000))
        assertFalse(reusableMetadata(100, 37000, 100, 38000))
    }
    @Test fun missingProviderMetadataCannotBeTrusted() {
        assertFalse(reusableMetadata(0, 37000, 0, 37000))
        assertFalse(reusableMetadata(100, null, 100, null))
        assertFalse(reusableMetadata(100, -1, 100, -1))
    }
}
