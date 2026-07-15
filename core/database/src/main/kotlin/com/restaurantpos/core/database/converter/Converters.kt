package com.restaurantpos.core.database.converter

import androidx.room.TypeConverter
import com.restaurantpos.core.model.*

class Converters {

    @TypeConverter fun orderStatusToString(v: OrderStatus): String = v.name
    @TypeConverter fun stringToOrderStatus(v: String): OrderStatus = OrderStatus.valueOf(v)

    @TypeConverter fun orderItemStatusToString(v: OrderItemStatus): String = v.name
    @TypeConverter fun stringToOrderItemStatus(v: String): OrderItemStatus = OrderItemStatus.valueOf(v)

    @TypeConverter fun orderTypeToString(v: OrderType): String = v.name
    @TypeConverter fun stringToOrderType(v: String): OrderType = OrderType.valueOf(v)

    @TypeConverter fun tableStatusToString(v: TableStatus): String = v.name
    @TypeConverter fun stringToTableStatus(v: String): TableStatus = TableStatus.valueOf(v)

    @TypeConverter fun paymentStatusToString(v: PaymentStatus): String = v.name
    @TypeConverter fun stringToPaymentStatus(v: String): PaymentStatus = PaymentStatus.valueOf(v)

    @TypeConverter fun paymentMethodToString(v: PaymentMethod): String = v.name
    @TypeConverter fun stringToPaymentMethod(v: String): PaymentMethod = PaymentMethod.valueOf(v)

    @TypeConverter fun fulfillmentStatusToString(v: OrderFulfillmentStatus): String = v.name
    @TypeConverter fun stringToFulfillmentStatus(v: String): OrderFulfillmentStatus = OrderFulfillmentStatus.valueOf(v)

    /** Map<String,String> stored as pipe-delimited "key=value|key=value". */
    @TypeConverter
    fun mapToString(map: Map<String, String>): String =
        map.entries.joinToString("|") { "${encodeField(it.key)}=${encodeField(it.value)}" }

    @TypeConverter
    fun stringToMap(value: String): Map<String, String> {
        if (value.isBlank()) return emptyMap()
        return value.split("|").associate { entry ->
            val idx = entry.indexOf('=')
            decodeField(entry.substring(0, idx)) to decodeField(entry.substring(idx + 1))
        }
    }

    private fun encodeField(s: String) = s.replace("\\", "\\\\").replace("|", "\\|").replace("=", "\\=")
    private fun decodeField(s: String) = s.replace("\\=", "=").replace("\\|", "|").replace("\\\\", "\\")
}
