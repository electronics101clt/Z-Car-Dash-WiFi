package com.cardash.integration.widgets

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.cardash.integration.R
import org.json.JSONObject
import java.net.URL

/**
 * Service for providing data to the StackWidget's StackView
 */
class StackWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return StackWidgetFactory(applicationContext)
    }
}

/**
 * Factory for creating RemoteViews for each metric in the stack
 */
class StackWidgetFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    data class MetricItem(
        val name: String,
        val value: String,
        val unit: String,
        val color: Int,
        val iconRes: Int,
        val progress: Int,
        val maxProgress: Int,
        val progressDrawable: Int
    )

    private var metrics = mutableListOf<MetricItem>()

    override fun onCreate() {
        // Initial data load will happen in onDataSetChanged
    }

    override fun onDataSetChanged() {
        // Fetch data from ESP32
        val data = fetchData()
        metrics.clear()

        if (data != null) {
            val speed = data.optDouble("speed", 0.0)
            val rpm = data.optDouble("rpm", 0.0)
            val coolant = data.optDouble("coolant", 0.0)
            val fuel = data.optDouble("fuel", 0.0)
            val voltage = data.optDouble("voltage", 0.0)
            val oil = data.optDouble("oil", 0.0)

            metrics.add(MetricItem(
                "SPEED", speed.toInt().toString(), "km/h",
                BaseWidgetProvider.getSpeedColor(speed),
                R.drawable.ic_speed,
                speed.toInt(), 220,
                R.drawable.progress_bar_fuel
            ))

            metrics.add(MetricItem(
                "RPM", rpm.toInt().toString(), "x1000",
                BaseWidgetProvider.getRpmColor(rpm),
                R.drawable.ic_rpm,
                rpm.toInt(), 8000,
                R.drawable.progress_bar_rpm
            ))

            metrics.add(MetricItem(
                "COOLANT", "${coolant.toInt()}°", "C",
                BaseWidgetProvider.getCoolantColor(coolant),
                R.drawable.ic_coolant,
                coolant.toInt(), 130,
                R.drawable.progress_bar_coolant
            ))

            metrics.add(MetricItem(
                "FUEL", "${fuel.toInt()}", "%",
                BaseWidgetProvider.getFuelColor(fuel),
                R.drawable.ic_fuel,
                fuel.toInt(), 100,
                R.drawable.progress_bar_fuel
            ))

            metrics.add(MetricItem(
                "VOLTAGE", String.format("%.1f", voltage), "V",
                BaseWidgetProvider.getVoltageColor(voltage),
                R.drawable.ic_voltage,
                (voltage * 10).toInt(), 180,
                R.drawable.progress_bar_voltage
            ))

            metrics.add(MetricItem(
                "OIL TEMP", "${oil.toInt()}°", "C",
                BaseWidgetProvider.getOilColor(oil),
                R.drawable.ic_oil,
                oil.toInt(), 150,
                R.drawable.progress_bar_coolant
            ))
        } else {
            // Disconnected state - show placeholder items
            metrics.add(MetricItem("SPEED", "--", "km/h", 0xFF888888.toInt(), R.drawable.ic_speed, 0, 220, R.drawable.progress_bar_fuel))
            metrics.add(MetricItem("RPM", "--", "x1000", 0xFF888888.toInt(), R.drawable.ic_rpm, 0, 8000, R.drawable.progress_bar_rpm))
            metrics.add(MetricItem("COOLANT", "--°", "C", 0xFF888888.toInt(), R.drawable.ic_coolant, 0, 130, R.drawable.progress_bar_coolant))
            metrics.add(MetricItem("FUEL", "--", "%", 0xFF888888.toInt(), R.drawable.ic_fuel, 0, 100, R.drawable.progress_bar_fuel))
        }
    }

    private fun fetchData(): JSONObject? {
        return try {
            val response = URL(BaseWidgetProvider.ESP32_URL).readText()
            JSONObject(response)
        } catch (e: Exception) {
            null
        }
    }

    override fun onDestroy() {
        metrics.clear()
    }

    override fun getCount(): Int = metrics.size

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_stack_item)
        val metric = metrics[position]

        views.setImageViewResource(R.id.metric_icon, metric.iconRes)
        views.setTextViewText(R.id.metric_value, metric.value)
        views.setTextColor(R.id.metric_value, metric.color)
        views.setTextViewText(R.id.metric_label, "${metric.name} (${metric.unit})")
        views.setProgressBar(R.id.metric_progress, metric.maxProgress, metric.progress, false)

        // Set fill intent for clicking
        val fillIntent = Intent()
        views.setOnClickFillInIntent(R.id.metric_value, fillIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true
}
