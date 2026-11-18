package io.github.muzammilawan.clipharbor.core

/**
 * Fragment parallelism for yt-dlp (`-N`), Phase 2B. Bounded on purpose: more connections can be faster on a good
 * Wi-Fi link and slower (or throttled) on mobile data, and there is no device measurement yet that justifies
 * anything above [DEFAULT] by default.
 */
object FragmentPolicy {
    /** Stored value meaning "decide from power state"; never passed to yt-dlp. */
    const val AUTO = 0
    const val MIN = 1
    const val MAX = 8
    const val DEFAULT = 4
    const val LOW_BATTERY = 15

    /** Selectable values, in UI order. */
    val choices = listOf(AUTO, 1, 2, DEFAULT)

    fun valid(setting: Int): Boolean = setting == AUTO || setting in MIN..MAX

    /** Random access to a fresh install's preference value; an out-of-range stored value falls back to [AUTO]. */
    fun sanitize(setting: Int): Int = if (valid(setting)) setting else AUTO

    /**
     * Resolves the stored setting to a value passed to yt-dlp. An explicit user choice always wins; otherwise power
     * saver or a nearly empty battery drops to one connection. [batteryPercent] null means the platform did not report
     * a level, which is not treated as "empty".
     */
    fun resolve(setting: Int, powerSave: Boolean = false, batteryPercent: Int? = null): Int {
        val stored = sanitize(setting)
        if (stored in MIN..MAX) return stored
        if (powerSave) return 1
        if (batteryPercent != null && batteryPercent <= LOW_BATTERY) return 1
        return DEFAULT
    }

    fun label(setting: Int): String = if (sanitize(setting) == AUTO) "Auto" else sanitize(setting).toString()

    /** Honest one-line explanation, kept next to the control so the default is never a mystery. */
    fun explanation(setting: Int): String = if (sanitize(setting) == AUTO)
        "Auto uses $DEFAULT parallel connections, and drops to 1 in battery saver or at $LOW_BATTERY% or less."
    else "Fixed: ${sanitize(setting)} parallel connection${if (sanitize(setting) == 1) "" else "s"}."
}
