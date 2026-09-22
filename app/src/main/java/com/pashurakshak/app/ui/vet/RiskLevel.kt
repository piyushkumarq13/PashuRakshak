package com.pashurakshak.app.ui.vet

import androidx.compose.ui.graphics.Color

enum class RiskLevel { LOW, MEDIUM, HIGH }

/**
 * App-wide risk/status palette: green = healthy/low, orange = medium, red = high.
 * Use these instead of ad-hoc colors so every screen stays consistent.
 */
object RiskColors {
    val lowGreen = Color(0xFF2E7D32)
    val mediumOrange = Color(0xFFEF6C00)
    val highRed = Color(0xFFD32F2F)
}

// Placeholder thresholds — refined server-side later.
internal fun riskLevelFor(score: Int): RiskLevel = when {
    score >= 60 -> RiskLevel.HIGH
    score >= 30 -> RiskLevel.MEDIUM
    else -> RiskLevel.LOW
}

@androidx.compose.runtime.Composable
internal fun RiskLevel.color(): Color = when (this) {
    RiskLevel.HIGH -> RiskColors.highRed
    RiskLevel.MEDIUM -> RiskColors.mediumOrange
    RiskLevel.LOW -> RiskColors.lowGreen
}

internal fun RiskLevel.label(): String = when (this) {
    RiskLevel.HIGH -> "High risk"
    RiskLevel.MEDIUM -> "Medium risk"
    RiskLevel.LOW -> "Low risk"
}
