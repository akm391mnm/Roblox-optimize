package com.kob.frameboost

import java.io.File

/** pct = 0..100, estimated = true เมื่อเป็นค่าประมาณจากความเร็วสัญญาณนาฬิกา ไม่ใช่โหลดจริง */
data class Reading(val pct: Int, val estimated: Boolean)

object SystemStats {

    private var havePrev = false
    private var lastTotal = 0L
    private var lastIdle = 0L

    private val gpuLoadFiles = listOf(
        "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
        "/sys/class/kgsl/kgsl-3d0/devfreq/gpu_load",
        "/sys/class/misc/mali0/device/utilization",
        "/sys/kernel/gpu/gpu_busy",
        "/sys/kernel/ged/hal/gpu_utilization",
        "/sys/module/ged/parameters/gpu_loading"
    )

    // คู่ (ความถี่ปัจจุบัน, ความถี่สูงสุด) ของ GPU
    private val gpuFreqPairs = listOf(
        "/sys/class/kgsl/kgsl-3d0/gpuclk" to "/sys/class/kgsl/kgsl-3d0/max_gpuclk",
        "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq" to "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq"
    )

    private fun read(path: String): String? = try {
        File(path).readText().trim().ifEmpty { null }
    } catch (e: Exception) {
        null
    }

    private fun firstInt(s: String): Int? = Regex("\\d+").find(s)?.value?.toIntOrNull()

    // ---------- CPU ----------

    fun cpu(): Reading? {
        // วิธีหลัก: /proc/stat (Android 8+ ส่วนใหญ่แอปทั่วไปอ่านไม่ได้)
        val line = read("/proc/stat")?.lineSequence()?.firstOrNull { it.startsWith("cpu ") }
        if (line != null) {
            val v = line.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
            if (v.size >= 5) {
                val idle = v[3] + v[4]
                val total = v.sum()
                val dTotal = total - lastTotal
                val dIdle = idle - lastIdle
                val had = havePrev
                lastTotal = total
                lastIdle = idle
                havePrev = true
                if (had && dTotal > 0) {
                    val pct = ((dTotal - dIdle) * 100 / dTotal).toInt().coerceIn(0, 100)
                    return Reading(pct, false)
                }
            }
        }
        // วิธีสำรอง: ประมาณจากความเร็วซีพียูปัจจุบัน / สูงสุด
        return cpuFromFreq()
    }

    private fun cpuFromFreq(): Reading? {
        val cores = File("/sys/devices/system/cpu").listFiles { f -> f.name.matches(Regex("cpu\\d+")) }
            ?: return null
        var cur = 0L
        var max = 0L
        for (c in cores) {
            val base = c.path + "/cpufreq/"
            val m = read(base + "cpuinfo_max_freq")?.toLongOrNull() ?: continue
            val f = read(base + "scaling_cur_freq")?.toLongOrNull()
                ?: read(base + "cpuinfo_cur_freq")?.toLongOrNull()
                ?: continue
            cur += f
            max += m
        }
        if (max <= 0) return null
        return Reading((cur * 100 / max).toInt().coerceIn(0, 100), true)
    }

    // ---------- GPU ----------

    fun gpu(): Reading? {
        // Adreno: "busy total" นับตั้งแต่อ่านครั้งก่อน
        read("/sys/class/kgsl/kgsl-3d0/gpubusy")?.let { s ->
            val n = Regex("\\d+").findAll(s).mapNotNull { it.value.toLongOrNull() }.toList()
            if (n.size >= 2 && n[1] > 0) {
                return Reading((n[0] * 100 / n[1]).toInt().coerceIn(0, 100), false)
            }
        }
        for (p in gpuLoadFiles) {
            val v = read(p)?.let { firstInt(it) }
            if (v != null) return Reading(v.coerceIn(0, 100), false)
        }
        // สำรอง: ประมาณจากความถี่ GPU
        for ((curP, maxP) in gpuFreqPairs) {
            val c = read(curP)?.toLongOrNull()
            val m = read(maxP)?.toLongOrNull()
            if (c != null && m != null && m > 0) {
                return Reading((c * 100 / m).toInt().coerceIn(0, 100), true)
            }
        }
        // Mali และอื่น ๆ ผ่าน devfreq
        val dirs = File("/sys/class/devfreq").listFiles { f ->
            val n = f.name.lowercase()
            n.contains("mali") || n.contains("gpu") || n.contains("kgsl")
        }
        if (dirs != null) {
            for (d in dirs) {
                val c = read(d.path + "/cur_freq")?.toLongOrNull()
                val m = read(d.path + "/max_freq")?.toLongOrNull()
                if (c != null && m != null && m > 0) {
                    return Reading((c * 100 / m).toInt().coerceIn(0, 100), true)
                }
            }
        }
        return null
    }
}
