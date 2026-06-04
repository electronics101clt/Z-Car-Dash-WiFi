package com.cardash.integration.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.widget.RemoteViews
import com.cardash.integration.R
import org.json.JSONObject

/**
 * Horizontal Strip Widget (4x1) - All metrics in a row
 */
class HorizontalStripWidget : BaseWidgetProvider() {

    override val layoutId: Int = R.layout.widget_horizontal_strip

    override fun setClickIntent(views: RemoteViews, pendingIntent: PendingIntent) {
        views.setOnClickPendingIntent(R.id.speed_value, pendingIntent)
        views.setOnClickPendingIntent(R.id.connection_icon, pendingIntent)
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

        // Update all values
        views.setTextViewText(R.id.speed_value, speed.toInt().toString())
        views.setTextColor(R.id.speed_value, getSpeedColor(speed))

        views.setTextViewText(R.id.rpm_value, rpm.toInt().toString())
        views.setTextColor(R.id.rpm_value, getRpmColor(rpm))

        views.setTextViewText(R.id.coolant_value, "${coolant.toInt()}°")
        views.setTextColor(R.id.coolant_value, getCoolantColor(coolant))

        views.setTextViewText(R.id.fuel_value, "${fuel.toInt()}%")
        views.setTextColor(R.id.fuel_value, getFuelColor(fuel))

        views.setTextViewText(R.id.voltage_value, String.format("%.1f", voltage))
        views.setTextColor(R.id.voltage_value, getVoltageColor(voltage))

        // Connection indicator
        views.setImageViewResource(R.id.connection_icon, R.drawable.ic_connection)

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
        views.setTextViewText(R.id.coolant_value, "--°")
        views.setTextViewText(R.id.fuel_value, "--%")
        views.setTextViewText(R.id.voltage_value, "--.-")

        views.setTextColor(R.id.speed_value, COLOR_DISCONNECTED)
        views.setImageViewResource(R.id.connection_icon, R.drawable.ic_disconnected)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
