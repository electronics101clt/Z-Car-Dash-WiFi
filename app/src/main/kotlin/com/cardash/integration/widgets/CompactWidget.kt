package com.cardash.integration.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.cardash.integration.R
import org.json.JSONObject

/**
 * Compact Essentials Widget (2x2) - Speed, RPM, and mini stats
 */
class CompactWidget : BaseWidgetProvider() {

    override val layoutId: Int = R.layout.widget_compact

    override fun setClickIntent(views: RemoteViews, pendingIntent: PendingIntent) {
        views.setOnClickPendingIntent(R.id.speed_value, pendingIntent)
        views.setOnClickPendingIntent(R.id.rpm_value, pendingIntent)
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

        // Update values
        views.setTextViewText(R.id.speed_value, speed.toInt().toString())
        views.setTextColor(R.id.speed_value, getSpeedColor(speed))

        views.setTextViewText(R.id.rpm_value, rpm.toInt().toString())
        views.setTextColor(R.id.rpm_value, getRpmColor(rpm))

        views.setTextViewText(R.id.fuel_value, "${fuel.toInt()}%")
        views.setTextColor(R.id.fuel_value, getFuelColor(fuel))

        views.setTextViewText(R.id.coolant_value, "${coolant.toInt()}°")
        views.setTextColor(R.id.coolant_value, getCoolantColor(coolant))

        // Connection status
        views.setImageViewResource(R.id.connection_icon, R.drawable.ic_connection)
        views.setTextViewText(R.id.connection_text, "Connected")
        views.setTextColor(R.id.connection_text, COLOR_CONNECTED)

        // Warning icon
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
        views.setTextViewText(R.id.fuel_value, "0%")
        views.setTextViewText(R.id.coolant_value, "0°")

        views.setImageViewResource(R.id.connection_icon, R.drawable.ic_disconnected)
        views.setTextViewText(R.id.connection_text, "Disconnected")
        views.setTextColor(R.id.connection_text, COLOR_DISCONNECTED)
        views.setViewVisibility(R.id.warning_icon, View.GONE)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
