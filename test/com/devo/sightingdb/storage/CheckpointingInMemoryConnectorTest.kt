package com.devo.sightingdb.storage

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.typesafe.config.ConfigFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class CheckpointingInMemoryConnectorTest {

    private lateinit var connector: CheckpointingInMemoryConnector
    private lateinit var tempPath: Path

    @BeforeEach
    fun setUp() {
        tempPath = Files.createTempDirectory("checkpointing-in-memory")
        val config = ConfigFactory.parseMap(
            mapOf(
                "path" to tempPath.toString(),
                "checkpointIntervalSeconds" to "3600"
            )
        )
        connector = CheckpointingInMemoryConnector().build(config) as CheckpointingInMemoryConnector
    }

    @AfterEach
    fun tearDown() {
        connector.close()
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

    @Test
    fun `Should back up and restore from disk`() {
        val path = Paths.get(tempPath.toString(), "attribute-cache.0")
        connector.observe("namespace", "value")
        connector.backup(tempPath.toString(), connector.map())
        val restored = connector.restore(path)
        assertThat(restored, equalTo(connector.map()))
    }
}
