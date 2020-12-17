package com.devo.sightingdb.storage

import com.devo.sightingdb.data.SightingWithStats
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class Namespace(
    val name: String,
    val config: MutableMap<String, String> = mutableMapOf(),
    val sightings: MutableMap<String, SightingWithStats> = ConcurrentHashMap()
) {
    fun put(sighting: SightingWithStats) {
        sightings[sighting.value] = sighting
    }

    fun get(value: String) = sightings[value]

    fun getAll() = sightings.values.toList()
}
