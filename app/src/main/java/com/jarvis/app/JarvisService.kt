package com.jarvis.app

import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.speech.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import org.json.JSONObject
import java.util.Locale

class JarvisService : Service(), TextToSpeech.OnInitListener {
    private lateinit var tts: TextToSpeech
    private var sr: SpeechRecognizer? = null
    private val main = Handler(Looper.getMainLooper())
    private var speaking = false
    private var awake = false
    private var alive = true
    private var torch = false

    private val wake = listOf("jarvis", "jarvish", "जार्विस", "जारविस", "ଜାର୍ଭିସ")
    private val blockPkgs = listOf("com.phonepe", "paisa", "paytm", "npci", "bhim", "upi", "bank", "wallet", "razorpay", "mobikwik", "freecharge")
    private val blockWords = listOf("pay", "bank", "upi", "wallet", "bhim", "paytm", "phonepe", "paisa")

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val ch = NotificationChannel("jarvis", "Jarvis", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        val n = Notification.Builder(this, "jarvis")
            .setContentTitle("Jarvis sun rahi hai")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).build()
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(1, n)
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        tts.language = Locale("hi", "IN")
        tts.voices?.firstOrNull {
            it.locale.language == "hi" && (it.name.contains("female", true) || it.name.contains("hia") || it.name.contains("hic"))
        }?.let { tts.voice = it }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onError(id: String?) { done() }
            override fun onDone(id: String?) { done() }
            fun done() { main.post { speaking = false; listen() } }
        })
        say("Jarvis taiyaar hai. Jarvis bolo aur phir command do.")
    }

    private fun say(t: String) {
        speaking = true
        sr?.cancel()
        tts.speak(t, TextToSpeech.QUEUE_FLUSH, null, "u" + System.currentTimeMillis())
    }

    private fun listen() {
        if (!alive || speaking) return
        if (sr == null) {
            sr = SpeechRecognizer.createSpeechRecognizer(this)
            sr?.setRecognitionListener(object : RecognitionListener {
                override fun onResults(r: Bundle?) {
                    val t = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                    if (t.isNotBlank()) heard(t.lowercase()) else restart()
                }
                override fun onError(e: Int) { restart() }
                override fun onReadyForSpeech(p: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(p: Bundle?) {}
                override fun onEvent(t: Int, p: Bundle?) {}
            })
        }
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        sr?.startListening(i)
    }

    private fun restart() { main.postDelayed({ listen() }, 500) }

    private fun heard(t: String) {
        var cmd = t
        val hasWake = wake.any { t.contains(it) }
        if (hasWake) { wake.forEach { cmd = cmd.replace(it, "") }; cmd = cmd.trim() }
        if (!hasWake && !awake) { restart(); return }
        if (cmd.isEmpty()) { awake = true; say("Ji, boliye."); return }
        awake = false
        if (cmd in listOf("stop", "band karo", "exit")) { say("Theek hai, band kar rahi hu."); alive = false; main.postDelayed({ stopSelf() }, 3000); return }
        val key = getSharedPreferences("jarvis", MODE_PRIVATE).getString("key", "") ?: ""
        Thread {
            val j = Gemini.ask(key, cmd)
            main.post { if (j == null) say("Server busy hai, dobara bolo.") else execute(j, cmd) }
        }.start()
    }

    private fun isBlocked(pkgOrText: String): Boolean {
        val s = pkgOrText.lowercase()
        return blockPkgs.any { s.contains(it) } || blockWords.any { s.contains(it) }
    }

    private fun execute(j: JSONObject, cmd: String) {
        val a = j.optString("action", "none")
        val arg = j.optString("arg", "")
        val reply = j.optString("say", "Ho gaya.")
        val acc = JarvisAccess.instance

        if (a == "none") { say(reply); return }
        if (isBlocked(cmd) || isBlocked(arg) || (a != "home" && isBlocked(JarvisAccess.fg))) {
            say("Payment ya bank app me main kaam nahi karti."); return
        }
        val needAcc = a in listOf("home", "back", "recents", "notifications", "quick_settings", "tap", "scroll_down", "scroll_up", "read_screen")
        if (needAcc && acc == null) { say("Pehle Accessibility ON karo."); return }

        var ok = true
        when (a) {
            "torch_on", "torch_off" -> {
                val cm = getSystemService(CameraManager::class.java)
                torch = a == "torch_on"
                try { cm.setTorchMode(cm.cameraIdList[0], torch) } catch (e: Exception) { ok = false }
            }
            "volume" -> {
                val am = getSystemService(AudioManager::class.java)
                val v = (arg.toIntOrNull() ?: 8).coerceIn(0, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
                am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
            }
            "open_app" -> ok = openApp(arg)
            "home" -> acc!!.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
            "back" -> acc!!.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            "recents" -> acc!!.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS)
            "notifications" -> acc!!.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            "quick_settings" -> acc!!.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
            "tap" -> ok = acc!!.tapText(arg)
            "scroll_down" -> ok = acc!!.scroll(true)
            "scroll_up" -> ok = acc!!.scroll(false)
            "read_screen" -> { say(acc!!.screenText().ifBlank { "Screen par kuch nahi dikha." }); return }
            "wifi_panel" -> go(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
            "bluetooth_panel" -> go(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            "call" -> go(Intent(Intent.ACTION_CALL, Uri.parse("tel:" + arg)))
            else -> ok = false
        }
        say(if (ok) reply else "Ye kaam nahi ho paya.")
    }

    private fun go(i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { (JarvisAccess.instance ?: this as android.content.Context).startActivity(i) } catch (e: Exception) { }
    }

    private fun openApp(name: String): Boolean {
        val n = name.lowercase().trim()
        if (n.isEmpty()) return false
        val pm = packageManager
        var best: Pair<String, String>? = null
        for (app in pm.getInstalledApplications(0)) {
            if (pm.getLaunchIntentForPackage(app.packageName) == null) continue
            val label = pm.getApplicationLabel(app).toString().lowercase()
            if (isBlocked(label) || isBlocked(app.packageName)) continue
            if (label == n) { best = app.packageName to label; break }
            if (label.contains(n) || n.contains(label)) {
                if (best == null || label.length < best.second.length) best = app.packageName to label
            }
        }
        val pkg = best?.first ?: return false
        val i = pm.getLaunchIntentForPackage(pkg) ?: return false
        go(i)
        return true
    }

    override fun onDestroy() {
        alive = false
        sr?.destroy()
        tts.shutdown()
        super.onDestroy()
    }
}
