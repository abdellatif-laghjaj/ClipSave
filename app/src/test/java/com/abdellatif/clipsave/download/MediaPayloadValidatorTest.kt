package com.abdellatif.clipsave.download

import com.abdellatif.clipsave.data.model.MediaType
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

class MediaPayloadValidatorTest {
    @Test
    fun acceptsAJpegImage() {
        val file = File.createTempFile("payload", ".jpg")
        try {
            file.writeBytes(
                byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) +
                    ByteArray(16)
            )
            MediaPayloadValidator.requireValid(file, MediaType.IMAGE, "image/jpeg")
        } finally {
            file.delete()
        }
    }

    @Test
    fun rejectsHtmlSavedWithImageExtension() {
        val file = File.createTempFile("payload", ".jpg")
        try {
            file.writeText("<!DOCTYPE html><html><body>Access denied</body></html>")
            assertThrows(IllegalStateException::class.java) {
                MediaPayloadValidator.requireValid(file, MediaType.IMAGE, "text/html")
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun rejectsHtmlSavedWithVideoExtension() {
        val file = File.createTempFile("payload", ".mp4")
        try {
            file.writeText("<!DOCTYPE html><html><body>Consent required</body></html>")
            assertThrows(IllegalStateException::class.java) {
                MediaPayloadValidator.requireValid(file, MediaType.VIDEO)
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun acceptsAnMp4ContainerHeader() {
        val file = File.createTempFile("payload", ".mp4")
        try {
            file.writeBytes(
                byteArrayOf(0, 0, 0, 24) +
                    "ftyp".toByteArray() +
                    "isom".toByteArray()
            )
            MediaPayloadValidator.requireValid(file, MediaType.VIDEO, "video/mp4")
        } finally {
            file.delete()
        }
    }
}
