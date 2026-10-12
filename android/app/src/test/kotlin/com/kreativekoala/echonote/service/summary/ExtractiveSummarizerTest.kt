package com.kreativekoala.echonote.service.summary

import com.kreativekoala.echonote.data.model.TranscriptSegment
import com.kreativekoala.echonote.data.model.TranscriptionLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractiveSummarizerTest {
    private val s = ExtractiveSummarizer()

    private val englishMeeting = """
        Thanks everyone for joining the project planning meeting. Today we need to decide the launch date for the new mobile app.
        The design team finished the onboarding screens last week. Sarah said the onboarding screens test well with new users.
        The main risk is the payment integration, because the payment provider changed its API. Tom will rewrite the payment integration before the end of the month.
        We also talked about lunch options for Friday. Someone suggested pizza.
        Marketing wants the launch date to be the fifteenth of March so the launch lines up with the conference. The launch date depends on the payment integration finishing on time.
        If the payment integration slips, we will move the launch date to the end of March instead.
        Please send your status updates before Thursday. That is all for today.
    """.trimIndent().replace("\n", " ")

    @Test fun englishMeetingKeepsTheTopicAndOriginalOrder() {
        val r = s.summarizeNow(englishMeeting, TranscriptionLanguage.ENGLISH)!!
        assertEquals(SummaryKind.EXTRACTIVE, r.kind)
        assertTrue(r.points.size in 3..8)
        val joined = r.points.joinToString(" ")
        assertTrue(joined.contains("launch date"))
        assertTrue(joined.contains("payment integration"))
        assertTrue("small talk should lose", !joined.contains("pizza"))
        // original order preserved
        val positions = r.points.map { englishMeeting.indexOf(it) }
        assertTrue(positions.all { it >= 0 })
        assertEquals(positions.sorted(), positions)
    }

    @Test fun isDeterministic() {
        val a = s.summarizeNow(englishMeeting, TranscriptionLanguage.ENGLISH)
        val b = s.summarizeNow(englishMeeting, TranscriptionLanguage.ENGLISH)
        assertEquals(a, b)
    }

    @Test fun tooShortReturnsNull() {
        assertNull(s.summarizeNow("", TranscriptionLanguage.ENGLISH))
        assertNull(s.summarizeNow("Remember to buy milk. Call the bank tomorrow.", TranscriptionLanguage.ENGLISH))
        assertNull(s.summarizeNow("今天天气很好。我们去公园。", TranscriptionLanguage.CHINESE))
    }

    @Test fun abbreviationsAndDecimalsDoNotSplitSentences() {
        val text = "Dr. Smith measured 3.5 liters of water per minute in the first trial of the study. " +
            "The second trial used 4.2 liters per minute and the water was warmer. " +
            "Mr. Jones wrote the water report for the committee and the committee read the water report. " +
            "The committee approved the water study budget after reading the report. " +
            "Next year the water study will continue with more trials."
        val r = s.summarizeNow(text, TranscriptionLanguage.ENGLISH)!!
        r.points.forEach { assertTrue(it, !it.startsWith("Smith") && !it.startsWith("5 liters") && !it.startsWith("Jones")) }
    }

    private val spanishLecture = """
        Hoy vamos a hablar sobre el cambio climático y sus efectos en la agricultura. El cambio climático afecta directamente a las cosechas de trigo y de maíz.
        Las temperaturas más altas reducen el rendimiento de las cosechas en muchas regiones. Los agricultores necesitan variedades de cultivos que resistan la sequía.
        Mi hermano tiene una granja pequeña y me contó una anécdota divertida sobre su perro. Bueno, volvamos al tema.
        El agua es el recurso más importante para la agricultura, y el cambio climático altera las lluvias. Cuando las lluvias cambian, los agricultores no pueden planificar la siembra.
        Los gobiernos deben invertir en riego eficiente y en investigación agrícola. La investigación agrícola ayuda a crear cultivos que resistan la sequía.
        En resumen, la agricultura necesita adaptarse al cambio climático con nuevas variedades, mejor riego y apoyo del gobierno.
    """.trimIndent().replace("\n", " ")

    @Test fun spanishPicksTopicSentences() {
        val r = s.summarizeNow(spanishLecture, TranscriptionLanguage.SPANISH)!!
        val joined = r.points.joinToString(" ")
        assertTrue(joined.contains("cambio climático"))
        assertTrue(joined.contains("agricultura") || joined.contains("agricultores"))
        assertTrue("anecdote should lose", !joined.contains("perro"))
        assertTrue(r.points.size in 3..8)
    }

    private val chinese = "今天我们讨论新产品的发布计划。新产品的发布日期定在三月十五日。发布日期取决于支付系统的开发进度。" +
        "支付系统由技术团队负责，技术团队需要在月底前完成支付系统。午饭大家想吃什么？我觉得可以点披萨。" +
        "如果支付系统延期，发布日期将推迟到三月底。市场部希望发布日期与行业大会同步。请大家在周四之前提交进度报告。今天就到这里。"

    @Test fun chineseSplitsOnFullStopsAndPicksTopicSentences() {
        val r = s.summarizeNow(chinese, TranscriptionLanguage.CHINESE)!!
        assertTrue(r.points.size in 3..8)
        val joined = r.points.joinToString("")
        assertTrue(joined.contains("发布日期"))
        assertTrue(joined.contains("支付系统"))
        assertTrue("small talk should lose", !joined.contains("披萨"))
        // each point is a whole sentence ending in a CJK delimiter, not a fragment
        r.points.forEach { assertTrue(it, it.endsWith("。") || it.endsWith("？")) }
        val pos = r.points.map { chinese.indexOf(it) }
        assertEquals(pos.sorted(), pos)
    }

    @Test fun chineseWithoutPunctuationUsesTimingsAndEndsInEllipsis() {
        val words = chinese.replace(Regex("[。？]"), "").map { it.toString() }
        // one character per 200 ms, with a 900 ms pause after each of the first sentences' worth
        var t = 0L
        val segs = words.mapIndexed { i, w ->
            if (i > 0 && i % 14 == 0) t += 900
            TranscriptSegment(w, t, t + 180).also { t += 200 }
        }
        val raw = words.joinToString("")
        val r = s.summarizeNow(raw, TranscriptionLanguage.CHINESE, segs)!!
        assertTrue(r.points.isNotEmpty())
        r.points.forEach { assertTrue(it, it.endsWith("…") && it.length <= 60) }
    }

    @Test fun unpunctuatedEnglishFallsBackToPauseWindows() {
        val sentences = listOf(
            "we need to talk about the budget for the new office", "the budget for the office is too high this year",
            "my cat likes sleeping on the keyboard", "the office budget must be cut by twenty percent",
            "finance will review the office budget on monday morning", "I forgot what I had for breakfast",
            "after the review the office budget will be final", "the final office budget goes to the board"
        )
        val segs = mutableListOf<TranscriptSegment>()
        var t = 0L
        for (sent in sentences) {
            for (w in sent.split(" ")) { segs += TranscriptSegment(w, t, t + 250); t += 300 }
            t += 1500
        }
        val raw = segs.joinToString(" ") { it.word }
        val r = s.summarizeNow(raw, TranscriptionLanguage.ENGLISH, segs)!!
        assertTrue(r.points.joinToString(" ").contains("budget"))
        r.points.forEach { assertTrue(it, it.endsWith("…")) }
        assertTrue(r.points.none { it.contains("cat") && !it.contains("budget") })
    }

    @Test fun unpunctuatedWithoutTimingsStillWorksByWordWindows() {
        val base = "the budget meeting covered the office budget and the hiring budget and the travel budget " +
            "while the team discussed the roadmap and the roadmap risks and the roadmap owners "
        val raw = (1..6).joinToString(" ") { base }.trim()
        val r = s.summarizeNow(raw, TranscriptionLanguage.ENGLISH)
        assertNotNull(r)
        r!!.points.forEach { assertTrue(it.endsWith("…")) }
    }

    @Test fun longTranscriptStaysWithinBudget() {
        val sb = StringBuilder()
        for (i in 1..120) {
            sb.append("In section $i we cover topic ${i % 7} about the quarterly planning process and its owners. ")
            if (i % 10 == 0) sb.append("The most important decision is that quarterly planning moves to the first week of each quarter. ")
        }
        val r = s.summarizeNow(sb.toString(), TranscriptionLanguage.ENGLISH)!!
        assertTrue(r.points.size <= 8)
        assertTrue(r.points.sumOf { it.length } <= 1300)
        assertTrue(r.points.joinToString(" ").contains("quarterly planning"))
    }

    @Test fun serialChoosingSkipsNearDuplicates() {
        val dup = "The launch is scheduled for March and the launch team is ready for the launch. "
        val text = dup.repeat(3) + "Budget approval came from finance after a long review of the numbers. " +
            "The venue was booked by the events team for the second week. " + "Catering will be handled by a local company with vegetarian options. " +
            "Security staff will arrive two hours before the doors open to the public."
        val r = s.summarizeNow(text, TranscriptionLanguage.ENGLISH)!!
        assertEquals(1, r.points.count { it.startsWith("The launch is scheduled") })
    }

    @Test fun japaneseAndKoreanDoNotCrash() {
        val ja = "今日は新しい製品の発売について話します。発売日は三月十五日に決まりました。発売日は支払いシステムの完成に左右されます。" +
            "支払いシステムは技術チームが月末までに完成させます。昼ご飯はピザにしましょう。もし支払いシステムが遅れたら発売日は三月末になります。皆さん木曜日までに進捗を送ってください。"
        assertNotNull(s.summarizeNow(ja, TranscriptionLanguage.JAPANESE))
        val ko = "오늘은 새 제품 출시 계획에 대해 이야기합니다. 출시 날짜는 3월 15일로 정해졌습니다. 출시 날짜는 결제 시스템 완성에 달려 있습니다. " +
            "결제 시스템은 기술팀이 이달 말까지 완성합니다. 점심은 피자로 합시다. 결제 시스템이 늦어지면 출시 날짜는 3월 말로 미뤄집니다. 목요일까지 진행 상황을 보내 주세요."
        assertNotNull(s.summarizeNow(ko, TranscriptionLanguage.KOREAN))
    }

    @Test fun suspendEntryPointMatchesSync() {
        val viaSuspend = kotlinx.coroutines.runBlocking { s.summarize(englishMeeting, TranscriptionLanguage.ENGLISH) }
        assertEquals(s.summarizeNow(englishMeeting, TranscriptionLanguage.ENGLISH), viaSuspend)
    }
}
