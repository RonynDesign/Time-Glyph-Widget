package com.ronyndesign.yearaim.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import java.util.Calendar
import java.util.concurrent.TimeUnit

class WidgetUpdateService : Service() {
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        updateWidgets()
        
        // Schedule the next update at midnight
        scheduleNextUpdate()
        
        return START_NOT_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    private fun updateWidgets() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(this, YearProgressWidget::class.java)
        )
        
        if (appWidgetIds.isNotEmpty()) {
            // Update all instances of the widget by manually calling YearProgressWidget method
            for (appWidgetId in appWidgetIds) {
                YearProgressWidget.updateAppWidget(this, appWidgetManager, appWidgetId, true)
            }
        }
        
        // Stop the service when done
        stopSelf()
    }
    
    private fun scheduleNextUpdate() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, YearProgressWidget::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Calculate time until midnight
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        
        val currentTimeMillis = System.currentTimeMillis()
        val triggerAtMillis = calendar.timeInMillis
        
        // Schedule the alarm
        alarmManager.setExact(
            AlarmManager.RTC,
            triggerAtMillis,
            pendingIntent
        )
    }
    
    companion object {
        // Helper method to start the service
        fun startWidgetUpdateService(context: Context) {
            val serviceIntent = Intent(context, WidgetUpdateService::class.java)
            context.startService(serviceIntent)
        }
    }
} 