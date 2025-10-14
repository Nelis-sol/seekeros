package com.myagentos.app

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import java.util.concurrent.TimeUnit

class UsageStatsManager(private val context: Context) {
    
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val packageManager = context.packageManager
    
    /**
     * Check if the app has permission to access usage statistics
     */
    fun hasUsageStatsPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val time = System.currentTimeMillis()
            val usageStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                time - TimeUnit.DAYS.toMillis(1),
                time
            )
            usageStats.isNotEmpty()
        } else {
            true
        }
    }
    
    /**
     * Request usage stats permission from user
     */
    fun requestUsageStatsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            context.startActivity(intent)
        }
    }
    
    /**
     * Get recently used apps (last 7 days)
     * @param limit Maximum number of apps to return
     * @return List of AppInfo sorted by last used time (most recent first)
     */
    fun getRecentlyUsedApps(limit: Int = 10): List<AppInfo> {
        if (!hasUsageStatsPermission()) {
            return emptyList()
        }
        
        val time = System.currentTimeMillis()
        val startTime = time - TimeUnit.DAYS.toMillis(7) // Last 7 days
        
        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            time
        )
        
        // Filter and sort by last time used
        val recentApps = usageStats
            .filter { it.lastTimeUsed > 0 } // Only apps that were actually used
            .filter { it.packageName != context.packageName } // Exclude our own app
            .filter { !isSystemApp(it.packageName) } // Exclude system apps and Android processes
            .distinctBy { it.packageName } // Remove duplicates by package name
            .sortedByDescending { it.lastTimeUsed } // Most recent first
            .take(limit)
        
        // Convert to AppInfo objects and filter out non-launchable apps
        val result = recentApps.mapNotNull { usageStat ->
            try {
                val applicationInfo = packageManager.getApplicationInfo(usageStat.packageName, 0)
                val appName = packageManager.getApplicationLabel(applicationInfo).toString()
                val icon = packageManager.getApplicationIcon(applicationInfo)
                val isSystemApp = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                
                // Check if app can be launched
                val launchIntent = packageManager.getLaunchIntentForPackage(usageStat.packageName)
                if (launchIntent == null) {
                    // Skip apps that can't be launched
                    return@mapNotNull null
                }
                
                AppInfo(
                    packageName = usageStat.packageName,
                    appName = appName,
                    icon = icon,
                    isSystemApp = isSystemApp
                )
            } catch (e: Exception) {
                // Skip apps that can't be resolved
                null
            }
        }
        
        return result
    }
    
    /**
     * Get usage statistics for a specific app
     */
    fun getAppUsageStats(packageName: String): UsageStats? {
        if (!hasUsageStatsPermission()) {
            return null
        }
        
        val time = System.currentTimeMillis()
        val startTime = time - TimeUnit.DAYS.toMillis(7) // Same as getRecentlyUsedApps
        
        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            time
        )
        
        return usageStats.find { it.packageName == packageName }
    }
    
    /**
     * Check if a package is a system app or Android process that should be filtered out
     */
    private fun isSystemApp(packageName: String): Boolean {
        // Common Android system packages to exclude
        val systemPackages = setOf(
            "android",
            "com.android.systemui",
            "com.android.launcher3",
            "com.android.quickstep",
            "com.mediatek.batterywarning",
            "com.android.phone",
            "com.android.mms",
            "com.android.calendar",
            "com.android.contacts",
            "com.android.dialer",
            "com.android.gallery3d",
            "com.android.camera2",
            "com.android.music",
            "com.android.providers",
            "com.android.permissioncontroller", // "Rechtenbeheer" (Permissions)
            "com.android.packageinstaller", // Package installer
            "com.android.intentresolver", // Intent resolver
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.google.android.apps",
            "com.google.android.gallery3d", // Google Photos/Gallery
            "com.qualcomm",
            "com.mediatek",
            "com.samsung",
            "com.huawei",
            "com.xiaomi",
            "com.oneplus",
            "com.oppo",
            "com.vivo",
            "com.realme"
        )
        
        // Check if package starts with any system package prefix
        return systemPackages.any { packageName.startsWith(it) }
    }
}
