package com.devo.sightingdb.routes

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get

fun Route.info() {
    get("/info") {
        call.respond(HttpStatusCode.NotImplemented)
    }
}
