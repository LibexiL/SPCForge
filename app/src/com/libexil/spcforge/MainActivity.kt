package com.libexil.spcforge

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.libexil.spcforge.core.Repo
import com.libexil.spcforge.screens.ActiveScreen
import com.libexil.spcforge.screens.HomeScreen
import com.libexil.spcforge.screens.MoreScreen
import com.libexil.spcforge.screens.OutlookScreen
import com.libexil.spcforge.screens.ReportsScreen
import com.libexil.spcforge.screens.Screen
import com.libexil.spcforge.screens.TropicsScreen
import com.libexil.spcforge.ui.Bg
import com.libexil.spcforge.ui.Anim
import com.libexil.spcforge.ui.C
import com.libexil.spcforge.ui.dp
import com.libexil.spcforge.ui.rounded
import com.libexil.spcforge.ui.Icon
import com.libexil.spcforge.ui.IconView
import com.libexil.spcforge.ui.ImageCache
import com.libexil.spcforge.ui.W
import com.libexil.spcforge.ui.dpi
import com.libexil.spcforge.ui.ripple
import java.util.Locale

class MainActivity : Activity() {
    lateinit var prefs: Prefs
    private lateinit var content: FrameLayout
    private lateinit var titleView: TextView
    private lateinit var backBtn: IconView
    private lateinit var openBtn: IconView
    private lateinit var refreshBtn: IconView
    private lateinit var indicator: View
    private val navViews = ArrayList<View>()
    /** The view sliding out while a transition runs. */
    private var outgoing: View? = null
    private var shown: Screen? = null
    private var titleTarget = "SPCForge"
    private val navItems = ArrayList<Pair<IconView, TextView>>()
    private val stack = ArrayList<Screen>()
    private var tab = 0
    private var locationCallback: ((Boolean) -> Unit)? = null

    private val tabs = listOf("Home" to Icon.HOME, "Outlooks" to Icon.MAP, "Watches" to Icon.WARNING, "Reports" to Icon.LIST, "Tropics" to Icon.HURRICANE, "More" to Icon.MORE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        window.statusBarColor = C.panel
        window.navigationBarColor = C.panel

        val root = W.col(this).apply { setBackgroundColor(C.window) }
        // top bar
        val bar = W.row(this).apply { setBackgroundColor(C.panel); setPadding(dpi(4f), dpi(4f), dpi(4f), dpi(4f)) }
        backBtn = iconButton(Icon.BACK) { onBackPressed() }
        titleView = W.text(this, "SPCForge", 19f, C.text, true).apply { maxLines = 1; setPadding(dpi(8f), 0, 0, 0) }
        openBtn = iconButton(Icon.OPEN) { current()?.webUrl?.let { openUrl(it) } }
        bar.addView(backBtn, LinearLayout.LayoutParams(dpi(44f), dpi(44f)))
        bar.addView(titleView, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(openBtn, LinearLayout.LayoutParams(dpi(44f), dpi(44f)))
        refreshBtn = iconButton(Icon.REFRESH) { refresh() }
        bar.addView(refreshBtn, LinearLayout.LayoutParams(dpi(44f), dpi(44f)))
        root.addView(bar)

        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))

