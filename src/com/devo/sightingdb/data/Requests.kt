package com.devo.sightingdb.data

import kotlinx.serialization.Serializable

@Serializable
data class SightingRequest(val namespace: String, val value: String, val timestamp: Long? = null)

@Serializable
data class BulkSightingRequest(val items: List<SightingRequest>)
