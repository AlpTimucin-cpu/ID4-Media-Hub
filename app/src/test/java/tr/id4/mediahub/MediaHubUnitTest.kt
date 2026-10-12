package tr.id4.mediahub

import org.junit.Assert.*
import org.junit.Test

/** Runs as part of :app:testDebugUnitTest on a proper Android/Gradle builder. */
class MediaHubUnitTest {
    @Test fun cockpitPlannerKeepsRecentOrderDropsMissingAndNeverExposesUrls() {
        val tv = listOf(
            VideoChannel("v1", "Haber HD", "https://example.org/private?key=x", "Haber",
                true, "", "haber.tr"),
            VideoChannel("v2", "Belgesel", "https://example.org/doc.m3u8", "Kultur",
                false, "", "doc.tr")
        )
        val radio = listOf(
            Station("r1", "Caz FM", "https://radio.example/aac?token=secret",
                "Muzik", true),
            Station("r2", "Pop Radyo", "https://radio.example/mp3",
                "Muzik", false)
        )
        val summary = CockpitPlanner.build(
            listOf("tv:deleted", "radio:r1", "tv:v1", "radio:r1", "unknown:id",
                "tv:v2", "radio:r2"), tv, radio)
        assertEquals(listOf("r1", "v1", "v2", "r2"), summary.recents.map { it.id })
        assertEquals(listOf("v1", "r1"), summary.favourites.map { it.id })
        assertEquals("haber.tr", summary.recents.first { it.id == "v1" }.tvgId)
        assertEquals(2, summary.tvCount)
        assertEquals(2, summary.radioCount)
        assertFalse(summary.toString().contains("secret"))
        assertFalse(summary.toString().contains("private?key"))
        assertTrue(CockpitPlanner.build(emptyList(), tv, radio, recentLimit = 0).recents.isEmpty())
        assertEquals(1, CockpitPlanner.build(
            listOf("radio:r1", "tv:v1"), tv, radio, recentLimit = 1
        ).recents.size)
    }

    @Test fun integrationCatalogIsUniqueAndCoversAllPreviousAppResearch() {
        val sources = IntegrationCatalog.sources
        val ids = sources.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        assertTrue(ids.size >= 12)
        val required = listOf("aptv", "media3", "web", "screenonauto", "fermata",
            "screen2auto", "shizuku", "aaad", "mirrormobile", "carstream", "sportzx", "diplay", "androidauto")
        required.forEach { assertNotNull("Missing source: " + it, IntegrationCatalog.byId(it)) }
        assertEquals(IntegrationKind.NATIVE, IntegrationCatalog.byId("aptv")?.kind)
        assertEquals(IntegrationKind.EXTERNAL, IntegrationCatalog.byId("screenonauto")?.kind)
        assertEquals(IntegrationKind.REFERENCE, IntegrationCatalog.byId("mirrormobile")?.kind)
        assertTrue(IntegrationCatalog.byId("screen2auto")!!.packages.contains(
            "android.loandamaps.it.aaad_157ff3"))
        assertTrue(IntegrationCatalog.byId("screenonauto")!!.packages.contains(
            "idv.lzn.screenonauto"))
        assertTrue(IntegrationCatalog.byId("web")!!.packages.contains("com.kododake.aabrowser"))
    }

    @Test fun driveDashboardRecentOrderDeduplicatesAndIgnoresStaleIds() {
        val entries = DriveHomePlanner.recent(
            listOf("tv:one", "radio:r2", "tv:one", "tv:missing", "unknown:x",
                "radio:r1", "badentry", "tv:two"),
            mapOf("one" to "Haber", "two" to "Sinema"),
            mapOf("r1" to "Müzik", "r2" to "Radyo 2"),
            4
        )
        assertEquals(listOf("Haber", "Radyo 2", "Müzik", "Sinema"),
            entries.map { it.title })
        assertEquals(listOf("tv", "radio", "radio", "tv"),
            entries.map { it.kind })
        assertEquals(emptyList<DriveRecentItem>(), DriveHomePlanner.recent(
            listOf("tv:one"), mapOf("one" to "Haber"), emptyMap(), 0))
        assertEquals(emptyList<DriveRecentItem>(), DriveHomePlanner.recent(
            listOf("tv:unknown"), emptyMap(), emptyMap()))
    }

