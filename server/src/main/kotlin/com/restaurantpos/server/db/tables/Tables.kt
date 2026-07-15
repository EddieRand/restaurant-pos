package com.restaurantpos.server.db.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

/** Incoming sync records pushed by Android devices. */
object SyncLogTable : Table("sync_log") {
    val id = varchar("id", 64).uniqueIndex()
    val entityType = varchar("entity_type", 32)
    val entityId = varchar("entity_id", 64).index()
    val operation = varchar("operation", 16)
    val payload = text("payload")
    val updatedAt = long("updated_at")
    val retryCount = integer("retry_count").default(0)
    val status = varchar("status", 16).default("PENDING")
    val receivedAt = long("received_at")
    override val primaryKey = PrimaryKey(id)
}

object ChannelsTable : Table("channels") {
    val id        = varchar("id", 64)
    val name      = varchar("name", 128)
    val sortOrder = integer("sort_order").default(10)
    val enabled   = bool("enabled").default(true)
    val color     = varchar("color", 32).default("gray") // tailwind color name for badge
    override val primaryKey = PrimaryKey(id)
}

object MenuCategoriesTable : Table("menu_categories") {
    val id         = varchar("id", 64)
    val name       = varchar("name", 128)
    val sortOrder  = integer("sort_order").default(10)
    val activeFrom = varchar("active_from", 5).nullable()   // "HH:MM" or null = all day
    val activeTo   = varchar("active_to", 5).nullable()     // "HH:MM" or null = all day
    val daysOfWeek = varchar("days_of_week", 32).nullable() // "1,3,5" or null = every day
    override val primaryKey = PrimaryKey(id)
}

object PaymentMethodsTable : Table("payment_methods") {
    val id          = varchar("id", 64)
    val code        = varchar("code", 32).uniqueIndex()      // value stored in PaymentsTable.method, immutable after creation
    val baseType    = varchar("base_type", 16).default("OTHER") // CASH | CARD | OTHER — drives cash-drawer/accounting behavior
    val displayName = varchar("display_name", 64)            // single-language merchant-entered label
    val color       = varchar("color", 32).default("gray")   // tailwind color name for badge
    val sortOrder   = integer("sort_order").default(10)
    val isActive    = bool("is_active").default(true)
    override val primaryKey = PrimaryKey(id)
}

