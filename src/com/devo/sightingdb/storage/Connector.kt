package com.devo.sightingdb.storage

import com.devo.sightingdb.data.SightingWithStats
import com.typesafe.config.Config
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import java.time.LocalDateTime

@Suppress("TooManyFunctions")
abstract class Connector {
    companion object {
        const val ALL = "/_all"
        const val SHADOW = "/_shadow"
    }

    private val commitLog = KotlinLogging.logger("commit-log")

    abstract fun build(config: Config): Connector
    abstract fun getNamespaceConfig(namespace: String, key: String): String?
    abstract fun putNamespaceConfig(namespace: String, key: String, value: String)

    internal abstract fun write(namespace: String, sighting: SightingWithStats)
    internal abstract fun read(namespace: String, value: String): SightingWithStats?
    internal abstract fun readNamespace(namespace: String): List<SightingWithStats>?

    private fun SightingWithStats.resolveConsensus(): SightingWithStats {
        val consensus = read(ALL, value)?.consensus ?: 0
        return copy(consensus = consensus)
    }

    private fun writeAndLog(namespace: String, sighting: SightingWithStats) {
        commitLog.info { "$namespace ${Json.encodeToString(sighting.withoutStats())}" }
        write(namespace, sighting)
    }

    fun observe(
        namespace: String,
        value: String,
        time: LocalDateTime = LocalDateTime.now(),
        withConsensus: Boolean = true
    ) {
        val consensusSighting = when (val v = read(ALL, value)) {
            null -> SightingWithStats.new(value, time)
            else -> v
        }
        val sighting = when (val v = read(namespace, value)) {
            null -> SightingWithStats.new(value, time)
            else -> v.inc()
        }
        writeAndLog(namespace, sighting)
        if (withConsensus) {
            writeAndLog(ALL, consensusSighting.copy(consensus = consensusSighting.consensus + 1))
        }
    }

    fun get(namespace: String, value: String): SightingWithStats? = read(namespace, value).also {
        if (it == null) {
            observe("$SHADOW$namespace", value, withConsensus = false)
        }
    }?.resolveConsensus()

    fun get(namespace: String): List<SightingWithStats>? {
        return readNamespace(namespace)?.map { it.resolveConsensus() }
    }

    open fun close() {
        // noop
    }
}

open class ConnectorException(message: String, cause: Throwable?) : RuntimeException(message, cause)
