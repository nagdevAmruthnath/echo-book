package com.echobooks.app.data

import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.Charset
import javax.xml.parsers.DocumentBuilderFactory

data class ParsedChapter(val title: String, val text: String)

data class ParsedBook(
    val title: String,
    val author: String,
    val chapters: List<ParsedChapter>
)

object EbookParser {

    fun parse(extension: String, content: InputStream, fallbackTitle: String): ParsedBook {
        val bytes = content.readBytes()
        return when (extension.lowercase()) {
            "epub" -> parseEpub(bytes, fallbackTitle)
            "mobi", "azw", "azw3", "prc" -> parseMobi(bytes, fallbackTitle)
            else -> parseTxt(bytes, fallbackTitle)
        }
    }

    // ---------- Plain text ----------

    fun parseTxt(bytes: ByteArray, fallbackTitle: String): ParsedBook {
        val text = String(bytes, Charsets.UTF_8).replace("\uFEFF", "")
        val chapters = splitIntoChapters(text)
        return ParsedBook(title = fallbackTitle, author = "EchoBooks", chapters = chapters)
    }

    // ---------- EPUB ----------

    fun parseEpub(bytes: ByteArray, fallbackTitle: String): ParsedBook {
        val entries = zipEntries(bytes)
        val container = entries["META-INF/container.xml"]
            ?: throw IllegalArgumentException("Not a valid EPUB (missing container.xml)")
        val rootfile = firstTagAttribute(container, "rootfile", "full-path")
            ?: throw IllegalArgumentException("Not a valid EPUB (missing rootfile)")
        val opf = entries.remove(rootfile)
            ?: throw IllegalArgumentException("Not a valid EPUB (missing OPF)")

        val doc = parseXml(opf)
        val nsUri = "*"
        val title = firstElementText(doc, nsUri, "title") ?: fallbackTitle
        val author = firstElementText(doc, nsUri, "creator")?.takeIf { it.isNotBlank() } ?: "EchoBooks"
        val opfDir = rootfile.substringBeforeLast('/', "")

        val manifest = mutableMapOf<String, String>()
        forEachElement(doc, nsUri, "item") { e ->
            val id = e.getAttribute("id").takeIf { it.isNotEmpty() }
            val href = e.getAttribute("href").takeIf { it.isNotEmpty() }
            if (id != null && href != null) manifest[id] = href
        }
        val spine = mutableListOf<String>()
        forEachElement(doc, nsUri, "itemref") { e ->
            if (e.getAttribute("linear").equals("no", ignoreCase = true)) return@forEachElement
            val idref = e.getAttribute("idref")
            if (idref.isNotEmpty()) spine += idref
        }

        val chapters = mutableListOf<ParsedChapter>()
        spine.forEachIndexed { i, idref ->
            val href = manifest[idref] ?: return@forEachIndexed
            val path = resolvePath(opfDir, href)
            val raw = entries[path] ?: entries[href] ?: return@forEachIndexed
            var content = String(raw, Charsets.UTF_8)
            val htmlTitle = Regex("(?is)<title[^>]*>(.*?)</title>").find(content)?.groupValues?.get(1)
            content = htmlToText(content)
            if (content.isBlank()) return@forEachIndexed
            val parts = splitIntoChapters(content)
            val name = htmlTitle?.stripEntities()?.trim()?.takeIf { it.isNotEmpty() }
                ?: parts.firstOrNull()?.title ?: "Chapter ${i + 1}"
            val body = parts.joinToString("\n\n") { it.text }
            chapters += ParsedChapter(name, body)
        }

        val finalTitle = title.takeIf { it.isNotBlank() } ?: fallbackTitle
        return ParsedBook(finalTitle, author, chapters)
    }

    // ---------- MOBI / AZW ----------