        // bottom navigation: a row of items over a highlight pill that glides to the selected one
        val navFrame = FrameLayout(this).apply { setBackgroundColor(C.panel) }
        indicator = View(this).apply { background = rounded(C.accentSoft, dp(16f)); alpha = 0f }
        navFrame.addView(indicator, FrameLayout.LayoutParams(dpi(52f), dpi(32f)))
        val nav = W.row(this).apply { setPadding(0, dpi(4f), 0, dpi(4f)) }
        navFrame.addView(nav, FrameLayout.LayoutParams(-1, -2))
        tabs.forEachIndexed { i, (label, icon) ->
            val item = W.col(this).apply {
                gravity = Gravity.CENTER; isClickable = true; background = ripple(null)
                setOnClickListener { selectTab(i) }
            }
            val iv = IconView(this, icon, C.dim)
            val tv = W.text(this, label, 11f, C.dim).apply { gravity = Gravity.CENTER; maxLines = 1 }
            item.addView(iv, LinearLayout.LayoutParams(dpi(30f), dpi(28f)))
            item.addView(tv)
            navItems.add(iv to tv)
            navViews.add(item)
            nav.addView(item, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(navFrame)
        navFrame.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> placeIndicator(animate = false) }
        setContentView(root)

        selectTab(0)
        Crash.takeReport()?.let { report ->
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("SPCForge closed unexpectedly last time")
                .setMessage("You can share the details so the problem can be fixed.")
                .setPositiveButton("Share") { _, _ ->
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report), "Share crash report"))
                }
                .setNegativeButton("Dismiss", null).show()
        }
        if (prefs.useLocation && hasLocationPermission() && System.currentTimeMillis() - prefs.locatedAt > 15 * 60_000L) locate(silent = true) {
            if (it) current()?.reload(false)
        }
    }

    private fun iconButton(icon: Icon, onClick: () -> Unit) = IconView(this, icon).apply {
        isClickable = true; background = ripple(null); setOnClickListener { onClick() }
    }

    override fun onResume() {
        super.onResume()
        Updater.onResume(this)
        current()?.let { s -> if (System.currentTimeMillis() - s.loadedAt > 5 * 60_000L && s.loadedAt > 0) s.reload(false) }
    }

    // ------------------------------------------------------------------ navigation
    fun current(): Screen? = stack.lastOrNull()

    fun selectTab(i: Int) {
        if (i == tab && stack.size == 1) { current()?.scrollTop(); return }
        val dir = if (i == tab) -1 else Integer.signum(i - tab)
        setNav(i)
        stack.clear()
        show(when (i) {
            0 -> HomeScreen(this)
            1 -> OutlookScreen(this, 1)
            2 -> ActiveScreen(this, 0)
            3 -> ReportsScreen(this)
            4 -> TropicsScreen(this)
            else -> MoreScreen(this)
        }, dir, tabSwitch = true)
    }

    /** Opens [s] on top of the current screen. */
    fun push(s: Screen) = show(s, 1, tabSwitch = false)

    /** Switches to an outlook / watches tab from elsewhere (e.g. Home's cards). */
    fun goTab(i: Int, screen: Screen) {
        val dir = if (i == tab) 1 else Integer.signum(i - tab)
        setNav(i)
        stack.clear()
        show(screen, dir, tabSwitch = true)
    }

    private fun show(s: Screen, dir: Int = 0, tabSwitch: Boolean = false) {
        stack.add(s)
        attach(s, dir, tabSwitch)
    }

    // ---- bottom bar
    private fun setNav(i: Int) {
        val prev = tab
        tab = i
        navItems.forEachIndexed { k, (iv, tv) ->
            val was = k == prev; val now = k == i
            if (was == now && navViews.isNotEmpty() && indicator.alpha > 0f) return@forEachIndexed
            if (indicator.alpha == 0f) { iv.setTint(if (now) C.accent else C.dim); tv.setTextColor(if (now) C.text else C.dim); return@forEachIndexed }
            tint(iv, if (was) C.accent else C.dim, if (now) C.accent else C.dim)
            Anim.textColor(tv, if (was) C.text else C.dim, if (now) C.text else C.dim)
            if (now) {
                iv.scaleX = 0.8f; iv.scaleY = 0.8f
                iv.animate().scaleX(1f).scaleY(1f).setDuration(320).setInterpolator(android.view.animation.OvershootInterpolator(2.2f)).start()
            }
        }
        placeIndicator(animate = true)
    }

    private fun tint(iv: IconView, from: Int, to: Int) {
        android.animation.ValueAnimator.ofObject(android.animation.ArgbEvaluator(), from, to).apply {
            duration = 200; addUpdateListener { iv.setTint(it.animatedValue as Int) }
        }.start()
    }

    /** Moves the highlight pill behind the selected tab's icon. */
    private fun placeIndicator(animate: Boolean) {
        val item = navViews.getOrNull(tab) ?: return
        val iv = navItems[tab].first
        if (item.width == 0) return
        val parentRow = item.parent as View
        val x = parentRow.left + item.left + (item.width - indicator.width) / 2f
        val y = parentRow.top + item.top + iv.top + (iv.height - indicator.height) / 2f
        if (!animate || indicator.alpha == 0f) {
            indicator.animate().cancel()
            indicator.translationX = x; indicator.translationY = y; indicator.scaleX = 1f
            if (indicator.alpha < 1f) indicator.animate().alpha(1f).setDuration(200).start()
        } else {
            indicator.scaleX = 1.2f
            indicator.animate().translationX(x).translationY(y).scaleX(1f).alpha(1f)
                .setDuration(Anim.SCREEN_MS).setInterpolator(Anim.ease).start()
        }
    }

    // ---- screen transitions
    /**
     * Shows [s]. dir > 0 slides it in from the right (opening something / a tab further right),
     * dir < 0 from the left (going back); tab switches move less and lean on a cross-fade.
     */
    private fun attach(s: Screen, dir: Int, tabSwitch: Boolean) {
        // finish a transition that's still running
        outgoing?.let { o -> o.animate().cancel(); content.removeView(o); o.translationX = 0f; o.alpha = 1f }
        outgoing = null
        if (shown === s) { updateBar(); return }
        val old: View? = if (content.childCount > 0) content.getChildAt(content.childCount - 1) else null
        val v = s.view()
        shown = s
        v.animate().cancel()
        v.translationX = 0f; v.alpha = 1f
        val lp = FrameLayout.LayoutParams(-1, -1)
        if (dir < 0) content.addView(v, 0, lp) else content.addView(v, lp)
        updateBar()
        if (old == null || dir == 0 || content.width == 0) { if (old != null) content.removeView(old); return }

        val w = content.width.toFloat()
        val shift = if (tabSwitch) w * 0.10f else w * 0.28f
        if (dir > 0) {
            v.translationX = shift; v.alpha = 0f
            v.animate().translationX(0f).alpha(1f).setDuration(Anim.SCREEN_MS).setInterpolator(Anim.ease).start()
            old.animate().translationX(-shift * 0.4f).alpha(0f).setDuration(Anim.SCREEN_MS * 3 / 4).setInterpolator(Anim.ease)
                .withEndAction { finishOut(old) }.start()
        } else {
            v.translationX = -shift * 0.4f; v.alpha = 0f
            v.animate().translationX(0f).alpha(1f).setDuration(Anim.SCREEN_MS).setInterpolator(Anim.ease).start()
            old.animate().translationX(shift).alpha(0f).setDuration(Anim.SCREEN_MS * 3 / 4).setInterpolator(Anim.ease)
                .withEndAction { finishOut(old) }.start()
        }
        outgoing = old
    }

    private fun finishOut(o: View) {
        content.removeView(o)
        o.translationX = 0f; o.alpha = 1f
        if (outgoing === o) outgoing = null
    }

    fun updateBar() {
        val s = current() ?: return
        val canBack = stack.size > 1
        if (titleTarget != s.title) {
            titleTarget = s.title
            titleView.animate().cancel()
            titleView.animate().alpha(0f).translationX(0f).setDuration(90).withEndAction {
                titleView.text = titleTarget
                titleView.translationX = dp(8f)
                titleView.animate().alpha(1f).translationX(0f).setDuration(180).setInterpolator(Anim.ease).start()
            }.start()
        }
        if (canBack != (backBtn.visibility == View.VISIBLE)) {
            if (canBack) {
                backBtn.visibility = View.VISIBLE
                backBtn.alpha = 0f; backBtn.translationX = dp(10f)
                backBtn.animate().alpha(1f).translationX(0f).setDuration(200).setInterpolator(Anim.ease).start()
            } else backBtn.visibility = View.GONE
        }
        titleView.setPadding(if (canBack) 0 else dpi(12f), 0, 0, 0)
        val showOpen = s.webUrl != null
        if (showOpen != (openBtn.visibility == View.VISIBLE)) {
            openBtn.visibility = if (showOpen) View.VISIBLE else View.GONE
            if (showOpen) { openBtn.alpha = 0f; openBtn.animate().alpha(1f).setDuration(200).start() }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (stack.size > 1) {
            stack.removeAt(stack.size - 1)
            attach(stack.last(), -1, tabSwitch = false)
        } else if (tab != 0) selectTab(0)
        else super.onBackPressed()
    }

    fun refresh() {
        refreshBtn.animate().cancel()
        refreshBtn.rotation = 0f
        refreshBtn.animate().rotation(360f).setDuration(600).setInterpolator(Anim.easeInOut).start()
        Repo.clear()
        ImageCache.clear()
        current()?.reload(true)
        toast("Refreshing…")
    }

    fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    fun openUrl(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { toast("No app can open $url") }
    }

    /** Shows a place in the user's maps app. */
    fun openMap(lat: Double, lon: Double, label: String) {
        val q = Uri.encode(label)
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(String.format(Locale.US, "geo:%.4f,%.4f?q=%.4f,%.4f(%s)", lat, lon, lat, lon, q)))) }
        catch (_: Exception) { openUrl(String.format(Locale.US, "https://www.google.com/maps/search/?api=1&query=%.4f,%.4f", lat, lon)) }
    }

    // ------------------------------------------------------------------ location
    fun hasLocationPermission() = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Turns on "your location" and finds it; [done] gets whether a position was found. */
    fun locate(silent: Boolean = false, done: (Boolean) -> Unit) {
        if (!hasLocationPermission()) {
            if (silent) { done(false); return }
            locationCallback = done
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 7)
            return
        }
        prefs.useLocation = true
        findLocation(silent, done)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val cb = locationCallback ?: return
        locationCallback = null
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) { prefs.useLocation = true; findLocation(false, cb) }
        else { toast("Location wasn't allowed – you can still see everything nationwide"); cb(false) }
    }

    @SuppressLint("MissingPermission")
    private fun findLocation(silent: Boolean, done: (Boolean) -> Unit) {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        var best: Location? = null
        for (p in listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
            try { lm.getLastKnownLocation(p)?.let { if (best == null || it.time > best!!.time) best = it } } catch (_: Exception) {}
        }
        val fresh = best?.let { System.currentTimeMillis() - it.time < 30 * 60_000L } ?: false
        if (best != null && fresh) { save(best!!, done); return }
        var finished = false
        val finish: (Location?) -> Unit = { loc ->
            if (!finished) {
                finished = true
                val use = loc ?: best
                if (use != null) save(use, done) else { if (!silent) toast("Couldn't find your location – is Location turned on?"); done(false) }
            }
        }
        val provider = when {
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> null
        }
        if (provider == null) { finish(null); return }
        if (!silent) toast("Finding your location…")
        try {
            if (Build.VERSION.SDK_INT >= 30) lm.getCurrentLocation(provider, null, mainExecutor) { finish(it) }
            else @Suppress("DEPRECATION") lm.requestSingleUpdate(provider, object : LocationListener {
                override fun onLocationChanged(l: Location) { finish(l) }
                @Deprecated("Deprecated in Java") override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
                override fun onProviderEnabled(p: String) {}
                override fun onProviderDisabled(p: String) {}
            }, mainLooper)
        } catch (_: Exception) { finish(null); return }
        Bg.main.postDelayed({ finish(null) }, 20_000)
    }

    private fun save(l: Location, done: (Boolean) -> Unit) {
        prefs.lat = l.latitude; prefs.lon = l.longitude; prefs.locatedAt = System.currentTimeMillis()
        Bg.run({
            try {
                @Suppress("DEPRECATION")
                Geocoder(this, Locale.US).getFromLocation(l.latitude, l.longitude, 1)?.firstOrNull()?.let { ad ->
                    val city = ad.locality ?: ad.subAdminArea ?: ""
                    val st = ad.adminArea?.let { STATES[it] ?: it } ?: ""
                    listOf(city, st).filter { it.isNotEmpty() }.joinToString(", ")
                } ?: ""
            } catch (_: Exception) { "" }
        }) { r ->
            prefs.placeName = r.getOrNull() ?: ""
            done(true)
        }
    }

    companion object {
        val STATES = mapOf("Alabama" to "AL", "Alaska" to "AK", "Arizona" to "AZ", "Arkansas" to "AR", "California" to "CA", "Colorado" to "CO",
            "Connecticut" to "CT", "Delaware" to "DE", "Florida" to "FL", "Georgia" to "GA", "Hawaii" to "HI", "Idaho" to "ID", "Illinois" to "IL",
            "Indiana" to "IN", "Iowa" to "IA", "Kansas" to "KS", "Kentucky" to "KY", "Louisiana" to "LA", "Maine" to "ME", "Maryland" to "MD",
            "Massachusetts" to "MA", "Michigan" to "MI", "Minnesota" to "MN", "Mississippi" to "MS", "Missouri" to "MO", "Montana" to "MT",
            "Nebraska" to "NE", "Nevada" to "NV", "New Hampshire" to "NH", "New Jersey" to "NJ", "New Mexico" to "NM", "New York" to "NY",
            "North Carolina" to "NC", "North Dakota" to "ND", "Ohio" to "OH", "Oklahoma" to "OK", "Oregon" to "OR", "Pennsylvania" to "PA",
            "Rhode Island" to "RI", "South Carolina" to "SC", "South Dakota" to "SD", "Tennessee" to "TN", "Texas" to "TX", "Utah" to "UT",
            "Vermont" to "VT", "Virginia" to "VA", "Washington" to "WA", "West Virginia" to "WV", "Wisconsin" to "WI", "Wyoming" to "WY",
            "District of Columbia" to "DC")
    }
}
