package com.devo.sightingdb.perf

import com.devo.sightingdb.storage.CheckpointingInMemoryConnector
import com.typesafe.config.ConfigFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import kotlin.system.measureTimeMillis

class CheckpointingInMemoryConnectorPerfTest {

    private lateinit var connector: CheckpointingInMemoryConnector
    private lateinit var workingDir: String

    @BeforeEach
    fun setUp() {
        workingDir = Files.createTempDirectory("checkpointing-perf").toString()
        connector = CheckpointingInMemoryConnector().build(
            ConfigFactory.parseMap(
                mapOf(
                    "path" to workingDir,
                    "checkpointIntervalSeconds" to Duration.ofHours(1)
                )
            )
        ) as CheckpointingInMemoryConnector
    }

    @AfterEach
    fun tearDown() {
        connector.close()
    }

    @ExperimentalCoroutinesApi
    @Test
    @Tag("PerfTest")
    fun `Should efficiently write and read ipv4 addresses`() {
        PerfSuite.readWriteIPv4(connector, 10)
        connector.close()
        val backupMillis = measureTimeMillis {
            connector.backup(workingDir, connector.map())
        }
        println("Backed up store in $backupMillis millis")
        // Clear the map to reclaim heap
        connector.map().clear()
        val restoreMillis = measureTimeMillis {
            connector.restore(Paths.get(workingDir, "attribute-cache.0"))
        }
        println("Restored store in $restoreMillis millis")
    }
}
