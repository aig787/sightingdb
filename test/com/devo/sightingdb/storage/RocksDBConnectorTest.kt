package com.devo.sightingdb.storage

import com.typesafe.config.ConfigFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files

class RocksDBConnectorTest {

    private lateinit var connector: RocksDBConnector

    @BeforeEach
    fun setUp() {
        val path = Files.createTempDirectory("rocks-connector-test")
        connector =
            RocksDBConnector().build(ConfigFactory.parseMap(mapOf("path" to path.toString()))) as RocksDBConnector
    }

    @Test
    fun `Should write sighting`() {
        ConnectorTestSuite.writeSighting(connector)
    }

    @Test
    fun `Should read sighting`() {
        ConnectorTestSuite.readSighting(connector)
    }

    @Test
    fun `Should read namespace`() {
        ConnectorTestSuite.readNamespace(connector)
    }

    @Test
    fun `Should insert into _shadow`() {
        ConnectorTestSuite.insertShadow(connector)
    }

    @Test
    fun `Should insert consensus into _all`() {
        ConnectorTestSuite.insertConsensus(connector)
    }

    @Test
    fun `Should update consensus`() {
        ConnectorTestSuite.updateConsensus(connector)
    }

    @Test
    fun `Should retrieve consensus on get`() {
        ConnectorTestSuite.retrieveConsensus(connector)
    }
}
