package com.pashurakshak.app.data.local

import tech.turso.libsql.Row

// Positional accessors for libSQL rows (values arrive as Long/String/Double/null).
internal fun Row.string(index: Int): String = this[index] as String

internal fun Row.stringOrNull(index: Int): String? = this[index] as String?

internal fun Row.long(index: Int): Long = (this[index] as Number).toLong()

internal fun Row.int(index: Int): Int = (this[index] as Number).toInt()

internal fun Row.double(index: Int): Double = (this[index] as Number).toDouble()

internal fun Row.boolean(index: Int): Boolean = int(index) != 0
