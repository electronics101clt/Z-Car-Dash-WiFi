package com.cardash.integration.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.cardash.integration.R
import org.json.JSONObject

/**
 * Stack Widget (2x2) - Swipe through metrics one at a time using StackView
 */
class StackWidget : BaseWidgetProvider() {

    override val layoutId: Int = R.layout.widget_stack

    override fun setClickIntent(views: RemoteViews, pendingIntent: PendingIntent) {
        views.setOnClickPendingIntent(R.id.connection_icon, pendingIntent)
    }

    override fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, layoutId)

        // Set up click intent to open main activity
        val intent = Intent(context, com.cardash.integration.MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, appWidgetId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setClickIntent(views, pendingIntent)

        // Set up the RemoteViews adapter for StackView
        val serviceIntent = Intent(context, StackWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        views.setRemoteAdapter(R.id.stack_view, serviceIntent)

        // Set empty view (shown when no data)
        views.setEmptyView(R.id.stack_view, R.id.last_update)

        // Set click template for stack items
        val clickIntent = Intent(context, com.cardash.integration.MainActivity::class.java)
        val clickPendingIntent = PendingIntent.getActivity(
            context, 0, clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setPendingIntentTemplate(R.id.stack_view, clickPendingIntent)

        // Update time
        views.setTextViewText(R.id.last_update, getCurrentTime().substring(0, 5))

        appWidgetManager.updateAppWidget(appWidgetId, views)
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.stack_view)
    }

    override fun updateWidgetWithData(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        views: RemoteViews,
        data: JSONObject
    ) {
        // Data handling is done by the RemoteViewsFactory
        views.setImageViewResource(R.id.connection_icon, R.drawable.ic_connection)
        views.setTextViewText(R.id.last_update, getCurrentTime().substring(0, 5))
        appWidgetManager.updateAppWidget(appWidgetId, views)
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.stack_view)
    }

    override fun showDisconnectedState(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        views: RemoteViews
    ) {
        views.setImageViewResource(R.id.connection_icon, R.drawable.ic_disconnected)
        views.setTextViewText(R.id.last_update, "00:00")
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
