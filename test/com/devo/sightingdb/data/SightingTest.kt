package com.devo.sightingdb.data

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class SightingTest {

    @Test
    fun `Should keep track of counts by hour`() {
        val now = LocalDateTime.now()
        val attr = SightingWithStats.new("abcd", now)
        val incremented = attr.inc(now)
        assertThat(incremented.count, equalTo(BigInteger.valueOf(2)))
        val hourBucket = now.truncatedTo(ChronoUnit.HOURS)
        assertThat(incremented.stats, equalTo(mapOf(hourBucket.toString() to 2L)))
    }
}
