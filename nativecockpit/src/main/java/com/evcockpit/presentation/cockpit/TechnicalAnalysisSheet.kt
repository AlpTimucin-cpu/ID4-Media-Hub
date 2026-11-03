package com.evcockpit.presentation.cockpit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.evcockpit.domain.model.EngineeringMath
import com.evcockpit.domain.model.EngineeringSnapshot
import com.evcockpit.domain.model.ObdPhase
import com.evcockpit.domain.model.TimedValue
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Separate live engineering page. No fabricated values or certification.
 * View only: sampling is owned by the singleton OBD repository/service.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TechnicalAnalysisSheet(
    snapshot: EngineeringSnapshot,
    obdPhase: ObdPhase,
    onDismiss: () -> Unit
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(2_000)
        }
    }
    val result = remember(snapshot, now) { EngineeringMath.calculate(snapshot, now) }
    val connected = obdPhase == ObdPhase.CONNECTED
    val recent = snapshot.lastValidResponseAtMs?.let { now - it in 0..120_000L } == true

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Navy
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(.94f)
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Canlı Teknik Hesaplamalar", style = MaterialTheme.typography.headlineSmall)
            Text(
                when {
                    !connected -> "OBD bağlı değil · bağlanınca salt-okunur ölçümler otomatik başlar."
                    !recent -> "OBD bağlı · henüz geçerli MEB ölçümü gelmedi veya veriler eski."
                    else -> "OBD bağlı · güncel MEB sensör verileri alınıyor."
                },
                style = MaterialTheme.typography.bodyMedium
            )
            if (connected) {
                Text("Başarısız/eksik ardışık okuma: ${snapshot.unsuccessfulReads}",
                    style = MaterialTheme.typography.labelSmall)
            }
            HorizontalDivider()
            Text("Anlık batarya", style = MaterialTheme.typography.titleLarge)
            Metric("BMS doluluk", snapshot.socPercent.valueIfFresh(now), "%", 1)
            Metric("Paket gerilimi", snapshot.packVoltageV.valueIfFresh(now), "V", 2)
            Metric("Batarya akımı (+ şarj / − tüketim)", snapshot.packCurrentA.valueIfFresh(now), "A", 2)
            Metric("Gerilim × akım", result.batteryPowerKw, "kW", 2)
            Metric("Dinamik şarj akım sınırı", snapshot.chargeCurrentLimitA.valueIfFresh(now), "A", 1)
            Text(
                "Güç: P(kW) = U(V) × I(A) / 1000. Gerilim ve akım okumaları en fazla 12 saniye aralıklı olmalı.",
                style = MaterialTheme.typography.bodySmall
            )
            HorizontalDivider()
            Text("Hücre ve termal yönetim", style = MaterialTheme.typography.titleLarge)
            Metric("En yüksek hücre", snapshot.cellMaxV.valueIfFresh(now), "V", 4)
            Metric("En düşük hücre", snapshot.cellMinV.valueIfFresh(now), "V", 4)
            Metric("Hücre farkı", result.batteryCellSpreadMv, "mV", 2)
            Metric("En yüksek sensör", snapshot.temperatureMaxC.valueIfFresh(now), "°C", 2)
            Metric("En düşük sensör", snapshot.temperatureMinC.valueIfFresh(now), "°C", 2)
            Metric("Sıcaklık farkı", result.batteryTemperatureSpreadC, "°C", 2)
            Text("Hücre ve sıcaklık farkları eşleştirilmiş, zamanca yakın okumalardan hesaplanır.",
                style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("Batarya ömrü ve enerji sayaçları", style = MaterialTheme.typography.titleLarge)
            Metric("BMS kapasite parametresi", snapshot.capacityParameterKwh.valueIfFresh(now, 900_000L), "kWh", 2)
            Metric("Birikimli şarj enerjisi", snapshot.accumulatedChargedKwh.valueIfFresh(now, 900_000L), "kWh", 1)
            Metric("Birikimli deşarj enerjisi", snapshot.accumulatedDischargedKwh.valueIfFresh(now, 900_000L), "kWh", 1)
            Metric("Eşdeğer tam deşarj çevrimi", result.dischargeEquivalentFullCycles, "FEC", 1)
            Text("FEC = birikimli deşarj (kWh) / 52 kWh. Başlangıçtan beri sayaçların sıfırlanmadığı varsayılır.",
                style = MaterialTheme.typography.bodySmall)
            Text("SoH: doğrulanmadı. Kapasite parametresi / 52 oranı resmi SoH değildir; ODIS kapasite testi gerekir.",
                style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("Sürüş profiline göre tahmini menzil", style = MaterialTheme.typography.titleLarge)
            Metric("Yaz · 10,5 kWh/100 km", result.estimatedSummerRangeKm, "km", 0)
            Metric("Kış · 18 kWh/100 km", result.estimatedWinterRangeKm, "km", 0)
            Text(
                "Bu iki değer gerçek zamanlı tüketim ölçümü değildir. Senin mevsimsel tüketim ortalamalarından " +
                    "52 kWh fabrika net kapasitesi kullanılarak hesaplanan model tahminidir.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Veriler yalnızca teşhis amaçlıdır; hücre arızası, izolasyon güvenliği, " +
                    "SoH veya servis kararı için tek başına yeterli değildir. " +
                    "Bağlantı kesilince canlı değerler temizlenir.",
                style = MaterialTheme.typography.bodySmall
            )
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Haritaya dön") }
            Spacer(Modifier.height(22.dp))
        }
    }
}

private fun TimedValue<Double>.valueIfFresh(now: Long, ttl: Long = 120_000L): Double? =
    current(now, ttl)

@Composable
private fun Metric(name: String, value: Double?, unit: String, precision: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            value?.takeIf { it.isFinite() }?.let {
                String.format(Locale.forLanguageTag("tr-TR"), "%.${precision}f %s", it, unit)
            } ?: "—",
            style = MaterialTheme.typography.titleSmall
        )
    }
}
