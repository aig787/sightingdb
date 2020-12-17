package com.devo.sightingdb

import com.devo.sightingdb.data.BulkSightingRequest
import com.devo.sightingdb.data.SightingRequest
import com.devo.sightingdb.data.SightingWithStats
import com.devo.sightingdb.data.SightingWithoutStats
import com.devo.sightingdb.storage.Connector
import com.devo.sightingdb.storage.InMemoryConnector
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ApplicationTest {

    private lateinit var connector: Connector

    @BeforeEach
    fun setUp() {
        connector = InMemoryConnector()
    }

    @Test
    fun testWriteNew() = withTestApplication({
        mainModule(connector)
    }) {
        with(handleRequest(HttpMethod.Get, "/w/a/namespace/?val=abcd")) {
            assertEquals(HttpStatusCode.Created, response.status())
            val attr = connector.get("/a/namespace", "abcd")!!
            assertThat(attr.count.toInt(), equalTo(1))
        }
    }

    @Test
    fun testWriteExisting() = withTestApplication({
        mainModule(connector)
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
        mainModule(connector)
    }) {
        val items = (0 until 50).map { SightingRequest("/namespace/$it", it.toString()) }
        val call = handleRequest(HttpMethod.Post, "/wb") {
            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(Json.encodeToString(BulkSightingRequest(items)))
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
        mainModule(connector)
    }) {
        with(handleRequest(HttpMethod.Get, "/r/a/namespace/?val=abcd")) {
            assertEquals(HttpStatusCode.NotFound, response.status())
        }
    }

    @Test
    fun testReadExisting() = withTestApplication({
        mainModule(connector)
    }) {
        val namespace = "/a/namespace"
        val value = "abcd"
        connector.observe(namespace, value)
        with(handleRequest(HttpMethod.Get, "/r/a/namespace/?val=abcd")) {
            assertEquals(HttpStatusCode.OK, response.status())
            val attr: SightingWithoutStats = Json.decodeFromString(response.content!!)
            assertThat(attr, equalTo(connector.get(namespace, value)?.withoutStats()))
        }
    }

    @Test
    fun testReadNamespace() = withTestApplication({
        mainModule(connector)
    }) {
        val namespace = "/a/namespace"
        val items = (0 until 10).map {
            SightingRequest(namespace, it.toString()).also { sighting ->
                connector.observe(sighting.namespace, sighting.value)
            }
        }
        with(handleRequest(HttpMethod.Get, "/r/a/namespace")) {
            assertEquals(HttpStatusCode.OK, response.status())
            val read = Json.decodeFromString<Map<String, List<SightingWithoutStats>>>(response.content!!)["items"]!!
            assertEquals(items.size, read.size)
        }
    }

    @Test
    fun testReadBulkExisting() = withTestApplication({
        mainModule(connector)
    }) {
        val items = (0 until 50).map {
            SightingRequest("/namespace/$it", it.toString()).also { req ->
                connector.observe(req.namespace, req.value)
            }
        }
        val call = handleRequest(HttpMethod.Post, "/rb") {
            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(Json.encodeToString(BulkSightingRequest(items)))
        }
        with(call) {
            assertEquals(HttpStatusCode.OK, response.status())
            val read = Json.decodeFromString<Map<String, List<SightingWithoutStats>>>(response.content!!)
            val readItems = read["items"]!!
            assertThat(readItems.size, equalTo(items.size))
            assertThat(readItems.map { it.value }.toSet(), equalTo(items.map { it.value }.toSet()))
        }
    }

    @Test
    fun testReadStatsNew() = withTestApplication({
        mainModule(connector)
    }) {
        with(handleRequest(HttpMethod.Get, "/rs/a/namespace/?val=abcd")) {
            assertEquals(HttpStatusCode.NotFound, response.status())
        }
    }

    @Test
    fun testReadStatsExisting() = withTestApplication({
        mainModule(connector)
    }) {
        val namespace = "/a/namespace"
        val value = "abcd"
        connector.observe(namespace, value)
        with(handleRequest(HttpMethod.Get, "/rs/a/namespace/?val=abcd")) {
            assertEquals(HttpStatusCode.OK, response.status())
            val attr: SightingWithStats = Json.decodeFromString(response.content!!)
            assertThat(attr, equalTo(connector.get(namespace, value)))
        }
    }

    @Test
    fun testReadBulkStatsExisting() = withTestApplication({
        mainModule(connector)
    }) {
        val items = (0 until 50).map {
            SightingRequest("/namespace/$it", it.toString()).also { req ->
                connector.observe(req.namespace, req.value)
            }
        }
        val call = handleRequest(HttpMethod.Post, "/rbs") {
            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(Json.encodeToString(BulkSightingRequest(items)))
        }
        with(call) {
            assertEquals(HttpStatusCode.OK, response.status())
            val read = Json.decodeFromString<Map<String, List<SightingWithStats>>>(response.content!!)
            val readItems = read["items"]!!
            assertThat(readItems.size, equalTo(items.size))
            assertThat(readItems.map { it.value }.toSet(), equalTo(items.map { it.value }.toSet()))
            readItems.forEach { withStats ->
                assertThat(withStats.stats.size, equalTo(1))
            }
        }
    }
}
