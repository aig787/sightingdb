package com.devo.sightingdb

import com.devo.sightingdb.data.BulkSightingRequest
import com.devo.sightingdb.data.BulkSightingsResponse
import com.devo.sightingdb.data.BulkSightingsWithStats
import com.devo.sightingdb.data.BulkSightingsWithoutStats
import com.devo.sightingdb.data.Message
import com.devo.sightingdb.data.MessageResponse
import com.devo.sightingdb.data.NamespaceResponse
import com.devo.sightingdb.data.ReadBulkResponse
import com.devo.sightingdb.data.ReadResponse
import com.devo.sightingdb.data.SightingResponse
import com.devo.sightingdb.storage.Connector
import com.typesafe.config.ConfigFactory
import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.serialization.json
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

private fun getNamespace(call: ApplicationCall): String? =
    call.parameters.getAll("namespace")?.joinToString("/")?.let {
        cleanNamespace(it)
    }

private fun cleanNamespace(namespace: String): String =
    if (namespace.startsWith("/")) namespace else "/$namespace"

private fun getVal(call: ApplicationCall): String? = call.parameters["val"]

private fun readValue(connector: Connector, namespace: String, value: String, withStats: Boolean): ReadResponse {
    return when (val sighting = connector.get(namespace, value)) {
        null -> MessageResponse(HttpStatusCode.NotFound, Message("Value $value not found in $namespace"))
        else -> SightingResponse(
            HttpStatusCode.OK,
            if (withStats) sighting else sighting.withoutStats()
        )
    }
}

private fun readNamespace(connector: Connector, namespace: String, withStats: Boolean): ReadResponse {
    return when (val sightings = connector.get(namespace)) {
        null -> MessageResponse(HttpStatusCode.NotFound, Message("Namespace $namespace not found"))
        else -> NamespaceResponse(
            HttpStatusCode.OK,
            if (withStats) {
                BulkSightingsWithStats(sightings)
            } else {
                BulkSightingsWithoutStats(sightings.map { it.withoutStats() })
            }
        )
    }
}

private fun read(connector: Connector, namespace: String?, value: String?, withStats: Boolean): ReadResponse {
    return when (namespace) {
        null -> MessageResponse(HttpStatusCode.BadGateway, Message("Namespace must be specified"))
        else -> {
            when (value) {
                null -> readNamespace(connector, namespace, withStats)
                else -> readValue(connector, namespace, value, withStats)
            }
        }
    }
}

private fun readBulk(connector: Connector, toRead: BulkSightingRequest, withStats: Boolean): ReadBulkResponse {
    val items = toRead.items.mapNotNull {
        connector.get(it.namespace, it.value)
    }
    val response = if (withStats) {
        BulkSightingsWithStats(items)
    } else {
        BulkSightingsWithoutStats(items.map { it.withoutStats() })
    }
    val status = if (items.size == toRead.items.size) HttpStatusCode.OK else HttpStatusCode.MultiStatus
    return BulkSightingsResponse(status, response)
}

fun Application.mainModule(connector: Connector) {
    install(DefaultHeaders)
    install(ContentNegotiation) {
        json()
    }
    routing {
        get("/w/{namespace...}") {
            val namespace = getNamespace(call)
            val value = getVal(call)
            when {
                namespace == null -> call.respond(HttpStatusCode.BadRequest, Message("Must specify namespace"))
                value == null -> call.respond(HttpStatusCode.BadRequest, Message("Must specify val"))
                else -> {
                    connector.observe(namespace, value)
                    call.respond(HttpStatusCode.Created, Message("Successfully wrote attribute"))
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
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(it.timestamp), ZoneOffset.UTC)
                    )
                }
            }
            call.respond(HttpStatusCode.Created, "Successfully wrote ${toWrite.items.size} attributes")
        }
        get("/rs/{namespace...}") {
            val response = read(connector, getNamespace(call), getVal(call), true)
            call.respond(response.status, response.value)
        }
        get("/r/{namespace...}") {
            val response = read(connector, getNamespace(call), getVal(call), false)
            call.respond(response.status, response.value)
        }
        post("/rb") {
            val response = readBulk(connector, call.receive(), false)
            call.respond(response.status, response.value)
        }
        post("/rbs") {
            val response = readBulk(connector, call.receive(), true)
            call.respond(response.status, response.value)
        }
    }
}

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.main(testing: Boolean = false) {
    val config = ConfigFactory.load()
    val connectorConfig = config.getConfig("sightingdb.connector")
    val connectorClass = Class.forName(connectorConfig.getString("class"))
    mainModule((connectorClass.getConstructor().newInstance() as Connector).build(connectorConfig))
}
