package com.pashurakshak.app.ui.farmer

internal fun prettifyStatus(dbValue: String): String = prettifyFactorLabel(dbValue)

private fun prettifyFactorLabel(value: String): String =
    value.split('_').joinToString(" ") { part -> part.replaceFirstChar { it.uppercaseChar() } }
