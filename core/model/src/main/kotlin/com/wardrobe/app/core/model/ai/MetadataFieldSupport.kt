package com.wardrobe.app.core.model.ai

/**
 * The declared, testable field-support contract per [AiResultSource]
 * (M23 — "AI wardrobe auto-fill failure" root-cause fix). Before this, which
 * [MetadataField]s the on-device engine can ever produce lived only as prose
 * in [com.wardrobe.app.core.image.metadata.OnDeviceMetadataEngine]'s KDoc —
 * real and honest, but not something the review screen or diagnostics could
 * consult, so a field the engine structurally cannot detect (e.g. Fabric)
 * rendered identically to one it simply didn't detect on this photo (e.g. an
 * undetected Brand). That collapsed two very different situations into one
 * "Unknown — please choose" row.
 *
 * [ON_DEVICE_SUPPORTED_FIELDS] is the *only* set of fields
 * `OnDeviceMetadataEngine.generateMetadata` ever constructs a
 * [MetadataSuggestion] for — color via k-means clustering, pattern via a
 * luminance-variance heuristic, brand via on-device OCR. Every other field
 * requires either a cloud vision model or a dedicated on-device classifier
 * this app does not have; adding one is a real product decision (a new
 * dependency and inference cost), not something this contract can shortcut
 * by fabricating a capability. [CLOUD] and [AiResultSource.MANUAL] are
 * intentionally unbounded here: `MetadataPromptSupport.buildMetadataSystemPrompt`
 * already requests every [MetadataField] from the cloud provider, so a cloud
 * result missing a field always means "asked but not detected," never
 * "structurally impossible to ask."
 */
object MetadataFieldSupport {
    val ON_DEVICE_SUPPORTED_FIELDS: Set<MetadataField> =
        setOf(
            MetadataField.PRIMARY_COLOR,
            MetadataField.SECONDARY_COLOR,
            MetadataField.PATTERN,
            MetadataField.BRAND,
        )

    fun isSupported(
        field: MetadataField,
        source: AiResultSource,
    ): Boolean =
        when (source) {
            AiResultSource.ON_DEVICE -> field in ON_DEVICE_SUPPORTED_FIELDS
            AiResultSource.CLOUD, AiResultSource.MANUAL -> true
        }
}
