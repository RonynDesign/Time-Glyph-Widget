package com.ronyndesign.yearaim.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.RemoteViews
import com.ronyndesign.yearaim.MainActivity
import com.ronyndesign.yearaim.R
import java.util.Calendar

class YearProgressWidget : AppWidgetProvider() {
    
    // Static values to ensure consistency
    companion object {
        private const val PREFS_NAME = "com.ronyndesign.yearaim.YearProgressWidget"
        private const val PREF_WEEK_KEY = "current_week"
        private const val PREF_PERCENT_KEY = "current_percent"
        private const val PREF_LAST_UPDATE_KEY = "last_update_day"
        private const val PREF_YEAR_KEY = "current_year"
        
        // Cache for values to prevent recalculation between method calls
        @Volatile private var cachedWeek: Int = -1
        @Volatile private var cachedPercent: String = ""
        @Volatile private var cachedDay: Int = -1
        @Volatile private var cachedYear: Int = -1
        private val cacheLock = Any()
        
        // Update the widget
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            recalculate: Boolean
        ) {
            val calendar = Calendar.getInstance()
            val currentYear = calendar.get(Calendar.YEAR)
            val currentDay = calendar.get(Calendar.DAY_OF_YEAR)
            
            // Get stored or calculated values, with thread safety
            val (weekOfYear, progressPercentFormatted) = synchronized(cacheLock) {
                // Check if we already have valid cached values
                if (cachedDay == currentDay && cachedYear == currentYear && cachedWeek != -1 && cachedPercent.isNotEmpty()) {
                    return@synchronized Pair(cachedWeek, cachedPercent)
                }
                
                // Check if we need to recalculate or can use stored values
                if (recalculate || cachedDay != currentDay || cachedWeek == -1) {
                    // Setup calendar for consistent calculations
                    calendar.firstDayOfWeek = Calendar.MONDAY
                    calendar.minimalDaysInFirstWeek = 4
                    
                    // Calculate new values
                    val weekOfYear = calendar.get(Calendar.WEEK_OF_YEAR)
                    
                    // Calculate total days in year (account for leap years)
                    val isLeapYear = (currentYear % 4 == 0 && (currentYear % 100 != 0 || currentYear % 400 == 0))
                    val daysInYear = if (isLeapYear) 366 else 365
                    
                    // Get the current day of year
                    val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
                    
                    // Calculate percentage based on day of year for more precision
                    val progressPercentExact = Math.min(100f, (dayOfYear.toFloat() / daysInYear.toFloat() * 100))
                    val formattedPercent = String.format("%.1f", progressPercentExact)
                    
                    // Update cache
                    cachedWeek = weekOfYear
                    cachedPercent = formattedPercent
                    cachedDay = currentDay
                    cachedYear = currentYear
                    
                    // Also store in preferences as backup
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit()
                        .putInt(PREF_WEEK_KEY, weekOfYear)
                        .putString(PREF_PERCENT_KEY, formattedPercent)
                        .putInt(PREF_LAST_UPDATE_KEY, currentDay)
                        .putInt(PREF_YEAR_KEY, currentYear)
                        .apply()
                    
                    Pair(weekOfYear, formattedPercent)
                } else {
                    // Use cached values directly since they're valid
                    Pair(cachedWeek, cachedPercent)
                }
            }
            
            // Create a new RemoteViews
            val views = RemoteViews(context.packageName, R.layout.year_progress_widget)
            
            // Update text views
            views.setTextViewText(R.id.tvYear, currentYear.toString())
            views.setTextViewText(R.id.tvProgress, "${progressPercentFormatted}% complete. Week $weekOfYear")
            
            // Update week dots
            updateWeekDots(context, views, weekOfYear)
            
            // Setup click intent for widget
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Make the entire widget clickable
            views.setOnClickPendingIntent(R.id.widget_root_layout, pendingIntent)
            
            // Tell the AppWidgetManager to perform an update on the current widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
        
        // Update the week dots in the widget
        private fun updateWeekDots(context: Context, views: RemoteViews, currentWeek: Int) {
            // Create a new layout based on the current week
            for (week in 1..52) {
                val dotResourceId = context.resources.getIdentifier("dot_$week", "id", context.packageName)
                
                if (dotResourceId > 0) {
                    // Choose which drawable to display
                    val drawableId = when {
                        week < currentWeek -> R.drawable.dot_past_week
                        week == currentWeek -> R.drawable.dot_current_week
                        else -> R.drawable.dot_future_week
                    }
                    
                    // Set the correct drawable for this week
                    views.setImageViewResource(dotResourceId, drawableId)
                }
            }
        }
        
        // Function to determine how many weeks are in a given year
        private fun getWeeksInYear(year: Int): Int {
            val cal = Calendar.getInstance()
            cal.firstDayOfWeek = Calendar.MONDAY
            cal.minimalDaysInFirstWeek = 4
            cal.set(Calendar.YEAR, year)
            cal.set(Calendar.MONTH, Calendar.DECEMBER)
            cal.set(Calendar.DAY_OF_MONTH, 31)
            return cal.get(Calendar.WEEK_OF_YEAR)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Perform this loop procedure for each widget that belongs to this provider
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, true)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        // Widget size has changed, update it but don't recalculate percentage
        updateAppWidget(context, appWidgetManager, appWidgetId, false)
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // First widget created, start the update service
        WidgetUpdateService.startWidgetUpdateService(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Last widget deleted, no need for updates
        // Stop service if needed
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        // Handle daily midnight update
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                intent.component
            )
            
            if (appWidgetIds != null && appWidgetIds.isNotEmpty()) {
                onUpdate(context, appWidgetManager, appWidgetIds)
            }
            
            // Schedule the next update
            WidgetUpdateService.startWidgetUpdateService(context)
        }
    }
} 