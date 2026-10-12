package com.kreativekoala.echonote.service.summary

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryStringsTest {
    @Test fun everyLocaleHasEverySummaryStringAndPlaceholdersMatch() {
        val dirs = File("src/main/res").listFiles { f -> f.isDirectory && f.name.startsWith("values") }!!
        assertEquals(20, dirs.count { File(it, "strings_summary.xml").exists() })
        val re = Regex("<string name=\"(summary_[a-z_]+)\">([^<]*)</string>")
        val en = re.findAll(File("src/main/res/values/strings_summary.xml").readText()).associate { it.groupValues[1] to it.groupValues[2] }
        assertTrue(en.size >= 13)
        for (d in dirs) {
            val f = File(d, "strings_summary.xml")
            if (!f.exists()) continue
            val m = re.findAll(f.readText()).associate { it.groupValues[1] to it.groupValues[2] }
            assertEquals(d.name, en.keys, m.keys)
            m.values.forEach { assertTrue(d.name, it.isNotBlank()) }
        }
    }
}
