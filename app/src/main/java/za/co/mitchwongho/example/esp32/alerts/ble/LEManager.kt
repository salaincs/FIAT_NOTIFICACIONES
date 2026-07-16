package za.co.mitchwongho.example.esp32.alerts.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.preference.PreferenceManager
import no.nordicsemi.android.ble.BleManager
import timber.log.Timber
import za.co.mitchwongho.example.esp32.alerts.app.ForegroundService
import za.co.mitchwongho.example.esp32.alerts.app.SettingsActivity
import java.util.*

class LEManager(context: Context) : BleManager(context) {

    var espDisplayMessageCharacteristic: BluetoothGattCharacteristic? = null
    var espDisplayTimeCharacteristic: BluetoothGattCharacteristic? = null
    var espDisplayOrientationCharacteristic: BluetoothGattCharacteristic? = null

    private var _callback: BleManagerGattCallback? = null

    companion object {
        val MTU = 500
        val ESP_SERVICE_UUID = UUID.fromString("3db02924-b2a6-4d47-be1f-0f90ad62a048")
        val ESP_DISPLAY_MESSAGE_CHARACTERISITC_UUID = UUID.fromString("8d8218b6-97bc-4527-a8db-13094ac06b1d")
        val ESP_DISPLAY_TIME_CHARACTERISITC_UUID = UUID.fromString("b7b0a14b-3e94-488f-b262-5d584a1ef9e1")
        val ESP_DISPLAY_ORIENTATION_CHARACTERISITC_UUID = UUID.fromString("0070b87e-d825-43f5-be0c-7d86f75e4900")

        fun readBatteryLevel(context: Context): Int {
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus: Intent? = context.registerReceiver(null, intentFilter)
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            return if (level != -1 && scale != -1) ((level.toFloat() / scale.toFloat()) * 100f).toInt() else -1
        }

        fun isCharging(context: Context): Boolean {
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus: Intent? = context.registerReceiver(null, intentFilter)
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        }
    }

    override fun getGattCallback(): BleManagerGattCallback {
        if (_callback == null) {
            _callback = object : BleManagerGattCallback() {
                override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
                    val gattService: BluetoothGattService? = gatt.getService(ESP_SERVICE_UUID)
                    if (espDisplayMessageCharacteristic == null) {
                        espDisplayMessageCharacteristic = gattService?.getCharacteristic(ESP_DISPLAY_MESSAGE_CHARACTERISITC_UUID)
                    }
                    if (espDisplayTimeCharacteristic == null) {
                        espDisplayTimeCharacteristic = gattService?.getCharacteristic(ESP_DISPLAY_TIME_CHARACTERISITC_UUID)
                    }
                    if (espDisplayOrientationCharacteristic == null) {
                        espDisplayOrientationCharacteristic = gattService?.getCharacteristic(ESP_DISPLAY_ORIENTATION_CHARACTERISITC_UUID)
                    }
                    return gattService != null && espDisplayMessageCharacteristic != null && espDisplayTimeCharacteristic != null && espDisplayOrientationCharacteristic != null
                }

                override fun initialize() {
                    val batteryLevelPercent = readBatteryLevel(context)
                    writeTimeAndBatteryLevel(batteryLevelPercent, ForegroundService.formatter.format(Date()))
                    
                    // Enviar estado de carga inicial
                    val chargeStatus = if (isCharging(context)) "Cargando" else "Descargando"
                    writeNotification("POWER|$chargeStatus")
                }

                override fun onServicesInvalidated() {
                    espDisplayMessageCharacteristic = null
                    espDisplayTimeCharacteristic = null
                    espDisplayOrientationCharacteristic = null
                }
            }
        }
        return _callback!!
    }

    fun writeNotification(message: String): Boolean {
        return write(message)
    }

    fun writeTimeAndBatt(message: String): Boolean {
        val batteryLevelPercent = readBatteryLevel(context)
        return writeTimeAndBatteryLevel(batteryLevelPercent, message)
    }

    private fun write(message: String): Boolean {
        return if (isConnected && espDisplayMessageCharacteristic != null) {
            requestMtu(MTU).enqueue()
            writeCharacteristic(espDisplayMessageCharacteristic, message.toByteArray(), BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT).enqueue()
            true
        } else false
    }

    private fun writeTimeAndBatteryLevel(battLevel: Int, message: String): Boolean {
        return if (isConnected && espDisplayTimeCharacteristic != null) {
            requestMtu(MTU).enqueue()
            writeCharacteristic(espDisplayTimeCharacteristic, (battLevel.toChar() + message).toByteArray(), BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT).enqueue()
            true
        } else false
    }

    override fun shouldAutoConnect(): Boolean {
        return true
    }
}