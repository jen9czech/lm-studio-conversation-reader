package cz.jan.conversationreader

internal fun matchesLibraryName(name: String, query: String): Boolean = name.contains(query.trim(), ignoreCase = true)

internal fun matchesLibraryConversation(title: String, query: String, includeFullText: Boolean, fullTextMatch: Boolean): Boolean =
    matchesLibraryName(title, query) || (includeFullText && fullTextMatch)
