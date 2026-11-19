package com.libexil.spcforge

import android.app.AlertDialog
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.libexil.spcforge.core.Net
import com.libexil.spcforge.core.Releases
import com.libexil.spcforge.core.Releases.Release
import com.libexil.spcforge.ui.C
import com.libexil.spcforge.ui.W
import com.libexil.spcforge.ui.dpi
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * Keeps SPCForge up to date from its GitHub releases: looks about once a day, offers a new version,
 * downloads the APK and hands it to Android's installer (which asks the user to confirm).
 */
object Updater {
    private const val ACTION_STATUS = "com.libexil.spcforge.UPDATE_STATUS"
    private const val CHECK_EVERY_MS = 20L * 3600_000L
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var checking = false
    @Volatile private var downloading = false
    @Volatile private var cancelled = false
    private var receiverOn = false
    private var waitingForPermission: File? = null

    private fun installed(ctx: Context) = (ctx.applicationContext as App).versionName()
    private fun dir(ctx: Context) = File(ctx.cacheDir, "updates")
    private fun dialog(a: MainActivity) = AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)

    fun onResume(a: MainActivity) {
        waitingForPermission?.let { f -> if (a.packageManager.canRequestPackageInstalls()) { waitingForPermission = null; installFile(a, f) } }
        if (!a.prefs.autoUpdateCheck || checking || downloading) return
        if (System.currentTimeMillis() - a.prefs.lastUpdateCheck < CHECK_EVERY_MS) return
        main.postDelayed({ if (!a.isDestroyed) check(a, manual = false) }, 6_000)
    }

    fun check(a: MainActivity, manual: Boolean) {
        if (checking) return
        if (downloading) { if (manual) a.toast("The update is downloading…"); return }
        checking = true
        if (manual) a.toast("Checking for updates…")
        Thread({
            var rel: Release? = null
            var err: String? = null
            var reached = true
            try {
                rel = Releases.parse(Net.getText(Releases.LATEST_URL, accept = "application/vnd.github+json"))
                if (rel == null) err = "no published version found"
            } catch (e: Net.HttpError) {
                err = if (e.code == 404) "no versions have been published yet" else "GitHub answered ${e.code}"
            } catch (e: Exception) {
                err = e.message ?: e.javaClass.simpleName; reached = false
            }
            main.post {
                checking = false
                if (a.isDestroyed) return@post
                if (reached) a.prefs.lastUpdateCheck = System.currentTimeMillis()
                if (!downloading) dir(a).listFiles()?.forEach { it.delete() }
                val have = installed(a)
                when {
                    rel == null -> if (manual) a.toast("Couldn't check for updates: $err")
                    !Releases.isNewer(rel.version, have) -> if (manual) a.toast("SPCForge $have is the newest version")
                    !manual && rel.version == a.prefs.skippedVersion -> Unit
                    else -> offer(a, rel)
                }
            }
        }, "update-check").apply { isDaemon = true }.start()
    }

    private fun mb(bytes: Long) = String.format(Locale.US, "%.1f MB", bytes / 1048576.0)

    private fun offer(a: MainActivity, rel: Release) {
        val size = if (rel.apkSize > 0) " · ${mb(rel.apkSize)}" else ""
        val msg = "You have ${installed(a)}$size\n\n" + rel.notes.ifBlank { "" }
        val b = dialog(a).setTitle("SPCForge ${rel.version} is available").setMessage(msg.trim())
        if (rel.apkUrl.isEmpty()) b.setPositiveButton("Open release page") { _, _ -> open(a, rel.pageUrl) }
        else b.setPositiveButton("Update now") { _, _ -> download(a, rel) }
        b.setNegativeButton("Later", null)
        b.setNeutralButton("Skip this version") { _, _ -> a.prefs.skippedVersion = rel.version }
        b.show()
    }

    private fun open(a: MainActivity, url: String) {
        try { a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { a.toast(url) }
    }

    private fun download(a: MainActivity, rel: Release) {
        if (downloading) return
        val col = W.col(a).apply { val p = a.dpi(22f); setPadding(p, a.dpi(12f), p, 0) }
        val bar = ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal).apply { max = 1000; isIndeterminate = true }
        val label = TextView(a).apply { setTextColor(C.dim); text = "Starting…" }
        col.addView(bar); col.addView(label)
        val dlg = dialog(a).setTitle("Downloading SPCForge ${rel.version}").setView(col)
            .setNegativeButton("Cancel") { _, _ -> cancelled = true }.setCancelable(false).show()
        downloading = true; cancelled = false
        val app = a.applicationContext
        val out = File(dir(a), "SPCForge-${rel.version}.apk")
        Thread({
            var err: String? = null
            try {
                fetch(rel, out) { got, total ->
                    main.post {
                        if (total > 0) { bar.isIndeterminate = false; bar.progress = (got * 1000 / total).toInt() }
                        label.text = if (total > 0) "${mb(got)} of ${mb(total)}" else mb(got)
                    }
                }
                val info = app.packageManager.getPackageArchiveInfo(out.path, 0) ?: throw IOException("the file isn't a readable app")
                if (info.packageName != app.packageName) throw IOException("the file is a different app")
            } catch (e: Exception) {
                out.delete(); err = if (cancelled) null else e.message ?: e.javaClass.simpleName
            }
            main.post {
                downloading = false
                try { dlg.dismiss() } catch (_: Exception) {}
                if (cancelled) return@post
                if (err != null) { Toast.makeText(app, "The update didn't download: $err", Toast.LENGTH_LONG).show(); return@post }
                if (a.isDestroyed) installFile(app, out) else install(a, out)
            }
        }, "update-download").apply { isDaemon = true }.start()
    }

    private fun fetch(rel: Release, out: File, progress: (Long, Long) -> Unit) {
        out.parentFile?.mkdirs()
        val part = File(out.path + ".part")
        val c = Net.open(rel.apkUrl, timeoutMs = 30_000, accept = "application/octet-stream")
        try {
            val total = if (rel.apkSize > 0) rel.apkSize else c.contentLengthLong
            Net.stream(c).use { input ->
                part.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    var got = 0L; var last = 0L
                    while (true) {
                        if (cancelled) throw IOException("cancelled")
                        val n = input.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n); got += n
                        val now = System.currentTimeMillis()
                        if (now - last > 150) { last = now; progress(got, total) }
                    }
                    progress(got, total)
                    if (rel.apkSize > 0 && got != rel.apkSize) throw IOException("incomplete download")
                }
            }
        } finally { c.disconnect() }
        if (!part.renameTo(out)) throw IOException("couldn't save the download")
    }

    private fun install(a: MainActivity, f: File) {
        if (a.packageManager.canRequestPackageInstalls()) { installFile(a.applicationContext, f); return }
        waitingForPermission = f
        dialog(a).setTitle("Allow updates")
            .setMessage("Android needs your OK once before SPCForge can install its own updates. Turn on \"Allow from this source\" on the next screen, then come back.")
            .setPositiveButton("Open the setting") { _, _ ->
                try { a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${a.packageName}"))) }
                catch (_: Exception) { a.toast("Allow SPCForge under Settings → Apps → Special app access → Install unknown apps") }
            }
            .setNegativeButton("Not now") { _, _ -> waitingForPermission = null }
            .show()
    }

    private fun installFile(ctx: Context, f: File) {
        try {
            registerReceiver(ctx)
            val pi = ctx.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(ctx.packageName)
            params.setSize(f.length())
            val id = pi.createSession(params)
            pi.openSession(id).use { s ->
                f.inputStream().use { input -> s.openWrite("base.apk", 0, f.length()).use { o -> input.copyTo(o, 64 * 1024); s.fsync(o) } }
                val intent = Intent(ACTION_STATUS).setPackage(ctx.packageName)
                var flags = PendingIntent.FLAG_UPDATE_CURRENT
                if (Build.VERSION.SDK_INT >= 31) flags = flags or PendingIntent.FLAG_MUTABLE
                s.commit(PendingIntent.getBroadcast(ctx, id, intent, flags).intentSender)
            }
        } catch (e: Exception) {
            Toast.makeText(ctx, "Couldn't start the install: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun registerReceiver(ctx: Context) {
        if (receiverOn) return
        val app = ctx.applicationContext
        val filter = IntentFilter(ACTION_STATUS)
        if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(Status, filter, Context.RECEIVER_NOT_EXPORTED) else app.registerReceiver(Status, filter)
        receiverOn = true
    }

    private object Status : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
            when (status) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                        else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                    if (confirm != null) {
                        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try { ctx.startActivity(confirm) } catch (_: Exception) {}
                    }
                }
                PackageInstaller.STATUS_SUCCESS -> Unit
                else -> {
                    val text = when (status) {
                        PackageInstaller.STATUS_FAILURE_ABORTED -> "Update cancelled"
                        PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                            "The update is signed differently from the installed app. Uninstall SPCForge, then install the new version from GitHub."
                        PackageInstaller.STATUS_FAILURE_STORAGE -> "Not enough free space for the update"
                        else -> "The update didn't install${if (msg.isNullOrBlank()) "" else ": $msg"}"
                    }
                    Toast.makeText(ctx, text, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
