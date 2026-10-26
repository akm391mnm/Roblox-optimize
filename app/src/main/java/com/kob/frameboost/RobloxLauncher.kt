package com.kob.frameboost

import android.content.Context
import android.content.Intent
import android.widget.Toast

object RobloxLauncher {
    private const val PKG = "com.roblox.client"

    fun isInstalled(context: Context): Boolean =
        context.packageManager.getLaunchIntentForPackage(PKG) != null

    fun launch(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(PKG)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            Toast.makeText(context, "ยังไม่ได้ติดตั้ง Roblox", Toast.LENGTH_SHORT).show()
        }
    }
}
