package com.devo.sightingdb.data

import io.ktor.http.HttpStatusCode

data class Message(val message: String)

data class WroteOk(val count: Int = 1) {
    val message = "ok"
}

data class BulkSightings(val items: List<Sighting>)

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
