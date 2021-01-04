package com.devo.sightingdb.routes.v1

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class SightingRequest(val namespace: String, val value: String, val timestamp: Long? = null)
data class BulkSightingRequest(val items: List<SightingRequest>)
