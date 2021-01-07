package com.devo.sightingdb.routes.v1

import com.devo.sightingdb.data.SightingKey
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

const val NOT_FOUND_STRING = "Not found"

private val log = KotlinLogging.logger { }

fun Route.v1(connector: Connector) {
    configure()
    info()
    read(connector)
    write(connector)
    delete(connector)
}

fun Route.read(connector: Connector) {
    get("/rs/{namespace...}") {
        val response = read(connector, getNamespace(call), getVal(call))
        call.respond(response.status, response.value)
    }
    get("/r/{namespace...}") {
        when (val response = read(connector, getNamespace(call), getVal(call))) {
            is SightingResponse -> call.respond(response.status, response.value.copy(serializeWithStats = false))
            is BulkSightingsResponse -> call.respond(
                response.status,
                response.value.copy(items = response.value.items.map { it.copy(serializeWithStats = false) })
            )
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

fun Route.write(connector: Connector) {
    get("/w/{namespace...}") {
        val namespace = getNamespace(call)
        val value = getVal(call)
        when {
            namespace == null -> call.respond(HttpStatusCode.BadRequest, StringMessage("Must specify namespace"))
            value == null -> call.respond(HttpStatusCode.BadRequest, StringMessage("Must specify val"))
            else -> {
                log.info { "Observed $namespace $value" }
                connector.observe(namespace, value)
                call.respond(HttpStatusCode.Created, CountOk())
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
        call.respond(HttpStatusCode.Created, CountOk(toWrite.items.size))
    }
}

fun Route.delete(connector: Connector) {
    get("/d/{namespace...}") {
        val response = delete(connector, getNamespace(call), getVal(call))
        call.respond(response.status, response.value)
    }
    post("/db") {
        val toDelete = call.receive<BulkSightingRequest>()
        toDelete.items.forEach {
            connector.delete(it.namespace, it.value)
            log.info { "Deleted ${SightingKey(it.namespace, it.value)}" }
        }
        call.respond(HttpStatusCode.OK, CountOk(toDelete.items.size))
    }
}

fun Route.configure() {
    get("/c") {
        call.respond(HttpStatusCode.NotImplemented)
    }
}

fun Route.info() {
    get("/info") {
        call.respond(HttpStatusCode.NotImplemented)
    }
}

private fun readValue(connector: Connector, namespace: String, value: String): ReadResponse {
    log.info { "Reading value $namespace $value" }
    return when (val sighting = connector.get(namespace, value)) {
        null -> {
            log.info { "No value found for ${SightingKey(namespace, value)}" }
            SightingKeyResponse(
                HttpStatusCode.NotFound,
                SightingKeyMessage(NOT_FOUND_STRING, SightingKey(namespace, value))
            )
        }
        else -> {
            log.info { "Found sighting ${sighting.value}" }
            SightingResponse(sighting)
        }
    }
}

private fun deleteValue(connector: Connector, namespace: String, value: String): DeletedResponse {
    val sighting = SightingKey(namespace, value)
    log.info { "Deleting $sighting" }
    return if (connector.delete(namespace, value)) {
        SightingKeyResponse(HttpStatusCode.OK, SightingKeyMessage("Deleted", sighting))
    } else {
        log.info { "$sighting not found" }
        SightingKeyResponse(HttpStatusCode.NotFound, SightingKeyMessage(NOT_FOUND_STRING, sighting))
    }
}

private fun deleteNamespace(connector: Connector, namespace: String): NamespaceResponse {
    log.info { "Deleting namespace $namespace" }
    return if (connector.delete(namespace)) {
        NamespaceResponse(HttpStatusCode.OK, NamespaceMessage("Deleted", namespace))
    } else {
        log.info { "Namespace $namespace not found" }
        NamespaceResponse(HttpStatusCode.NotFound, NamespaceMessage(NOT_FOUND_STRING, namespace))
    }
}

private fun readNamespace(connector: Connector, namespace: String): ReadBulkResponse {
    log.info { "Reading all values for namespace $namespace" }
    return when (val sightings = connector.get(namespace)) {
        null -> {
            log.info { "Namespace $namespace not found" }
            NamespaceResponse(HttpStatusCode.NotFound, NamespaceMessage(NOT_FOUND_STRING, namespace))
        }
        else -> {
            log.info { "Found ${sightings.size} in namespace $namespace" }
            BulkSightingsResponse(BulkSightings(sightings))
        }
    }
}

private fun read(connector: Connector, namespace: String?, value: String?): ReadResponse {
    return when (namespace) {
        null -> MessageResponse(HttpStatusCode.NotFound, StringMessage("Namespace must be specified"))
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
    return BulkSightingsResponse(response)
}

private fun delete(connector: Connector, namespace: String?, value: String?): DeletedResponse {
    return when (namespace) {
        null -> MessageResponse(HttpStatusCode.BadGateway, StringMessage("Namespace must be specified"))
        else -> {
            when (value) {
                null -> deleteNamespace(connector, namespace)
                else -> deleteValue(connector, namespace, value)
            }
        }
    }
}
