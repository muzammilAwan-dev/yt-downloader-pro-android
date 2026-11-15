# 02. Architecture

All Kotlin below is **UNTESTED reference code** written for this package. It was not compiled. Treat it as a sketch.

## 1. Layers

```
app/        Application, MainActivity (single), navigation graph, DI wiring
core/       Result types, errors, config, task model, option validator
engine/     Engine interface + YoutubeDlEngine (youtubedl-android), JS runtime setup, updater
download/   TaskRepository (Room), DownloadWorker, foreground notification, MediaStore publisher
feature/    home (paste/share + picker), queue, library, settings, login (WebView cookies)
```

Split into Gradle modules only when build time demands it. Cross-feature access goes through interfaces in `core/`.

## 2. Task state machine

```
QUEUED -> RESOLVING -> DOWNLOADING -> MERGING -> POST_PROCESSING -> PUBLISHING -> DONE
   any stage -> PAUSED | FAILED(error) | CANCELED
   PAUSED -> QUEUED (resume)       FAILED -> QUEUED (retry, subject to error.retryable)
```

Rules:
- Every transition is written to Room first, then acted on. UI and notification observe the table.
- Each stage is idempotent: re-running it from persisted inputs must be safe.
- **Re-resolve on retry.** Media URLs expire. Snaptube's format model stores expiry/time-to-live per format (see doc 03), so do not
  retry with a stale URL; call `resolve` again. A 403 on media maps to `HTTP_403_ON_MEDIA` -> `RE_RESOLVE_AND_RETRY`.
