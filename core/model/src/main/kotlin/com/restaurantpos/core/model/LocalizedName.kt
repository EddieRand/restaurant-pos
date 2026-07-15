package com.restaurantpos.core.model

/**
 * Resolves a display name from a multilingual name map for the given BCP-47 [locale].
 *
 * Name maps arrive from multiple sources with inconsistent key styles: the on-device
 * seeder uses bare language keys (`"en"`, `"zh"`) while server/Web Admin menus use
 * full BCP-47 tags (`"en-US"`, `"zh-CN"`). Callers must never key directly with a
 * single style or names silently fall back to opaque ids on the other source.
 *
 * Resolution order: exact language → exact locale tag → English → any value → "".
 */
fun Map<String, String>.localizedName(locale: String): String {
    val lang = locale.substringBefore('-')
    return this[lang]
        ?: this[locale]
        ?: this["en"]
        ?: this.entries.firstOrNull { it.key.substringBefore('-') == lang }?.value
        ?: this.entries.firstOrNull { it.key.substringBefore('-') == "en" }?.value
        ?: values.firstOrNull()
        ?: ""
}
