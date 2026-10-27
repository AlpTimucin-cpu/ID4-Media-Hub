package tr.id4.mediahub.nativecockpit.data.routing

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query

/** Demo search is debounced; Photon demo server is NOT a production SLA. */
interface PhotonService {
    @GET("api/")
    suspend fun search(
        @Query("q") query: String,
        @Query("lang") language: String? = null,
        @Query("limit") limit: Int = 5
    ): ResponseBody
}
