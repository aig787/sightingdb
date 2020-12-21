package com.devo.sightingdb.routes

import com.devo.sightingdb.data.BulkSightingRequest
import com.devo.sightingdb.data.BulkSightings
import com.devo.sightingdb.data.BulkSightingsResponse
import com.devo.sightingdb.data.Message
import com.devo.sightingdb.data.MessageResponse
import com.devo.sightingdb.data.NamespaceResponse
import com.devo.sightingdb.data.ReadBulkResponse
import com.devo.sightingdb.data.ReadResponse
import com.devo.sightingdb.data.SightingResponse
import com.devo.sightingdb.data.WroteOk
import com.devo.sightingdb.getNamespace
import com.devo.sightingdb.getVal
import com.devo.sightingdb.storage.Connector
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

private fun readValue(connector: Connector, namespace: String, value: String): ReadResponse {
    return when (val sighting = connector.get(namespace, value)) {
        null -> MessageResponse(HttpStatusCode.NotFound, Message("Value $value not found in $namespace"))
        else -> SightingResponse(
            HttpStatusCode.OK,
            sighting
        )
    }
}

private fun readNamespace(connector: Connector, namespace: String): ReadResponse {
    return when (val sightings = connector.get(namespace)) {
        null -> MessageResponse(HttpStatusCode.NotFound, Message("Namespace $namespace not found"))
        else -> NamespaceResponse(
            HttpStatusCode.OK,
            BulkSightings(sightings)
        )
    }
}

private fun read(connector: Connector, namespace: String?, value: String?): ReadResponse {
    return when (namespace) {
        null -> MessageResponse(HttpStatusCode.BadGateway, Message("Namespace must be specified"))
        else -> {
            when (value) {
                null -> readNamespace(connector, namespace)
                else -> readValue(connector, namespace, value)
            }
        }
    }
}

private fun readBulk(connector: Connector, toRead: BulkSightingRequest): ReadBulkResponse {
    val items = toRead.items.mapNotNull {
        connector.get(it.namespace, it.value)
    }
    val response = BulkSightings(items)
    return BulkSightingsResponse(HttpStatusCode.OK, response)
}

fun Route.readWrite(connector: Connector) {
    get("/w/{namespace...}") {
        val namespace = getNamespace(call)
        val value = getVal(call)
        when {
            namespace == null -> call.respond(HttpStatusCode.BadRequest, Message("Must specify namespace"))
            value == null -> call.respond(HttpStatusCode.BadRequest, Message("Must specify val"))
            else -> {
                connector.observe(namespace, value)
                call.respond(HttpStatusCode.Created, WroteOk())
            }
        }
    }
    post("/wb") {
        val toWrite = call.receive<BulkSightingRequest>()
        toWrite.items.forEach {
            when (it.timestamp) {
                null -> connector.observe(it.namespace, it.value)
                else -> connector.observe(
                    it.namespace,
                    it.value,
                    OffsetDateTime.ofInstant(Instant.ofEpochMilli(it.timestamp), ZoneOffset.UTC)
                )
            }
        }
        call.respond(HttpStatusCode.Created, WroteOk(toWrite.items.size))
    }
    get("/rs/{namespace...}") {
        val response = read(connector, getNamespace(call), getVal(call))
        call.respond(response.status, response.value)
    }
    get("/r/{namespace...}") {
        when (val response = read(connector, getNamespace(call), getVal(call))) {
            is SightingResponse -> call.respond(response.status, response.value.copy(serializeWithStats = false))
            else -> call.respond(response.status, response.value)
        }
    }
    post("/rb") {
        when (val response = readBulk(connector, call.receive())) {
            is BulkSightingsResponse -> call.respond(
                response.status,
                response.value.copy(items = response.value.items.map { it.copy(serializeWithStats = false) })
            )
            else -> call.respond(response.status, response.value)
        }
    }
    post("/rbs") {
        val response = readBulk(connector, call.receive())
        call.respond(response.status, response.value)
    }
}
