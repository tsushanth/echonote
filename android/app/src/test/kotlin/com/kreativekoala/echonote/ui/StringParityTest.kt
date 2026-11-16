package com.kreativekoala.echonote.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Every translatable string and plural of values/ exists in every values-xx folder (no English fallback), has no extras,
 * keeps the same format specifiers, and plurals carry the CLDR categories of that language.
 */
class StringParityTest {
    private val res = File("src/main/res")

    private class Entry(val plural: Boolean, val specs: List<String>, val quantities: Set<String>)

    private fun load(dir: File): Map<String, Entry> {
        val out = HashMap<String, Entry>()
        val files = dir.listFiles { f -> f.name.startsWith("strings") && f.name.endsWith(".xml") } ?: return out
        for (f in files.sortedBy { it.name }) {
            val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f).documentElement
            val nodes = root.childNodes
            for (i in 0 until nodes.length) {
                val e = nodes.item(i) as? Element ?: continue
                if (e.getAttribute("translatable") == "false") continue
                val name = e.getAttribute("name")
                when (e.tagName) {
                    "string" -> out[name] = Entry(false, specs(e.textContent), emptySet())
                    "plurals" -> {
                        val items = e.getElementsByTagName("item")
                        val q = (0 until items.length).map { (items.item(it) as Element).getAttribute("quantity") }.toSet()
                        out[name] = Entry(true, emptyList(), q)
                    }
                }
            }
        }
        return out
    }

    private fun specs(text: String): List<String> =
        Regex("%(\\d+\\$)?[sd]|%%").findAll(text).map { it.value.replace(Regex("\\d+\\$"), "") }.sorted().toList()

    private fun required(folder: String): Set<String> = when (folder.removePrefix("values-").substringBefore("-r")) {
        "ar" -> setOf("zero", "one", "two", "few", "many", "other")
        "pl", "ru", "cs", "uk" -> setOf("one", "few", "many", "other")
        "ro" -> setOf("one", "few", "other")
        "fr", "es", "it", "pt" -> setOf("one", "many", "other")
        "ja", "ko", "zh", "th", "vi", "in", "ms" -> setOf("other")
        else -> setOf("one", "other")
    }

    @Test
    fun everyLocaleHasEveryStringAndPluralWithTheSameFormatSpecifiers() {
        val base = load(File(res, "values"))
        assertTrue("default strings not found (run from android/)", base.size > 300)
        val locales = res.listFiles { f -> f.isDirectory && f.name.startsWith("values-") && File(f, "strings.xml").exists() }!!
        assertEquals(26, locales.size)
        for (dir in locales) {
            val got = load(dir)
            assertEquals("${dir.name} missing keys", emptySet<String>(), base.keys - got.keys)
            assertEquals("${dir.name} extra keys", emptySet<String>(), got.keys - base.keys)
            for ((k, b) in base) {
                val g = got.getValue(k)
                assertEquals("${dir.name} $k kind", b.plural, g.plural)
                if (!b.plural) assertEquals("${dir.name} $k format specifiers", b.specs, g.specs)
                else assertTrue("${dir.name} $k needs plural categories ${required(dir.name)}, has ${g.quantities}", g.quantities.containsAll(required(dir.name)))
            }
        }
    }

    @Test
    fun localesConfigListsExactlyTheShippedLanguages() {
        val shipped = res.listFiles { f -> f.isDirectory && f.name.startsWith("values-") && File(f, "strings.xml").exists() }!!
            .map {
                when (val c = it.name.removePrefix("values-")) {
                    "in" -> "id"
                    "tl" -> "fil"
                    "pt-rBR" -> "pt-BR"
                    "zh-rCN" -> "zh-CN"
                    else -> c
                }
            }.toSet() + "en"
        val listed = Regex("android:name=\"([^\"]+)\"").findAll(File(res, "xml/locales_config.xml").readText()).map { it.groupValues[1] }.toSet()
        assertEquals(shipped, listed)
    }
}
