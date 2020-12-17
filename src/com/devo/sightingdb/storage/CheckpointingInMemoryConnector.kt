package com.devo.sightingdb.storage

import com.typesafe.config.Config
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.encodeToHexString
import mu.KotlinLogging
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class CheckpointingInMemoryConnector : InMemoryConnector() {

    private val log = KotlinLogging.logger { }
    private val running = AtomicBoolean(true)
    private val backupLock = ReentrantLock()
    private lateinit var checkpointJob: Job

    @ExperimentalSerializationApi
    @Suppress("BlockingMethodInNonBlockingContext")
    override fun build(config: Config): Connector {
        super.build(config)
        checkpointJob = GlobalScope.launch {
            val path = config.getString("path")
            val intervalMillis = config.getDuration("checkpointInterval").toMillis()
            while (running.get()) {
                delay(intervalMillis)
                backup(path, store)
            }
        }
        return this
    }

    @ExperimentalSerializationApi
    internal fun restore(path: Path): MutableMap<String, Namespace> {
        log.info { "Reading checkpoint from $path" }
        backupLock.withLock {
            val map = mutableMapOf<String, Namespace>()
            FileInputStream(path.toFile()).bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val parts = line.split(" ")
                    assert(parts.size == 2)
                    map[parts[0]] = Cbor.decodeFromHexString(parts[1])
                }
            }
            return map
        }
    }

    @ExperimentalSerializationApi
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
            FileOutputStream(backupZero.toFile()).bufferedWriter().use { fos ->
                store.forEach { (name, namespace) ->
                    fos.appendLine("$name ${Cbor.encodeToHexString(namespace)}")
                }
            }
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
