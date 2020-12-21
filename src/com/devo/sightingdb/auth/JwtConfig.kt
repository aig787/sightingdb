package com.devo.sightingdb.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.fasterxml.jackson.databind.JsonMappingException
import io.ktor.application.Application
import io.ktor.application.application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.Principal
import io.ktor.auth.UserPasswordCredential
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.jwt.jwt
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import java.time.Duration
import java.time.Instant
import java.util.Date

class JwtConfig(secret: String, private val issuer: String, val validity: Duration) {

    data class User(val name: String, val password: String) : Principal

    data class Token(val token: String)

    private val algorithm: Algorithm = Algorithm.HMAC256(secret)
    val verifier: JWTVerifier = JWT.require(algorithm).withIssuer(issuer).build()

    private fun getExpiration() = Date.from(Instant.now().plusMillis(validity.toMillis()))

    fun makeToken(user: User): Token = Token(
        JWT.create()
            .withSubject("Authentication")
            .withIssuer(issuer)
            .withClaim("user", user.name)
            .withClaim("password", user.password)
            .withExpiresAt(getExpiration())
            .sign(algorithm)
    )
}

val Application.jwtConfig get() = JwtConfig(jwtSecret, jwtIssuer, jwtValidity)

val Application.jwtUsers
    get() = environment.config.configList("ktor.jwt.users").map {
        JwtConfig.User(it.property("name").getString(), it.property("password").getString())
    }.toSet()

val Application.jwtValidity: Duration
    get() = Duration.ofSeconds(
        environment.config.property("ktor.jwt.validitySeconds").getString().toLong()
    )

val Application.jwtSecret get() = environment.config.property("ktor.jwt.secret").getString()

val Application.jwtIssuer get() = environment.config.property("ktor.jwt.issuer").getString()

fun Application.jwtAuth() {
    install(Authentication) {
        jwt {
            verifier(jwtConfig.verifier)
            realm = jwtIssuer
            validate {
                with(it.payload) {
                    if (getClaim("user").isNull || getClaim("password").isNull) {
                        null
                    } else {
                        JWTPrincipal(this)
                    }
                }
            }
        }
    }
}

fun Route.jwtLogin() {
    post("/login") {
        try {
            call.receive<UserPasswordCredential>().let { u ->
                val user = JwtConfig.User(u.name, u.password)
                if (application.jwtUsers.contains(user)) {
                    call.respond(application.jwtConfig.makeToken(user))
                } else {
                    call.respond(HttpStatusCode.Unauthorized)
                }
            }
        } catch (e: JsonMappingException) {
            call.respond(HttpStatusCode.BadRequest)
        }
    }
}
