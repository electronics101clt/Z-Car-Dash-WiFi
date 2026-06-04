package com.cardash.integration.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.widget.RemoteViews
import com.cardash.integration.R
import org.json.JSONObject

/**
 * Small Speed Widget (2x1) - Minimal speed display
 */
class SpeedSmallWidget : BaseWidgetProvider() {

    override val layoutId: Int = R.layout.widget_speed_small

    override fun setClickIntent(views: RemoteViews, pendingIntent: PendingIntent) {
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

        views.setTextViewText(R.id.speed_value, speed.toInt().toString())
        views.setTextColor(R.id.speed_value, getSpeedColor(speed))
        views.setImageViewResource(R.id.connection_status, R.drawable.ic_connection)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    override fun showDisconnectedState(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        views: RemoteViews
    ) {
        views.setTextViewText(R.id.speed_value, "--")
        views.setTextColor(R.id.speed_value, COLOR_DISCONNECTED)
        views.setImageViewResource(R.id.connection_status, R.drawable.ic_disconnected)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