    @Test fun m3uAdvertisedEpgRequiresHttpsAndExplicitApproval() {
        val playlist = """#EXTM3U x-tvg-url="http://unsafe.example/epg.xml,https://guide.example/epg.xml.gz"
#EXTINF:-1 tvg-id="trt.tr" group-title="TV",TRT
https://tv.example/stream.m3u8
"""
        assertEquals("https://guide.example/epg.xml.gz",
            M3uParser.suggestedGuideUrl(playlist))
        assertNull(M3uParser.suggestedGuideUrl(
            "#EXTM3U url-tvg=\"http://insecure.example/guide.xml\""))
        assertEquals("https://example.org/guide.xml",
            M3uParser.suggestedGuideUrl(
                "\uFEFF#EXTM3U url-tvg=\"https://example.org/guide.xml\"\n#EXTINF:-1,A"))
        assertNull(M3uParser.suggestedGuideUrl("#EXTINF:-1,Missing header"))
    }

    @Test fun epgIndexNormalizesIdsFindsNowNextAndProgress() {
        val now = 10_000L
        val programmes = listOf(
            EpgProgramme(" TRT1.TR ", "Eski", 7_000L, 9_000L),
            EpgProgramme(" TRT1.TR ", "Gündem", 9_000L, 11_000L),
            EpgProgramme("trt1.tr", "Yeni Haberler", 11_000L, 13_000L),
            EpgProgramme("OTHER.TR", "Sinema", 8_000L, 12_000L),
            EpgProgramme("TRT1.TR", "Hatalı", 8_000L, 7_000L)
        )
        val index = EpgScheduleIndex(programmes, now)
        assertEquals("Gündem", index.current("trt1.tr")?.title)
        assertEquals("Yeni Haberler", index.next(" TRT1.TR ")?.title)
        assertEquals(50, index.progressPercent("trt1.tr"))
        assertTrue(index.hasCurrent("trt1.tr"))
        assertTrue(index.matchingProgramme("trt1.tr", "gündem"))
        assertTrue(index.matchingProgramme("trt1.tr", "yeni haberler"))
        assertFalse(index.matchingProgramme("trt1.tr", "spor"))
        assertFalse(index.hasCurrent("missing"))
        assertEquals(0, index.progressPercent("missing"))
    }

    @Test fun uploadedFermataXDebugPackageIsRecognized() {
        val source = IntegrationCatalog.byId("fermatax")
        assertNotNull(source)
        assertEquals(IntegrationKind.EXTERNAL, source!!.kind)
        assertTrue(source.packages.contains("me.app.fermataX.auto.debug"))
        assertTrue(source.packages.contains("me.app.fermataX.auto"))
    }

    @Test fun explicitHlsDashAndUnknownFormatsAreClassified() {
        assertEquals(StreamFormat.HLS, StreamFormatDetector.detect(
            "https://example.org/live.m3u8?token=not-logged"))
        assertEquals(StreamFormat.DASH, StreamFormatDetector.detect(
            "https://example.org/manifest.MPD?quality=hd"))
        assertEquals(StreamFormat.AUTO, StreamFormatDetector.detect(
            "https://example.org/video.mp4"))
        assertEquals(StreamFormat.AUTO, StreamFormatDetector.detect(
            "https://example.org/opaque-stream"))
    }

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

    @Test fun xmltvEpgMatchesM3uTvgId() {
        val playlist = "#EXTM3U\n#EXTINF:-1 tvg-id=\"trt1.tr\" group-title=\"Ulusal\",TRT 1\nhttps://example.org/tv.m3u8"
        val entry = M3uParser.parse(playlist).single()
        assertEquals("trt1.tr", entry.tvgId)
        val exported = M3uExporter.export(listOf(entry))
        assertTrue(exported.contains("tvg-id=\"trt1.tr\""))
        val imported = M3uParser.parse(exported).single()
        assertEquals(entry.tvgId, imported.tvgId)
        val xml = """<tv><programme channel="trt1.tr" start="20261012090000 +0000" stop="20261012120000 +0000"><title>Sabah Haberleri</title></programme></tv>"""
        val now = 1_760_000_000_000L
        val shows = EpgGuide.parse(xml.toByteArray(Charsets.UTF_8), now)
        // The input is outside this synthetic clock's 3-day window and must be rejected.
        assertTrue(shows.isEmpty())
        val validNow = java.text.SimpleDateFormat("yyyyMMddHHmmss Z", java.util.Locale.US)
            .parse("20261012100000 +0000")!!.time
        val valid = EpgGuide.parse(xml.toByteArray(Charsets.UTF_8), validNow)
        assertEquals(1, valid.size)
        assertEquals("Sabah Haberleri", EpgGuide.current(valid, imported.tvgId, imported.name, validNow)?.title)
        assertNull(EpgGuide.current(valid, "wrong-id", imported.name, validNow))
        assertNull(EpgGuide.current(valid, "", imported.name, validNow))
    }

