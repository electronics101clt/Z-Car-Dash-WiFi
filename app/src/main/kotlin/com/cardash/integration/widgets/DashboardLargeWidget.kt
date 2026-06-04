package com.cardash.integration.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.cardash.integration.R
import org.json.JSONObject

/**
 * Large Full Dashboard Widget (4x3) - Complete dashboard with all gauges and trip data
 */
class DashboardLargeWidget : BaseWidgetProvider() {

    override val layoutId: Int = R.layout.widget_dashboard_large

    override fun setClickIntent(views: RemoteViews, pendingIntent: PendingIntent) {
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
        views.setOnClickPendingIntent(R.id.speed_value, pendingIntent)
    }

    override fun updateWidgetWithData(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        views: RemoteViews,
        data: JSONObject
    ) {
        val speed = data.optDouble("speed", 0.0)
        val rpm = data.optDouble("rpm", 0.0)
        val coolant = data.optDouble("coolant", 0.0)
        val fuel = data.optDouble("fuel", 0.0)
        val voltage = data.optDouble("voltage", 0.0)
        val oil = data.optDouble("oil", 0.0)
        val trip = data.optDouble("trip", 0.0)
        val avgSpeed = data.optDouble("avgSpeed", 0.0)
        val maxSpeed = data.optDouble("maxSpeed", 0.0)

        // Primary gauges - Speed
        views.setTextViewText(R.id.speed_value, speed.toInt().toString())
        views.setTextColor(R.id.speed_value, getSpeedColor(speed))

        // Primary gauges - RPM
        views.setTextViewText(R.id.rpm_value, rpm.toInt().toString())
        views.setTextColor(R.id.rpm_value, getRpmColor(rpm))
        views.setProgressBar(R.id.rpm_progress, 8000, rpm.toInt(), false)

        // Secondary gauges - Coolant
        views.setTextViewText(R.id.coolant_value, "${coolant.toInt()}°")
        views.setTextColor(R.id.coolant_value, getCoolantColor(coolant))
        views.setProgressBar(R.id.coolant_progress, 130, coolant.toInt(), false)

        // Secondary gauges - Fuel
        views.setTextViewText(R.id.fuel_value, "${fuel.toInt()}%")
        views.setTextColor(R.id.fuel_value, getFuelColor(fuel))
        views.setProgressBar(R.id.fuel_progress, 100, fuel.toInt(), false)

        // Secondary gauges - Voltage
        views.setTextViewText(R.id.voltage_value, String.format("%.1fV", voltage))
        views.setTextColor(R.id.voltage_value, getVoltageColor(voltage))
        views.setProgressBar(R.id.voltage_progress, 180, (voltage * 10).toInt(), false)

        // Secondary gauges - Oil
        views.setTextViewText(R.id.oil_value, "${oil.toInt()}°")
        views.setTextColor(R.id.oil_value, getOilColor(oil))
        views.setProgressBar(R.id.oil_progress, 150, oil.toInt(), false)

        // Trip info
        views.setTextViewText(R.id.trip_value, String.format("%.1f km", trip))
        views.setTextViewText(R.id.avg_speed_value, String.format("%.0f km/h", avgSpeed))
        views.setTextViewText(R.id.max_speed_value, String.format("%.0f km/h", maxSpeed))

        // Connection status
        views.setImageViewResource(R.id.connection_icon, R.drawable.ic_connection)
        views.setTextViewText(R.id.connection_text, "Connected to ESP32")
        views.setTextColor(R.id.connection_text, COLOR_CONNECTED)

        // Update time
        views.setTextViewText(R.id.last_update, "Updated: ${getCurrentTime()}")

        // Warning indicator
        if (hasWarning(data)) {
            views.setViewVisibility(R.id.warning_icon, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.warning_icon, View.GONE)
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    override fun showDisconnectedState(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        views: RemoteViews
    ) {
        views.setTextViewText(R.id.speed_value, "0")
        views.setTextViewText(R.id.rpm_value, "0")
        views.setTextViewText(R.id.coolant_value, "0°")
        views.setTextViewText(R.id.fuel_value, "0%")
        views.setTextViewText(R.id.voltage_value, "0.-V")
        views.setTextViewText(R.id.oil_value, "0°")
        views.setTextViewText(R.id.trip_value, "0 km")
        views.setTextViewText(R.id.avg_speed_value, "0 km/h")
        views.setTextViewText(R.id.max_speed_value, "0 km/h")

        views.setProgressBar(R.id.rpm_progress, 8000, 0, false)
        views.setProgressBar(R.id.coolant_progress, 130, 0, false)
        views.setProgressBar(R.id.fuel_progress, 100, 0, false)
        views.setProgressBar(R.id.voltage_progress, 180, 0, false)
        views.setProgressBar(R.id.oil_progress, 150, 0, false)

        views.setImageViewResource(R.id.connection_icon, R.drawable.ic_disconnected)
        views.setTextViewText(R.id.connection_text, "Tap to connect to ESP32")
        views.setTextColor(R.id.connection_text, COLOR_DISCONNECTED)

        views.setTextViewText(R.id.last_update, "Updated: --:--:--")
        views.setViewVisibility(R.id.warning_icon, View.GONE)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
