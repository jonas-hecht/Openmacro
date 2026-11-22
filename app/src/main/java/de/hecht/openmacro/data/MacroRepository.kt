/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package de.hecht.openmacro.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import de.hecht.openmacro.model.Macro
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class MacroFile(val version: Int = 1, val macros: List<Macro> = emptyList())

/** Stores all macros in a single JSON file in the app's private storage. */
object MacroRepository {
    private const val TAG = "MacroRepository"

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    private lateinit var file: AtomicFile
    private val _macros = MutableStateFlow<List<Macro>>(emptyList())
    val macros: StateFlow<List<Macro>> = _macros.asStateFlow()

    fun init(context: Context) {
        val base = File(context.filesDir, "macros.json")
        file = AtomicFile(base)
        _macros.value = load(base)
    }

    fun get(id: String): Macro? = _macros.value.find { it.id == id }

    fun upsert(macro: Macro) = modify { list ->
        if (list.any { it.id == macro.id }) list.map { if (it.id == macro.id) macro else it }
        else list + macro
    }

    fun delete(id: String) = modify { list -> list.filterNot { it.id == id } }

    fun setEnabled(id: String, enabled: Boolean) = modify { list ->
        list.map { if (it.id == id) it.copy(enabled = enabled) else it }
    }

    @Synchronized
    private fun modify(transform: (List<Macro>) -> List<Macro>) {
        _macros.update(transform)
        save(_macros.value)
    }

    private fun load(base: File): List<Macro> {
        if (!base.exists()) return emptyList()
        return try {
            json.decodeFromString<MacroFile>(file.readFully().decodeToString()).macros
        } catch (e: Exception) {
            Log.e(TAG, "Could not read macros, keeping a copy of the broken file", e)
            base.copyTo(File(base.parentFile, "macros.json.broken"), overwrite = true)
            emptyList()
        }
    }

    private fun save(macros: List<Macro>) {
        val out = file.startWrite()
        try {
            out.write(json.encodeToString(MacroFile(macros = macros)).encodeToByteArray())
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            Log.e(TAG, "Could not save macros", e)
        }
    }
}
