package com.jarvis.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Gemini {
    private val models = listOf("gemini-3.5-flash-lite", "gemini-3.5-flash", "gemini-3.1-flash-lite")

    const val SYSTEM = """Tum Jarvis ho, ek female AI assistant. User Hindi, English ya Odia (mix) bolta hai.
Sirf JSON do: {"action":"...","arg":"...","say":"..."}
action options: none, torch_on, torch_off, volume (arg 0-15), open_app (arg app ka naam),
home, back, recents, notifications, quick_settings, tap (arg screen par button ka text),
scroll_down, scroll_up, read_screen, wifi_panel, bluetooth_panel, call (arg number).
Normal baat ho to action none. say me Hinglish me chhota (1 line) jawab do."""

    fun ask(key: String, text: String, extra: String = ""): JSONObject? {
        for (i in 0 until 6) {
            try {
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/${models[i % 3]}:generateContent")
                val c = url.openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.connectTimeout = 15000
                c.readTimeout = 30000
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.setRequestProperty("x-goog-api-key", key)
                val body = JSONObject()
                    .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM + extra))))
                    .put("contents", JSONArray().put(JSONObject().put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", text)))))
                    .put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
                c.outputStream.use { it.write(body.toString().toByteArray()) }
                if (c.responseCode == 200) {
                    val r = JSONObject(c.inputStream.bufferedReader().readText())
                    val t = r.getJSONArray("candidates").getJSONObject(0)
                        .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                    return JSONObject(t)
                }
            } catch (e: Exception) { }
            Thread.sleep(2000)
        }
        return null
    }
}
