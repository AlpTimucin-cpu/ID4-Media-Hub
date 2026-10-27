package tr.id4.mediahub.nativecockpit.data.remote

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.Instant
import java.util.zip.GZIPInputStream
import org.json.JSONObject
import tr.id4.mediahub.nativecockpit.domain.model.ChargingStation
import tr.id4.mediahub.nativecockpit.domain.model.ChargingSocket
import tr.id4.mediahub.nativecockpit.domain.model.GeoCoordinate

/** Reject schema drift and corrupted/partial snapshots instead of showing false completeness. */
object EpdkSnapshotParser {
    const val MAX_GZIP_BYTES = 10_000_000
    private const val MAX_JSON_BYTES = 24_000_000
    private const val MIN_STATIONS = 5_000
    data class Parsed(val verifiedAtUtc: String, val stations: List<ChargingStation>)

    fun readBounded(stream: InputStream, maxBytes: Int): ByteArray {
        val result=ByteArrayOutputStream()
        val buffer=ByteArray(8192)
        while(true) {
            val n=stream.read(buffer)
            if(n<0)break
            if(result.size()+n>maxBytes) error("EPDK yanıtı izin verilen boyutu aşıyor")
            result.write(buffer,0,n)
        }
        return result.toByteArray()
    }

    fun parseGzip(bytes: ByteArray): Parsed {
        require(bytes.size in 1..MAX_GZIP_BYTES) { "EPDK gzip boyutu geçersiz" }
        val json=GZIPInputStream(ByteArrayInputStream(bytes)).use { readBounded(it,MAX_JSON_BYTES) }
        return parseJson(String(json,Charsets.UTF_8))
    }

    fun parseJson(json: String): Parsed {
        val root=JSONObject(json)
        require(root.optInt("schema_version") == 1) { "EPDK sürüm uyumsuz" }
        require(!root.optBoolean("prices_included",true) &&
            !root.optBoolean("live_status_included",true)) { "Kaynak beklenmedik alanlar içeriyor" }
        val verified=root.getString("verified_at_utc")
        val verifiedTime=Instant.parse(verified)
        val now=Instant.now()
        require(!verifiedTime.isAfter(now.plusSeconds(300))) { "EPDK tarihi gelecekte" }
        val raw=root.getJSONArray("stations")
        require(raw.length() in MIN_STATIONS..100_000) { "EPDK kayıt sayısı beklenenden az" }
        val items=ArrayList<ChargingStation>(raw.length())
        val ids=HashSet<String>()
        for(i in 0 until raw.length()) {
            val record=raw.optJSONObject(i) ?: continue
            if(!record.optBoolean("public",false) ||
                record.optString("service_kind")!="HALKA_ACIK")continue
            val id=record.optString("official_station_id").trim()
            if(id.isBlank() || !ids.add(id))continue
            val lat=record.optDouble("lat",Double.NaN)
            val lon=record.optDouble("lon",Double.NaN)
            val coords=GeoCoordinate(lat,lon)
            if(!coords.valid()||lat !in 35.0..43.0||lon !in 25.0..46.0)continue
            val socketsJson=record.optJSONArray("sockets")
            val sockets=buildList {
                if(socketsJson!=null)for(j in 0 until minOf(socketsJson.length(),24)) {
                    val socket=socketsJson.optJSONObject(j) ?: continue
                    add(ChargingSocket(
                        socket.optString("socket_id").take(48),
                        socket.optString("type").take(60),
                        socket.optString("kind").take(50),
                        socket.optString("power_raw").take(24)))
                }
            }
            items.add(ChargingStation(id,record.optString("name","Şarj istasyonu").take(150),
                record.optString("operator").take(160),record.optString("address").take(260),
                coords,sockets,verified))
        }
        require(items.size>=MIN_STATIONS && items.size>=raw.length()*.8) {
            "EPDK snapshot eksik veya yanlış ayrıştırıldı"
        }
        return Parsed(verified,items)
    }
}
