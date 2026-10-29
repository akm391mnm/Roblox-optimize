package com.kob.frameboost

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * ไม่ทำอะไรกับหน้าจอ ใช้เป็นที่ให้สร้างหน้าต่างชนิด TYPE_ACCESSIBILITY_OVERLAY
 * ซึ่ง Android ถือว่าเชื่อถือได้ จึงไม่โดนบล็อกการแตะทะลุ
 */
class FrameBoostAccessibility : AccessibilityService() {

    companion object {
        @Volatile
        var instance: FrameBoostAccessibility? = null
    }

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}
}
