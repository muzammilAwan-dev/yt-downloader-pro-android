package io.github.muzammilawan.clipharbor.diagnostics

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Real storage checks for the Diagnostics screen; the JVM tests use fakes with the same interface. */
class AndroidStorageProbe(private val context: Context) : StorageProbe {
    override suspend fun appPrivateRoundTrip(): ProbeResult = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(context.filesDir, "selftest-${System.currentTimeMillis()}.tmp")
            val payload = "clipharbor-selftest".toByteArray()
            file.writeBytes(payload)
            val read = file.readBytes()
            file.delete()
            if (!read.contentEquals(payload)) {
                ProbeResult(false, "read-back mismatch")
            } else {
                ProbeResult(true, "wrote/read/deleted ${payload.size} bytes in ${context.filesDir.name}/")
            }
        }.getOrElse { ProbeResult(false, "app-private write failed: ${it.message}") }
    }

    override suspend fun mediaStoreInsert(): ProbeResult = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return@withContext ProbeResult(false, "API ${Build.VERSION.SDK_INT}: MediaStore Downloads needs API 29")
        }
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "clipharbor-selftest.txt")
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/ClipHarbor")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return@runCatching ProbeResult(false, "insert returned null")
            context.contentResolver.openOutputStream(uri)?.use { it.write("probe".toByteArray()) }
            val deleted = runCatching { context.contentResolver.delete(uri, null, null) }.getOrDefault(0)
            ProbeResult(true, "inserted and deleted $deleted row(s): $uri")
        }.getOrElse { ProbeResult(false, "MediaStore insert failed: ${it.message}") }
    }

    override fun freeSpaceMb(): Long? = runCatching { context.filesDir.usableSpace / (1024 * 1024) }.getOrNull()
}

/** DNS + TLS reachability check; deliberately a plain HTTPS GET so certificate problems surface too. */
class AndroidNetworkProbe : NetworkProbe {
    override suspend fun dnsAndTls(host: String): ProbeResult = withContext(Dispatchers.IO) {
        runCatching {
            val addresses = InetAddress.getAllByName(host)
            if (addresses.isEmpty()) return@runCatching ProbeResult(false, "DNS returned no address")
            val connection = (URL("https://$host/").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                instanceFollowRedirects = false
                useCaches = false
            }
            try {
                val code = connection.responseCode
                if (code in 200..399) {
                    ProbeResult(true, "dns=${addresses.first().hostAddress} https=$code")
                } else {
                    ProbeResult(false, "dns ok but https=$code")
                }
            } finally {
                connection.disconnect()
            }
        }.getOrElse { ProbeResult(false, "network check failed: ${it.message}") }
    }
}
