package com.abdellatif.clipsave.download

import com.abdellatif.clipsave.data.model.MediaType
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Stops login pages, consent pages, rate-limit responses, and JSON errors from being saved with a
 * media extension. This is intentionally small and deterministic; yt-dlp still owns codec support.
 */
object MediaPayloadValidator {
    fun requireValid(file: File, mediaType: MediaType, contentType: String? = null) {
        if (!file.isFile || file.length() < 12L) {
            throw IllegalStateException("The downloaded file is empty or incomplete.")
        }

        val declaredType = contentType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            .orEmpty()
        if (
            declaredType.startsWith("text/") ||
            declaredType == "application/json" ||
            declaredType == "application/problem+json"
        ) {
            throw IllegalStateException("The server returned a web page instead of media.")
        }

        val header = ByteArray(minOf(512L, file.length()).toInt())
        file.inputStream().use { it.read(header) }
        val textHeader = String(header, StandardCharsets.ISO_8859_1)
            .trimStart('\u0000', '\uFEFF', ' ', '\t', '\r', '\n')
            .lowercase()
        if (
            textHeader.startsWith("<!doctype") ||
            textHeader.startsWith("<html") ||
            textHeader.startsWith("<?xml") ||
            textHeader.startsWith("{\"error\"") ||
            textHeader.startsWith("{\"status\"")
        ) {
            throw IllegalStateException("The server returned a web page instead of media.")
        }

        if (mediaType == MediaType.VIDEO && declaredType.startsWith("image/")) {
            throw IllegalStateException("The server returned an image instead of a video.")
        }
        if (
            mediaType == MediaType.IMAGE && declaredType.isNotBlank() &&
            !declaredType.startsWith("image/")
        ) {
            throw IllegalStateException("The server returned a non-image file.")
        }

        val ext = file.extension.lowercase()
        val hasExpectedContainer = when (ext) {
            "mp4", "m4v", "mov" -> header.size >= 8 &&
                header.copyOfRange(4, 8).contentEquals("ftyp".toByteArray())
            "webm", "mkv" -> header.take(4).map { it.toInt() and 0xFF } ==
                listOf(0x1A, 0x45, 0xDF, 0xA3)
            "avi" -> header.size >= 12 &&
                String(header, 0, 4, StandardCharsets.US_ASCII) == "RIFF" &&
                String(header, 8, 4, StandardCharsets.US_ASCII) == "AVI "
            else -> true
        }
        if (mediaType == MediaType.VIDEO && !hasExpectedContainer) {
            throw IllegalStateException("The downloaded file is not a valid ${ext.ifBlank { "video" }} file.")
        }

        val hasExpectedImage = when (ext) {
            "jpg", "jpeg" -> header.take(3).map { it.toInt() and 0xFF } ==
                listOf(0xFF, 0xD8, 0xFF)
            "png" -> header.take(8).map { it.toInt() and 0xFF } ==
                listOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
            "gif" -> textHeader.startsWith("gif87a") || textHeader.startsWith("gif89a")
            "webp" -> header.size >= 12 &&
                String(header, 0, 4, StandardCharsets.US_ASCII) == "RIFF" &&
                String(header, 8, 4, StandardCharsets.US_ASCII) == "WEBP"
            "avif", "heic", "heif" -> header.size >= 12 &&
                String(header, 4, 4, StandardCharsets.US_ASCII) == "ftyp"
            else -> true
        }
        if (mediaType == MediaType.IMAGE && !hasExpectedImage) {
            throw IllegalStateException("The downloaded file is not a valid ${ext.ifBlank { "image" }} file.")
        }
    }
}
