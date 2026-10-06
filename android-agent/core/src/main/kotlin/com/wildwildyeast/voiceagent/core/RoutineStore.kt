package com.wildwildyeast.voiceagent.core

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File

/** A routine that fits a spoken command, with the values for its slots. */
data class RoutineMatch(val routine: Routine, val slots: Map<Int, String> = emptyMap())

/** Persistence for learned routines. */
interface RoutineStore {
    fun all(): List<Routine>
    fun save(routine: Routine)
    fun delete(normalized: String)
    fun clear()

    /**
     * Best match for a spoken command, or null. A template match wins (it carries
     * the new slot values), then an exact normalized match, then high token
     * overlap among routines without slots.
     */
    fun find(command: String, minSimilarity: Double = 0.8): RoutineMatch? {
        val n = Routine.normalize(command)
        if (n.isBlank()) return null
        val routines = all()
        routines.filter { it.template != null }
            .sortedByDescending { it.template!!.count { c -> c == ' ' } }
            .forEach { r -> Slots.match(r.template!!, command)?.let { return RoutineMatch(r, it) } }
        routines.firstOrNull { it.template == null && it.normalized == n }?.let { return RoutineMatch(it) }
        return routines
            .filter { it.template == null }
            .map { it to Routine.similarity(n, it.normalized) }
            .filter { it.second >= minSimilarity }
            .maxByOrNull { it.second }
            ?.let { RoutineMatch(it.first) }
    }
}

class InMemoryRoutineStore : RoutineStore {
    private val items = LinkedHashMap<String, Routine>()
    override fun all(): List<Routine> = items.values.toList()
    override fun save(routine: Routine) { items[routine.normalized] = routine }
    override fun delete(normalized: String) { items.remove(normalized) }
    override fun clear() = items.clear()
}

/** Routines kept in one JSON file. Writes are atomic (temp file + rename). */
class JsonFileRoutineStore(private val file: File) : RoutineStore {
    private val mapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .enable(SerializationFeature.INDENT_OUTPUT)

    private data class Document(val routines: List<Routine> = emptyList())

    @Volatile
    private var cache: MutableMap<String, Routine>? = null

    @Synchronized
    private fun load(): MutableMap<String, Routine> {
        cache?.let { return it }
        val loaded = LinkedHashMap<String, Routine>()
        if (file.exists()) {
            runCatching { mapper.readValue<Document>(file) }
                .getOrNull()?.routines?.forEach { loaded[it.normalized] = it }
        }
        cache = loaded
        return loaded
    }

    @Synchronized
    private fun persist(items: Map<String, Routine>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        mapper.writeValue(tmp, Document(items.values.toList()))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    @Synchronized override fun all(): List<Routine> = load().values.toList()
    @Synchronized override fun save(routine: Routine) { val m = load(); m[routine.normalized] = routine; persist(m) }
    @Synchronized override fun delete(normalized: String) { val m = load(); m.remove(normalized); persist(m) }
    @Synchronized override fun clear() { val m = load(); m.clear(); persist(m) }
}
