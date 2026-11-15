package app.merachride.data

import android.util.AtomicFile
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * A list of items kept in memory as a StateFlow and saved to one JSON file in the app's private
 * storage. Writes go through [AtomicFile], so a crash mid-save can't leave a half-written file.
 */
class JsonFileStore<T>(
    file: File,
    private val scope: CoroutineScope,
    private val encode: (List<T>) -> String,
    decode: (String) -> List<T>,
) {
    private val atomic = AtomicFile(file)
    private val writeLock = Mutex()

    // The files are small (a few KB), so loading them once at startup is fine.
    private val _items = MutableStateFlow(load(file, decode))
    val items: StateFlow<List<T>> = _items.asStateFlow()

    fun update(change: (List<T>) -> List<T>) {
        _items.update(change)
        scope.launch(Dispatchers.IO) {
            writeLock.withLock {
                // Writes whatever is current, so a burst of edits always ends with the latest list on disk.
                val text = encode(_items.value)
                val out = atomic.startWrite()
                try {
                    out.write(text.toByteArray(Charsets.UTF_8))
                    atomic.finishWrite(out)
                } catch (e: Exception) {
                    atomic.failWrite(out)
                    Log.e(TAG, "Saving ${atomic.baseFile.name} failed", e)
                }
            }
        }
    }

    private fun load(file: File, decode: (String) -> List<T>): List<T> {
        if (!file.exists()) return emptyList()
        return try {
            decode(String(atomic.readFully(), Charsets.UTF_8))
        } catch (e: Exception) {
            // Keep the unreadable file for diagnosis instead of overwriting it on the next save.
            Log.e(TAG, "Reading ${file.name} failed; keeping a copy", e)
            file.copyTo(File(file.parentFile, "${file.name}.broken"), overwrite = true)
            emptyList()
        }
    }

    private companion object {
        const val TAG = "MerachRide"
    }
}
