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
import mu.KotlinLogging
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

private val log = KotlinLogging.logger { }

private fun readValue(connector: Connector, namespace: String, value: String): ReadResponse {
    log.info { "Reading value $namespace $value" }
    return when (val sighting = connector.get(namespace, value)) {
        null -> {
            log.info { "No value found for $namespace $value" }
            MessageResponse(HttpStatusCode.NotFound, Message("Value $value not found in $namespace"))
        }
        else -> {
            log.info { "Found sighting ${sighting.value}" }
            SightingResponse(
                HttpStatusCode.OK,
                sighting
            )
        }
    }
}

private fun readNamespace(connector: Connector, namespace: String): ReadResponse {
    log.info { "Reading all values for namespace $namespace" }
    return when (val sightings = connector.get(namespace)) {
        null -> {
            log.info { "Namespace $namespace not found" }
            MessageResponse(HttpStatusCode.NotFound, Message("Namespace $namespace not found"))
        }
        else -> {
            log.info { "Found ${sightings.size} in namespace $namespace" }
            NamespaceResponse(
                HttpStatusCode.OK,
                BulkSightings(sightings)
            )
        }
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
        log.info { "Reading ${it.namespace} ${it.value}" }
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
                log.info { "Observed $namespace $value" }
                connector.observe(namespace, value)
                call.respond(HttpStatusCode.Created, WroteOk())
            }
        }
    }
    post("/wb") {
        val toWrite = call.receive<BulkSightingRequest>()
        toWrite.items.forEach {
            log.info { "Observed ${it.namespace} ${it.value}" }
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