    fun parseMobi(bytes: ByteArray, fallbackTitle: String): ParsedBook {
        if (bytes.size < 78) throw IllegalArgumentException("File too small to be a Kindle book")
        val numRecords = be16(bytes, 80)
        val recordOffsets = IntArray(numRecords) { i -> be32(bytes, 82 + i * 8).toInt() }
        if (numRecords == 0) throw IllegalArgumentException("Kindle book contains no records")
        val rec0 = recordOffsets[0]
        if (rec0 + 16 > bytes.size) throw IllegalArgumentException("Corrupt Kindle book")

        val compression = be16(bytes, rec0)
        if (compression != 1 && compression != 2) {
            throw IllegalArgumentException(
                "This Kindle book uses an unsupported compression (code $compression). " +
                    "Try converting it to a .txt or .epub file and importing again."
            )
        }
        val textLength = be32(bytes, rec0 + 4)
        val textRecords = be16(bytes, rec0 + 8)
        val textEncoding = be16(bytes, rec0 + 28)
        val mobiHeaderLength = be32(bytes, rec0 + 20).toInt()
        val exthFlags = be32(bytes, rec0 + 128)

        var firstTextRecord = 1
        val exthFlagsReally = exthFlags.toInt()
        if ((exthFlagsReally and 0x40) != 0) {
            val exthStart = rec0 + 16 + mobiHeaderLength
            if (exthStart + 8 <= bytes.size && byteString(bytes, exthStart, 4) == "EXTH") {
                val exthTotal = be32(bytes, exthStart + 4)
                if (exthStart - rec0 + exthTotal.toInt() > lengthOfRecord(bytes, rec0, recordOffsets)) {
                    firstTextRecord = 2
                }
            }
        }

        val author = readExth(bytes, rec0, mobiHeaderLength, 100) ?: "EchoBooks"
        val exthTitle = readExth(bytes, rec0, mobiHeaderLength, 503)
            ?: readExth(bytes, rec0, mobiHeaderLength, 501)
        val title = exthTitle?.takeIf { it.isNotBlank() } ?: palmDbName(bytes) ?: fallbackTitle

        val charset = when (textEncoding) {
            65001 -> Charsets.UTF_8
            1252 -> Charset.forName("windows-1252")
            else -> Charsets.ISO_8859_1
        }

        val out = ByteArrayOutputStream()
        val textLengthInt = textLength.toInt().coerceAtLeast(0)
        var recordsUsed = 0
        var r = firstTextRecord
        while (r < numRecords && recordsUsed < textRecords && out.size() < textLengthInt) {
            val rec = sliceRecord(bytes, recordOffsets, r)
            val chunk = if (compression == 2) palmDocDecompress(rec) else rec
            out.write(chunk)
            recordsUsed++
            r++
        }
        if (out.size() == 0) throw IllegalArgumentException("Kindle book contained no readable text")

        var rawText = String(out.toByteArray(), charset)
        if (rawText.length > textLengthInt) rawText = rawText.take(textLengthInt)
        rawText = stripMobiMarkup(rawText)

        val chapters = splitIntoChapters(rawText)
        return ParsedBook(title, author, chapters)
    }

    private fun readExth(bytes: ByteArray, rec0: Int, mobiHeaderLength: Int, type: Long): String? {
        val exthStart = rec0 + 16 + mobiHeaderLength
        if (exthStart + 12 > bytes.size) return null
        if (byteString(bytes, exthStart, 4) != "EXTH") return null
        val exthLen = be32(bytes, exthStart + 4)
        val count = be32(bytes, exthStart + 8)
        var p = exthStart + 12
        repeat(count.toInt().coerceAtLeast(0)) {
            if (p + 8 > exthStart + exthLen) return@repeat
            val t = be32(bytes, p)
            val len = be32(bytes, p + 4).toInt()
            if (p + len > bytes.size) return@repeat
            if (t == type) {
                val data = bytes.copyOfRange(p + 8, p + len)
                return data.toString(Charsets.UTF_8).trim()
            }
            p += len
        }
        return null
    }

    private fun palmDbName(bytes: ByteArray): String? {
        var name = CharArray(32) { bytes[it].toInt().toChar() }.joinToString("").trim()
        name = name.replace(Regex("[^\\x20-\\x7E]"), "")
        return name.takeIf { it.length >= 2 }?.trim()
    }

    // ---------- Helpers ----------

    private fun splitIntoChapters(text: String): List<ParsedChapter> {
        val norm = text.replace("\r\n", "\n").replace("\r", "\n").trim()
        if (norm.isEmpty()) return emptyList()
        val heading = Regex(
            "(?m)^\\s*(chapter|prologue|epilogue|preface|introduction|part|book|section)\\b[^\\n]*$",
            RegexOption.IGNORE_CASE
        )
        val matches = heading.findAll(norm).toList()
        if (matches.size < 2) return fallbackChunks(norm)

        val chapters = mutableListOf<ParsedChapter>()
        val pre = norm.substring(0, matches[0].range.first).trim()
        if (pre.length > 120) chapters += ParsedChapter("Chapter 1", pre)
        for (i in matches.indices) {
            val m = matches[i]
            val start = m.range.last + 1
            val end = matches.getOrNull(i + 1)?.range?.first ?: norm.length
            val body = norm.substring(start, end).trim()
            if (body.isNotEmpty()) {
                chapters += ParsedChapter(m.value.trim().take(80), body)
            }
        }
        return chapters.ifEmpty { fallbackChunks(norm) }
    }

