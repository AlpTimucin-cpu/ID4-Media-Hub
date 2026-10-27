package tr.id4.mediahub.nativecockpit.data.routing

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** DEMO ONLY: route alternatives and steps must come from OSRM, not local geometry. */
interface OsrmService {
    @GET("route/v1/driving/{coordinates}")
    suspend fun route(
        @Path(value="coordinates", encoded=true) coordinates: String,
        @Query("alternatives") alternatives: Int = 6,
        @Query("steps") steps: Boolean = true,
        @Query("geometries") geometries: String = "geojson",
        @Query("overview") overview: String = "full"
    ): ResponseBody
}
