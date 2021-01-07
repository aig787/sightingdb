package com.devo.sightingdb.storage

import com.devo.sightingdb.data.Sighting
import io.ktor.config.ApplicationConfig

open class InMemoryConnector : Connector() {

    protected val store = mutableMapOf<String, Namespace>()

    internal fun map() = store

    override fun write(namespace: String, sighting: Sighting) {
        store.getOrPut(namespace, { Namespace(namespace) }).put(sighting)
    }

    override fun read(namespace: String, value: String): Sighting? {
        return store[namespace]?.get(value)
    }

    override fun build(config: ApplicationConfig): Connector = this

    override fun getNamespaceConfig(namespace: String, key: String): String? =
        store[namespace]?.config?.get(key)

    override fun putNamespaceConfig(namespace: String, key: String, value: String) {
        store.getOrPut(namespace, { Namespace(namespace) }).config.put(key, value)
    }

    override fun readNamespace(namespace: String): List<Sighting>? =
        store[namespace]?.all()

    override fun delete(namespace: String, value: String): Boolean {
        return when (val n = store[namespace]) {
            null -> false
            else -> if (n.sightings.containsKey(value)) {
                n.sightings.remove(value)
                true
            } else false
        }
    }

    override fun deleteNamespace(namespace: String): Boolean =
        if (store.containsKey(namespace)) {
            store.remove(namespace)
            true
        } else {
            false
        }
}
