package cz.jan.conversationreader

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Write and sync a sibling temporary file, then atomically replace the last valid copy. */
internal fun atomicWrite(destination: File, bytes: ByteArray) {
    val temporary = File.createTempFile(".${destination.name}.", ".tmp", destination.parentFile)
    try {
        FileOutputStream(temporary).use { stream -> stream.write(bytes); stream.fd.sync() }
        Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } finally { temporary.delete() }
}
