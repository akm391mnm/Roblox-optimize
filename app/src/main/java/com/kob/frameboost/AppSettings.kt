package com.kob.frameboost

import android.content.Context

class AppSettings(ctx: Context) {
    private val p = ctx.getSharedPreferences("frameboost", Context.MODE_PRIVATE)

    var frame: Int
        get() = p.getInt("frame", 0)
        set(v) = p.edit().putInt("frame", v).apply()

    var target: Int
        get() = p.getInt("target", 0)
        set(v) = p.edit().putInt("target", v).apply()

    var quality: Int
        get() = p.getInt("quality", 0)
        set(v) = p.edit().putInt("quality", v).apply()

    var scale: Int
        get() = p.getInt("scale", 2)
        set(v) = p.edit().putInt("scale", v).apply()

    var upscaler: Int
        get() = p.getInt("upscaler", 0)
        set(v) = p.edit().putInt("upscaler", v).apply()

    var sharp: Int
        get() = p.getInt("sharp", 50)
        set(v) = p.edit().putInt("sharp", v).apply()
}
