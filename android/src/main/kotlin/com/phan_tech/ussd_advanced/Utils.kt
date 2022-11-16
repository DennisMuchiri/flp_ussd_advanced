package com.phan_tech.ussd_advanced

import android.content.Context
import android.provider.Settings
import android.provider.Settings.SettingNotFoundException
import android.text.TextUtils.SimpleStringSplitter
import android.util.Log


object Utils {
    fun isAccessibilitySettingsOn(mContext: Context?): Boolean {
        var accessibilityEnabled = 0
        val service = mContext!!.packageName + "/" + AccessibilityListener::class.java.getCanonicalName()
        val serviceKt = mContext!!.packageName + "/" + USSDServiceKT::class.java.getCanonicalName()
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                    mContext.applicationContext.contentResolver,
                    Settings.Secure.ACCESSIBILITY_ENABLED)
        } catch (e: SettingNotFoundException) {
        }
        val mStringColonSplitter = SimpleStringSplitter(':')
        if (accessibilityEnabled == 1) {
            val settingValue = Settings.Secure.getString(
                    mContext.applicationContext.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            if (settingValue != null) {
                mStringColonSplitter.setString(settingValue)
                while (mStringColonSplitter.hasNext()) {
                    val accessibilityService = mStringColonSplitter.next()
                    Log.d("ACC SERV",accessibilityService);
                    if (accessibilityService.equals(service, ignoreCase = true)|| accessibilityService.equals(serviceKt, ignoreCase = true)) {
                        return true
                    }
                }
            }
        } else {
        }
        return false
    }
}