package com.devo.sightingdb.perf

import com.devo.sightingdb.storage.RocksDBConnector
import com.typesafe.config.ConfigFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files

class RocksDBConnectorPerfTest {

    private lateinit var connector: RocksDBConnector

    @BeforeEach
    fun setUp() {
        val path = Files.createTempDirectory("rocks-connector-perf")
        connector =
            RocksDBConnector().build(ConfigFactory.parseMap(mapOf("path" to path.toString()))) as RocksDBConnector
    }

    @ExperimentalCoroutinesApi
    @Test
    @Tag("PerfTest")
    fun `Should efficiently write and read ipv4 addresses`() {
        PerfSuite.readWriteIPv4(connector, 10)
    }
}
