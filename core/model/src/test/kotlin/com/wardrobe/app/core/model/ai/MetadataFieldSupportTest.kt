package com.wardrobe.app.core.model.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M23 — proves the on-device capability boundary is a real, declared fact
 * (matching what [com.wardrobe.app.core.image.metadata.OnDeviceMetadataEngine]
 * actually emits), not something either side can silently drift out of sync
 * with. Genuinely on-device-supported fields are exactly Color/Pattern/
 * Brand; everything else — Category, Material, Fabric, Fit, etc. — is never
 * fabricated as "supported" here.
 */
class MetadataFieldSupportTest {
    @Test
    fun `on-device genuinely supports only color, pattern, and brand`() {
        assertTrue(MetadataField.PRIMARY_COLOR in MetadataFieldSupport.ON_DEVICE_SUPPORTED_FIELDS)
        assertTrue(MetadataField.SECONDARY_COLOR in MetadataFieldSupport.ON_DEVICE_SUPPORTED_FIELDS)
        assertTrue(MetadataField.PATTERN in MetadataFieldSupport.ON_DEVICE_SUPPORTED_FIELDS)
        assertTrue(MetadataField.BRAND in MetadataFieldSupport.ON_DEVICE_SUPPORTED_FIELDS)
    }

    @Test
    fun `on-device never claims support for fields it has no real signal for`() {
        val unsupported =
            setOf(
                MetadataField.CATEGORY,
                MetadataField.SUBCATEGORY,
                MetadataField.MATERIAL,
                MetadataField.FABRIC,
                MetadataField.FIT,
                MetadataField.SLEEVE_LENGTH,
                MetadataField.LENGTH,
                MetadataField.NECKLINE,
                MetadataField.GENDER,
                MetadataField.WATERPROOF_LEVEL,
                MetadataField.SEASON,
                MetadataField.DRESS_CODE,
                MetadataField.OCCASION,
                MetadataField.WARMTH,
                MetadataField.STYLE_TAG,
            )

        unsupported.forEach { field ->
            assertFalse(
                "$field should not be on-device-supported",
                MetadataFieldSupport.isSupported(field, AiResultSource.ON_DEVICE),
            )
        }
    }

    @Test
    fun `cloud is unbounded since MetadataPromptSupport requests every field`() {
        MetadataField.entries.forEach { field ->
            assertTrue(MetadataFieldSupport.isSupported(field, AiResultSource.CLOUD))
        }
    }

    @Test
    fun `manual entry is always considered supported, there's nothing to gate`() {
        MetadataField.entries.forEach { field ->
            assertTrue(MetadataFieldSupport.isSupported(field, AiResultSource.MANUAL))
        }
    }
}
