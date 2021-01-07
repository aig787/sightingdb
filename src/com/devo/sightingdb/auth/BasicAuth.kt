package com.devo.sightingdb.auth

import io.ktor.application.Application
import io.ktor.auth.UserIdPrincipal
import io.ktor.auth.authentication
import io.ktor.auth.basic

val Application.basicUsers: Set<String>
    get() = environment.config.configList("ktor.basicAuth.users").map { it.property("password").getString() }.toSet()

fun Application.basicAuth() {
    authentication {
        basic(name = "basic") {
            realm = "sightingdb"
            validate { credentials ->
                if (basicUsers.contains(credentials.password)) {
                    UserIdPrincipal(credentials.password)
                } else {
                    null
                }
            }
        }
    }
}
