package com.devo.sightingdb.perf

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import com.devo.sightingdb.data.SightingWithStats
import com.devo.sightingdb.storage.Connector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import kotlin.random.Random
import kotlin.system.measureTimeMillis
import kotlin.test.assertTrue

object PerfSuite {

    private fun generateIPv4(count: Int, start: Int = 0) = sequence {
        val s = Int.MIN_VALUE + start
        (s..(s + count)).forEach { i -> yield(i.toIp()) }
    }

    fun Int.toIp(): String {
        val b1 = (this shr 24) and 0xff
        val b2 = (this shr 16) and 0xff
        val b3 = (this shr 8) and 0xff
        val b4 = this and 0xff
        return "$b1.$b2.$b3.$b4"
    }

    @ExperimentalCoroutinesApi
    fun CoroutineScope.produceIps(count: Int, start: Int = 0) = produce {
        generateIPv4(count, start).forEach {
            send(it)
        }
    }

    private fun CoroutineScope.launchIpWriter(
        connector: Connector,
        namespaces: List<String>,
        channel: ReceiveChannel<String>
    ) = launch {
        for (ip in channel) {
            connector.observe(namespaces.random(), ip)
        }
    }

    @ExperimentalCoroutinesApi
    private fun runSegment(
        connector: Connector,
        namespaces: List<String>,
        start: Int,
        count: Int,
        iterations: Int
    ): List<Long> =
        (0 until iterations).map { i ->
            println("Iteration $i")
            runBlocking {
                val time = measureTimeMillis {
                    val jobs = mutableListOf<Job>()
                    val ips = produceIps(count, start)
                    repeat(10) { jobs.add(launchIpWriter(connector, namespaces, ips)) }
                    jobs.forEach { it.join() }
                }
                println("Took $time millis")
                time
            }
        }

    @ExperimentalCoroutinesApi
    fun writeIPv4(connector: Connector, namespaces: List<String>): Int {
        val segments = 2
        val iterations = 2
        val count = 500000
        println("Running $iterations iterations on $segments segments of $count")
        (LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger).level = Level.WARN
        val time = (0 until segments).map {
            println("Segment $it")
            runSegment(connector, namespaces, it * count, count, iterations)
        }.flatten().sum()
        println("Took $time millis in total")
        return count
    }

    @ExperimentalCoroutinesApi
    fun readWriteIPv4(connector: Connector, namespaceCount: Int) {
        writeIPv4(connector, (0 until namespaceCount).map { "/namespace/$it" })
        val namespace = "/namespace/${Random.nextInt(0, namespaceCount)}"
        var namespaceValues: List<SightingWithStats>
        val readNamespaceMillis = measureTimeMillis {
            namespaceValues = connector.get(namespace)!!
            assertTrue(namespaceValues.isNotEmpty())
        }
        println("Read namespace $namespace in $readNamespaceMillis millis")
        var value: SightingWithStats
        val readMillis = measureTimeMillis {
            value = connector.get(namespace, namespaceValues.random().value)!!
        }
        println("Read $value from $namespace in $readMillis millis")
    }
}
