package com.myagentos.app

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class CustomWidgetPickerActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: WidgetAdapter
    private var appWidgetId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_picker)

        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        if (appWidgetId == -1) {
            finish()
            return
        }

        setupUI()
        loadWidgets()
    }

    private fun setupUI() {
        recyclerView = findViewById(R.id.widgetRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = WidgetAdapter { providerInfo ->
            selectWidget(providerInfo)
        }
        recyclerView.adapter = adapter
    }

    private fun loadWidgets() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val availableWidgets = appWidgetManager.installedProviders.filter { 
            // Filter out system widgets and our own widget
            !it.provider.packageName.contains("android") &&
            it.provider.packageName != packageName
        }.sortedBy { it.loadLabel(packageManager) } // Sort alphabetically
        adapter.updateWidgets(availableWidgets)
    }

    private fun selectWidget(providerInfo: AppWidgetProviderInfo) {
        val resultIntent = Intent()
        resultIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        resultIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, providerInfo.provider)
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    class WidgetAdapter(
        private val onWidgetSelected: (AppWidgetProviderInfo) -> Unit
    ) : RecyclerView.Adapter<WidgetAdapter.WidgetViewHolder>() {
        
        private var widgets = listOf<AppWidgetProviderInfo>()

        fun updateWidgets(newWidgets: List<AppWidgetProviderInfo>) {
            widgets = newWidgets
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WidgetViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_widget_picker, parent, false)
            return WidgetViewHolder(view)
        }

        override fun onBindViewHolder(holder: WidgetViewHolder, position: Int) {
            val widget = widgets[position]
            holder.bind(widget)
        }

        override fun getItemCount(): Int = widgets.size

        inner class WidgetViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val iconView: ImageView = itemView.findViewById(R.id.widgetIcon)
            private val nameView: TextView = itemView.findViewById(R.id.widgetName)
            private val descriptionView: TextView = itemView.findViewById(R.id.widgetDescription)

            fun bind(providerInfo: AppWidgetProviderInfo) {
                try {
                    val packageManager = itemView.context.packageManager
                    val appInfo = packageManager.getApplicationInfo(providerInfo.provider.packageName, 0)
                    val appName = packageManager.getApplicationLabel(appInfo).toString()
                    
                    // Get widget-specific information
                    val widgetLabel = providerInfo.loadLabel(packageManager)
                    val widgetDescription = providerInfo.loadDescription(itemView.context)
                    
                    // Use widget label if available, otherwise fallback to app name
                    val displayName = if (widgetLabel.isNotEmpty()) widgetLabel else "$appName Widget"
                    nameView.text = displayName
                    
                    // Show widget description if available, otherwise show size info
                    val displayDescription = if (!widgetDescription.isNullOrEmpty()) {
                        "$widgetDescription • ${providerInfo.minWidth}×${providerInfo.minHeight}dp"
                    } else {
                        "Size: ${providerInfo.minWidth}×${providerInfo.minHeight}dp"
                    }
                    descriptionView.text = displayDescription
                    
                    // Try to get widget preview image first, then app icon
                    try {
                        val previewImage = providerInfo.loadPreviewImage(itemView.context, 0)
                        if (previewImage != null) {
                            iconView.setImageDrawable(previewImage)
                        } else {
                            val appIcon = packageManager.getApplicationIcon(appInfo)
                            iconView.setImageDrawable(appIcon)
                        }
                    } catch (e: Exception) {
                        iconView.setImageResource(R.mipmap.ic_launcher)
                    }
                    
                    itemView.setOnClickListener {
                        onWidgetSelected(providerInfo)
                    }
                } catch (e: Exception) {
                    nameView.text = providerInfo.provider.packageName
                    descriptionView.text = "Unknown widget"
                    iconView.setImageResource(R.mipmap.ic_launcher)
                }
            }
        }
    }
}
