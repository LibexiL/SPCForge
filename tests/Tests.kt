package com.libexil.spcforge.tests

import com.libexil.spcforge.core.Geo
import com.libexil.spcforge.core.Html
import com.libexil.spcforge.core.Releases
import com.libexil.spcforge.core.Spc
import com.libexil.spcforge.core.TextFmt
import com.libexil.spcforge.core.Time
import kotlin.system.exitProcess

private var failures = 0
private fun check(name: String, ok: Boolean, detail: () -> String = { "" }) {
    if (ok) println("ok   $name") else { failures++; println("FAIL $name ${detail()}") }
}

private val OUTLOOK_HTML = """
<html><head><title>Nov 18, 2026 2000 UTC Day 1 Convective Outlook</title></head><body>
<img src="/images/spc_logo.gif"><img src="/new/images/nav_bar.gif">
<a href="javascript:show_tab('otlk_2000')" onmouseover="show_tab('otlk_2000')">Categorical</a>
<a href="javascript:show_tab('probotlk_2000_torn')">Tornado</a>
<a href="javascript:show_tab('probotlk_2000_wind')">Wind</a>
<a href="javascript:show_tab('probotlk_2000_hail')">Hail</a>
<a href="day1otlk_1300.html">1300Z</a>
<img name="otlk_img" src="day1otlk_2000.png" width="815">
<table><tr><td>SLIGHT</td><td>42,104</td><td>1,234,567</td><td>Tulsa, OK...Wichita, KS...</td></tr>
<tr><td>MARGINAL</td><td>88,000</td><td>5,000,000</td><td>Dallas, TX...</td></tr>
<tr><td>Area (sq. mi.)</td><td>x</td><td>y</td></tr></table>
<pre>
   SPC AC 181952

   Day 1 Convective Outlook
   NWS Storm Prediction Center Norman OK
   0152 PM CST Wed Nov 18 2026

   Valid 182000Z - 191200Z

   ...THERE IS A SLIGHT RISK OF SEVERE THUNDERSTORMS ACROSS PARTS OF
   NORTHEAST OKLAHOMA AND SOUTHEAST KANSAS...

   ...SUMMARY...
   Scattered severe thunderstorms capable of large hail and damaging
   winds are expected this evening across parts of northeast Oklahoma
   &amp; southeast Kansas.

   ..Smith.. 11/18/2026

   <a href="/products/outlook/archive/pts.txt">CLICK TO GET WUUS01 PTSDY1 PRODUCT</a>
</pre></body></html>
""".trimIndent()

private val CSV = """
Time,F_Scale,Location,County,State,Lat,Lon,Comments
2215,EF2,3 N Norman,Cleveland,OK,35.27,-97.44,Tornado crossed I-35, damaging homes. (OUN)
Time,Speed,Location,County,State,Lat,Lon,Comments
2300,UNK,Tulsa,Tulsa,OK,36.15,-95.99,Trees down. (TSA)
0130,80,Joplin,Jasper,MO,37.08,-94.51,Measured gust. (SGF)
Time,Size,Location,County,State,Lat,Lon,Comments
2230,175,Moore,Cleveland,OK,35.34,-97.49,Golf ball hail. (OUN)
2245,200,Moore,Cleveland,OK,35.34,-97.49,(OUN)
""".trimIndent()

private val MD_JSON = """
{"type":"FeatureCollection","features":[{"type":"Feature","properties":{"product_id":"202611182100-KWNS-ACUS11-SWOMCD",
"num":2145,"year":2026,"issue":"2026-11-18T21:00:00Z","expire":"2026-11-18T23:00:00Z","concerning":"Severe potential...Watch likely",
"watch_confidence":80},"geometry":{"type":"Polygon","coordinates":[[[-98,35],[-95,35],[-95,37],[-98,37],[-98,35]]]}}]}
""".trimIndent()

private val NWS_JSON = """
{"type":"FeatureCollection","features":[
{"properties":{"event":"Tornado Watch","areaDesc":"Cleveland, OK; McClain, OK; Grady, OK","expires":"2026-11-19T03:00:00-06:00","ends":"2026-11-19T04:00:00Z",
 "parameters":{"VTEC":["/O.NEW.KOUN.TO.A.0612.261118T2200Z-261119T0400Z/"]}}},
{"properties":{"event":"Tornado Watch","areaDesc":"Montgomery, KS","expires":"2026-11-19T03:00:00-06:00","ends":"2026-11-19T04:00:00Z",
 "parameters":{"VTEC":["/O.NEW.KICT.TO.A.0612.261118T2200Z-261119T0400Z/"]}}},
{"properties":{"event":"Severe Thunderstorm Watch","areaDesc":"Jasper, MO","expires":"2026-11-19T02:00:00Z","ends":null,
 "parameters":{"VTEC":["/O.NEW.KSGF.SV.A.0613.261118T2300Z-261119T0500Z/"]}}},
{"properties":{"event":"Flood Watch","areaDesc":"X, MO","parameters":{"VTEC":["/O.NEW.KSGF.FA.A.0003.000000T0000Z-261119T0500Z/"]}}}
]}
""".trimIndent()

