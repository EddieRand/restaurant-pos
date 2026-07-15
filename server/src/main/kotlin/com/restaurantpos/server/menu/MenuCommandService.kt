package com.restaurantpos.server.menu

import com.restaurantpos.server.db.tables.MenuItemsTable
import com.restaurantpos.server.model.CreateMenuItemRequest
import com.restaurantpos.server.model.UpdateMenuItemRequest
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

/**
 * Canonical server-side menu mutation boundary.
 *
 * HTTP routes and controlled AI execution must call this service instead of
 * writing MenuItemsTable independently, so validation and optimistic locking
 * stay identical across human and AI initiated changes.
 */
class MenuCommandService(
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun create(request: CreateMenuItemRequest) {
        require(request.id.isNotBlank()) { "Menu item id is required" }
        require(request.names.isNotBlank()) { "Menu item names are required" }
        require(request.priceMinorUnit >= 0) { "Menu item price cannot be negative" }
        require(request.categoryId.isNotBlank()) { "Menu item category is required" }
        val updatedAt = now()
        transaction {
            MenuItemsTable.insert {
                it[id] = request.id
                it[names] = request.names
                it[priceMinorUnit] = request.priceMinorUnit
                it[taxRateId] = request.taxRateId
                it[categoryId] = request.categoryId
                it[course] = request.course
                it[isSoldOut] = false
                it[imageUrl] = request.imageUrl
                it[allergens] = request.allergens
                it[availableChannels] = request.availableChannels.joinToString("|")
                it[stockCount] = request.stockCount
                it[MenuItemsTable.updatedAt] = updatedAt
            }
        }
    }

    fun update(itemId: String, request: UpdateMenuItemRequest): Boolean {
        request.priceMinorUnit?.let { require(it >= 0) { "Menu item price cannot be negative" } }
        val updatedAt = now()
        return transaction {
            MenuItemsTable.update({ MenuItemsTable.id eq itemId }) { stmt ->
                request.names?.let { stmt[names] = it }
                request.priceMinorUnit?.let { stmt[priceMinorUnit] = it }
                request.taxRateId?.let { stmt[taxRateId] = it }
                request.categoryId?.let { stmt[categoryId] = it }
                request.course?.let { stmt[course] = it }
                request.isSoldOut?.let { stmt[isSoldOut] = it }
                request.imageUrl?.let { stmt[imageUrl] = it }
                request.allergens?.let { stmt[allergens] = it }
                request.availableChannels?.let { stmt[availableChannels] = it.joinToString("|") }
                request.stockCount?.let { stmt[stockCount] = it }
                stmt[MenuItemsTable.updatedAt] = updatedAt
            } > 0
        }
    }

    fun delete(itemId: String): Boolean = transaction {
        MenuItemsTable.deleteWhere { id eq itemId } > 0
    }

    fun setAvailability(itemIds: List<String>, isSoldOut: Boolean): Int {
        if (itemIds.isEmpty()) return 0
        val updatedAt = now()
        return transaction {
            MenuItemsTable.update({ MenuItemsTable.id inList itemIds.distinct() }) {
                it[MenuItemsTable.isSoldOut] = isSoldOut
                it[MenuItemsTable.updatedAt] = updatedAt
            }
        }
    }

    /** Must be called inside the proposal execution transaction. */
    fun updatePriceIfVersionInTransaction(
        itemId: String,
        expectedUpdatedAt: Long,
        newPriceMinorUnit: Long,
        mutationTimestamp: Long,
    ): Boolean {
        require(newPriceMinorUnit >= 0) { "Menu item price cannot be negative" }
        return MenuItemsTable.update({
            (MenuItemsTable.id eq itemId) and (MenuItemsTable.updatedAt eq expectedUpdatedAt)
        }) {
            it[priceMinorUnit] = newPriceMinorUnit
            it[updatedAt] = mutationTimestamp.coerceAtLeast(expectedUpdatedAt + 1)
        } > 0
    }
}

fun UpdateMenuItemRequest.isSoldOutOnlyMutation(): Boolean =
    isSoldOut != null &&
        names == null &&
        priceMinorUnit == null &&
        taxRateId == null &&
        categoryId == null &&
        course == null &&
        imageUrl == null &&
        allergens == null &&
        availableChannels == null &&
        stockCount == null
