package com.devo.sightingdb.storage

import com.devo.sightingdb.data.Sighting
import com.devo.sightingdb.toHex
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.common.hash.Hashing
import io.ktor.config.ApplicationConfig
import mu.KotlinLogging
import org.mapdb.DB
import org.mapdb.DBMaker
import org.mapdb.Serializer
import org.rocksdb.BlockBasedTableConfig
import org.rocksdb.ColumnFamilyDescriptor
import org.rocksdb.ColumnFamilyHandle
import org.rocksdb.ColumnFamilyOptions
import org.rocksdb.CompactionPriority
import org.rocksdb.CompactionStyle
import org.rocksdb.CompressionType
import org.rocksdb.DBOptions
import org.rocksdb.LRUCache
import org.rocksdb.RocksDB
import org.rocksdb.WriteOptions
import org.rocksdb.util.SizeUnit
import java.nio.file.Paths

@Suppress("TooManyFunctions")
class RocksDBConnector : Connector() {

    @Suppress("UnstableApiUsage")
    companion object {
        private const val PREFIX_BYTES = 12
        private const val WRITE_BUFFER_SIZE = 512 * SizeUnit.MB
        private const val BYTES_PER_SYNC = 1048576L
        private const val MAX_WRITE_BUFFER = 10
        private const val MAX_BACKGROUND_COMPACTIONS = 5
        private const val MIN_BUFFER_TO_MERGE = 2
        private const val CACHE_BYTES = 512 * SizeUnit.MB
        private const val CACHE_SHARD_BITS = 4

        private val hf_128 = Hashing.murmur3_128()
        private val charset = Charsets.UTF_8
    }

    init {
        RocksDB.loadLibrary()
    }

    private lateinit var db: RocksDB
    private lateinit var writeOptions: WriteOptions
    private lateinit var metaDB: DB
    private lateinit var columnFamilyNames: MutableSet<String>

    private val cfOptions = ColumnFamilyOptions().apply {
        val tableOptions = BlockBasedTableConfig().apply {
            val cache = LRUCache(CACHE_BYTES, CACHE_SHARD_BITS)
            setBlockCache(cache)
        }
        optimizeUniversalStyleCompaction()
        setTableFormatConfig(tableOptions)
        useFixedLengthPrefixExtractor(PREFIX_BYTES)
        setLevelCompactionDynamicLevelBytes(true)
        setWriteBufferSize(WRITE_BUFFER_SIZE)
        setMaxWriteBufferNumber(MAX_WRITE_BUFFER)
        setMinWriteBufferNumberToMerge(MIN_BUFFER_TO_MERGE)
        setCompactionStyle(CompactionStyle.UNIVERSAL)
        setCompactionPriority(CompactionPriority.MinOverlappingRatio)
        setCompressionType(CompressionType.LZ4_COMPRESSION)
    }

    private val log = KotlinLogging.logger { }
    private val mapper = ObjectMapper(CBORFactory()).findAndRegisterModules()
    private val columnFamilyHandles = mutableMapOf<String, ColumnFamilyHandle>()

    override fun build(config: ApplicationConfig): Connector {
        val path = config.property("path").getString()

        metaDB = DBMaker.fileDB(Paths.get(path, "meta").toFile()).fileMmapEnable().make()
        columnFamilyNames = metaDB.hashSet("cfnames").serializer(Serializer.STRING).createOrOpen()

        val options = DBOptions().apply {
            setUnorderedWrite(true)
            setAllowMmapReads(true)
            setAllowMmapWrites(true)
            setCreateIfMissing(true)
            setCreateMissingColumnFamilies(true)
            setMaxOpenFiles(-1)
            setMaxBackgroundCompactions(MAX_BACKGROUND_COMPACTIONS)
            setBytesPerSync(BYTES_PER_SYNC)
        }
        writeOptions = WriteOptions().apply {
            disableWAL()
        }

        val openedHandles = mutableListOf<ColumnFamilyHandle>()
        db = RocksDB.open(
            options,
            Paths.get(path, "rocks").toString(),
            readColumnFamilyDescriptors(columnFamilyNames),
            openedHandles
        )
        columnFamilyHandles.putAll(openedHandles.map { it.name.decodeToString() to it })
        return this
    }

    override fun close() {
        db.close()
    }

    @ExperimentalUnsignedTypes
    override fun write(namespace: String, sighting: Sighting) {
        val key = getKey(sighting.value)
        log.trace { "Writing $sighting to $namespace/${key.toHex()}" }
        write(namespace, key, serialize(sighting.copy(serializeWithStats = true)))
    }

    private fun write(namespace: String, key: ByteArray, value: ByteArray) {
        if (!columnFamilyHandles.containsKey(namespace)) {
            log.info { "Creating column family $namespace" }
            columnFamilyHandles[namespace] =
                db.createColumnFamily(ColumnFamilyDescriptor(namespace.encodeToByteArray(), cfOptions))
            columnFamilyNames.add(namespace)
        }
        db.put(columnFamilyHandles[namespace], writeOptions, key, value)
    }

    private fun delete(namespace: String, key: ByteArray) {
        columnFamilyHandles[namespace]?.run {
            db.delete(this, writeOptions, key)
        }
    }

    private fun get(namespace: String, key: ByteArray): ByteArray? =
        if (columnFamilyHandles.containsKey(namespace)) {
            db.get(columnFamilyHandles[namespace]!!, key)
        } else {
            null
        }

    override fun read(namespace: String, value: String): Sighting? =
        if (!columnFamilyHandles.containsKey(namespace)) {
            null
        } else {
            db.get(columnFamilyHandles[namespace], getKey(value))?.let { deserialize(it) }
        }

    private fun getKey(value: String): ByteArray = hash128(value)

    private fun serialize(sighting: Sighting): ByteArray = mapper.writeValueAsBytes(sighting)

    private fun deserialize(bytes: ByteArray): Sighting = mapper.readValue(bytes)

    override fun readNamespace(namespace: String): List<Sighting>? {
        return if (!columnFamilyHandles.containsKey(namespace)) {
            null
        } else {
            db.newIterator(columnFamilyHandles[namespace]).use {
                val sightings = mutableListOf<Sighting>()
                it.seekToFirst()
                while (it.isValid) {
                    sightings.add(deserialize(it.value()))
                    it.next()
                }
                return sightings
            }
        }
    }

    override fun delete(namespace: String, value: String): Boolean {
        val key = getKey(value)
        return get(namespace, key)?.let {
            delete(namespace, key)
            true
        } ?: false
    }

    override fun deleteNamespace(namespace: String): Boolean =
        columnFamilyHandles[namespace]?.let {
            db.dropColumnFamily(it)
            columnFamilyHandles.remove(namespace)
            true
        } ?: false

    override fun getNamespaceConfig(namespace: String, key: String): String? {
        TODO("Not yet implemented")
    }

    override fun putNamespaceConfig(namespace: String, key: String, value: String) {
        TODO("Not yet implemented")
    }

    private fun readColumnFamilyDescriptors(columnFamilyNames: Set<String>): List<ColumnFamilyDescriptor> {
        return if (columnFamilyNames.isEmpty()) {
            listOf("default", ALL, "/_config")
        } else {
            columnFamilyNames
        }.map {
            ColumnFamilyDescriptor(it.encodeToByteArray(), cfOptions)
        }
    }

    @Suppress("UnstableApiUsage")
    private fun hash128(value: String): ByteArray =
        hf_128.newHasher().putString(value, charset).hash().asBytes()
}
