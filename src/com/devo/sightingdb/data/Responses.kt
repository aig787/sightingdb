package com.devo.sightingdb.data

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

@Serializable
data class Message(val message: String)

sealed class BulkSightings
@Serializable
data class BulkSightingsWithStats(val items: List<SightingWithStats>): BulkSightings()
@Serializable
data class BulkSightingsWithoutStats(val items: List<SightingWithoutStats>): BulkSightings()

interface Response {
    val status: HttpStatusCode
    val value: Any
}

interface ReadResponse : Response
interface ReadBulkResponse : Response

data class MessageResponse(
    override val status: HttpStatusCode,
    override val value: Message
) : ReadResponse, ReadBulkResponse

data class SightingResponse(
    override val status: HttpStatusCode = HttpStatusCode.OK,
    override val value: Sighting
) : ReadResponse

data class NamespaceResponse(
    override val status: HttpStatusCode = HttpStatusCode.OK,
    override val value: BulkSightings
) : ReadResponse

data class BulkSightingsResponse(
    override val status: HttpStatusCode = HttpStatusCode.OK,
    override val value: BulkSightings
) : ReadBulkResponse
