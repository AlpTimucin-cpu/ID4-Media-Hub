package com.evcockpit.data.radio

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

/** Radio Browser's JSON fields; blank values are rejected in RadioRepository. */
data class RadioStation(
    @SerializedName("stationuuid") val id: String = "",
    val name: String = "",
    @SerializedName("url_resolved") val urlResolved: String = "",
    val favicon: String = "",
    val tags: String = "",
    val votes: Int = 0
)

interface RadioBrowserApi {
    // Radio Browser Turkish stations; ranked in descending community vote order.
    @GET("json/stations/bycountryexact/Turkey")
    suspend fun stations(
        @Query("hidebroken") hideBroken: Boolean = true,
        @Query("order") order: String = "votes",
        @Query("reverse") reverse: Boolean = true,
        @Query("limit") limit: Int = 500
    ): List<RadioStation>
}