object MenuItemsTable : Table("menu_items") {
    val id                = varchar("id", 64)
    val names             = text("names")                      // JSON: {"en":"Burger","zh":"汉堡"}
    val priceMinorUnit    = long("price_minor_unit")
    val taxRateId         = varchar("tax_rate_id", 64).nullable()
    val categoryId        = varchar("category_id", 64)
    val course            = integer("course").default(1)
    val isSoldOut         = bool("is_sold_out").default(false)
    val imageUrl          = varchar("image_url", 512).nullable()
    val allergens         = varchar("allergens", 256).default("")          // pipe-separated
    val availableChannels = varchar("available_channels", 128).default("") // pipe-separated, "" = all
    val stockCount        = long("stock_count").nullable()                 // null = unlimited
    val updatedAt         = long("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object MenuProfilesTable : Table("menu_profiles") {
    val id         = varchar("id", 64)
    val name       = varchar("name", 128)
    val enabled    = bool("enabled").default(true)
    val startTime  = varchar("start_time", 5).nullable()    // "HH:MM" or null = all day
    val endTime    = varchar("end_time", 5).nullable()      // "HH:MM" or null = all day
    val daysOfWeek = varchar("days_of_week", 32).nullable() // "1,3,5" or null = every day
    val updatedAt  = long("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object ModifierGroupsTable : Table("modifier_groups") {
    val id            = varchar("id", 64)
    val name          = text("name")                           // JSON: {"zh":"份量","en":"Size"}
    val selectionType = varchar("selection_type", 16).default("SINGLE") // SINGLE | MULTIPLE
    val required      = bool("required").default(false)
    val minSelect     = integer("min_select").default(0)
    val maxSelect     = integer("max_select").default(1)       // 0 = unlimited
    override val primaryKey = PrimaryKey(id)
}

object ModifierOptionsTable : Table("modifier_options") {
    val id                  = varchar("id", 64)
    val groupId             = varchar("group_id", 64).references(ModifierGroupsTable.id, onDelete = ReferenceOption.CASCADE)
    val name                = text("name")                     // JSON: {"zh":"加蛋","en":"Add Egg"}
    val priceAdjustMinorUnit = long("price_adjust_minor_unit").default(0)
    val isDefault           = bool("is_default").default(false)
    val sortOrder           = integer("sort_order").default(0)
    override val primaryKey = PrimaryKey(id)
}

object MenuItemProfilesTable : Table("menu_item_profiles") {
    val menuItemId    = varchar("menu_item_id", 64).references(MenuItemsTable.id, onDelete = ReferenceOption.CASCADE)
    val menuProfileId = varchar("menu_profile_id", 64).references(MenuProfilesTable.id, onDelete = ReferenceOption.CASCADE)
    override val primaryKey = PrimaryKey(menuItemId, menuProfileId)
}

object OrdersTable : Table("orders") {
    val id = varchar("id", 64)
    val type = varchar("type", 32)
    val tableId = varchar("table_id", 64).nullable()
    val guestCount = integer("guest_count").default(1)
    val sourceTerminalId = varchar("source_terminal_id", 64)
    val operatorId = varchar("operator_id", 64).default("")
    val subtotalMinorUnit = long("subtotal_minor_unit").default(0)
    val taxTotalMinorUnit = long("tax_total_minor_unit").default(0)
    val serviceChargeMinorUnit = long("service_charge_minor_unit").default(0)
    val tipMinorUnit = long("tip_minor_unit").default(0)
    val discountMinorUnit = long("discount_minor_unit").default(0)
    val status = varchar("status", 32)
    val orderNotes = varchar("order_notes", 512).default("")
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    val pickupCode = varchar("pickup_code", 16).nullable()
    val fulfillmentStatus = varchar("fulfillment_status", 32).default("NOT_READY")
    override val primaryKey = PrimaryKey(id)
}

object KitchenTicketsTable : Table("kitchen_tickets") {
    val id = varchar("id", 64)
    val orderId = varchar("order_id", 64).index()
    val orderItemIds = text("order_item_ids") // JSON array
    val stationId = varchar("station_id", 64)
    val course = integer("course").default(1)
    val status = varchar("status", 32).default("NEW")
    val createdAt = long("created_at")
    val bumpedAt = long("bumped_at").nullable()
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object OrderItemsTable : Table("order_items") {
    val id = varchar("id", 64)
    val orderId = varchar("order_id", 64).index()
    val menuItemId = varchar("menu_item_id", 64)
    val menuItemNameSnapshot = text("menu_item_name_snapshot") // JSON map
    val quantity = integer("quantity")
    val unitPriceMinorUnit = long("unit_price_minor_unit")
    val taxRateId = varchar("tax_rate_id", 64).nullable()
    val course = integer("course").default(1)
    val status = varchar("status", 32)
    val notes = varchar("notes", 512).default("")
    val categoryId = varchar("category_id", 64).nullable()
    val allergenSnapshot = varchar("allergen_snapshot", 256).default("")
    val comboId = varchar("combo_id", 64).nullable()
    override val primaryKey = PrimaryKey(id)
}

object PaymentsTable : Table("payments") {
    val id = varchar("id", 64)
    val orderId = varchar("order_id", 64).index()
    val amountMinorUnit = long("amount_minor_unit")
    val method = varchar("method", 32)
    val status = varchar("status", 32)
    val operatorId = varchar("operator_id", 64).default("")
    val refundedPaymentId = varchar("refunded_payment_id", 64).nullable()
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object QrCodesTable : Table("qr_codes") {
    val code = varchar("code", 96)
    val scope = varchar("scope", 24) // MENU, TABLE, PICKUP, DELIVERY
    val tableId = varchar("table_id", 64).nullable()
    val enabled = bool("enabled").default(true)
    val expiresAt = long("expires_at").nullable()
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(code)
}

object QrSessionsTable : Table("qr_sessions") {
    val id = varchar("id", 64)
    val code = varchar("code", 96).index()
    val orderType = varchar("order_type", 32)
    val tableId = varchar("table_id", 64).nullable()
    val status = varchar("status", 32).default("ACTIVE")
    val customerInfoJson = text("customer_info_json").default("{}")
    val cartJson = text("cart_json").default("""{"items":[]}""")
    val orderId = varchar("order_id", 64).nullable()
    val sessionToken = varchar("session_token", 96)
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object PaymentIntentsTable : Table("payment_intents") {
    val id = varchar("id", 64)
    val sessionId = varchar("session_id", 64).nullable().index()
    val orderId = varchar("order_id", 64).nullable().index()
    val amountMinorUnit = long("amount_minor_unit")
    val method = varchar("method", 32).default("QR_CODE")
    val status = varchar("status", 32).default("REQUIRES_CONFIRMATION")
    val provider = varchar("provider", 32).default("MOCK")
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object UsersTable : Table("pos_users") {
    val id = varchar("id", 64)
    val displayName = varchar("display_name", 128)
    val role = varchar("role", 32)
    val pinHash = varchar("pin_hash", 128)
    val isActive = bool("is_active").default(true)
    val createdAt = long("created_at")
    val hourlyWageMinorUnit = long("hourly_wage_minor_unit").default(0)
    override val primaryKey = PrimaryKey(id)
}

/** Employee shift schedules — for labor cost estimation (scheduled vs actual hours). */
object ShiftSchedulesTable : Table("shift_schedules") {
    val id = varchar("id", 64)
    val operatorId = varchar("operator_id", 64).index()
    val date = varchar("date", 10) // "YYYY-MM-DD"
    val startTime = varchar("start_time", 5) // "HH:mm"
    val endTime = varchar("end_time", 5) // "HH:mm"
    val notes = varchar("notes", 256).default("")
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(id)
}

/** Web-only admin accounts with email+password login. */
object AdminUsersTable : Table("admin_users") {
    val id = varchar("id", 64)
    val email = varchar("email", 256).uniqueIndex()
    val passwordHash = varchar("password_hash", 128)
    val role = varchar("role", 32).default("manager")
    val isActive = bool("is_active").default(true)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object TablesTable : Table("floor_tables") {
    val id = varchar("id", 64)
    val name = varchar("name", 64)
    val sectionId = varchar("section_id", 64)
    val capacity = integer("capacity").default(4)
    val currentOrderId = varchar("current_order_id", 64).nullable()
    val status = varchar("status", 32).default("AVAILABLE")
    val shape = varchar("shape", 16).default("square")
    val x = integer("x").default(0)
    val y = integer("y").default(0)
    val w = integer("w").default(1)
    val h = integer("h").default(1)
    val updatedAt = long("updated_at").default(0L)
    override val primaryKey = PrimaryKey(id)
}

/**
 * Latest Customer Display phase per terminal, pushed by the cashier (CDS_STATE sync entity).
 * Drives the customer-facing display via GET /public/cds/state. Last-write-wins by updatedAt.
 */
object CdsStateTable : Table("cds_state") {
    val id = varchar("id", 64)              // terminalId — one CDS state per terminal/display
    val orderId = varchar("order_id", 64).nullable()
    val phase = varchar("phase", 16).default("WELCOME")
    val updatedAt = long("updated_at").default(0L)
    override val primaryKey = PrimaryKey(id)
}

object FloorSectionsTable : Table("floor_sections") {
    val id = varchar("id", 64)
    val name = varchar("name", 64)
    val sortOrder = integer("sort_order").default(0)
    override val primaryKey = PrimaryKey(id)
}

object CouponsTable : Table("coupons") {
    val id = varchar("id", 64)
    val code = varchar("code", 64).uniqueIndex()
    val type = varchar("type", 16).default("FIXED")
    val value = long("value").default(0)
    val expiresAt = long("expires_at")
    val isActive = bool("is_active").default(true)
    override val primaryKey = PrimaryKey(id)
}

object GiftCardsTable : Table("gift_cards") {
    val id = varchar("id", 64)
    val code = varchar("code", 32).uniqueIndex()
    val balanceMinorUnit = long("balance_minor_unit").default(0)
    val customerId = varchar("customer_id", 64).nullable()
    val isActive = bool("is_active").default(true)
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object GiftCardTransactionsTable : Table("gift_card_transactions") {
    val id = varchar("id", 64)
    val giftCardId = varchar("gift_card_id", 64).index()
    val type = varchar("type", 16) // ISSUE / TOPUP / REDEEM / ADJUST
    val amountMinorUnit = long("amount_minor_unit") // positive = credit, negative = debit
    val orderId = varchar("order_id", 64).nullable()
    val operatorId = varchar("operator_id", 64).default("")
    val note = text("note").default("")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object CombosTable : Table("combos") {
    val id = varchar("id", 64)
    val names = text("names")                       // JSON map
    val comboPriceMinorUnit = long("combo_price_minor_unit").default(0)
    val taxRateId = varchar("tax_rate_id", 64).nullable()
    val isActive = bool("is_active").default(true)
    override val primaryKey = PrimaryKey(id)
}

object ComboComponentsTable : Table("combo_components") {
    val id = varchar("id", 64)
    val comboId = varchar("combo_id", 64).index()
    val menuItemId = varchar("menu_item_id", 64)
    val quantity = integer("quantity").default(1)
    val sortOrder = integer("sort_order").default(0)
    override val primaryKey = PrimaryKey(id)
}

/** Stores the single RegionConfig JSON blob per terminal. */
object SettingsTable : Table("settings") {
    val key = varchar("key", 64)
    val value = text("value")                       // JSON blob
    override val primaryKey = PrimaryKey(key)
}

// ── Controlled AI write plane ───────────────────────────────────────────────

object AiPriceProposalsTable : Table("ai_price_proposals") {
    val id = varchar("id", 64)
    val actorId = varchar("actor_id", 64)
    val instruction = varchar("instruction", 512)
    val locale = varchar("locale", 32)
    val status = varchar("status", 24)
    val tool = varchar("tool", 64)
    val currencyCode = varchar("currency_code", 8)
    val minorUnitDigits = integer("minor_unit_digits")
    val createdAt = long("created_at")
    val expiresAt = long("expires_at").index()
    val executedAt = long("executed_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object AiPriceProposalChangesTable : Table("ai_price_proposal_changes") {
    val proposalId = varchar("proposal_id", 64).index()
    val itemId = varchar("item_id", 64)
    val itemName = varchar("item_name", 256)
    val oldPriceMinorUnit = long("old_price_minor_unit")
    val newPriceMinorUnit = long("new_price_minor_unit")
    val deltaMinorUnit = long("delta_minor_unit")
    val deltaPercentBasisPoints = long("delta_percent_basis_points").nullable()
    val expectedUpdatedAt = long("expected_updated_at")
    override val primaryKey = PrimaryKey(proposalId, itemId)
}

object AiMutationAuditsTable : Table("ai_mutation_audits") {
    val id = varchar("id", 64)
    val actorId = varchar("actor_id", 64)
    val proposalId = varchar("proposal_id", 64).uniqueIndex()
    val tool = varchar("tool", 64)
    val entityType = varchar("entity_type", 32)
    val entityId = varchar("entity_id", 64)
    val beforeMinorUnit = long("before_minor_unit")
    val afterMinorUnit = long("after_minor_unit")
    val idempotencyKey = varchar("idempotency_key", 128).uniqueIndex()
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

// ── CRM ───────────────────────────────────────────────────────────────────────

object CustomersTable : Table("customers") {
    val id = varchar("id", 64)
    val name = varchar("name", 128)
    val phone = varchar("phone", 32).index()
    val email = varchar("email", 128).nullable()
    val gender = varchar("gender", 8).nullable()       // M / F / OTHER
    val birthday = varchar("birthday", 5).nullable()   // MM-DD
    val tags = text("tags").default("")                // pipe-separated
    val notes = text("notes").nullable()
    val totalSpendMinorUnit = long("total_spend_minor_unit").default(0L)
    val loyaltyPoints = long("loyalty_points").default(0L)
    val membershipTierId = varchar("membership_tier_id", 64).nullable()
    val totalVisits = integer("total_visits").default(0)
    val lastVisitAt = long("last_visit_at").default(0L)
    val registeredAt = long("registered_at")
    val updatedAt = long("updated_at").default(0L)
    override val primaryKey = PrimaryKey(id)
}

object LoyaltyTransactionsTable : Table("loyalty_transactions") {
    val id = varchar("id", 64)
    val customerId = varchar("customer_id", 64).index()
    val orderId = varchar("order_id", 64).nullable()
    val type = varchar("type", 16)                     // EARN / REDEEM / ADJUST / EXPIRE
    val points = long("points")
    val description = text("description").default("")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object MembershipTiersTable : Table("membership_tiers") {
    val id = varchar("id", 64)
    val name = varchar("name", 64)
    val minPoints = long("min_points").default(0L)
    val benefits = text("benefits").default("")        // pipe-separated
    val color = varchar("color", 32).default("gray")
    val discountPermille = long("discount_permille").default(0L)
    val pointsMultiplier = long("points_multiplier").default(100L) // 100 = 1x
    val sortOrder = integer("sort_order").default(0)
    override val primaryKey = PrimaryKey(id)
}

object CampaignsTable : Table("campaigns") {
    val id = varchar("id", 64)
    val name = varchar("name", 128)
    val type = varchar("type", 32)                    // COUPON_PUSH / IN_APP_NOTICE
    val targetSegment = varchar("target_segment", 32).default("ALL")
    val targetTierId = varchar("target_tier_id", 64).nullable()
    val couponId = varchar("coupon_id", 64).nullable()
    val message = text("message").default("")
    val scheduledAt = long("scheduled_at").nullable()
    val status = varchar("status", 16).default("DRAFT")
    val sentCount = integer("sent_count").default(0)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

// ── Inventory ─────────────────────────────────────────────────────────────────

object IngredientsTable : Table("ingredients") {
    val id = varchar("id", 64)
    val name = varchar("name", 128)
    val category = varchar("category", 64).default("")
    val unit = varchar("unit", 32)
    val purchaseUnit = varchar("purchase_unit", 32).nullable()
    val purchaseUnitFactor = double("purchase_unit_factor").nullable()
    val safetyStock = double("safety_stock").default(0.0)
    val currentStock = double("current_stock").default(0.0)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object StockMovementsTable : Table("stock_movements") {
    val id = varchar("id", 64)
    val ingredientId = varchar("ingredient_id", 64).index()
    val type = varchar("type", 16)         // IN / OUT / ADJUST
    val qty = double("qty")
    val note = text("note").default("")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object SuppliersTable : Table("suppliers") {
    val id = varchar("id", 64)
    val name = varchar("name", 128)
    val contact = varchar("contact", 128).default("")
    val phone = varchar("phone", 32).default("")
    val note = text("note").default("")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object PurchaseOrdersTable : Table("purchase_orders") {
    val id = varchar("id", 64)
    val status = varchar("status", 16).default("DRAFT")
    val supplier = varchar("supplier", 128).default("")
    val supplierId = varchar("supplier_id", 64).nullable()
    val note = text("note").default("")
    val createdAt = long("created_at")
    val confirmedAt = long("confirmed_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object PurchaseOrderItemsTable : Table("purchase_order_items") {
    val id = varchar("id", 64)
    val orderId = varchar("order_id", 64).index()
    val ingredientId = varchar("ingredient_id", 64)
    val qty = double("qty")
    val unitCost = long("unit_cost").default(0L)
    override val primaryKey = PrimaryKey(id)
}

object OutboundOrdersTable : Table("outbound_orders") {
    val id = varchar("id", 64)
    val status = varchar("status", 16).default("DRAFT")
    val type = varchar("type", 16).default("ISSUE") // ISSUE / WASTE / GIFT / OTHER
    val note = text("note").default("")
    val createdAt = long("created_at")
    val confirmedAt = long("confirmed_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object OutboundOrderItemsTable : Table("outbound_order_items") {
    val id = varchar("id", 64)
    val orderId = varchar("order_id", 64).index()
    val ingredientId = varchar("ingredient_id", 64)
    val qty = double("qty")
    override val primaryKey = PrimaryKey(id)
}

object BomLinesTable : Table("bom_lines") {
    val id = varchar("id", 64)
    val menuItemId = varchar("menu_item_id", 64).index()
    val ingredientId = varchar("ingredient_id", 64)
    val qty = double("qty")
    override val primaryKey = PrimaryKey(id)
}

object StocktakeOrdersTable : Table("stocktake_orders") {
    val id = varchar("id", 64)
    val status = varchar("status", 16).default("DRAFT")
    val note = text("note").default("")
    val createdAt = long("created_at")
    val submittedAt = long("submitted_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object StocktakeItemsTable : Table("stocktake_items") {
    val id = varchar("id", 64)
    val orderId = varchar("order_id", 64).index()
    val ingredientId = varchar("ingredient_id", 64)
    val systemQty = double("system_qty").default(0.0)
    val actualQty = double("actual_qty").default(0.0)
    override val primaryKey = PrimaryKey(id)
}

// ── Reservations & Waitlist ───────────────────────────────────────────────────

object ReservationsTable : Table("reservations") {
    val id = varchar("id", 64)
    val customerName = varchar("customer_name", 128)
    val phone = varchar("phone", 32)
    val customerId = varchar("customer_id", 64).nullable()
    val partySize = integer("party_size").default(2)
    val date = varchar("date", 10)        // "YYYY-MM-DD"
    val time = varchar("time", 5)         // "HH:MM"
    val tableId = varchar("table_id", 64).nullable()
    val status = varchar("status", 16).default("PENDING")
    val notes = text("notes").nullable()
    val internalNotes = text("internal_notes").nullable()
    val shiftId = varchar("shift_id", 64).nullable()
    val bookingSource = varchar("source", 16).default("ONLINE")
    val guestTags = varchar("guest_tags", 128).default("")   // pipe-separated
    val confirmationCode = varchar("confirmation_code", 32).nullable()
    val estimatedDurationMinutes = integer("estimated_duration_minutes").nullable()
    val depositAmount = long("deposit_amount").nullable()
    val depositPaid = bool("deposit_paid").default(false)
    val reminderSentAt = long("reminder_sent_at").nullable()
    val seatedAt = long("seated_at").nullable()
    val completedAt = long("completed_at").nullable()
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object WaitlistTable : Table("waitlist") {
    val id = varchar("id", 64)
    val customerName = varchar("customer_name", 128)
    val phone = varchar("phone", 32)
    val customerId = varchar("customer_id", 64).nullable()
    val partySize = integer("party_size").default(2)
    val requestedAt = long("requested_at")
    val quotedWaitMinutes = integer("quoted_wait_minutes").default(0)
    val status = varchar("status", 16).default("WAITING")
    val notifiedAt = long("notified_at").nullable()
    val expiresAt = long("expires_at").nullable()
    val tableId = varchar("table_id", 64).nullable()
    val preferences = varchar("preferences", 128).default("") // pipe-separated
    val seatedAt = long("seated_at").nullable()
    val notes = text("notes").nullable()
    override val primaryKey = PrimaryKey(id)
}

object ShiftsTable : Table("shifts") {
    val id = varchar("id", 64)
    val name = varchar("name", 128)
    val startTime = varchar("start_time", 5)
    val endTime = varchar("end_time", 5)
    val daysOfWeek = varchar("days_of_week", 32).default("") // comma-separated, "" = every day
    val maxCovers = integer("max_covers").default(100)
    val coverGoal = integer("cover_goal").default(80)
    val defaultTurnTimeMinutes = integer("default_turn_time_minutes").default(90)
    val slotIntervalMinutes = integer("slot_interval_minutes").default(30)
    val bookingCutoffMinutes = integer("booking_cutoff_minutes").default(60)
    val enabled = bool("enabled").default(true)
    override val primaryKey = PrimaryKey(id)
}

object SpecialDaysTable : Table("special_days") {
    val id = varchar("id", 64)
    val date = varchar("date", 10).uniqueIndex() // "YYYY-MM-DD"
    val label = varchar("label", 128)
    val maxCoversOverride = integer("max_covers_override").nullable()
    val closed = bool("closed").default(false)
    override val primaryKey = PrimaryKey(id)
}

/** Tableside PAD waiter-call requests. */
object WaiterCallsTable : Table("waiter_calls") {
    val id = varchar("id", 64)
    val tableId = varchar("table_id", 64).index()
    val tableDisplayName = varchar("table_display_name", 128)
    val reason = varchar("reason", 32).default("GENERAL")
    val createdAt = long("created_at")
    val acknowledgedAt = long("acknowledged_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

/* ── RBAC: roles & role_permissions (Batch 46) ────────────────── */

object RolesTable : Table("roles") {
    val id           = varchar("id", 32).uniqueIndex()   // "admin" / "manager" / ...
    val displayName  = varchar("display_name", 128)
    val isBuiltin   = bool("is_builtin").default(true)
    val sortOrder    = integer("sort_order").default(0)
    override val primaryKey = PrimaryKey(id)
}

object RolePermissionsTable : Table("role_permissions") {
    val roleId       = varchar("role_id", 32).index()
    val permissionKey = varchar("permission_key", 64).index()
    override val primaryKey = PrimaryKey(roleId, permissionKey)
}

// ── Modifier selections on order items ─────────────────────────────────

object OrderItemModifiersTable : Table("order_item_modifiers") {
    val id                        = varchar("id", 64)
    val orderItemId               = varchar("order_item_id", 64).index()
    val modifierGroupId           = varchar("modifier_group_id", 64)
    val modifierGroupNameSnapshot = text("modifier_group_name_snapshot")  // JSON map
    val optionId                  = varchar("option_id", 64)
    val optionNameSnapshot        = text("option_name_snapshot")          // JSON map
    val priceAdjustMinorUnit      = long("price_adjust_minor_unit").default(0)
    override val primaryKey = PrimaryKey(id)
}

// ── Cashier shifts (till sessions) ─────────────────────────────────────

object CashierShiftsTable : Table("cashier_shifts") {
    val id                          = varchar("id", 64)
    val shiftNumber                 = integer("shift_number").default(0)  // auto-increment sequence per terminal
    val terminalId                  = varchar("terminal_id", 64).index()
    val terminalName                = varchar("terminal_name", 128).default("")
    val storeName                   = varchar("store_name", 128).default("")
    val openedByOperatorId          = varchar("opened_by_operator_id", 64)
    val openedByName                = varchar("opened_by_name", 128).default("")
    val closedByOperatorId          = varchar("closed_by_operator_id", 64).nullable()
    val closedByName                = varchar("closed_by_name", 128).default("")
    val openedAt                    = long("opened_at")
    val closedAt                    = long("closed_at").nullable()
    val openingCashMinorUnit        = long("opening_cash_minor_unit").default(0)
    val actualClosingCashMinorUnit  = long("actual_closing_cash_minor_unit").nullable()
    val notes                       = varchar("notes", 512).default("")
    override val primaryKey = PrimaryKey(id)
}

// Cash movements (money in/out) during a shift
object CashMovementsTable : Table("cash_movements") {
    val id                = varchar("id", 64)
    val shiftId           = varchar("shift_id", 64).index()
    val type              = varchar("type", 8)   // "IN" | "OUT"
    val amountMinorUnit   = long("amount_minor_unit")
    val description       = varchar("description", 256).default("")
    val operatorId        = varchar("operator_id", 64)
    val operatorName      = varchar("operator_name", 128).default("")
    val createdAt         = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

// ── Daily Snapshots (pre-computed report data) ──────────────────────────

object DailySnapshotsTable : Table("daily_snapshots") {
    val date = varchar("date", 10).uniqueIndex()   // "YYYY-MM-DD"
    val grossRevenueMinorUnit = long("gross_revenue_minor_unit").default(0)
    val netRevenueMinorUnit   = long("net_revenue_minor_unit").default(0)
    val totalRefundsMinorUnit = long("total_refunds_minor_unit").default(0)
    val orderCount            = integer("order_count").default(0)
    val guestCount            = integer("guest_count").default(0)
    val totalDiscountMinorUnit       = long("total_discount_minor_unit").default(0)
    val totalTipMinorUnit           = long("total_tip_minor_unit").default(0)
    val totalServiceChargeMinorUnit = long("total_service_charge_minor_unit").default(0)
    val totalTaxMinorUnit          = long("total_tax_minor_unit").default(0)
    val paymentMethodBreakdown     = text("payment_method_breakdown").default("{}")  // JSON: {"CASH":1234,...}
    val topItemsJson               = text("top_items_json").default("[]")          // JSON array of {menuItemId,name,qty,revenue}
    val computedAt                = long("computed_at")
    override val primaryKey = PrimaryKey(date)
}

// ── Employee Timecards ────────────────────────────────────────────────────

object EmployeeTimecardsTable : Table("employee_timecards") {
    val id           = varchar("id", 64)
    val operatorId   = varchar("operator_id", 64).index()
    val operatorName = varchar("operator_name", 128).default("")
    val terminalId   = varchar("terminal_id", 64).default("")
    val clockInAt    = long("clock_in_at")
    val clockOutAt   = long("clock_out_at").nullable()   // null = currently clocked in
    val clockInNote  = varchar("clock_in_note", 256).default("")
    val clockOutNote = varchar("clock_out_note", 256).default("")
    val createdAt    = long("created_at")
    val updatedAt    = long("updated_at")
    override val primaryKey = PrimaryKey(id)
}
