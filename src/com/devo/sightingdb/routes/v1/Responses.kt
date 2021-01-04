package com.devo.sightingdb.routes.v1

import com.devo.sightingdb.data.Sighting
import com.devo.sightingdb.data.SightingKey
import io.ktor.http.HttpStatusCode

interface Message {
    val message: String
}

data class StringMessage(override val message: String) : Message
data class SightingKeyMessage(override val message: String, val sighting: SightingKey) : Message
data class NamespaceMessage(override val message: String, val namespace: String) : Message

data class CountOk(val count: Int = 1) {
    val message = "ok"
}

data class BulkSightings(val items: List<Sighting>)

interface Response {
    val status: HttpStatusCode
    val value: Any
}

interface ReadResponse : Response
interface ReadBulkResponse : ReadResponse
interface DeletedResponse : Response

data class SightingKeyResponse(
    override val status: HttpStatusCode,
    override val value: SightingKeyMessage
) : ReadResponse, DeletedResponse

data class NamespaceResponse(
    override val status: HttpStatusCode,
    override val value: NamespaceMessage
) : ReadBulkResponse, DeletedResponse, ReadResponse

data class MessageResponse(
    override val status: HttpStatusCode,
    override val value: Message
) : ReadResponse, ReadBulkResponse, DeletedResponse

data class SightingResponse(
    override val value: Sighting,
    override val status: HttpStatusCode = HttpStatusCode.OK
) : ReadResponse

data class BulkSightingsResponse(
    override val value: BulkSightings,
    override val status: HttpStatusCode = HttpStatusCode.OK
) : ReadBulkResponse
