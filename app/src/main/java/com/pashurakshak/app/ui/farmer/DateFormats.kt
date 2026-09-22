package com.pashurakshak.app.ui.farmer

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
private val dateTimeFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

internal fun formatDateMillis(millis: Long): String = dateFormat.format(Date(millis))

internal fun formatDateTimeMillis(millis: Long): String = dateTimeFormat.format(Date(millis))