private val OUTLOOK_GEO = """
{"type":"FeatureCollection","features":[
{"properties":{"category":"CATEGORICAL","threshold":"MRGL","issue":"2026-11-18T20:00:00Z","expire":"2026-11-19T12:00:00Z"},
 "geometry":{"type":"Polygon","coordinates":[[[-100,33],[-93,33],[-93,39],[-100,39],[-100,33]]]}},
{"properties":{"category":"CATEGORICAL","threshold":"SLGT","issue":"2026-11-18T20:00:00Z","expire":"2026-11-19T12:00:00Z"},
 "geometry":{"type":"MultiPolygon","coordinates":[[[[-98,35],[-95,35],[-95,37],[-98,37],[-98,35]]]]}},
{"properties":{"category":"TORNADO","threshold":"0.02","issue":"2026-11-18T20:00:00Z","expire":"2026-11-19T12:00:00Z"},
 "geometry":{"type":"Polygon","coordinates":[[[-98,35],[-95,35],[-95,37],[-98,37],[-98,35]]]}},
{"properties":{"category":"HAIL","threshold":"0.15","issue":"2026-11-18T20:00:00Z","expire":"2026-11-19T12:00:00Z"},
 "geometry":{"type":"Polygon","coordinates":[[[-98,35],[-95,35],[-95,37],[-98,37],[-98,35]]]}},
{"properties":{"category":"HAIL","threshold":"0.05","issue":"2026-11-18T20:00:00Z","expire":"2026-11-19T12:00:00Z"},
 "geometry":{"type":"Polygon","coordinates":[[[-100,33],[-93,33],[-93,39],[-100,39],[-100,33]]]}},
{"properties":{"category":"HAIL","threshold":"SIGN","issue":"2026-11-18T20:00:00Z","expire":"2026-11-19T12:00:00Z"},
 "geometry":{"type":"Polygon","coordinates":[[[-98,35],[-95,35],[-95,37],[-98,37],[-98,35]]]}}
]}
""".trimIndent()

