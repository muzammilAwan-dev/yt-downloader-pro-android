package io.github.muzammilawan.clipharbor.engine

import io.github.muzammilawan.clipharbor.core.DownloadError
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

class EngineFailure(val error: DownloadError) : Exception(error.code.name) {
    fun describe(): String = error.logFields()
}

/** Original test seam around blocking execution. Commands and raw output must not be logged as objects. */
internal class EngineCommand(val urls: List<String>, val options: List<Pair<String, String?>>) {
    override fun toString(): String = "EngineCommand(<private>)"
}

internal class EngineResponse(val exitCode: Int, val out: String, val err: String)

internal interface BlockingEngineRunner {
    fun execute(command: EngineCommand, internalId: String, callback: ((Float, Long, String) -> Unit)?): EngineResponse
    fun destroy(internalId: String)
}

/** Cancellation targets a coroutine job, not an untrusted string passed to the library's shell-based helper. */
internal class EngineExecution(private val destroy: (String) -> Unit) {
    private val jobs = mutableMapOf<String, Job>()

    suspend fun <T> run(taskId: String?, block: (String) -> T): T = withContext(Dispatchers.IO) {
        val key = taskId ?: UUID.randomUUID().toString()
        val job = currentCoroutineContext().job
        synchronized(jobs) {
            check(!jobs.containsKey(key)) { "Task already executing" }
            jobs[key] = job
        }
        val internalId = UUID.randomUUID().toString()
        try {
            runInterruptible {
                try {
                    job.ensureActive()
                    block(internalId).also { job.ensureActive() }
                } finally {
                    if (!job.isActive) runCatching { destroy(internalId) }
                }
            }
        } finally {
            synchronized(jobs) { if (jobs[key] === job) jobs.remove(key) }
        }
    }

    fun cancel(taskId: String): Boolean {
        val job = synchronized(jobs) { jobs[taskId] } ?: return false
        job.cancel()
        return true
    }
}

internal fun requireHttpsSource(url: String) {
    val uri = URI(url)
    require(url.length <= 8192 && url.none { it.isISOControl() } && uri.scheme.equals("https", ignoreCase = true) &&
        !uri.host.isNullOrBlank() && uri.rawUserInfo == null) { "A full HTTPS link without credentials is required" }
}
