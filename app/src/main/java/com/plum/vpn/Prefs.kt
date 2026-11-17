// file: app/src/main/java/com/plum/vpn/Prefs.kt
package com.plum.vpn

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val PREF_NAME = "plum_vpn_prefs"
    private const val KEY_DNS = "selected_dns"
    private const val KEY_WHITELIST = "whitelist_packages"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getDns(context: Context): String {
        return getPrefs(context).getString(KEY_DNS, "1.1.1.1") ?: "1.1.1.1"
    }

    fun setDns(context: Context, dns: String) {
        getPrefs(context).edit().putString(KEY_DNS, dns).apply()
    }

    fun getWhitelist(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_WHITELIST, emptySet()) ?: emptySet()
    }

    fun setWhitelist(context: Context, packages: Set<String>) {
        getPrefs(context).edit().putStringSet(KEY_WHITELIST, packages).apply()
    }
}
