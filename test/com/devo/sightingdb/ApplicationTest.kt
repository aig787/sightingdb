package com.devo.sightingdb

import com.devo.sightingdb.data.BulkSightingRequest
import com.devo.sightingdb.data.Sighting
import com.devo.sightingdb.data.SightingRequest
import com.devo.sightingdb.routes.readWrite
import com.devo.sightingdb.storage.Connector
import com.devo.sightingdb.storage.InMemoryConnector
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.routing
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ApplicationTest {

    private val mapper = jacksonObjectMapper().findAndRegisterModules()
    private lateinit var connector: Connector

    @BeforeEach
    fun setUp() {
        connector = InMemoryConnector()
    }

    @Test
    fun testWriteNew() = withTestApplication({
        install()
        routing { readWrite(connector) }
    }) {
        with(handleRequest(HttpMethod.Get, "/w/a/namespace/?val=abcd")) {
            assertEquals(HttpStatusCode.Created, response.status())
            val attr = connector.get("/a/namespace", "abcd")!!
            assertThat(attr.count.toInt(), equalTo(1))
        }
    }

    @Test
    fun testWriteExisting() = withTestApplication({
        install()
        routing { readWrite(connector) }
    }) {
        val namespace = "/a/namespace"
        val value = "abcd"
        connector.observe(namespace, value)
        with(handleRequest(HttpMethod.Get, "/w/a/namespace/?val=abcd")) {
            assertEquals(HttpStatusCode.Created, response.status())
            val attr = connector.get(namespace, value)
            assertThat(attr?.count?.toInt(), equalTo(2))
        }
    }

    @Test
    fun testWriteBulk() = withTestApplication({
        install()
        routing { readWrite(connector) }
    }) {
        val items = (0 until 50).map { SightingRequest("/namespace/$it", it.toString()) }
        val call = handleRequest(HttpMethod.Post, "/wb") {
            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(mapper.writeValueAsString(BulkSightingRequest(items)))
        }
        with(call) {
            assertEquals(HttpStatusCode.Created, response.status())
            items.forEach {
                assertNotNull(connector.get(it.namespace, it.value))
            }
        }
    }

    @Test
    fun testReadNew() = withTestApplication({
        install()
        routing { readWrite(connector) }
    }) {
        with(handleRequest(HttpMethod.Get, "/r/a/namespace/?val=abcd")) {
            assertEquals(HttpStatusCode.NotFound, response.status())
        }
    }

    @Test
    fun testReadExisting() = withTestApplication({
        install()
        routing { readWrite(connector) }
    }) {
        val namespace = "/a/namespace"
        val value = "abcd"
        connector.observe(namespace, value)
        with(handleRequest(HttpMethod.Get, "/r/a/namespace/?val=abcd")) {
            assertEquals(HttpStatusCode.OK, response.status())
            val response: Sighting = mapper.readValue(response.content!!)
            val stored = connector.get(namespace, value)?.copy(stats = emptyMap())
            assertThat(response, equalTo(stored))
        }
    }

    @Test
    fun testReadNamespace() = withTestApplication({
        install()
        routing { readWrite(connector) }
    }) {
        val namespace = "/a/namespace"
        val items = (0 until 10).map {
            SightingRequest(namespace, it.toString()).also { sighting ->
                connector.observe(sighting.namespace, sighting.value)
            }
        }
        with(handleRequest(HttpMethod.Get, "/r/a/namespace")) {
            assertEquals(HttpStatusCode.OK, response.status())
            val read = mapper.readValue<Map<String, List<Sighting>>>(response.content!!)["items"]!!
            assertEquals(items.size, read.size)
        }
    }

    @Test
    fun testReadBulkExisting() = withTestApplication({
        install()
        routing { readWrite(connector) }
    }) {
        val items = (0 until 50).map {
            SightingRequest("/namespace/$it", it.toString()).also { req ->
                connector.observe(req.namespace, req.value)
            }
        }
        val call = handleRequest(HttpMethod.Post, "/rb") {
            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(mapper.writeValueAsString(BulkSightingRequest(items)))
        }
        with(call) {
            assertEquals(HttpStatusCode.OK, response.status())
            val read = mapper.readValue<Map<String, List<Sighting>>>(response.content!!)
            val readItems = read["items"]!!
            assertThat(readItems.size, equalTo(items.size))
            assertThat(readItems.map { it.value }.toSet(), equalTo(items.map { it.value }.toSet()))
        }
    }

    @Test
    fun testReadStatsNew() = withTestApplication({
        install()
        routing { readWrite(connector) }
    }) {
        with(handleRequest(HttpMethod.Get, "/rs/a/namespace/?val=abcd")) {
            assertEquals(HttpStatusCode.NotFound, response.status())
        }
    }

    @Test
    fun testReadStatsExisting() = withTestApplication({
        install()
        routing { readWrite(connector) }
    }) {
        val namespace = "/a/namespace"
        val value = "abcd"
        connector.observe(namespace, value)
        with(handleRequest(HttpMethod.Get, "/rs/a/namespace/?val=abcd")) {
            assertEquals(HttpStatusCode.OK, response.status())
            val response: Sighting = mapper.readValue(response.content!!)
            val stored = connector.get(namespace, value)
            assertThat(response, equalTo(stored))
        }
    }

    @Test
    fun testReadBulkStatsExisting() = withTestApplication({
        install()
        routing { readWrite(connector) }
    }) {
        val items = (0 until 50).map {
            SightingRequest("/namespace/$it", it.toString()).also { req ->
                connector.observe(req.namespace, req.value)
            }
        }
        val call = handleRequest(HttpMethod.Post, "/rbs") {
            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(mapper.writeValueAsString(BulkSightingRequest(items)))
        }
        with(call) {
            assertEquals(HttpStatusCode.OK, response.status())
            val read = mapper.readValue<Map<String, List<Sighting>>>(response.content!!)
            val readItems = read["items"]!!
            assertThat(readItems.size, equalTo(items.size))
            assertThat(readItems.map { it.value }.toSet(), equalTo(items.map { it.value }.toSet()))
            readItems.forEach { withStats ->
                assertThat(withStats.stats.size, equalTo(1))
            }
        }
    }
}