fun main() {
    // ---- outlook page scraping
    check("issuance time", Spc.issuanceTime(OUTLOOK_HTML, 1) == "2000") { Spc.issuanceTime(OUTLOOK_HTML, 1).toString() }
    val imgs = Spc.outlookImages(1, "2000")
    check("categorical image", imgs[0].urls[0] == "https://www.spc.noaa.gov/products/outlook/day1otlk_2000.png") { imgs[0].urls.toString() }
    check("tornado image", imgs[1].urls[0] == "https://www.spc.noaa.gov/products/outlook/day1probotlk_2000_torn.png")
    check("day3 images", Spc.outlookImages(3, "0730").map { it.label } == listOf("Categorical", "Probabilistic"))
    val found = Html.images(OUTLOOK_HTML, Spc.outlookPage(1))
    check("page images skip logos", found == listOf("https://www.spc.noaa.gov/products/outlook/day1otlk_2000.png")) { found.toString() }
    check("title", Html.title(OUTLOOK_HTML) == "Nov 18, 2026 2000 UTC Day 1 Convective Outlook")
    val rt = Spc.riskTable(OUTLOOK_HTML)
    check("risk table", rt.size == 2 && rt[0][0] == "SLIGHT" && rt[1][3] == "Dallas, TX...") { rt.toString() }
    val pre = Html.pres(OUTLOOK_HTML)
    check("pre text", pre.size == 1 && pre[0].contains("&") && !pre[0].contains("<a") && pre[0].contains("CLICK TO GET")) { pre.toString() }
    val flowed = TextFmt.reflow(pre[0])
    println("---- reflowed\n$flowed\n----")
    check("reflow joins wrapped lines", flowed.contains("large hail and damaging winds are expected this evening across parts of northeast Oklahoma & southeast Kansas."))
    check("reflow joins headline", flowed.contains("ACROSS PARTS OF NORTHEAST OKLAHOMA"))
    check("reflow keeps short lines", flowed.contains("SPC AC 181952\n\nDay 1 Convective Outlook\nNWS Storm Prediction Center Norman OK"))
    check("reflow keeps headers", flowed.contains("...SUMMARY...\nScattered"))
    val ll = TextFmt.reflow("   LAT...LON   35100015 36039915 36689802 37169693 37329567 37099469\n               36109512 35279615 35100015\n\n   MOST PROBABLE PEAK TORNADO INTENSITY...UP TO 95 MPH")
    check("reflow keeps coordinate lists", ll.lines().size >= 3 && ll.lines()[1].trim().startsWith("36109512")) { ll }

    // ---- storm reports
    val reps = Spc.parseReports(CSV)
    check("reports parsed", reps.size == 5 && reps.count { it.kind == 'T' } == 1 && reps.count { it.kind == 'W' } == 2 && reps.count { it.kind == 'H' } == 2) { reps.toString() }
    check("report comment keeps commas", reps[0].comments == "Tornado crossed I-35, damaging homes. (OUN)") { reps[0].comments }
    check("report titles", reps[0].title == "Tornado (EF2)" && reps[1].title == "Wind damage" && reps[2].title == "Wind 80 mph" && reps[3].title == "Hail 1.75\"") {
        reps.map { it.title }.toString() }
    check("significant", reps[0].significant && reps[2].significant && !reps[3].significant && reps[4].significant)
    val day = Time.convectiveDayStart(Time.parseIso("2026-11-19T03:00:00Z"))
    check("convective day", day == Time.parseIso("2026-11-18T12:00:00Z"))
    check("report time after midnight", Time.reportTime("0130", day) == Time.parseIso("2026-11-19T01:30:00Z"))
    check("report time evening", Time.reportTime("2215", day) == Time.parseIso("2026-11-18T22:15:00Z"))

    // ---- MDs
    val mds = Spc.parseMds(MD_JSON)
    check("md parse", mds.size == 1 && mds[0].number == 2145 && mds[0].watchChance == 80 && mds[0].contains(36.0, -96.0) && !mds[0].contains(40.0, -96.0))
    check("md times", mds[0].issueMs == Time.parseIso("2026-11-18T21:00:00+00:00") && mds[0].issueMs > 0)

    // ---- watches
    val wmap = LinkedHashMap<Int, Spc.Watch>()
    Spc.parseNwsWatches(NWS_JSON, wmap)
    val w612 = wmap[612]
    check("watch grouping", wmap.size == 2 && w612 != null && w612.type == "TO" && w612.counties == 4 && w612.states == sortedSetOf("KS", "OK")) {
        wmap.values.map { "${it.number} ${it.type} ${it.counties} ${it.states}" }.toString() }
    check("watch names", w612!!.name == "Tornado Watch 612" && wmap[613]!!.name == "Severe Thunderstorm Watch 613")
    check("watch expiry", w612.expiresMs == Time.parseIso("2026-11-19T04:00:00Z")) { Time.zulu(w612.expiresMs) }

    // ---- point risk
    val areas = Spc.parseOutlookGeo(OUTLOOK_GEO)
    val r = Spc.riskAt(1, areas, 36.0, -96.0)
    check("risk category", r.category == "SLGT") { r.category.toString() }
    check("risk probs", r.probLine() == "Tornado 2% · Hail 15% (significant)") { r.probLine() }
    val r2 = Spc.riskAt(1, areas, 34.0, -99.0)
    check("risk marginal", r2.category == "MRGL" && r2.probLine() == "Hail 5%") { r2.probLine() }
    check("risk none", Spc.riskAt(1, areas, 45.0, -80.0).category == null)
    check("highest", Spc.highestCategory(areas) == "SLGT")
    check("geo rings", Geo.ringsContain(areas[0].rings, 36.0, -96.0))
    check("geo urls", Spc.outlookGeoUrls(Time.parseIso("2026-11-18T21:00:00Z"), 1).first().endsWith("valid=2026-11-18&cycle=20"))

    // ---- misc
    check("entities", Html.decode("a &amp; b &#39;c&#x27; &nbsp;") == "a & b 'c'  ")
    check("version compare", Releases.isNewer("1.0.1", "1.0.0") && !Releases.isNewer("1.0.0", "1.0.0"))
    val watchIdx = "<a href=\"ww0612.html\">Tornado Watch 612</a> <a href=\"ww0613.html\">Severe Thunderstorm Watch #613</a>"
    check("watch index numbers", Html.matches(watchIdx, Regex("""ww(\d{4})\.html""")) == listOf("0612", "0613"))

    println(if (failures == 0) "ALL OK" else "$failures FAILED")
    if (failures > 0) exitProcess(1)
}
