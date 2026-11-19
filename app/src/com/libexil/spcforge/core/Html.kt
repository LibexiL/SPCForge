package com.libexil.spcforge.core

import java.net.URL
import java.util.Locale

/** Just enough HTML handling to pull the useful parts out of SPC's pages. */
object Html {
    private val ENT = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "deg" to "°", "ndash" to "–", "mdash" to "—", "rsquo" to "’", "lsquo" to "‘", "rdquo" to "”", "ldquo" to "“", "copy" to "©")

    fun decode(s: String): String = Regex("""&(#x[0-9a-fA-F]+|#\d+|[a-zA-Z]+);""").replace(s) { m ->
        val k = m.groupValues[1]
        when {
            k.startsWith("#x") || k.startsWith("#X") -> k.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
            k.startsWith("#") -> k.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
            else -> ENT[k.lowercase(Locale.US)] ?: m.value
        }
    }

    /** Tags removed, <br> and block ends turned into line breaks, entities decoded. */
    fun strip(s: String): String = decode(
        s.replace(Regex("""(?is)<(script|style)[^>]*>.*?</\1>"""), "")
            .replace(Regex("""(?i)<br\s*/?>"""), "\n")
            .replace(Regex("""(?i)</(p|div|tr|li|h\d)>"""), "\n")
            .replace(Regex("""(?s)<[^>]*>"""), "")
    )

    /** The contents of every <pre> block, as plain text. */
    fun pres(html: String): List<String> =
        Regex("""(?is)<pre[^>]*>(.*?)</pre>""").findAll(html).map { strip(it.groupValues[1]).trimEnd().trimStart('\n', '\r') }
            .filter { it.isNotBlank() }.toList()

    fun title(html: String): String =
        Regex("""(?is)<title[^>]*>(.*?)</title>""").find(html)?.groupValues?.get(1)?.let { strip(it).replace(Regex("""\s+"""), " ").trim() } ?: ""

    fun resolve(base: String, ref: String): String = try { URL(URL(base), ref.trim()).toString() } catch (_: Exception) { ref }

    private val JUNK = listOf("logo", "nav", "spacer", "bullet", "header", "banner", "button", "arrow", "icon", "/images/",
        "blank", "usagov", "noaa_", "nws_", "weather.gov", "twitter", "facebook", "youtube", "rss", "email", "search",
        "top.", "skip", "thumb", "legend", "seal", "dot.", "pixel", "_sm.", "small", "new.gif", "bar_", "corner", "line.")

    /**
     * Every .png / .gif / .jpg file the page mentions (in tags or in its scripts), resolved against [base],
     * minus logos and navigation graphics. [keep] narrows further (e.g. to names containing a watch number).
     */
    fun images(html: String, base: String, keep: (String) -> Boolean = { true }): List<String> {
        val out = LinkedHashSet<String>()
        for (m in Regex("""(?i)(?<![A-Za-z0-9_./:\-])([A-Za-z0-9_./:\-]+\.(?:png|gif|jpe?g))(?![A-Za-z0-9])""").findAll(html)) {
            val ref = m.groupValues[1]
            val low = ref.lowercase(Locale.US)
            if (JUNK.any { low.contains(it) }) continue
            if (low.startsWith(".") && !low.startsWith("./") && !low.startsWith("../")) continue
            val abs = resolve(base, ref)
            if (!abs.startsWith("http")) continue
            if (keep(abs.substringAfterLast('/').lowercase(Locale.US))) out.add(abs)
        }
        return out.toList()
    }

    /** Distinct matches of [pattern]'s first group, in page order. */
    fun matches(html: String, pattern: Regex): List<String> = pattern.findAll(html).map { it.groupValues[1] }.distinct().toList()

    /** Every table row as a list of cell texts. */
    fun tableRows(html: String): List<List<String>> =
        Regex("""(?is)<tr[^>]*>(.*?)</tr>""").findAll(html).map { row ->
            Regex("""(?is)<t[dh][^>]*>(.*?)</t[dh]>""").findAll(row.groupValues[1])
                .map { strip(it.groupValues[1]).replace(Regex("""\s+"""), " ").trim() }.toList()
        }.filter { r -> r.any { it.isNotEmpty() } }.toList()
}

/** Makes SPC's fixed-width products (hard-wrapped at ~70 columns) read well on a phone. */
object TextFmt {
    private fun indent(s: String) = s.length - s.trimStart().length

    fun reflow(raw: String): String {
        val lines = raw.replace("\r", "").replace("\t", "    ").lines().map { it.trimEnd() }
        val base = lines.filter { it.isNotBlank() }.minOfOrNull { indent(it) } ?: 0
        val out = StringBuilder()
        val para = StringBuilder()
        var prev: String? = null           // previous line of the paragraph (with its indent, base removed)
        var prevFixed = false

        fun flush() {
            if (para.isNotEmpty()) { out.append(para).append('\n'); para.setLength(0) }
            prev = null
        }

        for (full in lines) {
            if (full.isBlank()) {
                flush()
                if (out.isNotEmpty() && !out.endsWith("\n\n")) out.append('\n')
                continue
            }
            val l = if (full.length >= base) full.substring(base) else full.trimStart()
            val t = l.trim()
            val fixed = indent(l) >= 2 || Regex("""\S {3,}\S""").containsMatchIn(t) || t.startsWith("&&") || t.startsWith("$$") ||
                Regex("""^\.\.[A-Za-z /.-]+\.\.\s+\d""").containsMatchIn(t)
            val p = prev
            val join = p != null && !fixed && !prevFixed && p.trim().length >= 48 &&
                !t.startsWith("...") && !t.startsWith("- ") && !t.startsWith("* ") && !t.startsWith("ATTN")
            if (join) para.append(' ').append(t)
            else {
                if (para.isNotEmpty()) out.append(para).append('\n')
                para.setLength(0)
                para.append(if (fixed) l.trimEnd() else t)
            }
            prev = l
            prevFixed = fixed
        }
        flush()
        return out.toString().trim('\n')
    }
}
