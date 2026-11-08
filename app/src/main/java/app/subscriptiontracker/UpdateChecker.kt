package app.subscriptiontracker

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Una versión nueva publicada en GitHub: su número, el APK y las notas del release.
data class UpdateInfo(val version: String, val apkUrl: String, val notes: String)

object UpdateChecker {
    private const val LATEST = "https://api.github.com/repos/%s/releases/latest"

    // Devuelve la versión nueva si hay una más reciente que `current`; si no, null.
    // Lanza excepción si no hay red o GitHub no responde: el llamador decide qué hacer.
    fun check(repo: String, current: String): UpdateInfo? {
        val conn = (URL(LATEST.format(repo)).openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        try {
            if (conn.responseCode != 200) return null
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = json.getString("tag_name").removePrefix("v")
            if (compareVersions(tag, current) <= 0) return null

            val assets = json.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("name").endsWith(".apk")) {
                    return UpdateInfo(tag, asset.getString("browser_download_url"), json.optString("body"))
                }
            }
            return null
        } finally {
            conn.disconnect()
        }
    }

    // Compara "1.2.3" con "1.10.0" parte por parte, como números.
    fun compareVersions(a: String, b: String): Int {
        val pa = a.split('.').map { it.toIntOrNull() ?: 0 }
        val pb = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val diff = pa.getOrElse(i) { 0 } - pb.getOrElse(i) { 0 }
            if (diff != 0) return diff
        }
        return 0
    }
}
