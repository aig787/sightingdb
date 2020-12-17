package com.devo.sightingdb.data

import com.devo.sightingdb.serde.BigIntegerSerializer
import com.devo.sightingdb.serde.DurationSerializer
import com.devo.sightingdb.serde.LocalDateTimeSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import java.math.BigInteger
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

enum class Format {
    RAW, SHA256, BASE_64
}

interface Sighting {
    val value: String
    val firstSeen: LocalDateTime
    val lastSeen: LocalDateTime
    val consensus: Long
    val count: BigInteger
    val tags: Map<String, String>
    val ttl: Duration
}

@Serializable
data class SightingWithStats(
    override val value: String,
    @Serializable(with = LocalDateTimeSerializer::class)
    @SerialName("first_seen")
    override val firstSeen: LocalDateTime,
    @Serializable(with = LocalDateTimeSerializer::class)
    @SerialName("last_seen")
    override val lastSeen: LocalDateTime,
    override val consensus: Long = 0,
    @Serializable(with = BigIntegerSerializer::class)
    override val count: BigInteger = BigInteger.ZERO,
    override val tags: Map<String, String> = emptyMap(),
    @Serializable(with = DurationSerializer::class)
    override val ttl: Duration = Duration.ZERO,
    val stats: Map<String, Long> = emptyMap()
) : Sighting {
    companion object {

        private val log = KotlinLogging.logger { }

        fun new(value: String, time: LocalDateTime = LocalDateTime.now()): SightingWithStats =
            SightingWithStats(
                value, time, time, count = BigInteger.ONE, stats = mapOf(
                    time.truncatedTo(ChronoUnit.HOURS).toString() to 1L
                )
            ).also {
                log.debug { "New sighting $it" }
            }
    }

    fun inc(time: LocalDateTime = LocalDateTime.now()): SightingWithStats {
        log.debug { "Incrementing $this" }
        val hourBucket = time.truncatedTo(ChronoUnit.HOURS).toString()
        val hourCount = stats[hourBucket] ?: 0
        return copy(
            count = count.inc(),
            firstSeen = minOf(firstSeen, time),
            lastSeen = maxOf(lastSeen, time),
            stats = stats + (hourBucket to hourCount + 1)
        )
    }

    fun withoutStats(): SightingWithoutStats = SightingWithoutStats(
        value, firstSeen, lastSeen, consensus, count, tags, ttl
    )
}

@Serializable
data class SightingWithoutStats(
    override val value: String,
    @Serializable(with = LocalDateTimeSerializer::class)
    @SerialName("first_seen")
    override val firstSeen: LocalDateTime,
    @Serializable(with = LocalDateTimeSerializer::class)
    @SerialName("last_seen")
    override val lastSeen: LocalDateTime,
    override val consensus: Long = 0,
    @Serializable(with = BigIntegerSerializer::class)
    override val count: BigInteger = BigInteger.ZERO,
    override val tags: Map<String, String> = emptyMap(),
    @Serializable(with = DurationSerializer::class)
    override val ttl: Duration = Duration.ZERO
) : Sighting
