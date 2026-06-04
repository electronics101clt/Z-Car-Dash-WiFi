package com.cardash.integration.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.RemoteViews
import com.cardash.integration.MainActivity
import com.cardash.integration.R
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Base class for all dashboard widgets with shared functionality
 */
abstract class BaseWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ESP32_URL = "http://192.168.4.1/api/data"
        val executor = Executors.newSingleThreadExecutor()
        val mainHandler = Handler(Looper.getMainLooper())

        // Warning thresholds
        const val FUEL_WARNING = 15
        const val COOLANT_WARNING_HIGH = 105
        const val COOLANT_WARNING_LOW = 60
        const val VOLTAGE_WARNING_LOW = 11.5
        const val VOLTAGE_WARNING_HIGH = 15.0
        const val OIL_WARNING_HIGH = 120
        const val RPM_WARNING = 6500

        // Color constants
        const val COLOR_SPEED = 0xFF00FF88.toInt()
        const val COLOR_RPM = 0xFFFF9500.toInt()
        const val COLOR_RPM_WARNING = 0xFFFF0000.toInt()
        const val COLOR_COOLANT = 0xFF00D9FF.toInt()
        const val COLOR_COOLANT_WARNING = 0xFFFF0000.toInt()
        const val COLOR_FUEL = 0xFFFFEB3B.toInt()
        const val COLOR_FUEL_WARNING = 0xFFFF5722.toInt()
        const val COLOR_VOLTAGE = 0xFF4CAF50.toInt()
        const val COLOR_VOLTAGE_WARNING = 0xFFF44336.toInt()
        const val COLOR_OIL = 0xFFFF5722.toInt()
        const val COLOR_TRIP = 0xFF9C27B0.toInt()
        const val COLOR_CONNECTED = 0xFF00D9FF.toInt()
        const val COLOR_DISCONNECTED = 0xFFF44336.toInt()
        const val COLOR_WARNING = 0xFFF44336.toInt()

        fun fetchDashboardData(): JSONObject? {
            return try {
                val response = URL(ESP32_URL).readText()
                JSONObject(response)
            } catch (e: Exception) {
                null
            }
        }

        fun getSpeedColor(speed: Double): Int = COLOR_SPEED

        fun getRpmColor(rpm: Double): Int {
            return if (rpm >= RPM_WARNING) COLOR_RPM_WARNING else COLOR_RPM
        }

        fun getCoolantColor(temp: Double): Int {
            return when {
                temp >= COOLANT_WARNING_HIGH -> COLOR_COOLANT_WARNING
                temp <= COOLANT_WARNING_LOW -> COLOR_COOLANT_WARNING
                else -> COLOR_COOLANT
            }
        }

        fun getFuelColor(level: Double): Int {
            return if (level <= FUEL_WARNING) COLOR_FUEL_WARNING else COLOR_FUEL
        }

        fun getVoltageColor(voltage: Double): Int {
            return when {
                voltage <= VOLTAGE_WARNING_LOW -> COLOR_VOLTAGE_WARNING
                voltage >= VOLTAGE_WARNING_HIGH -> COLOR_VOLTAGE_WARNING
                else -> COLOR_VOLTAGE
            }
        }

        fun getOilColor(temp: Double): Int {
            return if (temp >= OIL_WARNING_HIGH) COLOR_WARNING else COLOR_OIL
        }

        fun hasWarning(data: JSONObject): Boolean {
            val fuel = data.optDouble("fuel", 100.0)
            val coolant = data.optDouble("coolant", 90.0)
            val voltage = data.optDouble("voltage", 14.0)
            val oil = data.optDouble("oil", 90.0)
            val rpm = data.optDouble("rpm", 0.0)

            return fuel <= FUEL_WARNING ||
                   coolant >= COOLANT_WARNING_HIGH ||
                   coolant <= COOLANT_WARNING_LOW ||
                   voltage <= VOLTAGE_WARNING_LOW ||
                   voltage >= VOLTAGE_WARNING_HIGH ||
                   oil >= OIL_WARNING_HIGH ||
                   rpm >= RPM_WARNING
        }

        fun getCurrentTime(): String {
            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            return timeFormat.format(Date())
        }
    }

    abstract val layoutId: Int

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    protected open fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, layoutId)

        // Set up click intent to open main activity
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, appWidgetId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setClickIntent(views, pendingIntent)

        // Fetch data in background
        executor.execute {
            try {
                val data = fetchDashboardData()
                mainHandler.post {
                    if (data != null) {
                        updateWidgetWithData(context, appWidgetManager, appWidgetId, views, data)
                    } else {
                        showDisconnectedState(context, appWidgetManager, appWidgetId, views)
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    showErrorState(context, appWidgetManager, appWidgetId, views, e.message)
                }
            }
        }

        // Initial update
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    protected open fun setClickIntent(views: RemoteViews, pendingIntent: PendingIntent) {
        // Override in subclasses to set click targets
    }

    protected abstract fun updateWidgetWithData(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        views: RemoteViews,
        data: JSONObject
    )

    protected open fun showDisconnectedState(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        views: RemoteViews
    ) {
        // Default: try to update connection icon
        try {
            views.setImageViewResource(R.id.connection_icon, R.drawable.ic_disconnected)
        } catch (e: Exception) { }
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    protected open fun showErrorState(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        views: RemoteViews,
        message: String?
    ) {
        showDisconnectedState(context, appWidgetManager, appWidgetId, views)
    }

    override fun onEnabled(context: Context) {
        // First widget instance added
    }

    override fun onDisabled(context: Context) {
        // Last widget instance removed
    }
}
