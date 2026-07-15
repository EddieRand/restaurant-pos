package com.restaurantpos.core.domain.routing

/**
 * Routes menu items (by categoryId) to kitchen print stations.
 *
 * Pure business logic — no Android or hardware dependencies.
 * Config: a map of categoryId → stationId.
 * Items without an explicit mapping go to [defaultStationId].
 */
class KitchenRouter(
    private val routes: Map<String, String> = emptyMap(),
    val defaultStationId: String = "kitchen",
) {
    fun stationFor(categoryId: String): String = routes[categoryId] ?: defaultStationId

    fun <T> groupByStation(items: List<T>, categoryIdSelector: (T) -> String): Map<String, List<T>> =
        items.groupBy { stationFor(categoryIdSelector(it)) }
}
