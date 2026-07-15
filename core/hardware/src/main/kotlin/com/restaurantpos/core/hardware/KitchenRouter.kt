package com.restaurantpos.core.hardware

/**
 * @deprecated Use [com.restaurantpos.core.domain.routing.KitchenRouter] instead.
 * Kept here only for backward-compat with existing hardware unit tests.
 */
@Deprecated("Use core:domain routing.KitchenRouter", ReplaceWith("com.restaurantpos.core.domain.routing.KitchenRouter"))
class KitchenRouter(
    private val routes: Map<String, String>,
    val defaultStationId: String = "kitchen",
) {
    fun stationFor(categoryId: String): String = routes[categoryId] ?: defaultStationId

    fun <T> groupByStation(items: List<T>, categoryIdSelector: (T) -> String): Map<String, List<T>> =
        items.groupBy { stationFor(categoryIdSelector(it)) }
}
