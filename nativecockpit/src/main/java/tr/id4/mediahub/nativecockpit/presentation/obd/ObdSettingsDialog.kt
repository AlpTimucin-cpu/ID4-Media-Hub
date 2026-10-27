package tr.id4.mediahub.nativecockpit.presentation.obd

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import tr.id4.mediahub.nativecockpit.domain.model.ObdConnectionState
import tr.id4.mediahub.nativecockpit.domain.model.ObdPhase
import tr.id4.mediahub.nativecockpit.domain.model.MebResponseStatus
import java.util.Locale

@Composable
fun ObdSettingsDialog(
    state: ObdConnectionState,
    onRefresh: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onMebDiagnostic: (String) -> Unit,
    onClearDiagnostic: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth(.94f).fillMaxHeight(.90f).imePadding(),
            shape=MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("OBD Bağlantısı ve MEB Teşhis",style=MaterialTheme.typography.titleLarge)
                Text("Bluetooth Classic SPP ELM327. Adaptörü önce Android Bluetooth ayarlarında eşleştirin.",
                    style=MaterialTheme.typography.bodySmall)
                Text("Durum: ${state.phase}")
                state.error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
                OutlinedButton(onClick=onRefresh, modifier=Modifier.fillMaxWidth()) {
                    Text("Eşleştirilmiş cihazları yenile / izin iste")
                }
                if (state.devices.isEmpty()) Text("Eşleştirilmiş cihaz yok. Bu ekran tarama yapmaz.")
                state.devices.forEach { device ->
                    OutlinedButton(onClick={onConnect(device.address)},
                        enabled=state.phase != ObdPhase.CONNECTED && state.phase != ObdPhase.CONNECTING,
                        modifier=Modifier.fillMaxWidth()) { Text(device.name) }
                }
                if (state.phase == ObdPhase.CONNECTING) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.phase == ObdPhase.CONNECTED) {
                    Text(if (state.lastPollSucceeded) "SAE 010D gerçek hız yanıtı alındı"
                         else "010D hız yanıtı henüz doğrulanmadı")
                    HorizontalDivider()
                    Text("Deneysel MEB ECU okumaları",style=MaterialTheme.typography.titleMedium)
                    Text("YALNIZCA park hâlinde ve kendi aracınızda deneyin. Sorular salt-okuma UDS 22 servisidir. " +
                        "Topluluk PID kaynakları cihazınızda doğrulanmadı; sonuçlar navigasyondaki SoC/SoH alanına aktarılmaz.",
                        style=MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick={ { onMebDiagnostic("22028C") } },
                        enabled=!state.diagnosticBusy,modifier=Modifier.fillMaxWidth()) {
                        Text("BMS şarj yüzdesini oku · 22028C")
                    }
                    OutlinedButton(onClick={ { onMebDiagnostic("221E0E") } },
                        enabled=!state.diagnosticBusy,modifier=Modifier.fillMaxWidth()) {
                        Text("Batarya maksimum sıcaklığı · 221E0E")
                    }
                    OutlinedButton(onClick={ { onMebDiagnostic("221E3D") } },
                        enabled=!state.diagnosticBusy,modifier=Modifier.fillMaxWidth()) {
                        Text("Batarya akımı · yalnız ham yanıt · 221E3D")
                    }
                    if (state.diagnosticBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    state.diagnosticResult?.let { item ->
                        Text("Son sorgu: ${item.request} · ${item.status}",
                            style=MaterialTheme.typography.bodyMedium)
                        if (item.status == MebResponseStatus.VALID_RAW) {
                            item.candidateBmsSocPercent?.let { percent ->
                                Text(String.format(Locale.forLanguageTag("tr-TR"),
                                    "Aday BMS SoC: %.1f %% (araç ekranıyla karşılaştırılmalı)",percent))
                            }
                            item.candidateMaxBatteryTempC?.let { temp ->
                                Text(String.format(Locale.forLanguageTag("tr-TR"),
                                    "Aday maksimum batarya sıcaklığı: %.2f °C",temp))
                            }
                            item.payloadHex?.let { Text("Ham payload: $it",
                                style=MaterialTheme.typography.bodySmall) }
                        } else {
                            Text("Doğrulanmış ECU yanıtı alınamadı; veri kullanılmadı.",
                                style=MaterialTheme.typography.bodySmall)
                        }
                    }
                    TextButton(onClick=onClearDiagnostic,modifier=Modifier.fillMaxWidth()) {
                        Text("Teşhis sonucunu temizle")
                    }
                    Button(onClick=onDisconnect,modifier=Modifier.fillMaxWidth()) {
                        Text("OBD bağlantısını kes")
                    }
                }
                Text("BMS SoC (teşhis adayı), gösterge paneli SoC ve SoH aynı şey değildir. " +
                     "Kokpitte batarya sağlığı/enerji/sıcaklık henüz ölçülmüş veri olarak kullanılmıyor.",
                     style=MaterialTheme.typography.bodySmall)
                TextButton(onClick=onDismiss,modifier=Modifier.fillMaxWidth()) { Text("Haritaya dön") }
            }
        }
    }
}
