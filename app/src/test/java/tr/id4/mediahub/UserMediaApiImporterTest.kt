package tr.id4.mediahub

import org.junit.Assert.*
import org.junit.Test

class UserMediaApiImporterTest {
    @Test fun importsCommonJsonEnvelopeAndFiltersHttpOrInvalidEntries() {
        val json = """
          {"data":{"items":[
            {"name":"Lisanslı Film","stream_url":"https://media.example.org/film.mp4","category":"Filmler","poster":"https://media.example.org/poster.jpg"},
            {"title":"Yayın B","playUrl":"https://media.example.org/sport.m3u8","group_title":"Spor"},
            {"name":"Güvensiz","url":"http://example.org/cleartext.mp4"},
            {"name":"Yinelenen","url":"https://media.example.org/film.mp4"}
          ]}}
        """.trimIndent()
        val result=UserMediaApiImporter.parse(json)
        assertEquals(2,result.size)
        assertEquals("Lisanslı Film",result[0].name)
        assertEquals("Filmler",result[0].group)
        assertEquals("https://media.example.org/poster.jpg",result[0].logo)
        assertEquals("Spor",result[1].group)
    }

    @Test fun acceptsRootJsonArrayAndM3uButNotHtml() {
        assertEquals(1,UserMediaApiImporter.parse(
            """[{"title":"Demo","url":"https://example.org/live.m3u8"}]""").size)
        val m3u="#EXTM3U\n#EXTINF:-1 group-title=\"Dram\",Demo\nhttps://example.org/movie.m3u8\n"
        assertEquals(1,UserMediaApiImporter.parse(m3u).size)
        assertTrue(UserMediaApiImporter.parse("<html></html>").isEmpty())
    }
}
