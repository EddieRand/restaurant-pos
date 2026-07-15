package com.restaurantpos.core.database

import com.restaurantpos.core.database.dao.ComboDao
import com.restaurantpos.core.database.dao.CouponDao
import com.restaurantpos.core.database.dao.CustomerDao
import com.restaurantpos.core.database.dao.MenuItemDao
import com.restaurantpos.core.database.dao.ModifierGroupDao
import com.restaurantpos.core.database.dao.TableDao
import com.restaurantpos.core.database.dao.UserDao
import com.restaurantpos.core.database.entity.ComboComponentEntity
import com.restaurantpos.core.database.entity.ComboEntity
import com.restaurantpos.core.database.entity.CouponEntity
import com.restaurantpos.core.database.entity.CustomerEntity
import com.restaurantpos.core.database.entity.LoyaltyTransactionEntity
import com.restaurantpos.core.database.entity.MenuItemEntity
import com.restaurantpos.core.database.entity.ModifierEntity
import com.restaurantpos.core.database.entity.ModifierGroupEntity
import com.restaurantpos.core.database.entity.TableEntity
import com.restaurantpos.core.database.entity.UserEntity
import com.restaurantpos.core.domain.usecase.sha256
import com.restaurantpos.core.model.TableStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Seeds demo tables and menu items on first launch if the DB is empty. */
@Singleton
class DataSeeder @Inject constructor(
    private val tableDao: TableDao,
    private val menuItemDao: MenuItemDao,
    private val userDao: UserDao,
    private val modifierGroupDao: ModifierGroupDao,
    private val couponDao: CouponDao,
    private val comboDao: ComboDao,
    private val customerDao: CustomerDao,
) {
    fun seedIfEmpty() {
        CoroutineScope(Dispatchers.IO).launch {
            seedTables()
            seedMenuItems()
            seedUsers()
            seedModifierGroups()
            seedCoupons()
            seedCombos()
            seedCustomers()
        }
    }

    private suspend fun seedTables() {
        val existing = tableDao.observeAll().first()
        if (existing.isNotEmpty()) return
        val tables = (1..12).map { n ->
            TableEntity(
                id = "table-$n",
                name = "T$n",
                sectionId = if (n <= 6) "indoor" else "outdoor",
                capacity = if (n % 3 == 0) 6 else 4,
                currentOrderId = null,
                status = TableStatus.AVAILABLE,
            )
        }
        tableDao.upsertAll(tables)
    }

    private suspend fun seedMenuItems() {
        val existing = menuItemDao.observeAll().first()
        if (existing.isNotEmpty()) return
        // "Morning Cafe" menu. Categories match the QSR POS category tabs (Coffee/Tea/
        // Milk Tea/Food/Bakery/Desserts/Combo/Retail/Seasonal) — see POS_CATEGORY_TABS in
        // feature:order's OrderScreen.kt. These two lists previously drifted apart (this
        // seeder used old meal-part categories like cat-breakfast/cat-lunch/cat-dinner/
        // cat-drinks), so every QSR tab except Favorites/Desserts showed "no items" — found
        // via real-device regression testing. IDs mi-001 (burger) and mi-004 (salmon) are
        // kept because modifier groups attach to them; mi-011/013/021/022/031/032 are kept
        // because combos reference them.
        val items = listOf(
            // Food
            item("mi-101", mapOf("en" to "Avocado Toast",        "zh" to "牛油果吐司"),  1250L, "cat-food",     1),
            item("mi-102", mapOf("en" to "Acai Bowl",            "zh" to "巴西莓碗"),    1050L, "cat-food",     1),
            item("mi-001", mapOf("en" to "Classic Cheeseburger", "zh" to "经典芝士汉堡"), 1590L, "cat-food",     1),
            item("mi-103", mapOf("en" to "Kale & Quinoa Salad",  "zh" to "羽衣藜麦沙拉"), 1340L, "cat-food",     1),
            item("mi-104", mapOf("en" to "Spicy Chicken Wings",  "zh" to "香辣鸡翅"),    1190L, "cat-food",     1),
            item("mi-013", mapOf("en" to "Spring Rolls",         "zh" to "春卷"),        560L,  "cat-food",     0),
            item("mi-011", mapOf("en" to "French Fries",         "zh" to "薯条"),        500L,  "cat-food",     0),
            item("mi-004", mapOf("en" to "Grilled Salmon",       "zh" to "香煎三文鱼"),  2290L, "cat-food",     1),
            item("mi-105", mapOf("en" to "Truffle Pasta",        "zh" to "松露意面"),    1850L, "cat-food",     1),
            // Coffee
            item("mi-106", mapOf("en" to "Flat White",          "zh" to "馥芮白"),      420L,  "cat-coffee",   2),
            // Tea
            item("mi-021", mapOf("en" to "Cola",                "zh" to "可乐"),        250L,  "cat-tea",      2),
            item("mi-022", mapOf("en" to "Lemonade",            "zh" to "柠檬水"),       280L,  "cat-tea",      2),
            item("mi-023", mapOf("en" to "Iced Tea",            "zh" to "冰红茶"),       300L,  "cat-tea",      2),
            // Desserts
            item("mi-031", mapOf("en" to "Chocolate Cake",      "zh" to "巧克力蛋糕"),  680L,  "cat-desserts",  3),
            item("mi-032", mapOf("en" to "Ice Cream",           "zh" to "冰淇淋"),      450L,  "cat-desserts",  3),
        )
        menuItemDao.upsertAll(items)
    }

    private suspend fun seedUsers() {
        if (userDao.getById("user-admin") != null) return
        val now = System.currentTimeMillis()
        val defaultUsers = listOf(
            UserEntity("user-admin",   "Admin",     "admin",   "0000".sha256(), true, now),
            UserEntity("user-manager", "Manager",   "manager", "1111".sha256(), true, now),
            UserEntity("user-cashier", "Cashier 1", "cashier", "2222".sha256(), true, now),
            UserEntity("user-waiter",  "Waiter 1",  "waiter",  "3333".sha256(), true, now),
        )
        defaultUsers.forEach { userDao.upsert(it) }
    }

    private suspend fun seedModifierGroups() {
        // Skip if already seeded (check for the Cheeseburger cook-level group)
        if (modifierGroupDao.getByMenuItem("mi-001").isNotEmpty()) return

        // mi-001 Cheeseburger: cook level (required single) + extras (optional multi)
        val cheeseburgerGroups = listOf(
            ModifierGroupEntity("mg-cook", "mi-001", mapOf("en" to "Cook Level", "zh" to "熟度"), "SINGLE", true, 1, 1, 0),
            ModifierGroupEntity("mg-extra", "mi-001", mapOf("en" to "Extras", "zh" to "加料"), "MULTI", false, 0, 3, 1),
        )
        val cheeseburgerMods = listOf(
            ModifierEntity("m-rare",    "mg-cook",  mapOf("en" to "Rare",         "zh" to "三分熟"), 0L,   0),
            ModifierEntity("m-med",     "mg-cook",  mapOf("en" to "Medium",       "zh" to "五分熟"), 0L,   1),
            ModifierEntity("m-well",    "mg-cook",  mapOf("en" to "Well Done",    "zh" to "全熟"),   0L,   2),
            ModifierEntity("m-cheese",  "mg-extra", mapOf("en" to "Extra Cheese", "zh" to "加芝士"), 150L, 0),
            ModifierEntity("m-bacon",   "mg-extra", mapOf("en" to "Bacon",        "zh" to "培根"),   200L, 1),
            ModifierEntity("m-avocado", "mg-extra", mapOf("en" to "Avocado",      "zh" to "牛油果"), 180L, 2),
        )

        // mi-004 Grilled Salmon: sauce selection (optional single)
        val salmonGroups = listOf(
            ModifierGroupEntity("mg-sauce", "mi-004", mapOf("en" to "Sauce", "zh" to "配酱"), "SINGLE", false, 0, 1, 0),
        )
        val salmonMods = listOf(
            ModifierEntity("m-lemon",    "mg-sauce", mapOf("en" to "Lemon Butter", "zh" to "柠檬黄油"), 0L, 0),
            ModifierEntity("m-teriyaki", "mg-sauce", mapOf("en" to "Teriyaki",     "zh" to "照烧汁"),   0L, 1),
            ModifierEntity("m-garlic",   "mg-sauce", mapOf("en" to "Garlic Herb",  "zh" to "蒜香草药"), 0L, 2),
        )

        modifierGroupDao.upsertGroups(cheeseburgerGroups + salmonGroups)
        modifierGroupDao.upsertModifiers(cheeseburgerMods + salmonMods)
    }

    private suspend fun seedCoupons() {
        if (couponDao.getByCode("WELCOME10") != null) return
        val farFuture = 9_999_999_999_000L // year ~2286, effectively never expires
        listOf(
            CouponEntity("cpn-001", "WELCOME10", "PERCENT", 10L, farFuture),
            CouponEntity("cpn-002", "FLAT50",    "FIXED",  50L, farFuture),
        ).forEach { couponDao.upsert(it) }
    }

    private suspend fun seedCombos() {
        if (comboDao.getById("combo-lunch-a") != null) return
        // Lunch Set A: Cheeseburger + Fries + Cola = 1900 (saving ~300 vs individual)
        comboDao.upsertFull(
            ComboEntity("combo-lunch-a", mapOf("en" to "Lunch Set A", "zh" to "午市套餐A"), 1900L),
            listOf(
                ComboComponentEntity("cc-la-1", "combo-lunch-a", "mi-001", 1, 0), // Cheeseburger
                ComboComponentEntity("cc-la-2", "combo-lunch-a", "mi-011", 1, 1), // French Fries
                ComboComponentEntity("cc-la-3", "combo-lunch-a", "mi-021", 1, 2), // Cola
            ),
        )
        // Lunch Set B: Grilled Salmon + Spring Rolls + Lemonade = 2900 (saving ~360)
        comboDao.upsertFull(
            ComboEntity("combo-lunch-b", mapOf("en" to "Lunch Set B", "zh" to "午市套餐B"), 2900L),
            listOf(
                ComboComponentEntity("cc-lb-1", "combo-lunch-b", "mi-004", 1, 0), // Grilled Salmon
                ComboComponentEntity("cc-lb-2", "combo-lunch-b", "mi-013", 1, 1), // Spring Rolls
                ComboComponentEntity("cc-lb-3", "combo-lunch-b", "mi-022", 1, 2), // Lemonade
            ),
        )
        // Dessert Duo: Chocolate Cake + Ice Cream = 1000 (saving ~130)
        comboDao.upsertFull(
            ComboEntity("combo-dessert-duo", mapOf("en" to "Dessert Duo", "zh" to "甜品双拼"), 1000L),
            listOf(
                ComboComponentEntity("cc-dd-1", "combo-dessert-duo", "mi-031", 1, 0), // Chocolate Cake
                ComboComponentEntity("cc-dd-2", "combo-dessert-duo", "mi-032", 1, 1), // Ice Cream
            ),
        )
    }

    private suspend fun seedCustomers() {
        if (customerDao.observeAll().first().isNotEmpty()) return
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        val customers = listOf(
            CustomerEntity(
                id = "cust-001", name = "Olivia Bennett", phone = "+1 415 555 0142",
                email = "olivia.b@example.com", birthday = "03-14",
                tags = "VIP|Regular", notes = "Prefers oat milk. Allergic to peanuts.",
                totalSpendMinorUnit = 84230L, loyaltyPoints = 1240, membershipTierId = "tier-gold",
                totalVisits = 37, lastVisitAt = now - 2 * day, registeredAt = now - 400 * day, updatedAt = now,
            ),
            CustomerEntity(
                id = "cust-002", name = "Liam Carter", phone = "+1 415 555 0177",
                email = "liam.carter@example.com",
                tags = "Regular", notes = null,
                totalSpendMinorUnit = 31200L, loyaltyPoints = 480, membershipTierId = "tier-silver",
                totalVisits = 14, lastVisitAt = now - 6 * day, registeredAt = now - 180 * day, updatedAt = now,
            ),
            CustomerEntity(
                id = "cust-003", name = "Sophia Nguyen", phone = "+1 415 555 0193",
                email = null, birthday = "11-02",
                tags = "New", notes = "Met at the launch event.",
                totalSpendMinorUnit = 4600L, loyaltyPoints = 60, membershipTierId = null,
                totalVisits = 2, lastVisitAt = now - 1 * day, registeredAt = now - 12 * day, updatedAt = now,
            ),
            CustomerEntity(
                id = "cust-004", name = "Noah Williams", phone = "+1 415 555 0210",
                email = "noah.w@example.com",
                tags = "VIP", notes = null,
                totalSpendMinorUnit = 152900L, loyaltyPoints = 2310, membershipTierId = "tier-gold",
                totalVisits = 68, lastVisitAt = now - 12 * 3_600_000L, registeredAt = now - 720 * day, updatedAt = now,
            ),
        )
        customers.forEach { customerDao.upsert(it) }
        listOf(
            LoyaltyTransactionEntity("ltx-001", "cust-001", "ord-demo-1", "EARN", 120, "Earned on dine-in order", now - 2 * day),
            LoyaltyTransactionEntity("ltx-002", "cust-001", null, "REDEEM", -200, "Redeemed for free dessert", now - 30 * day),
            LoyaltyTransactionEntity("ltx-003", "cust-001", "ord-demo-0", "EARN", 90, "Earned on takeaway order", now - 45 * day),
            LoyaltyTransactionEntity("ltx-004", "cust-004", "ord-demo-2", "EARN", 240, "Earned on dine-in order", now - 12 * 3_600_000L),
        ).forEach { customerDao.insertTransaction(it) }
    }

    private fun item(
        id: String,
        names: Map<String, String>,
        price: Long,
        category: String,
        course: Int,
    ) = MenuItemEntity(
        id = id, names = names, priceMinorUnit = price,
        taxRateId = null, categoryId = category, course = course,
        isSoldOut = false, imageUrl = null,
    )
}
