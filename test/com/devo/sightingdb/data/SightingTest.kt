package com.devo.sightingdb.data

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

class SightingTest {

    @Test
    fun `Should keep track of counts by hour`() {
        val now = OffsetDateTime.now()
        val attr = Sighting.new("abcd", now)
        val incremented = attr.inc(now)
        assertThat(incremented.count, equalTo(BigInteger.valueOf(2)))
        val hourBucket = now.truncatedTo(ChronoUnit.HOURS)
        assertThat(incremented.stats, equalTo(mapOf(hourBucket to 2L)))
    }
}
