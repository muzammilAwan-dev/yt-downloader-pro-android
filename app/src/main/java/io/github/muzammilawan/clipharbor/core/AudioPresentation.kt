package io.github.muzammilawan.clipharbor.core

/** A bounded convenience view, not a quality/language recommendation. All exact streams remain available. */
object AudioPresentation {
    fun quickChoices(choices: List<AudioChoice>, selectedId: String? = null): List<AudioChoice> {
        val quick = choices.distinctBy { it.label }.take(3)
        val selected = choices.firstOrNull { it.formatId == selectedId }
        return if (selected != null && selected !in quick) quick + selected else quick
    }
}
