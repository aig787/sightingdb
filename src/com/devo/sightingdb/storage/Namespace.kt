package com.devo.sightingdb.storage

import com.devo.sightingdb.data.Sighting

data class Namespace(
    val name: String,
    val config: MutableMap<String, String> = mutableMapOf(),
    val sightings: MutableMap<String, Sighting> = mutableMapOf()
) {
    fun put(sighting: Sighting) {
        sightings[sighting.value] = sighting
    }

    fun get(value: String) = sightings[value]

    fun all() = sightings.values.toList()
}
