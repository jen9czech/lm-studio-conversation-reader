package cz.jan.conversationreader

import org.junit.Assert.*
import org.junit.Test

class ModelSourceTest {
    @Test fun repositoryIdentifiersAndSavedHuggingFaceUrlsOpenRepository() {
        assertEquals(ModelSource("https://huggingface.co/Publisher/Model-GGUF", true), huggingFaceSource("Publisher/Model-GGUF@q4_k_m"))
        assertEquals(ModelSource("https://huggingface.co/Publisher/Model", true), huggingFaceSource("https://huggingface.co/Publisher/Model/resolve/main/model.gguf"))
    }
    @Test fun aliasesAndLocalFilesUseSearchRatherThanGuessingProvenance() {
        val source = huggingFaceSource("some-model@q5_k_m")!!
        assertFalse(source.direct)
        assertEquals("https://huggingface.co/models?search=some-model", source.url)
        assertFalse(huggingFaceSource("folder/model.gguf")!!.direct)
        assertFalse(huggingFaceSource("https://example.com/Publisher/Model")!!.direct)
        assertFalse(huggingFaceSource("https://huggingface.co.evil.test/Publisher/Model")!!.direct)
        assertNull(huggingFaceSource("Unknown model"))
        assertNull(huggingFaceSource("  "))
    }
    @Test fun searchEncodesSpecialCharactersWithoutAdditionalQueryParameters() {
        assertEquals("https://huggingface.co/models?search=model+%26+other%3F", huggingFaceSource("model & other?")!!.url)
    }
}
