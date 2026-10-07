package com.inkio.reader.summary

import com.inkio.reader.data.OutlineItem
import org.junit.Assert.assertTrue
import org.junit.Test

class SummarizerTest {
    private val story = listOf(
        "The Lighthouse Keeper\n1\nElena arrived at the island on a grey morning in October. The ferryman warned her that the lighthouse had been dark for eleven years. She carried a single suitcase and her late father's journal, which spoke of a light that guided lost ships home.",
        "The Lighthouse Keeper\n2\nThe village of Marrow Bay greeted Elena with suspicion. Old Tomas, the baker, told her that the last keeper had vanished during a winter storm. Elena decided she would repair the lamp herself, whatever the villagers believed about the curse.",
        "The Lighthouse Keeper\n3\nInside the tower, Elena found rusted gears and a locked chest. The journal described a hidden key beneath the third step. When she opened the chest she discovered maps of shipwrecks marked in her father's handwriting.",
        "The Lighthouse Keeper\n4\nTomas revealed that Elena's father had been the keeper who disappeared. He had been searching for the wreck of the Aurelia, a ship carrying the island's lost records. Elena realised the maps were a trail her father had left for her.",
        "The Lighthouse Keeper\n5\nA storm rolled in from the north as Elena climbed the tower. She lit the repaired lamp for the first time in eleven years. Far out at sea, a small boat turned towards the light, and Elena saw a figure waving from its deck.",
    )

    @Test fun producesRecap() {
        val pages = story.mapIndexed { i, t -> i to t }.toMap()
        val r = Summarizer.summarize(pages, 0, 4, emptyList())
        println("SECTIONS"); r.sections.forEach { println("## ${it.title}"); it.points.forEach { p -> println(" - $p") } }
        println("LAST"); r.lastScene.forEach { println(" > $it") }
        println("NAMES ${r.names}"); println("KEYWORDS ${r.keywords}"); println("WORDS ${r.wordsRead}")
        assertTrue(r.hasText)
        assertTrue(r.sections.isNotEmpty())
        assertTrue(r.lastScene.isNotEmpty())
        assertTrue("Elena" in r.names)
        assertTrue(r.sections.flatMap { it.points }.none { it.contains("Lighthouse Keeper\n") })
    }

    @Test fun usesChapters() {
        val pages = story.mapIndexed { i, t -> i to t }.toMap()
        val r = Summarizer.summarize(pages, 0, 4, listOf(OutlineItem("Arrival", 0, 0), OutlineItem("The Tower", 2, 0), OutlineItem("The Storm", 4, 0)))
        println(r.sections.map { it.title to it.fromPage..it.toPage })
        assertTrue(r.sections.map { it.title } == listOf("Arrival", "The Tower", "The Storm"))
    }

    @Test fun scannedPdfHasNoText() {
        val r = Summarizer.summarize(mapOf(0 to "", 1 to "  "), 0, 1, emptyList())
        assertTrue(!r.hasText)
    }
}
