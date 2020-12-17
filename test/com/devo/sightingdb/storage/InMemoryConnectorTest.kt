package com.devo.sightingdb.storage

import com.typesafe.config.ConfigFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InMemoryConnectorTest {

    private lateinit var connector: InMemoryConnector

    @BeforeEach
    fun setUp() {
        connector = InMemoryConnector().build(ConfigFactory.empty()) as InMemoryConnector
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
