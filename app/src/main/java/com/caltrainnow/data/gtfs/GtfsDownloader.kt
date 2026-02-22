package com.caltrainnow.data.gtfs

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads and unzips the Caltrain GTFS data file.
 */
@Singleton
class GtfsDownloader @Inject constructor(
    private val context: Context,
    private val httpClient: OkHttpClient
) {

    companion object {
        const val GTFS_URL =
            "https://data.trilliumtransit.com/gtfs/caltrain-ca-us/caltrain-ca-us.zip"
    }

    /**
     * Download the GTFS zip file and extract it to a temporary directory.
     *
     * @param url The GTFS download URL (defaults to Caltrain's official URL)
     * @return File pointing to the directory containing extracted CSV files
     * @throws GtfsDownloadException if download or extraction fails
     */
    suspend fun download(url: String = GTFS_URL): File = withContext(Dispatchers.IO) {
        val outputDir = File(context.cacheDir, "gtfs_temp").also {
            it.deleteRecursively()
            it.mkdirs()
        }

        try {
            // Download the zip file
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                throw GtfsDownloadException(
                    "GTFS download failed: HTTP ${response.code} ${response.message}"
                )
            }

            val body = response.body
                ?: throw GtfsDownloadException("GTFS download returned empty body")

            // Save to temp file
            val zipFile = File(context.cacheDir, "gtfs_download.zip")
            FileOutputStream(zipFile).use { fos ->
                body.byteStream().use { input ->
                    input.copyTo(fos)
                }
            }

            // Extract the zip
            ZipInputStream(zipFile.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val outFile = File(outputDir, entry.name)
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // Clean up zip file
            zipFile.delete()

            // Verify expected files exist
            val requiredFiles = listOf(
                "stops.txt", "stop_times.txt", "trips.txt",
                "routes.txt", "calendar.txt"
            )
            val missingFiles = requiredFiles.filter { !File(outputDir, it).exists() }
            if (missingFiles.isNotEmpty()) {
                throw GtfsDownloadException(
                    "GTFS zip missing required files: ${missingFiles.joinToString()}"
                )
            }

            outputDir
        } catch (e: GtfsDownloadException) {
            throw e
        } catch (e: Exception) {
            throw GtfsDownloadException("GTFS download/extraction failed: ${e.message}", e)
        }
    }

    /**
     * Clean up temporary GTFS files.
     */
    fun cleanup() {
        File(context.cacheDir, "gtfs_temp").deleteRecursively()
        File(context.cacheDir, "gtfs_download.zip").delete()
    }
}

class GtfsDownloadException(message: String, cause: Throwable? = null) : Exception(message, cause)