    private fun fallbackChunks(norm: String): List<ParsedChapter> {
        val paragraphs = norm.split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (paragraphs.isEmpty()) return listOf(ParsedChapter("Chapter 1", norm))
        val chapters = mutableListOf<ParsedChapter>()
        var current = StringBuilder()
        var n = 0
        for (p in paragraphs) {
            if (current.isNotEmpty() && current.length + p.length + 2 > 3000) {
                chapters += ParsedChapter("Chapter ${++n}", current.toString().trim())
                current = StringBuilder()
            }
            if (current.isNotEmpty()) current.append("\n\n")
            current.append(p)
        }
        if (current.isNotBlank()) chapters += ParsedChapter("Chapter ${++n}", current.toString().trim())
        return chapters
    }

    private fun fallbackChunksWithTitle(norm: String, title: String): List<ParsedChapter> {
        val paragraphs = norm.split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (paragraphs.isEmpty()) return emptyList()
        var current = StringBuilder()
        val chapters = mutableListOf<ParsedChapter>()
        var n = 0
        for (p in paragraphs) {
            if (current.isNotEmpty() && current.length + p.length + 2 > 3000) {
                chapters += ParsedChapter(if (n == 0) title else "Chapter $n", current.toString().trim())
                current = StringBuilder()
            }
            if (current.isNotEmpty()) current.append("\n\n")
            current.append(p)
        }
        if (current.isNotBlank()) chapters += ParsedChapter(if (n == 0) title else "Chapter $n", current.toString().trim())
        return chapters
    }

    private fun htmlToText(html: String): String {
        var t = html
        t = Regex("(?i)<(script|style)[^>]*>.*?</\\1>", RegexOption.DOT_MATCHES_ALL).replace(t, "")
        t = Regex("(?i)<(p|div|br|li|h[1-6]|blockquote|section|article|tr|table)[^>]*>|</(p|div|li|h[1-6]|blockquote|section|article|tr|table)>")
            .replace(t) { "\n" }
        t = Regex("<[^>]+>").replace(t, "")
        t = decodeEntities(t)
        t = Regex("[ \\t\\r]+").replace(t, " ")
        t = Regex(" *\\n *").replace(t, "\n")
        t = Regex("\\n{3,}").replace(t, "\n\n")
        return t.trim()
    }

    private fun stripMobiMarkup(text: String): String {
        var t = text
        t = Regex("<[^>]+>").replace(t, "")
        t = t.replace("\u0000", "")
        t = Regex("\\\\[A-Za-z0-9\\[/]").replace(t, "")
        t = Regex("\\[[A-Za-z0-9/][^\\]]*\\]").replace(t, "")
        return htmlToText(t)
    }

