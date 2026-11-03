package com.evcockpit.data.radio

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RadioRepository @Inject constructor(private val api: RadioBrowserApi) {
    suspend fun stations(): List<RadioStation> = playableStations(api.stations())

    companion object {
        // Preserve the application's HTTPS-only network policy. Never rewrite
        // HTTP URLs to HTTPS: the station might not support TLS on that port.
        internal fun playableStations(stations: List<RadioStation>): List<RadioStation> =
            stations.filter {
                !it.id.isNullOrBlank() && !it.name.isNullOrBlank() &&
                    it.urlResolved?.toHttpUrlOrNull()?.isHttps == true
            }.sortedByDescending { it.votes }.distinctBy { it.urlResolved }.distinctBy { it.id }
    }
}
