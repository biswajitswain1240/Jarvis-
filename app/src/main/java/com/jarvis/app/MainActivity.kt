package com.jarvis.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val sp = getSharedPreferences("jarvis", MODE_PRIVATE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 120, 48, 48)
        }
        val title = TextView(this).apply { text = "JARVIS"; textSize = 30f }
        val key = EditText(this).apply { hint = "Gemini API key"; setText(sp.getString("key", "")) }
        fun btn(t: String, f: () -> Unit) = Button(this).apply { text = t; setOnClickListener { f() } }

        root.addView(title)
        root.addView(key)
        root.addView(btn("1. Key save karo") {
            sp.edit().putString("key", key.text.toString().trim()).apply()
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        })
        root.addView(btn("2. Accessibility ON karo (Jarvis chuno)") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(btn("3. Display over other apps ON karo") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        root.addView(btn("4. Jarvis START") {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.CALL_PHONE), 1)
                Toast.makeText(this, "Permission do, phir START dobara dabao", Toast.LENGTH_LONG).show()
            } else {
                sp.edit().putString("key", key.text.toString().trim()).apply()
                startForegroundService(Intent(this, JarvisService::class.java))
            }
        })
        root.addView(btn("STOP") { stopService(Intent(this, JarvisService::class.java)) })
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
