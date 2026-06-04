package com.cardash.integration.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.cardash.integration.R
import org.json.JSONObject

/**
 * Medium Dashboard Widget (4x2) - Enhanced with progress bars and icons
 */
class DashboardMediumWidget : BaseWidgetProvider() {

    override val layoutId: Int = R.layout.widget_dashboard_medium

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

        // Main gauges
        views.setTextViewText(R.id.speed_value, speed.toInt().toString())
        views.setTextColor(R.id.speed_value, getSpeedColor(speed))

        views.setTextViewText(R.id.rpm_value, rpm.toInt().toString())
        views.setTextColor(R.id.rpm_value, getRpmColor(rpm))
        views.setProgressBar(R.id.rpm_progress, 8000, rpm.toInt(), false)

        views.setTextViewText(R.id.coolant_value, coolant.toInt().toString())
        views.setTextColor(R.id.coolant_value, getCoolantColor(coolant))
        views.setProgressBar(R.id.coolant_progress, 130, coolant.toInt(), false)

        views.setTextViewText(R.id.fuel_value, fuel.toInt().toString())
        views.setTextColor(R.id.fuel_value, getFuelColor(fuel))
        views.setProgressBar(R.id.fuel_progress, 100, fuel.toInt(), false)

        // Secondary stats
        views.setTextViewText(R.id.voltage_value, String.format("%.1fV", voltage))
        views.setTextColor(R.id.voltage_value, getVoltageColor(voltage))

        views.setTextViewText(R.id.oil_value, String.format("%.0f°C", oil))
        views.setTextColor(R.id.oil_value, getOilColor(oil))

        views.setTextViewText(R.id.trip_value, String.format("%.1fkm", trip))

        // Connection status
        views.setImageViewResource(R.id.connection_icon, R.drawable.ic_connection)
        views.setTextViewText(R.id.connection_text, "Connected")
        views.setTextColor(R.id.connection_text, COLOR_CONNECTED)

        // Update time
        views.setTextViewText(R.id.last_update, getCurrentTime())

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
        views.setTextViewText(R.id.speed_value, "--")
        views.setTextViewText(R.id.rpm_value, "--")
        views.setTextViewText(R.id.coolant_value, "--")
        views.setTextViewText(R.id.fuel_value, "--")
        views.setTextViewText(R.id.voltage_value, "--.-V")
        views.setTextViewText(R.id.oil_value, "--°C")
        views.setTextViewText(R.id.trip_value, "--.-km")

        views.setProgressBar(R.id.rpm_progress, 8000, 0, false)
        views.setProgressBar(R.id.coolant_progress, 130, 0, false)
        views.setProgressBar(R.id.fuel_progress, 100, 0, false)

        views.setImageViewResource(R.id.connection_icon, R.drawable.ic_disconnected)
        views.setTextViewText(R.id.connection_text, "Tap to connect")
        views.setTextColor(R.id.connection_text, COLOR_DISCONNECTED)

        views.setTextViewText(R.id.last_update, "--:--:--")
        views.setViewVisibility(R.id.warning_icon, View.GONE)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
