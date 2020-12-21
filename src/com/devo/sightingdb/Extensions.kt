package com.devo.sightingdb

@ExperimentalUnsignedTypes
@Suppress("MagicNumber")
fun ByteArray.toHex() = asUByteArray().joinToString("") { it.toString(16).padStart(2, '0') }
