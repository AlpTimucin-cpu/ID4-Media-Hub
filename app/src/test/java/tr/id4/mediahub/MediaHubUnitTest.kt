package tr.id4.mediahub

import org.junit.Assert.*
import org.junit.Test

/** Runs as part of :app:testDebugUnitTest on a proper Android/Gradle builder. */
class MediaHubUnitTest {
    @Test fun pagesAreBoundedAndOverflowSafe() {
        val items = (0 until 2500).toList()
        assertEquals((40 until 60).toList(), LibraryPage.slice(items, 2, 20))
        assertEquals((2480 until 2500).toList(), LibraryPage.slice(items, 124, 20))
        assertTrue(LibraryPage.slice(items, 125, 20).isEmpty())
        assertTrue(LibraryPage.slice(items, Int.MAX_VALUE, Int.MAX_VALUE).isEmpty())
        assertTrue(LibraryPage.slice(items, -1, 20).isEmpty())
    }

    @Test fun radioQueueIsNeverUnbounded() {
        val stations = (0 until 2500).map {
            RadioSource("radio-$it", "Radio $it", "https://audio.example.org/$it.mp3")
        }
        val plan = RadioQueuePlanner.plan(stations, listOf(RadioRequest(id = "radio-2400")), 0)
        assertNotNull(plan)
        assertTrue(plan!!.ids.size <= RadioQueuePlanner.MAX_QUEUE_ITEMS)
        assertEquals("radio-2400", plan.ids[plan.index])
    }

    @Test fun m3uRejectsNonHttpsAndVideoDoesNotBecomeAudio() {
        val playlist = """#EXTM3U
#EXTINF:-1 group-title="Radio" radio="true",Music
https://audio.example.org/music.mp3
#EXTINF:-1 group-title="Sports",Match
https://tv.example.org/match.m3u8
#EXTINF:-1,Insecure
http://invalid.example.org/test.mp3
"""
        val parsed = M3uParser.parse(playlist)
        assertEquals(2, parsed.size)
        assertTrue(parsed[0].isAudio)
        assertFalse(parsed[1].isAudio)
    }


    @Test fun quotedUserAgentCommaDoesNotCorruptChannelNames() {
        val playlist = """#EXTM3U
#EXTINF:-1 tvg-logo="https://img.example.org/a.png" user-agent="Mozilla/5.0 (KHTML, like Gecko) Chrome/130" group-title="Movies",beIN Movies Turk
https://video.example.org/bein.m3u8
#EXTINF:-1 group-title="News" tvg-name="Haber 24",Ignored Label
https://video.example.org/news.m3u8
"""
        val channels = M3uParser.parse(playlist)
        assertEquals(2, channels.size)
        assertEquals("beIN Movies Turk", channels[0].name)
        assertEquals("Movies", channels[0].group)
        assertEquals("https://img.example.org/a.png", channels[0].logo)
        assertEquals("Haber 24", channels[1].name)
    }

    @Test fun pollutedStoredNamesAreCleanedWithoutDeletingRecords() {
        assertEquals("beIN Movies Turk", M3uParser.displayTitle(
            "like Gecko) Chrome/130.0.0 Safari/537.36\" group-title=\"Movies\",beIN Movies Turk"
        ))
        assertEquals("TRT Haber", M3uParser.displayTitle("TRT Haber"))
    }

    @Test fun probeDoesNotConfuseHeadFailureWithStreamFailure() {
        assertEquals(StreamStatus.HEAD_UNSUPPORTED, StreamProbe.classify(405).status)
        assertEquals(StreamStatus.LOGIN_REQUIRED, StreamProbe.classify(403).status)
        assertEquals(StreamStatus.SERVER_RESPONDS, StreamProbe.classify(200).status)
    }
}
