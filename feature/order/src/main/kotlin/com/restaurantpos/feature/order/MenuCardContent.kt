package com.restaurantpos.feature.order

/**
 * TEMP presentation content for the POS menu cards — description, badges, an emoji used as a
 * self-generated "photo" stand-in (no bundled image assets / no network), and favorite/star
 * flags driving the Favorites tab and the card star. The `MenuItem` domain model has no
 * description/tags/image yet; these move to real model fields when the Menu-management screen
 * gets proper data. Keyed by the item's English name; unknown items fall back to a generic line.
 *
 * Badge note (matches mockup): POPULAR & VEGAN render as green bottom badges; SPICY renders as a
 * red 🔥 overlay on the image (not a bottom badge).
 */
enum class MenuBadge { POPULAR, VEGAN, SPICY }

data class MenuCardExtra(
    val description: String,
    val badges: List<MenuBadge>,
    val emoji: String,
    val favorite: Boolean = false,
    val starred: Boolean = false,
)

object MenuCardContent {
    private val byName: Map<String, MenuCardExtra> = mapOf(
        "avocado toast" to MenuCardExtra("Sourdough, avocado, chili flakes, poached egg", listOf(MenuBadge.POPULAR), "🥑", favorite = true),
        "classic cheeseburger" to MenuCardExtra("Beef patty, cheddar, lettuce, tomato, pickles, onions", listOf(MenuBadge.POPULAR), "🍔", favorite = true, starred = true),
        "truffle pasta" to MenuCardExtra("Tagliatelle, mushroom, parmesan, truffle oil", emptyList(), "🍝", favorite = true),
        "kale & quinoa salad" to MenuCardExtra("Kale, quinoa, roasted squash, feta, almonds", listOf(MenuBadge.VEGAN), "🥗", favorite = true),
        "grilled salmon" to MenuCardExtra("Salmon, lemon herb butter, seasonal greens", listOf(MenuBadge.POPULAR), "🐟", favorite = true),
        "spicy chicken wings" to MenuCardExtra("House hot sauce, ranch, celery sticks", listOf(MenuBadge.SPICY), "🍗", favorite = true, starred = true),
        "acai bowl" to MenuCardExtra("Acai, banana, berries, granola, honey", listOf(MenuBadge.VEGAN), "🍓", favorite = true),
        "flat white" to MenuCardExtra("Double shot espresso with steamed milk", emptyList(), "☕", favorite = true),
        // Supporting items (non-favorite)
        "spring rolls" to MenuCardExtra("Crispy veggie rolls, sweet chili dip", emptyList(), "🥟"),
        "french fries" to MenuCardExtra("Hand-cut, sea salt, garlic aioli", emptyList(), "🍟"),
        "cola" to MenuCardExtra("Chilled classic cola", emptyList(), "🥤"),
        "lemonade" to MenuCardExtra("Fresh-squeezed, lightly sweet", emptyList(), "🍋"),
        "iced tea" to MenuCardExtra("House-brewed black tea over ice", emptyList(), "🧋"),
        "chocolate cake" to MenuCardExtra("Rich dark chocolate, ganache", emptyList(), "🍰"),
        "ice cream" to MenuCardExtra("Two scoops, vanilla or chocolate", emptyList(), "🍨"),
    )

    private val fallbackDescriptions = listOf(
        "Freshly prepared in-house",
        "Chef's daily selection",
        "Served with a side of greens",
        "Made to order",
    )
    private val fallbackEmojis = listOf("🍽️", "🥘", "🍴")

    fun forName(name: String): MenuCardExtra {
        val key = name.trim().lowercase()
        byName[key]?.let { return it }
        val h = name.hashCode() and Int.MAX_VALUE
        return MenuCardExtra(
            description = fallbackDescriptions[h % fallbackDescriptions.size],
            badges = emptyList(),
            emoji = fallbackEmojis[h % fallbackEmojis.size],
        )
    }
}
