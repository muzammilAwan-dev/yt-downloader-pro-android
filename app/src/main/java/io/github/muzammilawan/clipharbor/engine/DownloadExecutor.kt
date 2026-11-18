package io.github.muzammilawan.clipharbor.engine

import io.github.muzammilawan.clipharbor.core.DownloadError
import io.github.muzammilawan.clipharbor.core.AudioFormats
import io.github.muzammilawan.clipharbor.core.DownloadEvidence
import io.github.muzammilawan.clipharbor.core.DownloadOutcome
import io.github.muzammilawan.clipharbor.core.DownloadRequest
import io.github.muzammilawan.clipharbor.core.ErrorCode
import io.github.muzammilawan.clipharbor.core.ErrorMapper
import io.github.muzammilawan.clipharbor.core.FormatPlanner
import io.github.muzammilawan.clipharbor.core.ExactVideoFormats
import io.github.muzammilawan.clipharbor.core.ProgressEvent
import io.github.muzammilawan.clipharbor.core.SnapshotUnavailableException
import io.github.muzammilawan.clipharbor.core.Stage
import io.github.muzammilawan.clipharbor.log.LogRedactor
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException

/** Offline-testable download orchestration; the Android adapter supplies only blocking process execution. */
internal class DownloadExecutor(
    private val runner: BlockingEngineRunner,
    private val execution: EngineExecution,
    private val snapshotDirectory: File,
) {
    suspend fun download(
        request: DownloadRequest,
        onProgress: (ProgressEvent) -> Unit,
        onLine: (String) -> Unit,
    ): Result<DownloadOutcome> {
        val tail = ArrayDeque<String>()
        val evidence = DownloadEvidence()
        val callbackError = AtomicReference<Exception?>()
        val started = System.nanoTime()
        fun record(line: String) {
            if (line.isBlank()) return
            evidence.accept(line)
            synchronized(tail) {
                tail.addLast(LogRedactor.redactLine(line).take(MAX_LINE_CHARS))
                while (tail.size > TAIL_LINES) tail.removeFirst()
            }
        }
        fun outputTail(): List<String> = synchronized(tail) { tail.toList() }
        try {
            requireHttpsSource(request.url)
            require(request.mergeContainer in setOf("mp4", "mkv", "mp4/mkv")) { "Unsupported merge container" }
            val plan = request.qualityPlan
            val audio = request.audioPlan
            val exact = request.exactVideoPlan
            require(listOf(plan, audio, exact).count { it != null } <= 1) { "App-owned plans are mutually exclusive" }
            require(plan == null || request.maxHeight == plan.quality.pixels) { "Quality ceiling does not match plan" }
            require(request.concurrentFragments in 1..8) { "Unsupported fragment parallelism" }
            val selector = audio?.let(AudioFormats::selector) ?: exact?.let(ExactVideoFormats::selector)
                ?: plan?.let(FormatPlanner::selector) ?: FormatPlanner.selector(request.maxHeight)
            return execution.run(request.processId) { internalId ->
                val directory = request.outputDirectory
                check(directory.isDirectory || directory.mkdirs()) { "Cannot create staging directory" }
                fun perform(snapshot: File?): DownloadOutcome {
                    val options = buildList<Pair<String, String?>> {
                        add("--ignore-config" to null)
                        add("-v" to null)
                        add("-f" to selector)
                        if (audio == null) add("--merge-output-format" to (exact?.mergeContainer ?: request.mergeContainer))
                        add("-P" to directory.absolutePath)
                        add("-o" to (if (audio != null) AudioFormats.outputTemplate()
                            else exact?.let(ExactVideoFormats::outputTemplate) ?: plan?.let { FormatPlanner.outputTemplate(it.quality) } ?: FormatPlanner.outputTemplate()))
                        add("-N" to request.concurrentFragments.toString())
                        listOf("--windows-filenames", "--no-playlist", "--no-mtime", "--newline", "--progress", "--no-simulate", "--clean-info-json")
                            .forEach { add(it to null) }
                        add("--print" to DownloadEvidence.SELECTION_PRINT)
                        if (audio != null) add("--print" to DownloadEvidence.AUDIO_PRINT)
                        add("--print" to DownloadEvidence.FILE_PRINT)
                        snapshot?.let { add("--load-info-json" to it.absolutePath) }
                    }
                    val command = EngineCommand(if (snapshot == null) listOf(request.url) else emptyList(), options)
                    val response = runner.execute(command, internalId) { percent, eta, line ->
                        // The library's reader thread must not be killed by a consumer/parser/logger exception.
                        try {
                            record(line)
                            val safe = LogRedactor.redactLine(line).take(MAX_LINE_CHARS)
                            if (safe.isNotBlank()) onLine(safe)
                            val progress = line.startsWith("[download]") && percent in 0f..100f
                            onProgress(ProgressEvent(percent.takeIf { progress }, eta.takeIf { progress && it >= 0 }, safe))
                        } catch (error: Exception) {
                            callbackError.compareAndSet(null, error)
                        }
                    }
                    // A final unterminated line may never reach the callback; inspect returned output too.
                    response.err.lineSequence().forEach(::record)
                    response.out.lineSequence().forEach(::record)
                    if (response.exitCode != 0) {
                        val lines = outputTail()
                        val stage = failureStage(lines)
                        throw EngineFailure(ErrorMapper.fromExit(stage, response.exitCode, lines.joinToString("\n"), TAIL_LINES))
                    }
                    if (callbackError.get() != null) {
                        throw EngineFailure(DownloadError(ErrorCode.POST_PROCESS_FAILED, Stage.DOWNLOADING, detail = "Download callback failed", stderrTail = outputTail()))
                    }
                    val files = evidence.outputFiles(directory)
                    if (files.isEmpty()) {
                        throw EngineFailure(
                            DownloadError(ErrorCode.POST_PROCESS_FAILED, Stage.POST_PROCESSING,
                                detail = "exit=0 but no verified final output", exitCode = response.exitCode, stderrTail = outputTail()),
                        )
                    }
                    if (plan != null) {
                        val width = evidence.width()
                        val height = evidence.height()
                        if (width == null || height == null || minOf(width, height) > plan.quality.pixels ||
                            evidence.formatIds().none { it in plan.cappedIds }) {
                            throw EngineFailure(DownloadError(ErrorCode.POST_PROCESS_FAILED, Stage.POST_PROCESSING,
                                detail = "Selection evidence does not substantiate the quality ceiling", stderrTail = outputTail()))
                        }
                    }
                    if (audio != null && (evidence.formatIds() != listOf(audio.formatId) ||
                        evidence.audioFormatId() != audio.formatId || evidence.width() != null || evidence.height() != null ||
                        files.singleOrNull()?.extension?.lowercase() != audio.extension)) {
                        throw EngineFailure(DownloadError(ErrorCode.POST_PROCESS_FAILED, Stage.POST_PROCESSING,
                            detail = "Selection evidence does not substantiate original audio", stderrTail = outputTail()))
                    }
                    if (exact != null && !ExactVideoFormats.matches(exact, evidence.formatIds(), evidence.width(), evidence.height())) {
                        throw EngineFailure(DownloadError(ErrorCode.POST_PROCESS_FAILED, Stage.POST_PROCESSING,
                            detail = "Selection evidence does not substantiate the exact video source", stderrTail = outputTail()))
                    }
                    return DownloadOutcome(
                        files, response.exitCode, (System.nanoTime() - started) / 1_000_000, outputTail(),
                        evidence.formatIds(), evidence.height(), reusedResolve = snapshot != null, selectedWidth = evidence.width(),
                    )
                }
                val result = request.resolvedSnapshot?.withFile(request.url, request.processId, snapshotDirectory, ::perform)
                    ?: perform(null)
                Result.success(result)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            val lines = outputTail()
            val stage = failureStage(lines)
            val mapped = when (error) {
                is EngineFailure -> error
                is SnapshotUnavailableException -> EngineFailure(DownloadError(ErrorCode.RESOLVE_TIMEOUT, Stage.RESOLVING, detail = error.message))
                else -> {
                    val safeMessage = LogRedactor.redactLine(error.message.orEmpty()).take(MAX_LINE_CHARS)
                    val output = lines.joinToString("\n") + "\n" + safeMessage
                    // The library often throws instead of returning an exit code. Do not fabricate that code.
                    EngineFailure(DownloadError(ErrorMapper.classify(stage, output.lowercase()), stage, detail = safeMessage, stderrTail = lines))
                }
            }
            return Result.failure(mapped)
        } finally {
            request.resolvedSnapshot?.close()
        }
    }

    private fun failureStage(lines: List<String>): Stage =
        if (lines.any { it.startsWith("[Merger]") }) Stage.MERGING else Stage.DOWNLOADING

    companion object {
        private const val TAIL_LINES = 200
        private const val MAX_LINE_CHARS = 8192
    }
}
