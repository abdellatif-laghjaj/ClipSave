package com.abdellatif.clipsave.download

import com.abdellatif.clipsave.data.model.MediaType
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

class MediaPayloadValidatorTest {
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
