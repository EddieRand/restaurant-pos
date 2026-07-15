package com.restaurantpos.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.restaurantpos.core.database.converter.Converters
import com.restaurantpos.core.database.dao.*
import com.restaurantpos.core.database.entity.*

@Database(
    entities = [
        MenuItemEntity::class,
        OrderEntity::class,
        OrderItemEntity::class,
        TableEntity::class,
        PaymentEntity::class,
        SyncRecordEntity::class,
        ReservationEntity::class,
        KitchenTicketEntity::class,
        UserEntity::class,
        ModifierGroupEntity::class,
        ModifierEntity::class,
        CouponEntity::class,
        ComboEntity::class,
        ComboComponentEntity::class,
        WaiterCallEntity::class,
        MenuProfileEntity::class,
        MenuItemProfileEntity::class,
        CustomerEntity::class,
        LoyaltyTransactionEntity::class,
        DailySnapshotEntity::class,
        RoleEntity::class,
        RolePermissionEntity::class,
    ],
    version = 21,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class PosDatabase : RoomDatabase() {
    abstract fun menuItemDao(): MenuItemDao
    abstract fun orderDao(): OrderDao
    abstract fun orderItemDao(): OrderItemDao
    abstract fun tableDao(): TableDao
    abstract fun paymentDao(): PaymentDao
    abstract fun syncRecordDao(): SyncRecordDao
    abstract fun reservationDao(): ReservationDao
    abstract fun kitchenTicketDao(): KitchenTicketDao
    abstract fun userDao(): UserDao
    abstract fun modifierGroupDao(): ModifierGroupDao
    abstract fun couponDao(): CouponDao
    abstract fun comboDao(): ComboDao
    abstract fun waiterCallDao(): WaiterCallDao
    abstract fun menuProfileDao(): MenuProfileDao
    abstract fun customerDao(): CustomerDao
    abstract fun dailySnapshotDao(): DailySnapshotDao
    abstract fun roleDao(): RoleDao
    abstract fun rolePermissionDao(): RolePermissionDao
}
