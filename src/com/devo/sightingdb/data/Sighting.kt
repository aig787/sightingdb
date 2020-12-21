package com.devo.sightingdb.data

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import mu.KotlinLogging
import java.math.BigInteger
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

enum class Format {
    RAW, SHA256, BASE_64
}

@JsonSerialize(using = SightingSerializer::class)
data class Sighting(
    val value: String,
    @JsonProperty(value = "first_seen")
    val firstSeen: OffsetDateTime,
    @JsonProperty(value = "last_seen")
    val lastSeen: OffsetDateTime,
    val consensus: Long = 0,
    val count: BigInteger = BigInteger.ZERO,
    val tags: Map<String, String> = emptyMap(),
    val ttl: Duration = Duration.ZERO,
    @JsonIgnore
    val serializeWithStats: Boolean = true,
    val stats: Map<OffsetDateTime, Long> = emptyMap()
) {
    companion object {

        private val log = KotlinLogging.logger { }

        fun now(): OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)

        fun new(value: String, time: OffsetDateTime = now()): Sighting =
            Sighting(
                value, time, time, count = BigInteger.ONE, stats = mapOf(
                    time.truncatedTo(ChronoUnit.HOURS) to 1L
                )
            ).also {
                log.debug { "New sighting $it" }
            }
    }

    fun inc(time: OffsetDateTime = now()): Sighting {
        log.debug { "Incrementing $this" }
        val hourBucket = time.truncatedTo(ChronoUnit.HOURS)
        val hourCount = stats[hourBucket] ?: 0
        return copy(
            count = count.inc(),
            firstSeen = minOf(firstSeen, time),
            lastSeen = maxOf(lastSeen, time),
            stats = stats + (hourBucket to hourCount + 1)
        )
    }
}

class SightingSerializer : JsonSerializer<Sighting>() {
    override fun serialize(value: Sighting, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeStartObject()
        serializers.defaultSerializeField("value", value.value, gen)
        serializers.defaultSerializeField("first_seen", value.firstSeen, gen)
        serializers.defaultSerializeField("last_seen", value.lastSeen, gen)
        serializers.defaultSerializeField("consensus", value.consensus, gen)
        serializers.defaultSerializeField("count", value.count, gen)
        serializers.defaultSerializeField("tags", value.tags, gen)
        if (value.serializeWithStats) {
            serializers.defaultSerializeField("stats", value.stats, gen)
        }
        gen.writeEndObject()
    }
}
