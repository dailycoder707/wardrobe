package com.wardrobe.app.core.model.garment

/**
 * A fixed, non-user-editable vocabulary (phase-3-persistence.md refinement #1) —
 * stored as an indexed enum column on `garment_dress_codes`, not a separate reference
 * table.
 */
enum class DressCode { CASUAL, SMART_CASUAL, BUSINESS, FORMAL, ATHLETIC, LOUNGE }
