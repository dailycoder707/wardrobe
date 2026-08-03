package com.wardrobe.app.core.model.outfit

import org.junit.Assert.assertEquals
import org.junit.Test

class OutfitSlotTest {
    @Test
    fun `existing seeded category names still classify correctly`() {
        assertEquals(OutfitSlot.DRESS, OutfitSlot.classify("Dresses"))
        assertEquals(OutfitSlot.OUTERWEAR, OutfitSlot.classify("Outerwear"))
        assertEquals(OutfitSlot.SHOES, OutfitSlot.classify("Sneakers"))
        assertEquals(OutfitSlot.SHOES, OutfitSlot.classify("Boots"))
        assertEquals(OutfitSlot.SHOES, OutfitSlot.classify("Sandals"))
        assertEquals(OutfitSlot.WATCH, OutfitSlot.classify("Watches"))
        assertEquals(OutfitSlot.JEWELRY, OutfitSlot.classify("Earrings"))
        assertEquals(OutfitSlot.JEWELRY, OutfitSlot.classify("Necklaces"))
        assertEquals(OutfitSlot.JEWELRY, OutfitSlot.classify("Bracelets"))
        assertEquals(OutfitSlot.JEWELRY, OutfitSlot.classify("Rings"))
        assertEquals(OutfitSlot.ACCESSORIES, OutfitSlot.classify("Belts"))
        assertEquals(OutfitSlot.ACCESSORIES, OutfitSlot.classify("Scarves"))
        assertEquals(OutfitSlot.ACCESSORIES, OutfitSlot.classify("Hair Accessories"))
        assertEquals(OutfitSlot.ACCESSORIES, OutfitSlot.classify("Sunglasses"))
    }

    @Test
    fun `new clothing subcategories classify as TOP or OUTERWEAR or BOTTOM or DRESS`() {
        assertEquals(OutfitSlot.TOP, OutfitSlot.classify("T-Shirts"))
        assertEquals(OutfitSlot.TOP, OutfitSlot.classify("Shirts"))
        assertEquals(OutfitSlot.TOP, OutfitSlot.classify("Polo"))
        assertEquals(OutfitSlot.TOP, OutfitSlot.classify("Tank Tops"))
        assertEquals(OutfitSlot.TOP, OutfitSlot.classify("Hoodies"))
        assertEquals(OutfitSlot.TOP, OutfitSlot.classify("Sweaters"))
        assertEquals(OutfitSlot.TOP, OutfitSlot.classify("Kurtas"))
        assertEquals(OutfitSlot.OUTERWEAR, OutfitSlot.classify("Jackets"))
        assertEquals(OutfitSlot.OUTERWEAR, OutfitSlot.classify("Blazers"))
        assertEquals(OutfitSlot.OUTERWEAR, OutfitSlot.classify("Coats"))
        assertEquals(OutfitSlot.DRESS, OutfitSlot.classify("Sarees"))
        assertEquals(OutfitSlot.BOTTOM, OutfitSlot.classify("Jeans"))
        assertEquals(OutfitSlot.BOTTOM, OutfitSlot.classify("Pants"))
        assertEquals(OutfitSlot.BOTTOM, OutfitSlot.classify("Shorts"))
        assertEquals(OutfitSlot.BOTTOM, OutfitSlot.classify("Skirts"))
        assertEquals(OutfitSlot.BOTTOM, OutfitSlot.classify("Leggings"))
    }

    @Test
    fun `new footwear subcategories classify as SHOES`() {
        assertEquals(OutfitSlot.SHOES, OutfitSlot.classify("Running Shoes"))
        assertEquals(OutfitSlot.SHOES, OutfitSlot.classify("Heels"))
        assertEquals(OutfitSlot.SHOES, OutfitSlot.classify("Flats"))
        assertEquals(OutfitSlot.SHOES, OutfitSlot.classify("Loafers"))
        assertEquals(OutfitSlot.SHOES, OutfitSlot.classify("Slippers"))
    }

    @Test
    fun `new bag subcategories classify as BAG`() {
        assertEquals(OutfitSlot.BAG, OutfitSlot.classify("Backpack"))
        assertEquals(OutfitSlot.BAG, OutfitSlot.classify("Handbag"))
        assertEquals(OutfitSlot.BAG, OutfitSlot.classify("Tote"))
        assertEquals(OutfitSlot.BAG, OutfitSlot.classify("Laptop Bag"))
        assertEquals(OutfitSlot.BAG, OutfitSlot.classify("Sling Bag"))
        assertEquals(OutfitSlot.BAG, OutfitSlot.classify("Wallet"))
    }

    @Test
    fun `new jewelry subcategories classify as JEWELRY`() {
        assertEquals(OutfitSlot.JEWELRY, OutfitSlot.classify("Nose Ring"))
        assertEquals(OutfitSlot.JEWELRY, OutfitSlot.classify("Anklet"))
    }

    @Test
    fun `new accessory subcategories classify as ACCESSORIES`() {
        assertEquals(OutfitSlot.ACCESSORIES, OutfitSlot.classify("Cap"))
        assertEquals(OutfitSlot.ACCESSORIES, OutfitSlot.classify("Hat"))
        assertEquals(OutfitSlot.ACCESSORIES, OutfitSlot.classify("Gloves"))
        assertEquals(OutfitSlot.ACCESSORIES, OutfitSlot.classify("Socks"))
        assertEquals(OutfitSlot.ACCESSORIES, OutfitSlot.classify("Tie"))
        assertEquals(OutfitSlot.ACCESSORIES, OutfitSlot.classify("Bow Tie"))
        assertEquals(OutfitSlot.ACCESSORIES, OutfitSlot.classify("Hair Band"))
        assertEquals(OutfitSlot.ACCESSORIES, OutfitSlot.classify("Hair Clip"))
    }

    @Test
    fun `unrecognized category name yields null rather than a wrong guess`() {
        assertEquals(null, OutfitSlot.classify("Costume Props"))
    }

    @Test
    fun `fromIndex resolves every slot by its declaration order`() {
        OutfitSlot.entries.forEach { slot ->
            assertEquals(slot, OutfitSlot.fromIndex(slot.slotIndex))
        }
    }
}
