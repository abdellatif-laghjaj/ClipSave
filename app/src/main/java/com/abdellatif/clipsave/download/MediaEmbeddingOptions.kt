package com.abdellatif.clipsave.download

object MediaEmbeddingOptions {
    fun subtitleLanguages(deviceLanguage: String): String {
        val normalized = when (deviceLanguage.trim().lowercase()) {
            "iw" -> "he"
            "in" -> "id"
            "ji" -> "yi"
            else -> deviceLanguage.trim().lowercase()
        }.takeIf { it.matches(Regex("[a-z]{2,8}")) } ?: "en"

        return listOf(normalized, "en")
            .distinct()
            .flatMap { language -> listOf(language, "$language-orig") }
            .joinToString(",")
    }
}
