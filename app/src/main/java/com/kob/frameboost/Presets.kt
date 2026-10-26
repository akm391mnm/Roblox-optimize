package com.kob.frameboost

object Options {
    val frame = listOf("ปิด", "x1.5", "x2", "x3")
    val target = listOf("60 FPS", "90 FPS", "120 FPS")
    val targetHz = listOf(60, 90, 120)
    val quality = listOf("Blend เบา", "Motion-based")
    val scale = listOf("50%", "75%", "100%")
    val upscaler = listOf("Bilinear", "Sharpen", "FSR เบา")
}

data class Preset(
    val name: String,
    val frame: Int,
    val target: Int,
    val quality: Int,
    val scale: Int,
    val upscaler: Int,
    val sharp: Int
)

object Presets {
    val weak = Preset("เครื่องอ่อน", frame = 1, target = 0, quality = 0, scale = 0, upscaler = 0, sharp = 30)
    val mid = Preset("เครื่องกลาง", frame = 2, target = 1, quality = 0, scale = 1, upscaler = 1, sharp = 50)
    val strong = Preset("เครื่องแรง", frame = 2, target = 2, quality = 1, scale = 2, upscaler = 2, sharp = 50)
    val all = listOf(weak, mid, strong)
}
