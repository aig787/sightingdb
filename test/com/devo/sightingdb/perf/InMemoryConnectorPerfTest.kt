package com.devo.sightingdb.perf

import com.devo.sightingdb.storage.InMemoryConnector
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

class InMemoryConnectorPerfTest {

    private lateinit var connector: InMemoryConnector

    @BeforeEach
    fun setUp() {
        connector = InMemoryConnector()
    }

    @ExperimentalCoroutinesApi
    @Test
    @Tag("PerfTest")
    fun `Should efficiently write and read ipv4 addresses`() {
        PerfSuite.readWriteIPv4(connector, 10)
    }
}
