package io.github.muzammilawan.clipharbor.download

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.muzammilawan.clipharbor.core.TaskStage

/** Video + audio or audio-only. Matches the two picker branches in HomeScreen. */
enum class TaskMediaMode { VIDEO, AUDIO }

/**
 * One persisted task. This is the durable queue row, not a UI state: HomePhase stays a Compose concern.
 *
 * Deliberate deviation from docs/02 §3's `optionsJson: String`: validated typed columns instead of a raw option
 * blob. Nothing user-supplied can be smuggled into yt-dlp through this row; the worker rebuilds options from these
 * primitives with the same [io.github.muzammilawan.clipharbor.engine.DownloadExecutor] checks that already exist.
 */
@Entity(
    tableName = "tasks",
    indices = [Index("stage"), Index("createdAt")],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val mediaMode: TaskMediaMode,
    /** Short-edge pixels for video; 0 for audio-only. */
    val requestedResolution: Int,
    /** Original audio stream format id; null for video. Validated at write time by TaskRepository. */
    val audioFormatId: String?,
    /** "mp4", "mkv" or "mp4/mkv"; same set DownloadExecutor already accepts. */
    val mergeContainer: String,
    val stage: TaskStage,
    /** 0f..100f; only reflects the DOWNLOADING phase, never MERGING/POST_PROCESSING. */
    val progress: Float,
    /** [io.github.muzammilawan.clipharbor.core.ErrorCode] name; null unless stage = FAILED. */
    val errorCode: String?,
    /** content:// URI after publication; null before. Single artifact for v1; multi-file is a v2 concern. */
    val artifactUri: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
