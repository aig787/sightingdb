package com.devo.sightingdb

import com.devo.sightingdb.auth.basicAuth
import com.devo.sightingdb.auth.jwtAuth
import com.devo.sightingdb.auth.jwtLogin
import com.devo.sightingdb.routes.ping
import com.devo.sightingdb.routes.v1.v1
import com.devo.sightingdb.storage.Connector
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.application.log
import io.ktor.auth.Authentication
import io.ktor.auth.authenticate
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.features.StatusPages
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.route
import io.ktor.routing.routing
import org.slf4j.event.Level

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

internal fun getNamespace(call: ApplicationCall): String? =
    call.parameters.getAll("namespace")?.joinToString("/")?.let {
        cleanNamespace(it)
    }

internal fun cleanNamespace(namespace: String): String =
    if (namespace.startsWith("/")) namespace else "/$namespace"

internal fun getVal(call: ApplicationCall): String? = call.parameters["val"]

fun Application.install() {
    install(Authentication)
    install(DefaultHeaders)
    install(CallLogging) {
        level = Level.INFO
    }
    install(StatusPages) {
        exception<Throwable> { cause ->
            log.error("Uncaught exception", cause)
            call.respond(HttpStatusCode.InternalServerError)
        }
    }
    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            enable(SerializationFeature.INDENT_OUTPUT)
        }
    }
}


fun Route.allRoutes(connector: Connector) {
    ping()
    // V1 routes at root for backwards compatibility
    v1(connector)
    route("v1") {
        v1(connector)
    }
}

fun Application.routes(connector: Connector) {
    val authProviders = mutableListOf<String>()
    if (useJwt) {
        log.info("Using JWT authentication")
        jwtAuth()
        authProviders.add("jwt")
        routing {
            jwtLogin()
        }
    }
    if (useBasicAuth) {
        log.info("Using Basic authentication")
        basicAuth()
        authProviders.add("basic")
    }
    if (authProviders.isNotEmpty()) {
        routing {
            authenticate(*authProviders.toTypedArray()) {
                allRoutes(connector)
            }
        }
    } else {
        routing {
            allRoutes(connector)
        }
    }
}

val Application.useBasicAuth get() = environment.config.propertyOrNull("ktor.basicAuth") != null
val Application.useJwt get() = environment.config.propertyOrNull("ktor.jwt") != null

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.main(testing: Boolean = false) {
    val connector = let {
        val connectorConfig = environment.config.config("sightingdb.connector")
        val connectorClass = Class.forName(connectorConfig.property("class").getString())
        (connectorClass.getConstructor().newInstance() as Connector).build(connectorConfig)
    }
    install()
    routes(connector)
}
