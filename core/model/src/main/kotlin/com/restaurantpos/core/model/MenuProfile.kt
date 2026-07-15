package com.restaurantpos.core.model

data class MenuProfile(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val startTime: String? = null,       // "HH:MM" or null = all day
    val endTime: String? = null,         // "HH:MM" or null = all day
    val daysOfWeek: List<Int>? = null,   // 0=Sun…6=Sat; null/empty = every day
    val channels: List<String> = emptyList(), // empty = all channels
) {
    /** Returns true when this profile is active for the given ordering context. */
    fun matchesContext(channel: String?, timeHhmm: String?, dayOfWeek: Int?): Boolean {
        if (!enabled) return false
        if (channels.isNotEmpty() && channel != null && !channels.contains(channel)) return false
        if (startTime != null && endTime != null && timeHhmm != null) {
            if (timeHhmm < startTime || timeHhmm > endTime) return false
        }
        if (!daysOfWeek.isNullOrEmpty() && dayOfWeek != null) {
            if (!daysOfWeek.contains(dayOfWeek)) return false
        }
        return true
    }
}
