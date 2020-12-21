package com.devo.sightingdb.storage

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.config.ApplicationConfig
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.time.delay
import mu.KotlinLogging
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class CheckpointingInMemoryConnector : InMemoryConnector() {

    private val log = KotlinLogging.logger { }
    private val running = AtomicBoolean(true)
    private val backupLock = ReentrantLock()
    private val mapper = ObjectMapper(CBORFactory()).findAndRegisterModules()
    private lateinit var checkpointJob: Job

    @Suppress("BlockingMethodInNonBlockingContext")
    override fun build(config: ApplicationConfig): Connector {
        super.build(config)
        checkpointJob = GlobalScope.launch {
            val path = config.property("path").getString()
            val intervalSeconds = config.property("checkpointIntervalSeconds").getString().toLong()
            while (running.get()) {
                delay(Duration.ofSeconds(intervalSeconds))
                backup(path, store)
            }
        }
        return this
    }

    internal fun restore(path: Path): Map<String, Namespace> {
        log.info { "Reading checkpoint from $path" }
        backupLock.withLock {
            return mapper.readValue(path.toFile())
        }
    }

    internal fun backup(backupPath: String, store: Map<String, Namespace>) {
        backupLock.withLock {
            val backupZero = Paths.get(backupPath, "attribute-cache.0")
            val backupOne = Paths.get(backupPath, "attribute-cache.1")
            if (backupZero.toFile().exists()) {
                log.info { "Moving $backupZero to $backupOne" }
                if (backupOne.toFile().exists()) {
                    Files.delete(backupOne)
                }
                Files.move(backupZero, backupOne)
            }
            log.info { "Checkpointing to $backupZero" }
            mapper.writeValue(backupZero.toFile(), store)
            log.info { "Finished writing checkpoint" }
        }
    }

    override fun close() {
        runBlocking {
            checkpointJob.cancelAndJoin()
        }
        super.close()
    }
}
