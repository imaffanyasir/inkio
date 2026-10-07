package com.inkio.reader.summary

import com.inkio.reader.data.OutlineItem
import kotlin.math.ln
import kotlin.math.pow

data class RecapSection(val title: String, val fromPage: Int, val toPage: Int, val points: List<String>)

data class Recap(
    val sections: List<RecapSection>,
    val lastScene: List<String>,
    val keywords: List<String>,
    val names: List<String>,
    val wordsRead: Int,
    val hasText: Boolean,
)

/**
 * Offline extractive summariser. Picks the sentences that best represent what was
 * read (TF-IDF weighting with redundancy filtering), grouped into chapters or page
 * ranges, plus the most important lines from where the reader stopped.
 */
object Summarizer {

    private class Sentence(val text: String, val page: Int, val order: Int, val words: Set<String>) {
        var score = 0.0
    }

    fun summarize(pages: Map<Int, String>, from: Int, to: Int, outline: List<OutlineItem>): Recap {
        val cleaned = clean(pages)
        val totalWords = cleaned.values.sumOf { t -> t.split(wsRegex).count { it.isNotBlank() } }
        val sentences = ArrayList<Sentence>()
        var order = 0
        for ((p, text) in cleaned.toSortedMap()) {
            for (s in splitSentences(text)) {
                val words = tokens(s)
                if (s.length !in 40..360 || words.size < 5) continue
                val letters = s.count { it.isLetter() }
                if (letters < s.length * 0.65) continue // tables, references, equations
                sentences += Sentence(s, p, order++, words.toSet())
            }
        }
        if (sentences.size < 3) {
            return Recap(emptyList(), emptyList(), emptyList(), emptyList(), totalWords, hasText = false)
        }

        // document frequency per page
        val df = HashMap<String, Int>()
        val tf = HashMap<String, Int>()
        for ((_, text) in cleaned) {
            val ws = tokens(text)
            ws.forEach { tf[it] = (tf[it] ?: 0) + 1 }
            ws.toSet().forEach { df[it] = (df[it] ?: 0) + 1 }
        }
        val n = cleaned.size.coerceAtLeast(1)
        val weight = HashMap<String, Double>()
        for ((w, f) in tf) weight[w] = ln(1.0 + f) * ln(1.0 + n.toDouble() / (df[w] ?: 1))

        val names = findNames(cleaned.values)
        val nameSet = names.map { it.lowercase() }.toSet()
        for (s in sentences) {
            var sc = s.words.sumOf { weight[it] ?: 0.0 }
            sc /= s.words.size.toDouble().pow(0.55)
            if (s.words.any { it in nameSet }) sc *= 1.15
            if (s.text.contains('"') || s.text.contains('“')) sc *= 0.92 // prefer narration over dialogue
            s.score = sc
        }

        // sections: chapters from the outline when available, else even page ranges
        val ranges = buildRanges(from, to, outline)
        val sections = ranges.mapNotNull { (title, a, b) ->
            val inRange = sentences.filter { it.page in a..b }
            val pick = mmr(inRange, if (ranges.size <= 2) 3 else 2)
            if (pick.isEmpty()) null else RecapSection(title, a, b, pick.sortedBy { it.order }.map { it.text })
        }

        val tail = sentences.filter { it.page >= to - 2 }
        val lastScene = mmr(tail, 3).sortedBy { it.order }.map { it.text }

        val keywords = weight.entries.asSequence()
            .filter { it.key.length >= 4 && it.key !in nameSet && (tf[it.key] ?: 0) >= 3 }
            .sortedByDescending { it.value }
            .take(10).map { it.key.replaceFirstChar { c -> c.uppercase() } }.toList()

        return Recap(sections, lastScene, keywords, names.take(10), totalWords, hasText = true)
    }

    private fun buildRanges(from: Int, to: Int, outline: List<OutlineItem>): List<Triple<String, Int, Int>> {
        val top = outline.filter { it.depth == 0 }.ifEmpty { outline }
            .distinctBy { it.page }.sortedBy { it.page }
        val chapters = top.filter { it.page <= to }
        if (chapters.size >= 2) {
            val ranges = chapters.mapIndexed { i, c ->
                val end = if (i + 1 < chapters.size) (chapters[i + 1].page - 1).coerceAtLeast(c.page) else to
                Triple(c.title, c.page, end)
            }.filter { it.third >= from }
            if (ranges.size <= 6) return ranges
            val recent = ranges.takeLast(5)
            return listOf(Triple("Earlier chapters", ranges.first().second, recent.first().second - 1)) + recent
        }
        val pagesRead = to - from + 1
        val k = (pagesRead / 12).coerceIn(1, 5)
        val step = pagesRead.toFloat() / k
        return (0 until k).map { i ->
            val a = from + (i * step).toInt()
            val b = if (i == k - 1) to else from + ((i + 1) * step).toInt() - 1
            Triple("Pages ${a + 1}–${b + 1}", a, b)
        }
    }

