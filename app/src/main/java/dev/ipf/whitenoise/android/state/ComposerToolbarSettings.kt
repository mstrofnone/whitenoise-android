package dev.ipf.whitenoise.android.state

import org.json.JSONArray
import org.json.JSONObject

private const val MAX_STORED_COMPOSER_SHORTCUTS = 128

/** Stable presentation IDs only: callbacks, accounts and current capability never enter storage. */
internal enum class ComposerShortcut(
    val id: String,
) {
    SendingIdentity("sending_identity"),
    More("more"),
    Emoji("emoji"),
    Paste("paste"),
    Dictation("dictation"),
    VoiceNote("voice_note"),
    Camera("camera"),
    Gallery("gallery"),
    Files("files"),
    Location("location"),
    Person("person"),
    Contact("contact"),
    Poll("poll"),
}

internal enum class ComposerToolbarLayout(
    val id: String,
) {
    Automatic("automatic"),
    AlwaysExpanded("always_expanded"),
}

internal data class ComposerToolbarSettings(
    val shortcuts: List<ComposerShortcut> = defaultComposerShortcuts,
    val layout: ComposerToolbarLayout = ComposerToolbarLayout.Automatic,
) {
    /** More is a recovery route even when every optional launcher has been removed. */
    fun normalized(): ComposerToolbarSettings {
        val unique = shortcuts.distinct()
        return copy(shortcuts = if (ComposerShortcut.More in unique) unique else listOf(ComposerShortcut.More) + unique)
    }

    fun without(shortcut: ComposerShortcut): ComposerToolbarSettings =
        if (shortcut ==
            ComposerShortcut.More
        ) {
            this
        } else {
            copy(shortcuts = shortcuts - shortcut)
        }

    fun insert(
        shortcut: ComposerShortcut,
        index: Int,
    ): ComposerToolbarSettings {
        val remaining = shortcuts - shortcut
        return copy(shortcuts = remaining.toMutableList().apply { add(index.coerceIn(0, size), shortcut) }).normalized()
    }

    fun encode(): String =
        JSONObject()
            .put("version", 1)
            .put("shortcuts", JSONArray(normalized().shortcuts.map { it.id }))
            .put("layout", layout.id)
            .toString()

    companion object {
        /** Unknown IDs are ignored without scrambling surviving choices; malformed records reset safely. */
        fun decode(encoded: String?): ComposerToolbarSettings {
            if (encoded == null) return ComposerToolbarSettings()
            return runCatching {
                val record = JSONObject(encoded)
                require(record.get("version") == 1)
                val array = record.getJSONArray("shortcuts")
                require(array.length() <= MAX_STORED_COMPOSER_SHORTCUTS)
                val ids = (0 until array.length()).map { index -> array.get(index).also { require(it is String) } }
                val shortcuts = ids.mapNotNull { id -> ComposerShortcut.entries.firstOrNull { it.id == id } }
                val layout =
                    ComposerToolbarLayout.entries.firstOrNull { it.id == record.optString("layout") }
                        ?: ComposerToolbarLayout.Automatic
                ComposerToolbarSettings(shortcuts, layout).normalized()
            }.getOrElse { ComposerToolbarSettings() }
        }
    }
}

internal val defaultComposerShortcuts =
    listOf(
        ComposerShortcut.SendingIdentity,
        ComposerShortcut.More,
        ComposerShortcut.Emoji,
        ComposerShortcut.Paste,
        ComposerShortcut.Dictation,
        ComposerShortcut.VoiceNote,
    )
