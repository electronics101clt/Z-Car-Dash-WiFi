package com.cardash.integration.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.widget.RemoteViews
import com.cardash.integration.R
import org.json.JSONObject

/**
 * Gauges Widget (3x2) - Visual gauge-focused display
 */
class GaugesWidget : BaseWidgetProvider() {

    override val layoutId: Int = R.layout.widget_gauges

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

        // Speed gauge
        views.setTextViewText(R.id.speed_value, speed.toInt().toString())
        views.setTextColor(R.id.speed_value, getSpeedColor(speed))
        views.setProgressBar(R.id.speed_progress, 220, speed.toInt(), false)

        // RPM gauge
        views.setTextViewText(R.id.rpm_value, (rpm / 1000).toString().take(3))
        views.setTextColor(R.id.rpm_value, getRpmColor(rpm))
        views.setProgressBar(R.id.rpm_progress, 8000, rpm.toInt(), false)

        // Mini gauges
        views.setTextViewText(R.id.fuel_value, "${fuel.toInt()}%")
        views.setTextColor(R.id.fuel_value, getFuelColor(fuel))
        views.setProgressBar(R.id.fuel_progress, 100, fuel.toInt(), false)

        views.setTextViewText(R.id.coolant_value, "${coolant.toInt()}°")
        views.setTextColor(R.id.coolant_value, getCoolantColor(coolant))
        views.setProgressBar(R.id.coolant_progress, 130, coolant.toInt(), false)

        views.setTextViewText(R.id.voltage_value, String.format("%.1fV", voltage))
        views.setTextColor(R.id.voltage_value, getVoltageColor(voltage))
        views.setProgressBar(R.id.voltage_progress, 180, (voltage * 10).toInt(), false)

        // Connection status
        views.setImageViewResource(R.id.connection_icon, R.drawable.ic_connection)
        views.setTextViewText(R.id.last_update, getCurrentTime().substring(0, 5))

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
        views.setTextViewText(R.id.fuel_value, "--%")
        views.setTextViewText(R.id.coolant_value, "--°")
        views.setTextViewText(R.id.voltage_value, "--.-V")

        views.setProgressBar(R.id.speed_progress, 220, 0, false)
        views.setProgressBar(R.id.rpm_progress, 8000, 0, false)
        views.setProgressBar(R.id.fuel_progress, 100, 0, false)
        views.setProgressBar(R.id.coolant_progress, 130, 0, false)
        views.setProgressBar(R.id.voltage_progress, 180, 0, false)

        views.setImageViewResource(R.id.connection_icon, R.drawable.ic_disconnected)
        views.setTextViewText(R.id.last_update, "--:--")

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
