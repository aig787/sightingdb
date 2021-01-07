package com.devo.sightingdb.storage

import com.devo.sightingdb.data.Sighting
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import java.time.Instant.ofEpochSecond
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

object ConnectorTestSuite {

    fun writeSighting(connector: Connector) {
        val time = OffsetDateTime.ofInstant(ofEpochSecond(0), ZoneId.of("UTC"))
        connector.observe("/a/namespace", "abcd", time)
        val s = connector.get("/a/namespace", "abcd")
        assertThat(s?.firstSeen, equalTo(time))
        assertThat(s?.lastSeen, equalTo(time))
    }

    fun readSighting(connector: Connector) {
        val expected = Sighting.new("abcd")
        connector.write("/a/namespace", expected)
        val read = connector.get("/a/namespace", "abcd")
        assertThat(read, equalTo(expected))
    }

    fun readNamespace(connector: Connector) {
        (0 until 5).forEach { connector.observe("/namespace/a", it.toString()) }
        (0 until 5).forEach { connector.observe("/namespace/b", it.toString()) }
        val readA = connector.get("/namespace/a")
        assertEquals(5, readA?.size)
        val readB = connector.get("/namespace/b")
        assertEquals(5, readB?.size)
    }

    fun insertShadow(connector: Connector) {
        connector.get("/namespace/a", "value")
        assertNotNull(connector.get("/_shadow/namespace/a", "value"))
    }

    fun insertConsensus(connector: Connector) {
        connector.observe("/namespace/a", "value")
        val consensus = connector.get("/_all", "value")
        assertEquals(1, consensus?.count?.toInt())
    }

    fun updateConsensus(connector: Connector) {
        connector.observe("/namespace/a", "value")
        connector.observe("/namespace/b", "value")
        val consensus = connector.get("/_all", "value")
        assertEquals(2, consensus?.consensus)
    }

    fun retrieveConsensus(connector: Connector) {
        connector.observe("/namespace/a", "value")
        connector.observe("/namespace/b", "value")
        val s = connector.get("/namespace/a", "value")
        assertEquals(2, s?.consensus)
    }
}
