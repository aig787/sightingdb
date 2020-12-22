package com.devo.sightingdb.storage

import com.devo.sightingdb.data.Sighting
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.typesafe.config.Config
import io.ktor.config.ApplicationConfig
import io.ktor.config.HoconApplicationConfig
import mu.KotlinLogging
import java.time.OffsetDateTime

@Suppress("TooManyFunctions")
abstract class Connector {
    companion object {
        const val ALL = "/_all"
        const val SHADOW = "/_shadow"
    }

    private val commitLog = KotlinLogging.logger("commit-log")
    protected val jsonMapper: ObjectMapper = jacksonObjectMapper().findAndRegisterModules()

    abstract fun build(config: ApplicationConfig): Connector
    abstract fun getNamespaceConfig(namespace: String, key: String): String?
    abstract fun putNamespaceConfig(namespace: String, key: String, value: String)

    internal abstract fun write(namespace: String, sighting: Sighting)
    internal abstract fun read(namespace: String, value: String): Sighting?
    internal abstract fun readNamespace(namespace: String): List<Sighting>?
    internal abstract fun deleteNamespace(namespace: String): Boolean

    abstract fun delete(namespace: String, value: String): Boolean

    private fun Sighting.resolveConsensus(): Sighting {
        val consensus = read(ALL, value)?.consensus ?: 0
        return copy(consensus = consensus)
    }

    private fun writeAndLog(namespace: String, sighting: Sighting) {
        commitLog.info { "$namespace ${jsonMapper.writeValueAsString(sighting.copy(serializeWithStats = false))}" }
        write(namespace, sighting)
    }

    fun build(config: Config): Connector = build(HoconApplicationConfig(config))

    fun observe(
        namespace: String,
        value: String,
        time: OffsetDateTime = Sighting.now(),
        withConsensus: Boolean = true
    ) {
        val consensusSighting = when (val v = read(ALL, value)) {
            null -> Sighting.new(value, time)
            else -> v
        }
        val sighting = when (val v = read(namespace, value)) {
            null -> Sighting.new(value, time)
            else -> v.inc(time)
        }
        writeAndLog(namespace, sighting)
        if (withConsensus) {
            writeAndLog(ALL, consensusSighting.copy(consensus = consensusSighting.consensus + 1))
        }
    }

    fun get(namespace: String, value: String): Sighting? = read(namespace, value).also {
        if (it == null) {
            observe("$SHADOW$namespace", value, withConsensus = false)
        }
    }?.resolveConsensus()

    fun get(namespace: String): List<Sighting>? {
        return readNamespace(namespace)?.map { it.resolveConsensus() }
    }

    fun delete(namespace: String): Boolean = deleteNamespace(namespace)

    open fun close() {
        // noop
    }
}

open class ConnectorException(message: String, cause: Throwable?) : RuntimeException(message, cause)
