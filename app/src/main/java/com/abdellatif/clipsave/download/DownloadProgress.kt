package com.abdellatif.clipsave.download

import java.io.File
import kotlin.math.roundToLong

data class DownloadProgress(
    val percent: Int,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0,
    val speedBytesPerSecond: Long = 0,
    val etaSeconds: Long = -1
)

/** Parses both yt-dlp and aria2 progress lines without depending on their internal classes. */
internal object DownloadProgressParser {
    private val ratio = Regex(
        """(?i)(\d+(?:\.\d+)?)\s*([kmgtpe]?i?b)\s*/\s*(\d+(?:\.\d+)?)\s*([kmgtpe]?i?b)"""
    )
    private val totalAfterOf = Regex(
        """(?i)\bof\s+~?\s*(\d+(?:\.\d+)?)\s*([kmgtpe]?i?b)"""
    )
    private val speed = Regex(
        """(?i)(?:\bat\s+|\bDL:)(\d+(?:\.\d+)?)\s*([kmgtpe]?i?b)(?:/s)?"""
    )

    fun parse(
        progress: Float,
        etaSeconds: Long,
        line: String?,
        workDir: File? = null
    ): DownloadProgress {
        val percent = progress.toInt().coerceIn(0, 100)
        val output = line.orEmpty()
        val ratioMatch = ratio.find(output)
        val totalMatch = totalAfterOf.find(output)
        val parsedTotal = when {
            ratioMatch != null -> quantity(ratioMatch.groupValues[3], ratioMatch.groupValues[4])
            totalMatch != null -> quantity(totalMatch.groupValues[1], totalMatch.groupValues[2])
            else -> 0L
        }
        val parsedCompleted = when {
            ratioMatch != null -> quantity(ratioMatch.groupValues[1], ratioMatch.groupValues[2])
            parsedTotal > 0 -> (parsedTotal * (percent / 100.0)).roundToLong()
            else -> downloadedBytes(workDir)
        }
        val speedMatch = speed.find(output)
        val parsedSpeed = speedMatch?.let {
            quantity(it.groupValues[1], it.groupValues[2])
        } ?: 0L

        return DownloadProgress(
            percent = percent,
            bytesDownloaded = parsedCompleted.coerceAtLeast(0),
            totalBytes = parsedTotal.coerceAtLeast(0),
            speedBytesPerSecond = parsedSpeed.coerceAtLeast(0),
            etaSeconds = etaSeconds.takeIf { it >= 0 } ?: -1
        )
    }

    private fun downloadedBytes(directory: File?): Long = directory
        ?.walkTopDown()
        ?.filter { file ->
            file.isFile &&
                !file.name.endsWith(".ytdl") &&
                !file.name.endsWith(".aria2")
        }
        ?.sumOf(File::length)
        ?: 0L

    private fun quantity(value: String, unit: String): Long {
        val exponent = when (unit.lowercase()) {
            "kb", "kib" -> 1
            "mb", "mib" -> 2
            "gb", "gib" -> 3
            "tb", "tib" -> 4
            "pb", "pib" -> 5
            "eb", "eib" -> 6
            else -> 0
        }
        val base = if (unit.contains('i', ignoreCase = true)) 1024.0 else 1000.0
        return (value.toDoubleOrNull() ?: 0.0)
            .times(base.pow(exponent))
            .roundToLong()
    }

    private fun Double.pow(exponent: Int): Double {
        var result = 1.0
        repeat(exponent) { result *= this }
        return result
    }
}
