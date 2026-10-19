package com.example.hdify.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.example.hdify.logging.HdLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class DeviceThermalState {
    NORMAL,
    MODERATE,
    SEVERE,
    CRITICAL
}

data class DevicePowerStatus(
    val thermalState: DeviceThermalState = DeviceThermalState.NORMAL,
    val isBatterySaver: Boolean = false,
    val batteryPercent: Int = 100,
    val isCharging: Boolean = true
) {
    val isThrottlingRisk: Boolean
        get() = thermalState == DeviceThermalState.SEVERE || thermalState == DeviceThermalState.CRITICAL

    val isBatteryLowRisk: Boolean
        get() = !isCharging && batteryPercent < 15
}

@Singleton
class ThermalBatteryMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val _powerStatus = MutableStateFlow(DevicePowerStatus())
    val powerStatus: StateFlow<DevicePowerStatus> = _powerStatus.asStateFlow()

    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null
    private var batteryReceiver: BroadcastReceiver? = null

    fun startMonitoring() {
        stopMonitoring()
        // 1. Initial snapshot
        updatePowerStatus()

        // 2. Thermal status listener (Android 10+ / API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
            val listener = PowerManager.OnThermalStatusChangedListener { status ->
                val thermalState = when (status) {
                    PowerManager.THERMAL_STATUS_NONE,
                    PowerManager.THERMAL_STATUS_LIGHT -> DeviceThermalState.NORMAL
                    PowerManager.THERMAL_STATUS_MODERATE -> DeviceThermalState.MODERATE
                    PowerManager.THERMAL_STATUS_SEVERE -> DeviceThermalState.SEVERE
                    PowerManager.THERMAL_STATUS_CRITICAL,
                    PowerManager.THERMAL_STATUS_EMERGENCY,
                    PowerManager.THERMAL_STATUS_SHUTDOWN -> DeviceThermalState.CRITICAL
                    else -> DeviceThermalState.NORMAL
                }

                HdLogger.i(TAG, "Thermal status changed: status=$status ($thermalState)")
                _powerStatus.value = _powerStatus.value.copy(thermalState = thermalState)
            }
            thermalListener = listener
            try {
                powerManager.addThermalStatusListener(listener)
            } catch (e: Exception) {
                HdLogger.w(TAG, "Failed to register thermal status listener: ${e.message}")
            }
        }

        // 3. Battery status receiver
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                updatePowerStatus()
            }
        }
        batteryReceiver = receiver
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        try {
            context.registerReceiver(receiver, filter)
        } catch (e: Exception) {
            HdLogger.w(TAG, "Failed to register battery receiver: ${e.message}")
        }
    }

    fun stopMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermalListener != null && powerManager != null) {
            try {
                powerManager.removeThermalStatusListener(thermalListener!!)
            } catch (e: Exception) {
                HdLogger.w(TAG, "Failed to remove thermal status listener: ${e.message}")
            }
            thermalListener = null
        }

        if (batteryReceiver != null) {
            try {
                context.unregisterReceiver(batteryReceiver)
            } catch (e: Exception) {
                HdLogger.w(TAG, "Failed to unregister battery receiver: ${e.message}")
            }
            batteryReceiver = null
        }
    }

    private fun updatePowerStatus() {
        val isPowerSave = powerManager?.isPowerSaveMode ?: false

        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) ((level * 100) / scale) else 100

        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        _powerStatus.value = _powerStatus.value.copy(
            isBatterySaver = isPowerSave,
            batteryPercent = batteryPct,
            isCharging = isCharging
        )
    }

    companion object {
        private const val TAG = "ThermalBatteryMonitor"
    }
}