    /** Maximal marginal relevance: high-scoring sentences that don't repeat each other. */
    private fun mmr(cands: List<Sentence>, k: Int): List<Sentence> {
        val pool = cands.sortedByDescending { it.score }.take(60).toMutableList()
        val out = ArrayList<Sentence>()
        while (out.size < k && pool.isNotEmpty()) {
            val best = pool.maxByOrNull { c ->
                val sim = out.maxOfOrNull { jaccard(it.words, c.words) } ?: 0.0
                c.score * (1 - sim)
            }!!
            pool.remove(best)
            if (out.none { jaccard(it.words, best.words) > 0.45 }) out += best
        }
        return out
    }

    private fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val inter = a.count { it in b }
        return inter.toDouble() / (a.size + b.size - inter)
    }

    // ------------------------------------------------------------------ text cleaning

    private val wsRegex = Regex("""\s+""")
    private val wordRegex = Regex("""[A-Za-z][A-Za-z'’-]+""")
    private val sentenceRegex = Regex("""[^.!?…]+(?:[.!?…]+["'”’)\]]*)?""")
    private val abbrev = setOf("mr", "mrs", "ms", "dr", "st", "prof", "sr", "jr", "vs", "etc", "e.g", "i.e", "fig", "no", "vol", "ch")

    private fun clean(pages: Map<Int, String>): Map<Int, String> {
        val lineCount = HashMap<String, Int>()
        val norm = { l: String -> l.trim().lowercase().replace(Regex("""\d+"""), "#") }
        for (t in pages.values) t.lines().map(norm).filter { it.isNotEmpty() }.toSet()
            .forEach { lineCount[it] = (lineCount[it] ?: 0) + 1 }
        val repeated = if (pages.size >= 4) {
            lineCount.filter { it.value >= pages.size * 0.25 && it.key.length < 90 }.keys
        } else emptySet()

        return pages.mapValues { (_, t) ->
            t.lines()
                .filter { l -> val k = norm(l); k.isNotEmpty() && k !in repeated && !k.matches(Regex("""[#\s\-–—|.]*""")) }
                .joinToString("\n")
                .replace(Regex("""(\w)-\n(\w)"""), "$1$2")
                .replace('\n', ' ')
                .replace(wsRegex, " ")
                .trim()
        }.filterValues { it.isNotBlank() }
    }

    private fun splitSentences(text: String): List<String> {
        val raw = sentenceRegex.findAll(text).map { it.value.trim() }.filter { it.isNotEmpty() }.toList()
        val out = ArrayList<String>()
        var buf = ""
        for (piece in raw) {
            buf = if (buf.isEmpty()) piece else "$buf $piece"
            val lastWord = buf.trimEnd('.', ' ').substringAfterLast(' ').lowercase()
            if (lastWord in abbrev || lastWord.length == 1) continue
            out += buf
            buf = ""
        }
        if (buf.isNotEmpty()) out += buf
        return out
    }

    private fun tokens(s: String): List<String> =
        wordRegex.findAll(s).map { it.value.lowercase().trim('\'', '’', '-') }
            .filter { it.length > 2 && it !in stop }.toList()

    private fun findNames(texts: Collection<String>): List<String> {
        val cap = HashMap<String, Int>()
        val lower = HashMap<String, Int>()
        for (t in texts) {
            val words = Regex("""[A-Za-z][a-z'’]+|[.!?]""").findAll(t).map { it.value }.toList()
            for (i in words.indices) {
                val w = words[i]
                if (w.length < 3) continue
                val afterStop = i == 0 || words[i - 1].matches(Regex("""[.!?]"""))
                if (w[0].isUpperCase()) {
                    if (!afterStop) cap[w] = (cap[w] ?: 0) + 1
                } else lower[w] = (lower[w] ?: 0) + 1
            }
        }
        return cap.entries.filter { (w, c) ->
            c >= 3 && w.lowercase() !in stop && (lower[w.lowercase()] ?: 0) < c * 0.2
        }.sortedByDescending { it.value }.map { it.key.trimEnd('\'', '’') }.distinct()
    }

    private val stop = """
        a about above after again against all almost also although always am among an and another any anyone anything
        are around as at away back be became because become becomes been before being below between both but by came can
        cannot could did do does doing done down during each either else enough even ever every few first for from further
        get gets getting give given go goes going gone got had has have having he her here hers herself him himself his how
        however i if in into is it its itself just know known last least less let like made make makes many may me might
        more most much must my myself never new next no nor not nothing now of off often on once one only or other others
        our ours ourselves out over own perhaps put rather really said same say says see seem seemed seems shall she
        should since so some something still such take than that the their theirs them themselves then there these they
        thing things this those though through thus to together too took toward towards two under until up upon us used
        very was way we well went were what whatever when where whether which while who whom whose why will with within
        without would yet you your yours yourself yourselves chapter page also around told asked looked thought came
        upon again little much many will just like know could would should
        """.trim().split(Regex("""\s+""")).toSet()
}
