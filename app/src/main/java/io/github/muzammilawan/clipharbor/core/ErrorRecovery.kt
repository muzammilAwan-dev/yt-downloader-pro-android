package io.github.muzammilawan.clipharbor.core

data class RecoveryPresentation(val contractAction: String, val explanation: String,
    val freshResolve: Boolean = false, val waitMs: Long = 0)

/** Contract taxonomy plus an honest Phase 1 capability boundary; unsupported recovery is explained, never faked. */
object ErrorRecovery {
    fun forCode(code: ErrorCode): RecoveryPresentation = when (code) {
        ErrorCode.ENGINE_OUTDATED -> RecoveryPresentation("UPDATE_ENGINE_AND_RETRY_ONCE",
            "Extraction failed. Check Diagnostics and obtain a verified newer APK if needed. Runtime code updates are disabled; this does not prove the engine is outdated.")
        ErrorCode.JS_RUNTIME_MISSING -> RecoveryPresentation("REPAIR_ENGINE",
            "The bundled JavaScript runtime is unavailable. Check Diagnostics or reinstall a verified APK; no runtime repair download is available.")
        ErrorCode.FORMATS_WITHHELD -> RecoveryPresentation("RETRY_WITH_OTHER_PLAYER_CLIENTS_OR_SIGN_IN",
            "No selectable formats were reported. Alternate-client and in-app sign-in recovery are not available in Phase 1.")
        ErrorCode.LOGIN_REQUIRED, ErrorCode.AGE_OR_BOT_GATE -> RecoveryPresentation("OPEN_SIGN_IN",
            "The site requires verification or sign-in. In-app login is not available yet.")
        ErrorCode.GEO_BLOCKED -> RecoveryPresentation("EXPLAIN_NO_RETRY", "The site reports a regional restriction. No retry is offered.")
        ErrorCode.UNAVAILABLE -> RecoveryPresentation("EXPLAIN_NO_RETRY", "This content or operation is unavailable. Diagnostics has the details; no retry is offered.")
        ErrorCode.RESOLVE_TIMEOUT -> RecoveryPresentation("RETRY", "Finding formats timed out. Review the link and explicitly try a fresh site check.", true)
        ErrorCode.HTTP_403_ON_MEDIA -> RecoveryPresentation("RE_RESOLVE_AND_RETRY",
            "Media access was refused or expired. Review fresh formats before confirming another download; this is not a guaranteed 403 fix.", true)
        ErrorCode.NETWORK_OFFLINE -> RecoveryPresentation("AUTO_RESUME_ON_CONNECTIVITY",
            "Check your connection, then review fresh formats. Resuming is not automatic in this MVP.", true)
        ErrorCode.NETWORK_TIMEOUT -> RecoveryPresentation("RETRY_WITH_BACKOFF",
            "The connection timed out. Wait at least 5 seconds, then review fresh formats; nothing retries automatically.", true, 5_000)
        ErrorCode.NO_SPACE -> RecoveryPresentation("SHOW_FREE_SPACE_AND_PICK_FOLDER",
            "Not enough storage. Free some space, then review fresh formats. The destination is fixed; folder selection is not available in Phase 1.", true)
        ErrorCode.STORAGE_PERMISSION_LOST -> RecoveryPresentation("REPICK_FOLDER",
            "Saving access failed. Check Android storage and Diagnostics. Folder re-selection is not available in Phase 1.")
        ErrorCode.MERGE_FAILED -> RecoveryPresentation("KEEP_PARTS_OFFER_SAVE_SEPARATELY",
            "Merging failed. Private parts are retained, not published. Saving them separately is not available in Phase 1.")
        ErrorCode.POST_PROCESS_FAILED -> RecoveryPresentation("KEEP_RAW_FILE_OFFER_SAVE_WITHOUT_CONVERSION",
            "The output could not be verified. Private files are retained; unverified raw-file saving is not offered in Phase 1.")
        ErrorCode.FGS_TIMEOUT -> RecoveryPresentation("PAUSE_AND_RESCHEDULE",
            "Android paused execution. Review fresh formats to explicitly start again. No automatic rescheduling.", true)
        ErrorCode.CANCELED -> RecoveryPresentation("NONE", "Canceled. A new attempt requires an explicit review and download confirmation.")
    }
}
