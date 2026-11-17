// file: app/src/main/java/com/plum/vpn/AppList.kt
package com.plum.vpn

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

object AppList {
    fun getInstalledApps(context: Context): List<ApplicationInfo> {
        val pm = context.packageManager
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.packageName != context.packageName }
            .sortedBy { pm.getApplicationLabel(it).toString() }
    }
}
