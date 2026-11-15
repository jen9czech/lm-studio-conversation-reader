package cz.jan.conversationreader

/** Providers without reliable timestamps/sizes must be reread rather than serving stale titles. */
fun reusableMetadata(oldModified: Long, oldSize: Long?, modified: Long, size: Long?): Boolean =
    modified > 0 && size != null && size >= 0 && oldModified == modified && oldSize == size
