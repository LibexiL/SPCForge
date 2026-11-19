package com.libexil.spcforge

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Crash.install(this)
        Log.i("SPCForge", "SPCForge ${versionName()} on ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}")
    }

    fun versionName(): String = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "?" } catch (_: Exception) { "?" }
}

/** Saves an uncaught crash so the next launch can show it. */
object Crash {
    private lateinit var file: File

    fun install(app: App) {
        file = File(app.filesDir, "last_crash.txt")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = StringWriter(); e.printStackTrace(PrintWriter(sw))
                file.writeText("SPCForge ${app.versionName()} crashed\nDevice: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}\n" +
                    "Thread: ${t.name}\nTime: ${Date()}\n\n$sw")
            } catch (_: Throwable) {}
            previous?.uncaughtException(t, e)
        }
    }

    fun takeReport(): String? {
        if (!::file.isInitialized || !file.exists()) return null
        val s = try { file.readText() } catch (_: Exception) { null }
        file.delete()
        return s
    }
}

class Prefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("spcforge", Context.MODE_PRIVATE)

    var lat: Double
        get() = p.getFloat("lat", Float.NaN).toDouble()
        set(v) = p.edit().putFloat("lat", v.toFloat()).apply()
    var lon: Double
        get() = p.getFloat("lon", Float.NaN).toDouble()
        set(v) = p.edit().putFloat("lon", v.toFloat()).apply()
    var placeName: String
        get() = p.getString("place", "") ?: ""
        set(v) = p.edit().putString("place", v).apply()
    var locatedAt: Long
        get() = p.getLong("locAt", 0)
        set(v) = p.edit().putLong("locAt", v).apply()
    var useLocation: Boolean
        get() = p.getBoolean("useLoc", false)
        set(v) = p.edit().putBoolean("useLoc", v).apply()
    val hasLocation: Boolean get() = useLocation && !lat.isNaN() && !lon.isNaN()

    var reflow: Boolean
        get() = p.getBoolean("reflow", true)
        set(v) = p.edit().putBoolean("reflow", v).apply()
    var textSize: Float
        get() = p.getFloat("textSize", 15f)
        set(v) = p.edit().putFloat("textSize", v).apply()

    var mesoSector: String
        get() = p.getString("mesoSector", "19") ?: "19"
        set(v) = p.edit().putString("mesoSector", v).apply()
    var mesoParam: String
        get() = p.getString("mesoParam", "pmsl") ?: "pmsl"
        set(v) = p.edit().putString("mesoParam", v).apply()

    var autoUpdateCheck: Boolean
        get() = p.getBoolean("autoUpdate", true)
        set(v) = p.edit().putBoolean("autoUpdate", v).apply()
    var lastUpdateCheck: Long
        get() = p.getLong("lastUpdateCheck", 0)
        set(v) = p.edit().putLong("lastUpdateCheck", v).apply()
    var skippedVersion: String
        get() = p.getString("skippedVersion", "") ?: ""
        set(v) = p.edit().putString("skippedVersion", v).apply()
}