    @Test fun xmltvRejectsDocTypeAndExternalEntities() {
        val unsafe = """<!DOCTYPE tv [<!ENTITY secret SYSTEM "file:///etc/passwd">]><tv><programme channel="x" start="20261012090000 +0000" stop="20261012120000 +0000"><title>&secret;</title></programme></tv>"""
        try {
            EpgGuide.parse(unsafe.toByteArray(Charsets.UTF_8))
            fail("DTD must not be parsed")
        } catch (_: Exception) { /* Expected */ }
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
    @Test fun webLinksRejectUntrustedSchemesAndCredentials() {
        assertEquals("https://www.example.com/watch?id=15",
            BrowserStore.safeHttps(" https://www.example.com/watch?id=15 "))
        assertNull(BrowserStore.safeHttps("http://www.example.com/video"))
        assertNull(BrowserStore.safeHttps("javascript:alert(1)"))
        assertNull(BrowserStore.safeHttps("file:///etc/passwd"))
        assertNull(BrowserStore.safeHttps("https://user:password@example.com"))
        assertNull(BrowserStore.safeHttps("https://bad host.example"))
        assertNull(BrowserStore.safeHttps("https://"))
    }

    @Test fun globalIptvDirectoryGeneratesOnlyCountryListUrls() {
        assertEquals("https://iptv-org.github.io/iptv/countries/tr.m3u",
            WorldCatalog.countryCodeUrl("TR"))
        assertTrue(WorldCatalog.countries().any { it.url.endsWith("/tr.m3u") })
        assertTrue(WorldCatalog.genres.size >= 6)
        try {
            WorldCatalog.countryCodeUrl("../../etc")
            fail("Country codes must be exactly two ISO letters")
        } catch (_: IllegalArgumentException) {
            // Expected: invalid country input blocked.
        }
    }

    @Test fun carLibraryClientIsClassifiedWithoutLeakingPackageNames() {
        assertEquals("android_auto", CarConnectionJournal.classifyClient(
            "com.google.android.projection.gearhead", "tr.id4.mediahub"))
        assertEquals("phone", CarConnectionJournal.classifyClient(
            "tr.id4.mediahub", "tr.id4.mediahub"))
        assertEquals("other_browser", CarConnectionJournal.classifyClient(
            "com.example.some.media.client", "tr.id4.mediahub"))
    }

    @Test fun playbackReportTracksBufferingAndFirstFrameWithoutUrls() {
        val health = PlaybackDiagnostics()
        health.start(1000L)
        health.buffering(1020L)
        health.buffering(1030L) // Duplicate state must not add a new stall.
        health.ready(2520L)
        health.firstFrame(2600L)
        health.firstFrame(2700L) // First frame remains the first one.
        health.videoSize(1920, 1080)
        health.videoDecoder("c2.qti.avc.decoder")
        health.dropped(3)
        health.dropped(-5)
        health.retry()
        health.problem("ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT")
        val report = health.report(4000L)
        assertTrue(report.contains("1600 ms"))
        assertTrue(report.contains("1 kez, 1.5 sn"))
        assertTrue(report.contains("1920 × 1080"))
        assertTrue(report.contains("Düşen video kareleri: 3"))
        assertTrue(report.contains("Otomatik kurtarma denemeleri: 1"))
        assertFalse(report.contains("https://"))
        assertFalse(report.contains("Bearer"))
    }

    @Test fun playbackMetricStateResetsForNextChannel() {
        val health = PlaybackDiagnostics()
        health.start(1L)
        health.dropped(11)
        health.videoDecoder("hw.decoder")
        health.start(20L)
        val report = health.report(100L)
        assertTrue(report.contains("Düşen video kareleri: 0"))
        assertTrue(report.contains("Video çözücü: Bilinmiyor"))
        assertTrue(report.contains("İlk görüntü: Henüz alınmadı"))
    }

    @Test fun transientRetriesAreLimitedAndNeverApplyToAuthErrors() {
        val budget = StreamRetryBudget()
        assertNull(budget.nextDelay(false))
        assertEquals(1500L, budget.nextDelay(true))
        assertEquals(4000L, budget.nextDelay(true))
        assertNull(budget.nextDelay(true))
        budget.reset()
        assertEquals(1500L, budget.nextDelay(true))
        assertEquals(1, budget.attempts)
    }

    @Test fun carBrowseReportUsesFolderTypeWithoutStationNames() {
        assertEquals("Tüm radyolar", CarConnectionJournal.folderLabel("id4_audio_all"))
        assertEquals("Favori radyolar", CarConnectionJournal.folderLabel("id4_audio_favorites"))
        assertEquals("Radyo grubu", CarConnectionJournal.folderLabel("id4_group_Haber%20%26%20Sohbet"))
        assertEquals("Bilinmeyen klasör", CarConnectionJournal.folderLabel("https://secret.example/token"))
    }

}
