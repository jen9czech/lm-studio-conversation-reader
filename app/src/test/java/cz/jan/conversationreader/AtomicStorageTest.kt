package cz.jan.conversationreader

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class AtomicStorageTest {
    @Test fun replacesExistingFileWithoutLosingPreviousCopyToPartialTemporaryFiles() {
        val directory = Files.createTempDirectory("reader-atomic-test").toFile()
        try {
            val destination = directory.resolve("index.json")
            atomicWrite(destination, "first".toByteArray())
            directory.resolve(".index.json.interrupted.tmp").writeText("partial")
            assertEquals("first", destination.readText())
            atomicWrite(destination, "second".toByteArray())
            assertEquals("second", destination.readText())
            assertEquals(listOf(".index.json.interrupted.tmp", "index.json"), directory.list()!!.sorted())
        } finally { directory.deleteRecursively() }
    }
}
