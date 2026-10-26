package com.kob.frameboost

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var cfg: AppSettings
    private lateinit var spFrame: Spinner
    private lateinit var spTarget: Spinner
    private lateinit var spQuality: Spinner
    private lateinit var spScale: Spinner
    private lateinit var spUpscaler: Spinner
    private lateinit var sbSharp: SeekBar
    private lateinit var tvSharp: TextView
    private lateinit var tvHint: TextView
    private lateinit var tvUpdate: TextView
    private lateinit var btnUpdate: Button

    private var maxHz = 60f
    private var ramGb = 0.0

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cfg = AppSettings(this)
        readDeviceInfo()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(24))
        }
        tvHint = TextView(this)

        // หัวแอป
        root.addView(TextView(this).apply {
            text = "FrameBoost"
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "เวอร์ชัน ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})"
            textSize = 12f
        })

        // ปุ่มเปิด Roblox
        root.addView(Button(this).apply {
            text = "เปิด Roblox"
            setOnClickListener { RobloxLauncher.launch(this@MainActivity) }
        }, matchWrap(top = 12))

        // ข้อมูลเครื่อง + พรีเซ็ต
        val rec = recommendedPreset()
        root.addView(section("เครื่องของคุณ"))
        root.addView(TextView(this).apply {
            text = "RAM ≈ ${"%.1f".format(ramGb)} GB, จอสูงสุด ${maxHz.toInt()} Hz\nแนะนำ: ${rec.name}"
        })

        val presetRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        Presets.all.forEach { p ->
            presetRow.addView(Button(this).apply {
                text = p.name
                textSize = 12f
                setOnClickListener { applyPreset(p) }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(presetRow, matchWrap(top = 8))

        // เฟรม
        root.addView(section("เฟรม (Frame Gen)"))
        spFrame = spinner(root, "ตัวคูณเฟรม", Options.frame, cfg.frame) { cfg.frame = it }
        spTarget = spinner(root, "เป้าหมาย FPS", Options.target, cfg.target) { cfg.target = it }
        spQuality = spinner(root, "โหมดคุณภาพเฟรม", Options.quality, cfg.quality) { cfg.quality = it }

        // ความละเอียด
        root.addView(section("ความละเอียดภาพ"))
        spScale = spinner(root, "สเกลการจับภาพ", Options.scale, cfg.scale) { cfg.scale = it }
        spUpscaler = spinner(root, "ตัวขยายภาพ", Options.upscaler, cfg.upscaler) { cfg.upscaler = it }

        tvSharp = TextView(this)
        root.addView(tvSharp)
        sbSharp = SeekBar(this).apply {
            max = 100
            progress = cfg.sharp
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                    cfg.sharp = value
                    tvSharp.text = "ความคม (Sharpness): $value"
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        tvSharp.text = "ความคม (Sharpness): ${cfg.sharp}"
        root.addView(sbSharp)

        root.addView(tvHint, matchWrap(top = 8))
        root.addView(TextView(this).apply {
            text = "หมายเหตุ: เวอร์ชันนี้บันทึกการตั้งค่าได้แล้ว ส่วนตัวจับภาพ + สร้างเฟรมจะเพิ่มในเวอร์ชันถัดไป"
            textSize = 12f
        }, matchWrap(top = 8))

        // อัพเดท
        root.addView(section("อัพเดทแอป"))
        btnUpdate = Button(this).apply {
            text = "เช็กอัพเดท"
            setOnClickListener { checkUpdate() }
        }
        root.addView(btnUpdate)
        tvUpdate = TextView(this)
        root.addView(tvUpdate)

        setContentView(ScrollView(this).apply { addView(root) })
        updateHint()
    }

    // ---------- UI helpers ----------

    private fun matchWrap(top: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(top) }

    private fun section(title: String) = TextView(this).apply {
        text = title
        textSize = 18f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(20), 0, dp(4))
    }

    private fun spinner(
        parent: LinearLayout,
        label: String,
        options: List<String>,
        initial: Int,
        onSel: (Int) -> Unit
    ): Spinner {
        parent.addView(TextView(this).apply {
            text = label
            textSize = 13f
            setPadding(0, dp(8), 0, 0)
        })
        val sp = Spinner(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sp.adapter = adapter
        sp.setSelection(initial)
        sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                onSel(pos)
                updateHint()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        parent.addView(sp)
        return sp
    }

    // ---------- logic ----------

    @Suppress("DEPRECATION")
    private fun readDeviceInfo() {
        maxHz = windowManager.defaultDisplay.supportedModes.maxOf { it.refreshRate }
        val mi = ActivityManager.MemoryInfo()
        (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        ramGb = mi.totalMem / 1024.0 / 1024.0 / 1024.0
    }

    private fun recommendedPreset(): Preset = when {
        ramGb >= 7.0 && maxHz >= 90f -> Presets.strong
        ramGb >= 4.5 -> Presets.mid
        else -> Presets.weak
    }

    private fun applyPreset(p: Preset) {
        cfg.frame = p.frame
        cfg.target = p.target
        cfg.quality = p.quality
        cfg.scale = p.scale
        cfg.upscaler = p.upscaler
        cfg.sharp = p.sharp
        spFrame.setSelection(p.frame)
        spTarget.setSelection(p.target)
        spQuality.setSelection(p.quality)
        spScale.setSelection(p.scale)
        spUpscaler.setSelection(p.upscaler)
        sbSharp.progress = p.sharp
        updateHint()
    }

    private fun updateHint() {
        val hints = mutableListOf<String>()
        val hz = Options.targetHz[cfg.target]
        if (hz > maxHz.toInt() + 1) {
            hints.add("จอเครื่องนี้รองรับสูงสุด ${maxHz.toInt()} Hz เฟรมที่เกินจะไม่ถูกแสดง")
        }
        if (cfg.frame >= 2 && cfg.scale == 2 && ramGb < 4.5) {
            hints.add("เครื่องอาจหนักเกินไป ลองลดตัวคูณเฟรมหรือสเกลภาพ")
        }
        if (cfg.scale == 0) {
            hints.add("สเกล 50% ภาพจะนุ่มลง ลองเพิ่ม Sharpness ช่วย")
        }
        tvHint.text = hints.joinToString("\n")
    }

    // ---------- update ----------

    private fun checkUpdate() {
        btnUpdate.isEnabled = false
        tvUpdate.text = "กำลังเช็ก..."
        Thread {
            val info = UpdateChecker.fetchLatest()
            runOnUiThread {
                when {
                    info == null -> {
                        tvUpdate.text = "เช็กอัพเดทไม่สำเร็จ (ตรวจเน็ต หรือยังไม่มี release)"
                        btnUpdate.isEnabled = true
                    }
                    info.versionCode > BuildConfig.VERSION_CODE -> startDownload(info)
                    else -> {
                        tvUpdate.text = "เป็นเวอร์ชันล่าสุดแล้ว (build ${BuildConfig.VERSION_CODE})"
                        btnUpdate.isEnabled = true
                    }
                }
            }
        }.start()
    }

    private fun startDownload(info: UpdateChecker.Info) {
        if (!packageManager.canRequestPackageInstalls()) {
            tvUpdate.text = "ต้องอนุญาตติดตั้งแอปจากแหล่งนี้ก่อน แล้วกดเช็กอัพเดทอีกครั้ง"
            startActivity(
                Intent(
                    AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName")
                )
            )
            btnUpdate.isEnabled = true
            return
        }
        tvUpdate.text = "พบ build ${info.versionCode} กำลังโหลด..."
        Thread {
            val f = UpdateChecker.download(this, info.apkUrl) { pct ->
                runOnUiThread { tvUpdate.text = "กำลังโหลด $pct%" }
            }
            runOnUiThread {
                btnUpdate.isEnabled = true
                if (f == null) {
                    tvUpdate.text = "โหลดไม่สำเร็จ"
                } else {
                    tvUpdate.text = "กำลังเปิดตัวติดตั้ง..."
                    UpdateChecker.install(this, f)
                }
            }
        }.start()
    }
}