    private fun decodeEntities(input: String): String {
        val out = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '&') {
                val semi = input.indexOf(';', i)
                if (semi in (i + 2)..(i + 12)) {
                    val ent = input.substring(i + 1, semi)
                    val decoded: Char? = when {
                        ent == "amp" -> '&'
                        ent == "lt" -> '<'
                        ent == "gt" -> '>'
                        ent == "quot" -> '"'
                        ent == "apos" -> '\''
                        ent == "nbsp" -> ' '
                        ent == "mdash" || ent == "ndash" -> '–'
                        ent == "hellip" -> '…'
                        ent == "ldquo" -> '“'
                        ent == "rdquo" -> '”'
                        ent == "lsquo" -> '‘'
                        ent == "rsquo" -> '’'
                        ent == "emsp" || ent == "ensp" || ent == "thinsp" -> ' '
                        ent == "lrm" || ent == "rlm" -> ' '
                        ent.startsWith("#x", true) -> ent.drop(2).toIntOrNull(16)?.takeIf { it in 0..0xFFFF }?.toChar()
                        ent.startsWith("#") -> ent.drop(1).toIntOrNull()?.takeIf { it in 0..0xFFFF }?.toChar()
                        else -> null
                    }
                    if (decoded != null) {
                        out.append(decoded)
                        i = semi + 1
                        continue
                    }
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }

    private fun String.stripEntities(): String = decodeEntities(this)

    private fun zipEntries(bytes: ByteArray): MutableMap<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        ZipArchiveInputStream(ByteArrayInputStream(bytes)).use { zip ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val entry = zip.nextZipEntry ?: break
                val name = entry.name.trimEnd('/')
                if (name.isEmpty() || entry.isDirectory) continue
                val bos = ByteArrayOutputStream()
                while (true) {
                    val n = zip.read(buffer)
                    if (n <= 0) break
                    bos.write(buffer, 0, n)
                }
                entries[name] = bos.toByteArray()
            }
        }
        return entries
    }

    private fun firstTagAttribute(xml: ByteArray, tag: String, attribute: String): String? {
        val doc = parseXml(xml)
        forEachElement(doc, "*", tag) { e ->
            val v = e.getAttribute(attribute)
            if (v.isNotEmpty()) return v
        }
        return null
    }

    private fun parseXml(bytes: ByteArray): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isCoalescing = true
        }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }

    private fun firstElementText(doc: Document, ns: String, localName: String): String? {
        forEachElement(doc, ns, localName) { e ->
            val t = e.textContent.trim()
            if (t.isNotEmpty()) return t
        }
        return null
    }

    private inline fun forEachElement(doc: Document, ns: String, localName: String, action: (Element) -> Unit) {
        val nodes = doc.getElementsByTagNameNS(ns, localName)
        for (i in 0 until nodes.length) {
            val e = nodes.item(i)
            if (e is Element) action(e)
        }
    }

    private fun resolvePath(dir: String, href: String): String {
        if (href.startsWith("/")) return href.trimStart('/')
        val parts = mutableListOf<String>()
        dir.split('/').filter { it.isNotEmpty() && it != "." }.forEach { parts.add(it) }
        href.split('/').forEach { seg ->
            when (seg) {
                "", "." -> {}
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                else -> parts.add(seg)
            }
        }
        return parts.joinToString("/")
    }

    private fun be16(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)

    private fun be32(b: ByteArray, off: Int): Long =
        ((b[off].toLong() and 0xFF) shl 24) or ((b[off + 1].toLong() and 0xFF) shl 16) or
            ((b[off + 2].toLong() and 0xFF) shl 8) or (b[off + 3].toLong() and 0xFF)

    private fun byteString(b: ByteArray, off: Int, len: Int): String =
        buildString { for (i in 0 until len) append(b[off + i].toInt().toChar()) }

    private fun lengthOfRecord(bytes: ByteArray, index: Int, offsets: IntArray): Int {
        val start = offsets[index]
        val end = offsets.getOrNull(index + 1) ?: bytes.size
        return (end - start).coerceAtLeast(0)
    }

    private fun sliceRecord(bytes: ByteArray, offsets: IntArray, index: Int): ByteArray {
        val start = offsets[index]
        val len = lengthOfRecord(bytes, index, offsets)
        return bytes.copyOfRange(start, start + len)
    }

    /** Port of calibre's cPalmdoc_decompress (PalmDOC LZ77). */
    private fun palmDocDecompress(input: ByteArray): ByteArray {
        var out = ByteArray(input.size * 8 + 1024)
        var o = 0
        var i = 0
        fun put(v: Int) {
            if (o >= out.size) out = out.copyOf(out.size * 2)
            out[o++] = (v and 0xFF).toByte()
        }
        while (i < input.size) {
            val c = input[i].toInt() and 0xFF
            i++
            when {
                c in 1..8 -> {
                    var left = c
                    while (left-- > 0 && i < input.size) put(input[i++].toInt() and 0xFF)
                }
                c <= 0x7F -> put(c)
                c >= 0xC0 -> {
                    put(' '.code)
                    put(c xor 0x80)
                }
                else -> {
                    if (i < input.size) {
                        val c2 = input[i].toInt() and 0xFF
                        i++
                        val pair = (c shl 8) or c2
                        val di = (pair and 0x3FFF) shr 3
                        if (di > 0 && di <= o) {
                            val n = (pair and 7) + 3
                            repeat(n) { put(out[o - di].toInt() and 0xFF) }
                        }
                    }
                }
            }
        }
        return out.copyOf(o)
    }

    // keep splitter available for callers that need a titled fallback
    @Suppress("unused")
    private fun titledFallback(norm: String, title: String): List<ParsedChapter> = fallbackChunksWithTitle(norm, title)
}