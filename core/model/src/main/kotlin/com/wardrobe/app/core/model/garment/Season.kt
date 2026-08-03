package com.wardrobe.app.core.model.garment

/**
 * A fixed, non-user-editable vocabulary (phase-3-persistence.md refinement #1) —
 * stored as an indexed enum column on `garment_seasons`, not a separate reference
 * table.
 */
enum class Season { SPRING, SUMMER, AUTUMN, WINTER }
