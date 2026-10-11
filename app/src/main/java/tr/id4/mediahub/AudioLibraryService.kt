package tr.id4.mediahub

import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import android.net.Uri
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log

/** Driver-safe audio media library for Android Auto. Video stays in the phone Activity. */
class AudioLibraryService : MediaLibraryService() {
    private var session: MediaLibrarySession? = null
    private var player: ExoPlayer? = null
    private var receiverRegistered = false
    private var knownGroups = emptySet<String>()
    private val catalogReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_CATALOG_UPDATED) notifyCatalogueChanged()
        }
    }

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(), true)
            setHandleAudioBecomingNoisy(true)
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    // Avoid writing token-bearing stream URLs into shared diagnostics.
                    Log.e("ID4MediaHub", "Audio playback failure: ${error.errorCodeName}")
                }
            })
        }
        session = MediaLibrarySession.Builder(this, player!!, LibraryCallback()).build()
        knownGroups = StationStore.load(this).map { it.group }.toSet()
        val filter = IntentFilter(ACTION_CATALOG_UPDATED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(catalogReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(catalogReceiver, filter)
        }
        receiverRegistered = true
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        CarConnectionJournal.recordSessionConnection(this, controllerInfo.packageName)
        return session
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            unregisterReceiver(catalogReceiver)
            receiverRegistered = false
        }
        session?.release()
        session = null
        player?.release()
        player = null
        super.onDestroy()
    }

    /** Tell subscribed Android Auto browsers to reload the changed folder contents. */
    private fun notifyCatalogueChanged() {
        val library = session ?: return
        val radios = StationStore.load(this)
        val groups = radios.map { it.group }.toSet()
        library.notifyChildrenChanged(ROOT, 3, null)
        library.notifyChildrenChanged(ALL, radios.size, null)
        library.notifyChildrenChanged(FAVORITES, radios.count { it.favorite }, null)
        library.notifyChildrenChanged(GROUPS, groups.size, null)
        for (name in knownGroups + groups) {
            library.notifyChildrenChanged(GROUP_PREFIX + Uri.encode(name),
                radios.count { it.group == name }, null)
        }
        knownGroups = groups
    }

    private fun folder(id: String, title: String): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(MediaMetadata.Builder()
            .setTitle(title)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            .build())
        .build()

    private fun rootItem() = folder(ROOT, "ID.4 Media Hub")
    private fun allItem() = folder(ALL, "Tüm radyolar")
    private fun favoriteItem() = folder(FAVORITES, "Favori radyolar")
    private fun groupsItem() = folder(GROUPS, "Radyo grupları")
    private fun groupItem(name: String) = folder(GROUP_PREFIX + Uri.encode(name), name)

    /** All reads use the current local catalogue; re-opening the library reflects imports. */
    @OptIn(UnstableApi::class)
    private inner class LibraryCallback : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            CarConnectionJournal.recordLibraryBrowse(
                this@AudioLibraryService, browser.packageName
            )
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem(), params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            if (page < 0 || pageSize <= 0)
                return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
            // Slice the catalogue BEFORE building Media3 objects. With 2500 IPTV/radio entries,
            // creating metadata for every item on each 20-item page wastes memory and CPU.
            val items = when (parentId) {
                ROOT -> LibraryPage.slice(listOf(allItem(), favoriteItem(), groupsItem()), page, pageSize)
                ALL -> LibraryPage.slice(StationStore.load(this@AudioLibraryService), page, pageSize)
                    .map { it.toMediaItem() }
                FAVORITES -> LibraryPage.slice(StationStore.load(this@AudioLibraryService)
                    .filter { it.favorite }, page, pageSize).map { it.toMediaItem() }
                GROUPS -> LibraryPage.slice(StationStore.load(this@AudioLibraryService)
                    .map { it.group }.distinct().sorted(), page, pageSize).map { groupItem(it) }
                else -> {
                    if (!parentId.startsWith(GROUP_PREFIX))
                        return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
                    val group = Uri.decode(parentId.removePrefix(GROUP_PREFIX))
                    LibraryPage.slice(StationStore.load(this@AudioLibraryService)
                        .filter { it.group == group }, page, pageSize).map { it.toMediaItem() }
                }
            }
            return Futures.immediateFuture(LibraryResult.ofItemList(items, params))
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val item = when (mediaId) {
                ROOT -> rootItem()
                ALL -> allItem()
                FAVORITES -> favoriteItem()
                GROUPS -> groupsItem()
                else -> if (mediaId.startsWith(GROUP_PREFIX)) {
                    val group = Uri.decode(mediaId.removePrefix(GROUP_PREFIX))
                    if (StationStore.load(this@AudioLibraryService).any { it.group == group }) groupItem(group)
                    else null
                } else StationStore.load(this@AudioLibraryService)
                    .find { it.id == mediaId }?.toMediaItem()
            }
            return Futures.immediateFuture(
                if (item == null) LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                else LibraryResult.ofItem(item, null))
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            val count = matches(query).size
            session.notifySearchResultChanged(browser, query, count, params)
            return Futures.immediateFuture(LibraryResult.ofVoid(params))
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            if (page < 0 || pageSize <= 0)
                return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
            val found = LibraryPage.slice(matches(query), page, pageSize).map { it.toMediaItem() }
            return Futures.immediateFuture(LibraryResult.ofItemList(found, params))
        }

        private fun matches(query: String): List<Station> =
            StationStore.load(this@AudioLibraryService).filter {
                it.name.contains(query, ignoreCase = true) || it.group.contains(query, ignoreCase = true)
            }

        /** Browser metadata deliberately omits streaming URLs. Resolve only for playback. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>
        ): ListenableFuture<List<MediaItem>> {
            val stations = StationStore.load(this@AudioLibraryService)
            val resolved = mediaItems.mapNotNull { requested ->
                matchStation(stations, requested)?.toMediaItem(includeUri = true)
            }
            return if (resolved.isEmpty()) {
                Futures.immediateFailedFuture(IllegalArgumentException("No audio station matched"))
            } else Futures.immediateFuture(resolved)
        }

        /** Also resolves legacy Android Auto playFromMediaId/search requests. */
        @OptIn(UnstableApi::class)
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val stations = StationStore.load(this@AudioLibraryService)
            val sources = stations.map { RadioSource(it.id, it.name, it.url) }
            val requests = mediaItems.map {
                RadioRequest(it.mediaId, it.requestMetadata.mediaUri?.toString(), it.requestMetadata.searchQuery)
            }
            val plan = RadioQueuePlanner.plan(sources, requests, startIndex)
                ?: return Futures.immediateFailedFuture(IllegalArgumentException("No playable radio matched"))
            val byId = stations.associateBy { it.id }
            val playable = plan.ids.mapNotNull { byId[it]?.toMediaItem(includeUri = true) }
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(
                    playable, plan.index,
                    if (startPositionMs == C.TIME_UNSET) C.TIME_UNSET else startPositionMs.coerceAtLeast(0L)
                )
            )
        }

        private fun matchStation(stations: List<Station>, requested: MediaItem): Station? {
            val sources = stations.map { RadioSource(it.id, it.name, it.url) }
            val request = RadioRequest(requested.mediaId, requested.requestMetadata.mediaUri?.toString(),
                requested.requestMetadata.searchQuery)
            val selected = RadioQueuePlanner.resolve(sources, request) ?: return null
            return stations.firstOrNull { it.id == selected.id }
        }
    }

    companion object {
        const val ACTION_CATALOG_UPDATED = "tr.id4.mediahub.CATALOG_UPDATED"
        private const val ROOT = "id4_audio_root"
        const val ALL = "id4_audio_all"
        const val FAVORITES = "id4_audio_favorites"
        const val GROUPS = "id4_audio_groups"
        const val GROUP_PREFIX = "id4_group_"
    }
}
