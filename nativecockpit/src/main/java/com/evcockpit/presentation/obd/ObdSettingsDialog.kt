package com.evcockpit.presentation.obd

import androidx.compose.ui.res.stringResource
import com.evcockpit.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.evcockpit.domain.model.ObdConnectionState
import com.evcockpit.domain.model.ObdPhase
import com.evcockpit.domain.model.MebResponseStatus
import java.util.Locale

@Composable
fun ObdSettingsDialog(
    state: ObdConnectionState,
    onRefresh: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onMebDiagnostic: (String) -> Unit,
    onClearDiagnostic: () -> Unit,
    simulationSupported: Boolean,
    simulationEnabled: Boolean,
    onSimulationToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth(.94f).fillMaxHeight(.90f).imePadding(),
            shape=MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.native_obd_connection_and_meb_diagnostics),style=MaterialTheme.typography.titleLarge)
                if (simulationSupported) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.native_simulation_test_data))
                            Text(stringResource(R.string.native_not_actual_vehicle_data_available_in_debug_builds_only),
                                style=MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked=simulationEnabled, onCheckedChange=onSimulationToggle)
                    }
                    HorizontalDivider()
                }
                Text(stringResource(R.string.native_bluetooth_classic_spp_elm327_pair_the_adapter_in_android_bluetooth_set),
                    style=MaterialTheme.typography.bodySmall)
                Text("Durum: ${phaseLabel(state.phase)}")
                state.error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
                OutlinedButton(onClick=onRefresh, enabled=!simulationEnabled, modifier=Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.native_refresh_paired_devices_request_permission))
                }
                if (state.devices.isEmpty()) Text(stringResource(R.string.native_no_paired_devices_this_screen_does_not_scan))
                state.devices.forEach { device ->
                    OutlinedButton(onClick={onConnect(device.address)},
                        enabled=!simulationEnabled && state.phase != ObdPhase.CONNECTED && state.phase != ObdPhase.CONNECTING,
                        modifier=Modifier.fillMaxWidth()) { Text(device.name) }
                }
                if (state.phase == ObdPhase.CONNECTING) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.phase == ObdPhase.CONNECTED) {
                    Text(if (state.lastPollSucceeded) "SAE 010D gerçek hız yanıtı alındı"
                         else "010D hız yanıtı henüz doğrulanmadı")
                    HorizontalDivider()
                    Text(stringResource(R.string.native_experimental_meb_ecu_readings),style=MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.native_test_only_while_parked_and_in_your_own_vehicle_queries_use_read_only_u) +
                        "Topluluk PID kaynakları cihazınızda doğrulanmadı; sonuçlar navigasyondaki SoC/SoH alanına aktarılmaz.",
                        style=MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick={ { onMebDiagnostic("22028C") } },
                        enabled=!simulationEnabled && !state.diagnosticBusy,modifier=Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.native_read_bms_state_of_charge_22028c))
                    }
                    OutlinedButton(onClick={ { onMebDiagnostic("221E0E") } },
                        enabled=!simulationEnabled && !state.diagnosticBusy,modifier=Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.native_maximum_battery_temperature_221e0e))
                    }
                    OutlinedButton(onClick={ { onMebDiagnostic("221E3D") } },
                        enabled=!simulationEnabled && !state.diagnosticBusy,modifier=Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.native_battery_current_raw_response_only_221e3d))
                    }
                    if (state.diagnosticBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    state.diagnosticResult?.let { item ->
                        Text("Son sorgu: ${item.request} · ${diagnosticLabel(item.status)}",
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
                            Text(stringResource(R.string.native_no_validated_ecu_response_received_data_was_not_used),
                                style=MaterialTheme.typography.bodySmall)
                        }
                    }
                    TextButton(onClick=onClearDiagnostic,modifier=Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.native_clear_diagnostic_result))
                    }
                    Button(onClick=onDisconnect,modifier=Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.native_disconnect_obd))
                    }
                }
                Text(stringResource(R.string.native_bms_soc_diagnostic_candidate_dashboard_soc_and_soh_are_different_quant) +
                     "Kokpitte batarya sağlığı/enerji/sıcaklık henüz ölçülmüş veri olarak kullanılmıyor.",
                     style=MaterialTheme.typography.bodySmall)
                TextButton(onClick=onDismiss,modifier=Modifier.fillMaxWidth()) { Text(stringResource(R.string.native_back_to_map)) }
            }
        }
    }
}

private fun phaseLabel(phase: ObdPhase): String = when (phase) {
    ObdPhase.DISCONNECTED -> "Bağlı değil"
    ObdPhase.CONNECTING -> "Bağlanıyor"
    ObdPhase.CONNECTED -> "Bağlı"
    ObdPhase.ERROR -> "Hata"
}

private fun diagnosticLabel(status: MebResponseStatus): String = when (status) {
    MebResponseStatus.VALID_RAW -> "Geçerli ham yanıt"
    MebResponseStatus.NO_DATA -> "Veri yok"
    MebResponseStatus.NEGATIVE -> "ECU isteği reddetti"
    MebResponseStatus.WRONG_ECU -> "Farklı ECU yanıtı"
    MebResponseStatus.WRONG_IDENTIFIER -> "Farklı veri kimliği"
    MebResponseStatus.INCOMPLETE -> "Eksik yanıt"
    MebResponseStatus.MALFORMED -> "Geçersiz yanıt"
    MebResponseStatus.ADAPTER_ERROR -> "Adaptör hatası"
}
