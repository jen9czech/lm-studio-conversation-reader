package cz.jan.conversationreader

fun matchesLibraryMetadata(title: String, path: String, query: String): Boolean =
    query.isBlank() || title.contains(query, ignoreCase = true) || path.contains(query, ignoreCase = true)

fun matchesLinkedSearch(title: String, path: String, query: String, includeFullText: Boolean, fullTextMatch: Boolean): Boolean =
    matchesLibraryMetadata(title, path, query) || (includeFullText && fullTextMatch)
