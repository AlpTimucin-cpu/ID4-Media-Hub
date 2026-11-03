package com.evcockpit.data.radio

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

data class RadioStation(
    @SerializedName("stationuuid") val id: String = "",
    val name: String = "",
    @SerializedName("url_resolved") val urlResolved: String = "",
    val favicon: String = "",
    val tags: String = "",
    val votes: Int = 0
)

interface RadioBrowserApi {
    @GET("json/stations/search")
    suspend fun stations(
        @Query("countrycode") countryCode: String = "TR",
        @Query("hidebroken") hideBroken: Boolean = true,
        @Query("order") order: String = "votes",
        @Query("reverse") reverse: Boolean = true,
        @Query("limit") limit: Int = 200
    ): List<RadioStation>
}
