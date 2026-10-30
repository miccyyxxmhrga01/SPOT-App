package com.example.spotapp.data

import org.json.JSONArray
import org.json.JSONObject

/** Normalized positions keep the same composition on every screen and in Highlights. */
data class StoryOverlay(val id: String, val text: String, val x: Double = .5, val y: Double = .5,
    val size: Double = .07, val color: Long = 0xFFFFFFFF, val background: Boolean = true) {
    fun fields(): Map<String, Any> = mapOf("id" to id, "text" to text, "x" to x, "y" to y,
        "size" to size, "color" to color, "background" to background)
}

data class StoryComposition(val scale: Double = 1.0, val offsetX: Double = 0.0, val offsetY: Double = 0.0,
    val fill: Boolean = false, val rotation: Int = 0, val trimStartMs: Long = 0, val trimEndMs: Long = 0,
    val muted: Boolean = false, val imageDurationMs: Long = 5_000, val overlays: List<StoryOverlay> = emptyList(),
    val filter: String = "normal") {
    /** Photo stories are shown through the filter; the photo itself is uploaded unchanged. */
    val look: MediaLook get() = MediaLook(filter)
    // The filter is only written when used, so unfiltered stories keep exactly the same data as before.
    fun fields(): Map<String, Any?> = mapOf("scale" to scale, "offsetX" to offsetX, "offsetY" to offsetY,
        "fill" to fill, "rotation" to rotation, "trimStartMs" to trimStartMs, "trimEndMs" to trimEndMs,
        "muted" to muted, "imageDurationMs" to imageDurationMs, "overlays" to overlays.map { it.fields() }) +
        if (filter == "normal") emptyMap() else mapOf("filter" to filter)
    fun encode(): String = JSONObject(fields()).toString()
    fun validate() {
        require(scale.isFinite() && scale in 1.0..4.0 && offsetX.isFinite() && offsetX in -1.0..1.0 && offsetY.isFinite() && offsetY in -1.0..1.0)
        require(rotation in 0..3 && trimStartMs in 0..300_000 && (trimEndMs == 0L || trimEndMs in (trimStartMs + 1)..(trimStartMs + 60_000)))
        require(imageDurationMs in 1_000..60_000 && overlays.size <= 4 && overlays.distinctBy { it.id }.size == overlays.size)
        require(LookFilters.any { it.key == filter })
        overlays.forEach { require(it.id.length in 1..80 && it.text.isNotBlank() && it.text.length <= 160 &&
            it.x.isFinite() && it.x in .0..1.0 && it.y.isFinite() && it.y in .0..1.0 && it.size.isFinite() && it.size in .025.. .15 && it.color in 0..0xFFFFFFFF) }
    }
    companion object {
        fun decode(value: String): StoryComposition = runCatching { from(jsonMap(JSONObject(value))) }.getOrDefault(StoryComposition())
        fun from(value: Any?): StoryComposition {
            val m = value as? Map<*, *> ?: return StoryComposition()
            fun number(key: String, default: Number) = m[key] as? Number ?: default
            return StoryComposition(number("scale", 1).toDouble(), number("offsetX", 0).toDouble(), number("offsetY", 0).toDouble(),
                m["fill"] == true, number("rotation", 0).toInt(), number("trimStartMs", 0).toLong(), number("trimEndMs", 0).toLong(),
                m["muted"] == true, number("imageDurationMs", 5_000).toLong(),
                (m["overlays"] as? List<*>).orEmpty().take(4).mapNotNull { raw ->
                    val a = raw as? Map<*, *> ?: return@mapNotNull null
                    StoryOverlay(a["id"] as? String ?: return@mapNotNull null, a["text"] as? String ?: return@mapNotNull null,
                        (a["x"] as? Number)?.toDouble() ?: .5, (a["y"] as? Number)?.toDouble() ?: .5,
                        (a["size"] as? Number)?.toDouble() ?: .07, (a["color"] as? Number)?.toLong() ?: 0xFFFFFFFF, a["background"] == true)
                }, (m["filter"] as? String)?.takeIf { key -> LookFilters.any { it.key == key } } ?: "normal")
                .let { result -> runCatching { result.validate(); result }.getOrDefault(StoryComposition()) }
        }
        private fun jsonMap(value: JSONObject): Map<String, Any?> = value.keys().asSequence().associateWith { key ->
            when (val item = value.get(key)) {
                is JSONObject -> jsonMap(item)
                is JSONArray -> (0 until item.length()).map { index -> (item.get(index) as? JSONObject)?.let(::jsonMap) }
                JSONObject.NULL -> null
                else -> item
            }
        }
    }
}
