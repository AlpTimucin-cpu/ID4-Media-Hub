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

@Composable
fun ObdSettingsDialog(
    state: ObdConnectionState,
    onRefresh: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxWidth(.94f).heightIn(max = 520.dp).imePadding(),
            shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("OBD Bağlantısı", style = MaterialTheme.typography.titleLarge)
                Text("Yalnız Bluetooth Classic SPP ELM327. Adaptörü önce Android Bluetooth ayarlarında eşleştirin.",
                    style = MaterialTheme.typography.bodySmall)
                Text("Durum: ${state.phase}")
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                    Text("Eşleştirilmiş cihazları yenile / izin iste")
                }
                if (state.devices.isEmpty()) Text("Eşleştirilmiş cihaz bulunamadı. Bu ekran tarama yapmaz.")
                state.devices.forEach { device ->
                    OutlinedButton(onClick = { onConnect(device.address) },
                        enabled = state.phase != ObdPhase.CONNECTED &&
                            state.phase != ObdPhase.CONNECTING,
                        modifier = Modifier.fillMaxWidth()) {
                        Text(device.name)
                    }
                }
                if (state.phase == ObdPhase.CONNECTING) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.phase == ObdPhase.CONNECTED) {
                    Text(if (state.lastPollSucceeded) "010D hız verisi alındı" else "010D yanıtı bekleniyor")
                    Button(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) { Text("OBD bağlantısını kes") }
                }
                Text("Batarya SoC/SoH ve HV sıcaklığı: PID doğrulaması tamamlanmadığı için gösterilmiyor. " +
                    "Car Scanner aynı adaptöre bağlıysa önce o bağlantıyı kapatın.",
                    style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Haritaya dön") }
            }
        }
    }
}
