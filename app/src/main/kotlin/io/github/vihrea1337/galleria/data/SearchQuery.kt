package io.github.vihrea1337.galleria.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Что показывать в ленте: текст поиска + фильтры. Всё необязательно; пустой запрос — вся коллекция
 * (в порядке «популярности» музея).
 */
data class SearchQuery(
    val text: String? = null,
    val artistId: Long? = null,
    val color: ColorFilter? = null,
    /** Например, «Painting» — только живопись. */
    val type: String? = null,
    /** Не показывать эту работу (ряд «похожие» на экране картины). */
    val excludeId: Long? = null,
) {
    val isEmpty get() = text.isNullOrBlank() && artistId == null && color == null && type == null
}

/**
 * Поиск по цвету: у каждой работы API хранит преобладающий цвет в HSL. Фильтр — диапазон оттенка
 * (h, градусы) плюс насыщенность/светлота, чтобы «синий» не находил серо-голубые гравюры.
 * [hues] — несколько диапазонов: красный «переходит» через 0° (345…360 и 0…15).
 */
enum class ColorFilter(
    val hues: List<IntRange>,
    val minSaturation: Int = 35,
    val lightness: IntRange = 20..80,
    /** Цвет кружка в UI (ARGB). */
    val swatch: Long,
) {
    Red(listOf(345..360, 0..12), swatch = 0xFFC62828),
    Orange(listOf(13..35), swatch = 0xFFEF6C00),
    Yellow(listOf(36..60), swatch = 0xFFF9A825),
    Green(listOf(61..160), minSaturation = 25, swatch = 0xFF2E7D32),
    Teal(listOf(161..195), minSaturation = 25, swatch = 0xFF00838F),
    Blue(listOf(196..250), minSaturation = 30, swatch = 0xFF1565C0),
    Purple(listOf(251..344), minSaturation = 25, swatch = 0xFF6A1B9A),
    Dark(listOf(0..360), minSaturation = 0, lightness = 0..18, swatch = 0xFF212121),
    ;

    companion object {
        /** Ближайший фильтр к цвету картины — для ряда «похожие по цвету». */
        fun nearest(h: Int, s: Int, l: Int): ColorFilter? = when {
            l < 19 -> Dark
            s < 20 -> null // почти серое — «похожих по цвету» нет смысла искать
            else -> entries.firstOrNull { f -> f != Dark && f.hues.any { ((h % 360) + 360) % 360 in it } }
        }
    }
}

/**
 * Запрос к `/artworks/search` в формате `params` (JSON, как в Elasticsearch): текст — в `q` (ранжирует),
 * фильтры — в `query.bool.filter` (отсекают). Всегда только общественное достояние: у таких работ есть
 * картинка, и их можно показывать. Чистая функция — проверяется обычным JUnit.
 */
fun SearchQuery.toParams(page: Int, limit: Int, fields: String): String {
    val filters: JsonArray = buildJsonArray {
        addJsonObject { putJsonObject("term") { put("is_public_domain", true) } }
        artistId?.let { id -> addJsonObject { putJsonObject("term") { put("artist_id", id) } } }
        type?.let { t -> addJsonObject { putJsonObject("term") { put("artwork_type_title.keyword", t) } } }
        color?.let { c ->
            addJsonObject { putJsonObject("range") { putJsonObject("color.s") { put("gte", c.minSaturation) } } }
            addJsonObject {
                putJsonObject("range") {
                    putJsonObject("color.l") {
                        put("gte", c.lightness.first)
                        put("lte", c.lightness.last)
                    }
                }
            }
            // Несколько диапазонов оттенка — «хотя бы один» (bool.should).
            addJsonObject {
                putJsonObject("bool") {
                    putJsonArray("should") {
                        c.hues.forEach { r ->
                            addJsonObject {
                                putJsonObject("range") {
                                    putJsonObject("color.h") {
                                        put("gte", r.first)
                                        put("lte", r.last)
                                    }
                                }
                            }
                        }
                    }
                    put("minimum_should_match", 1)
                }
            }
        }
    }
    val params: JsonObject = buildJsonObject {
        text?.trim()?.takeIf { it.isNotEmpty() }?.let { put("q", it) }
        putJsonObject("query") {
            putJsonObject("bool") {
                put("filter", filters)
                excludeId?.let { id -> putJsonArray("must_not") { addJsonObject { putJsonObject("term") { put("id", id) } } } }
            }
        }
        putJsonArray("fields") { fields.split(',').forEach { add(it) } }
        put("page", page)
        put("limit", limit)
    }
    return params.toString()
}
