package app.merachride.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import app.merachride.protocol.FtmsUuids
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * Coroutine wrapper around a single BluetoothGatt connection.
 *
 * Android allows only one GATT operation in flight at a time, so every write goes through
 * [runOp], which serialises operations and suspends until the matching callback arrives.
 */
@SuppressLint("MissingPermission") // The UI only gets here after BLUETOOTH_CONNECT is granted.
class BleConnection(
    private val context: Context,
    /** The service the device must have (FTMS for the bike, Heart Rate for a monitor). */
    private val requiredService: UUID,
    private val log: (String) -> Unit,
) {
    class Notification(val uuid: UUID, val value: ByteArray)

    private val _connected = MutableStateFlow(false)
    /** True once the link is up AND services have been discovered. */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _notifications = MutableSharedFlow<Notification>(extraBufferCapacity = 64)
    /** Notifications and indications from any subscribed characteristic. */
    val notifications: SharedFlow<Notification> = _notifications.asSharedFlow()

    @Volatile private var gatt: BluetoothGatt? = null
    @Volatile private var discovery: CompletableDeferred<Boolean>? = null
    @Volatile private var pendingOp: CompletableDeferred<Boolean>? = null
    private val opMutex = Mutex()

    private val callback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            val current = gatt
            if (current != null && current !== g) {
                g.close() // Stale callback from an earlier connection.
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                log("Link up, discovering services")
                if (!g.discoverServices()) {
                    log("discoverServices() refused")
                    g.disconnect()
                }
            } else {
                log("Link down (status $status)")
                g.close()
                if (gatt === g) gatt = null
                markDisconnected()
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val hasService = g.getService(requiredService) != null
            val ok = status == BluetoothGatt.GATT_SUCCESS && hasService
            if (!ok) log("Service discovery failed (status $status, ${short(requiredService)} present: $hasService)")
            _connected.value = ok
            discovery?.complete(ok)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            pendingOp?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            pendingOp?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        // Android 13+ calls this one...
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            _notifications.tryEmit(Notification(c.uuid, value.copyOf()))
        }

        // ...and Android 12 and older call this one. On 13+ it only runs if the override above calls
        // super, which it doesn't; the version check guards against double delivery anyway.
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            val value = c.value ?: return
            _notifications.tryEmit(Notification(c.uuid, value.copyOf()))
        }
    }

    /** Connects and discovers services. Returns true if [requiredService] is available. */
    suspend fun connect(device: BluetoothDevice, timeoutMs: Long = 15_000): Boolean {
        disconnect()
        val result = CompletableDeferred<Boolean>()
        discovery = result
        val g = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        if (g == null) {
            log("connectGatt() returned null")
            return false
        }
        gatt = g
        val ok = withTimeoutOrNull(timeoutMs) { result.await() } ?: false
        discovery = null
        if (!ok) {
            log("Connect failed or timed out")
            disconnect()
        }
        return ok
    }

    fun disconnect() {
        val g = gatt ?: return
        gatt = null
        // Either call can throw if Android's Bluetooth service has already dropped this connection.
        try {
            g.disconnect()
        } catch (e: Exception) {
            log("disconnect() threw $e")
        }
        try {
            g.close() // close() means no further callbacks, so mark state ourselves.
        } catch (e: Exception) {
            log("close() threw $e")
        }
        markDisconnected()
    }

    private fun markDisconnected() {
        _connected.value = false
        discovery?.complete(false)
        pendingOp?.complete(false)
    }

    /** Turns on notifications (or indications) for a characteristic by writing its CCCD. */
    suspend fun enableNotifications(service: UUID, uuid: UUID, indicate: Boolean): Boolean {
        val g = gatt ?: return false
        val c = g.getService(service)?.getCharacteristic(uuid)
        if (c == null) {
            log("Characteristic ${short(uuid)} not found")
            return false
        }
        if (!g.setCharacteristicNotification(c, true)) {
            log("setCharacteristicNotification(${short(uuid)}) refused")
            return false
        }
        val cccd = c.getDescriptor(FtmsUuids.CCCD)
        if (cccd == null) {
            log("No CCCD on ${short(uuid)}")
            return false
        }
        val value = if (indicate) {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        }
        return runOp("subscribe ${short(uuid)}") { writeDescriptorCompat(it, cccd, value) }
    }

    /** Write-with-response to a characteristic. */
    suspend fun write(service: UUID, uuid: UUID, value: ByteArray): Boolean {
        val g = gatt ?: return false
        val c = g.getService(service)?.getCharacteristic(uuid)
        if (c == null) {
            log("Characteristic ${short(uuid)} not found")
            return false
        }
        return runOp("write ${short(uuid)}") {
            writeCharacteristicCompat(it, c, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        }
    }

    private suspend fun runOp(name: String, start: (BluetoothGatt) -> Boolean): Boolean = opMutex.withLock {
        val g = gatt ?: return@withLock false
        val done = CompletableDeferred<Boolean>()
        pendingOp = done
        var dead = false
        val started = try {
            start(g)
        } catch (e: Exception) {
            log("$name threw $e")
            dead = isDeadLink(e)
            false
        }
        if (!started) {
            pendingOp = null
            log("$name could not start")
            if (dead) {
                // Android's Bluetooth service has dropped this connection without calling
                // onConnectionStateChange, so nothing else would notice (issue #2). Drop it ourselves:
                // connected goes false and the owner's auto-reconnect starts. UNVERIFIED on the phone.
                log("Bluetooth dropped the connection without saying so; reconnecting")
                disconnect()
            }
            return@withLock false
        }
        val ok = withTimeoutOrNull(OP_TIMEOUT_MS) { done.await() } ?: false
        pendingOp = null
        if (!ok) log("$name failed or timed out")
        ok
    }

    @Suppress("DEPRECATION")
    private fun writeCharacteristicCompat(
        g: BluetoothGatt,
        c: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: Int,
    ): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(c, value, writeType) == BluetoothStatusCodes.SUCCESS
        } else {
            c.writeType = writeType
            c.value = value
            g.writeCharacteristic(c)
        }

    @Suppress("DEPRECATION")
    private fun writeDescriptorCompat(g: BluetoothGatt, d: BluetoothGattDescriptor, value: ByteArray): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(d, value) == BluetoothStatusCodes.SUCCESS
        } else {
            d.value = value
            g.writeDescriptor(d)
        }

    private fun short(uuid: UUID): String = "0x" + uuid.toString().substring(4, 8).uppercase()

    private companion object {
        const val OP_TIMEOUT_MS = 5_000L
    }
}

/**
 * True if [e] (or anything it wraps) is a DeadObjectException: the app's handle on Android's Bluetooth
 * service is gone, so this GATT connection can't be used again. Seen as
 * `RuntimeException: android.os.DeadObjectException` from writeCharacteristic (issue #2, 2026-11-19).
 */
internal fun isDeadLink(e: Throwable): Boolean =
    generateSequence(e) { it.cause }.take(10).any { it is android.os.DeadObjectException }
