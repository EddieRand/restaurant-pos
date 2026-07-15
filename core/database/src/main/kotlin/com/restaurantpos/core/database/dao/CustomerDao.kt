package com.restaurantpos.core.database.dao

import androidx.room.*
import com.restaurantpos.core.database.entity.CustomerEntity
import com.restaurantpos.core.database.entity.LoyaltyTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {

    @Query("SELECT * FROM customers ORDER BY registeredAt DESC")
    fun observeAll(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE name LIKE '%' || :q || '%' OR phone LIKE '%' || :q || '%' ORDER BY registeredAt DESC")
    suspend fun search(q: String): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE phone = :phone LIMIT 1")
    suspend fun getByPhone(phone: String): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(customer: CustomerEntity)

    @Query("UPDATE customers SET loyaltyPoints = loyaltyPoints + :delta WHERE id = :id")
    suspend fun adjustPoints(id: String, delta: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(txn: LoyaltyTransactionEntity)

    @Query("SELECT * FROM loyalty_transactions WHERE customerId = :customerId ORDER BY createdAt DESC")
    suspend fun getTransactions(customerId: String): List<LoyaltyTransactionEntity>
}