- Pre-flight free-space check before DOWNLOADING and before MERGING (merge needs room for inputs and output). Insufficient space is a
  warning state the user can resolve, not a hard failure (pattern seen in Snaptube's ffmpeg scheduler).
- Cap concurrency (default 2). Keep a separate cap for ffmpeg stages.

## 3. Reference snippets (UNTESTED)

```kotlin
enum class Stage { QUEUED, RESOLVING, DOWNLOADING, MERGING, POST_PROCESSING, PUBLISHING, DONE, PAUSED, FAILED, CANCELED }

sealed interface DownloadError {
    val retryable: Boolean
    data object EngineOutdated : DownloadError { override val retryable = true }
    data object JsRuntimeMissing : DownloadError { override val retryable = false }
    data object FormatsWithheld : DownloadError { override val retryable = true }
    data object LoginRequired : DownloadError { override val retryable = true }
    data object GeoBlocked : DownloadError { override val retryable = false }
    data object Unavailable : DownloadError { override val retryable = false }
    data object Http403OnMedia : DownloadError { override val retryable = true }
    data class Network(val offline: Boolean) : DownloadError { override val retryable = true }
    data object NoSpace : DownloadError { override val retryable = true }
    data object MergeFailed : DownloadError { override val retryable = true }
    data object PostProcessFailed : DownloadError { override val retryable = true }
}

interface Engine {
    suspend fun resolve(url: String, opts: ResolveOptions): Result<MediaInfo, DownloadError>
    suspend fun download(task: Task, onProgress: (Progress) -> Unit): Result<List<File>, DownloadError>
    suspend fun update(channel: UpdateChannel): Result<EngineVersion, DownloadError>
    fun version(): EngineVersion
}
```

```kotlin
// Room entity sketch
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val optionsJson: String,      // validated option set, never cookies
    val stage: Stage,
    val progress: Float,
    val errorCode: String?,
    val artifactUris: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
)
```

```kotlin
// Worker sketch: one worker per task; promote to foreground for long runs
class DownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result { /* load task, run stages, persist each transition */ return Result.success() }
    override suspend fun getForegroundInfo(): ForegroundInfo { /* notification + FOREGROUND_SERVICE_TYPE_DATA_SYNC */ TODO() }
}
```

Handling the Android 15 `onTimeout` limit: see doc 06. Persist PAUSED with reason `FGS_TIMEOUT`, then re-enqueue.

## 4. Validator (UNTESTED sketch)

```kotlin
class OptionValidator(private val allowed: Set<String>, private val blocked: Set<String>) {
    fun validate(args: List<String>): Boolean {
        var i = 0
        while (i < args.size) {
            val a = args[i]
            if (a.startsWith("-")) {
                val name = a.substringBefore('=')
                if (name in blocked || name !in allowed) return false
            } else if (!(a.startsWith("https://") || a.startsWith("http://"))) {
                // values of options are consumed by the option parser in the real implementation
            }
            i++
        }
        return true
    }
}
```

The real version must know which options take values; a flat scan like this is only illustrative. Load the lists from
`contracts/allowed-options.json`. Unit-test it against the blocked list first.

## 5. Config: two planes, data only

`AppConfig` = code defaults + optional remote JSON overlay (schema version, unknown fields ignored). The overlay may carry: site status
flags, recommended update channel, known-issue banner text. It must never carry code, script or URLs that get executed. Host it in the
GitHub repo; verify with a pinned hash or signature. (Snaptube has a compiled-defaults plus remote-overlay design; it also ships remotely
updatable extractor JS, which we reject.)

## 6. Cookies

**Revised 15 Nov 2026; proposed, not implemented.** The owner reports YouTube website sign-in inside the reference app's
browser and a sign-in recommendation after bot errors. This is not necessarily an OAuth integration. Google OAuth's
embedded-agent restriction must not be generalized to every direct website login; website acceptance and authenticated
download-session transfer need device proof. Full evidence and limits: `16-BROWSER-AUTH-AND-MEDIA-DISCOVERY.md` §§3–4.

Do not assume `WebView -> CookieManager -> Netscape file` is a complete cookie exporter: `getCookie(url)` returns request
header pairs without domain/path/expiry metadata. Do not invent a broad cookie scope or persist captured headers in Room.
AndroidX `CookieManagerCompat.getCookieInfo` (added in WebKit 1.6.0) provides URL-scoped cookies with set attributes when
`WebViewFeature.GET_COOKIE_INFO` is supported. This makes a validated task-cookie adapter plausible, not yet implemented
or device-tested; test the feature and cookie scope/partition semantics rather than guessing from header pairs.
Custom Tabs use browser state, not the app's WebView store; opening a logged-in tab does not authenticate yt-dlp. An Android
app cannot assume access to another browser's private cookie database.

Investigate opt-in, user-selected Netscape cookie import as a fallback, with strict validation, bounded input, correct
scope and task-private copies. Delete app-created task cookie files on all terminal paths and on startup after process
death; never write cookies, auth headers or tokens to logs/history. Import does not authorize deleting an owner's source
file. Persistent login is a separate explicit security/product decision, not solved merely by encrypting a copy while
leaving a WebView cookie store populated. Cookies may help some failures but cannot guarantee bot immunity or replace
PO tokens; respect rejected sign-in instead of spoofing the user agent or weakening TLS checks.

## 7. Storage

yt-dlp writes into an app-private directory. After success, the app publishes to MediaStore (Downloads/Movies/Music) or the user's SAF
tree. Never ask for MANAGE_EXTERNAL_STORAGE. Compute "can I write here" once at startup and expose one `StorageStatus` model (Snaptube
treats storage state as a first-class model that drives UI).

## 8. Entry points

- `ACTION_SEND` text/plain -> thin `ShareReceiverActivity` that validates the URL and opens a bottom sheet (YTDLnis offers a card from the
  share menu without opening the app).
- Optional external-automation intent (YTDLnis documents TYPE and BACKGROUND extras for Tasker/MacroDroid). If you add one, make it
  opt-in, require an explicit setting, and validate every extra.
- Optional launcher-icon variants via `activity-alias` + `PackageManager.setComponentEnabledSetting` (Snaptube's disabled second alias
  pattern). Low priority.
