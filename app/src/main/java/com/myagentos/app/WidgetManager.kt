package com.myagentos.app

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast

class WidgetManager(private val context: Context) {
    private val appWidgetManager = AppWidgetManager.getInstance(context)
    val appWidgetHost = AppWidgetHost(context, 1) // Use ID 1 for our host
    private val hostedWidgets = mutableListOf<HostedWidget>()

    data class HostedWidget(
        val appWidgetId: Int,
        val providerInfo: AppWidgetProviderInfo,
        val view: AppWidgetHostView
    )

    fun startListening() {
        appWidgetHost.startListening()
    }

    fun stopListening() {
        appWidgetHost.stopListening()
    }

    fun deleteHost() {
        appWidgetHost.deleteHost()
    }

    fun getAvailableWidgets(): List<AppWidgetProviderInfo> {
        return appWidgetManager.getInstalledProviders().filter { 
            // Filter out system widgets and our own widget
            !it.provider.packageName.contains("android") &&
            it.provider.packageName != context.packageName
        }
    }

    fun createWidget(providerInfo: AppWidgetProviderInfo, container: ViewGroup): HostedWidget? {
        return try {
            val appWidgetId = appWidgetHost.allocateAppWidgetId()
            
            // Bind the widget
            val bindResult = appWidgetManager.bindAppWidgetIdIfAllowed(
                appWidgetId, 
                providerInfo.provider
            )
            
            if (!bindResult) {
                Log.w("WidgetManager", "Could not bind widget: ${providerInfo.provider}")
                appWidgetHost.deleteAppWidgetId(appWidgetId)
                return null
            }

            // Create the widget view
            val widgetView = appWidgetHost.createView(context, appWidgetId, providerInfo)
            widgetView.setAppWidget(appWidgetId, providerInfo)
            
            // Set layout parameters
            val layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                providerInfo.minHeight
            )
            widgetView.layoutParams = layoutParams
            
            // Add to container
            container.addView(widgetView)
            
            val hostedWidget = HostedWidget(appWidgetId, providerInfo, widgetView)
            hostedWidgets.add(hostedWidget)
            
            Log.d("WidgetManager", "Created widget: ${providerInfo.provider}")
            hostedWidget
            
        } catch (e: Exception) {
            Log.e("WidgetManager", "Error creating widget", e)
            null
        }
    }

    fun removeWidget(hostedWidget: HostedWidget) {
        try {
            appWidgetHost.deleteAppWidgetId(hostedWidget.appWidgetId)
            hostedWidgets.remove(hostedWidget)
            Log.d("WidgetManager", "Removed widget: ${hostedWidget.providerInfo.provider}")
        } catch (e: Exception) {
            Log.e("WidgetManager", "Error removing widget", e)
        }
    }

    fun removeAllWidgets() {
        hostedWidgets.forEach { removeWidget(it) }
    }

    fun showWidgetPicker(activity: Activity, requestCode: Int) {
        try {
            Log.d("WidgetManager", "Creating custom widget picker intent...")
            val appWidgetId = appWidgetHost.allocateAppWidgetId()
            Log.d("WidgetManager", "Allocated app widget ID: $appWidgetId")
            
            val intent = Intent(activity, CustomWidgetPickerActivity::class.java)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            
            Log.d("WidgetManager", "Starting custom widget picker activity...")
            activity.startActivityForResult(intent, requestCode)
            Log.d("WidgetManager", "Custom widget picker activity started successfully")
        } catch (e: Exception) {
            Log.e("WidgetManager", "Error in showWidgetPicker", e)
            throw e
        }
    }

    fun configureWidget(activity: Activity, appWidgetId: Int, providerInfo: AppWidgetProviderInfo) {
        try {
            // Use the specific configuration activity for this widget
            val intent = Intent().apply {
                component = providerInfo.configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            activity.startActivityForResult(intent, 1002)
            Log.d("WidgetManager", "Starting configuration for widget: ${providerInfo.provider}")
        } catch (e: Exception) {
            Log.e("WidgetManager", "Error starting widget configuration", e)
            // Fallback: try to create the widget without configuration
            // We'll handle this in MainActivity instead
        }
    }

    fun getWidgetDisplayName(providerInfo: AppWidgetProviderInfo): String {
        return try {
            val packageManager = context.packageManager
            val appInfo = packageManager.getApplicationInfo(providerInfo.provider.packageName, 0)
            val appName = packageManager.getApplicationLabel(appInfo).toString()
            "$appName Widget"
        } catch (e: Exception) {
            providerInfo.provider.packageName
        }
    }
}
