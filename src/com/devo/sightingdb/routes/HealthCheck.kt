package com.devo.sightingdb.routes

import com.devo.sightingdb.routes.v1.StringMessage
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get

fun Route.ping() {
    get("/ping") {
        call.respond(HttpStatusCode.OK, StringMessage("Running"))
    }
}
