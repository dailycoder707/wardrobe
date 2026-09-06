package com.wardrobe.app.core.ui.components

/** What [AiActivityBanner] is reporting — always a real signal, never a
 * decorative "thinking" animation (M18 non-negotiable). [RUNNING] must only
 * be used while a real AI dispatch (`AiJobManager`) is genuinely in flight;
 * [SUCCESS]/[FAILED] describe something that already happened
 * ([com.wardrobe.app.core.model.ai.AiActivityEntry]); [INFO] is for
 * capability-availability/consent state, not an operation. */
enum class AiActivityTone { RUNNING, SUCCESS, FAILED, INFO }
