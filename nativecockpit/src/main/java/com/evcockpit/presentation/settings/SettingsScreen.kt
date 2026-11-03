package com.evcockpit.presentation.settings

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evcockpit.data.settings.UserSettingsRepository
import com.evcockpit.presentation.sound.ActiveSoundManager
import java.util.Locale

/** Separate Material3 panel; the original Tesla-style CockpitScreen is untouched. */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    activeSound: ActiveSoundManager,
    onClose: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val voices by viewModel.voiceOptions.collectAsStateWithLifecycle()
    var voicesExpanded by remember { mutableStateOf(false) }
    var profilesExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadInstalledTurkishVoices() }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(.94f).fillMaxHeight(.88f),
            shape = RoundedCornerShape(22.dp),
            color = Color(0xF20A1522),
            contentColor = Color.White,
            shadowElevation = 16.dp
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                Text("Ses ve Görünüm Ayarları", style = MaterialTheme.typography.titleLarge)
                HorizontalDivider()

                Text("Sesli Asistan", style = MaterialTheme.typography.titleMedium)
                SettingToggle(
                    "Araca binerken karşıla", settings.isWelcomeGreetingEnabled,
                    viewModel::setWelcome
                )

                Text("Türkçe karşılama sesi", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(
                    onClick = { voicesExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val current = voices.firstOrNull { it.name == settings.ttsVoiceName }
                    Text(current?.label ?: "Sistem varsayılan Türkçe sesi")
                }
                DropdownMenu(
                    expanded = voicesExpanded,
                    onDismissRequest = { voicesExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Sistem Türkçe sesi") },
                        onClick = {
                            viewModel.setVoice(null)
                            voicesExpanded = false
                        }
                    )
                    voices.forEach { v ->
                        DropdownMenuItem(
                            text = { Text(v.label) },
                            onClick = {
                                viewModel.setVoice(v.name)
                                voicesExpanded = false
                            }
                        )
                    }
                }
                Text(
                    "Android ses motorları cinsiyet garantisi vermez. Kadın/Erkek etiketi yalnız " +
                        "ses dosyasının adında mevcutsa gösterilir. Bu seçim Türkçe karşılamaya uygulanır.",
                    color = Color(0xFFAEC4D6),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Konuşma hızı: " + String.format(Locale.US, "%.2fx", settings.ttsSpeechRate),
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = settings.ttsSpeechRate,
                    onValueChange = viewModel::setRate,
                    valueRange = .7f..1.4f,
                    steps = 6
                )

                HorizontalDivider()
                Text("Active Sound · İç mekân", style = MaterialTheme.typography.titleMedium)
                SettingToggle(
                    "Yapay motor sesi", settings.isEngineSoundEnabled,
                    viewModel::setEngineEnabled
                )

                Text("Motor sesi profili", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(
                    onClick = { profilesExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(settings.engineSoundProfile) }
                DropdownMenu(
                    expanded = profilesExpanded,
                    onDismissRequest = { profilesExpanded = false }
                ) {
                    UserSettingsRepository.ENGINE_PROFILES.forEach { profile ->
                        DropdownMenuItem(
                            text = { Text(profile) },
                            onClick = {
                                viewModel.setEngineProfile(profile)
                                profilesExpanded = false
                            }
                        )
                    }
                }
                Text("Motor sesi seviyesi: " +
                    (settings.engineVolume / 1.5f * 150).toInt() + "%",
                    style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = settings.engineVolume,
                    onValueChange = viewModel::setEngineVolume,
                    valueRange = 0f..1.5f,
                    steps = 14
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { activeSound.preview(settings) }
                ) { Text("Sesi Dinle · 3 saniye") }
                Text(
                    "%150 arayüz ayarı Media3 içinde güvenli 0–1 aralığına ölçeklenir. " +
                        "Bu özellik aracın dış AVAS sisteminin yerine geçmez; " +
                        "OBD hızı yoksa motor sesi otomatik durur.",
                    color = Color(0xFFAEC4D6),
                    style = MaterialTheme.typography.bodySmall
                )

                HorizontalDivider()
                SettingToggle(
                    "Daha karanlık gece teması",
                    settings.isNightModeEnabled,
                    viewModel::setNight
                )
                Spacer(Modifier.height(2.dp))
                TextButton(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Kapat") }
            }
        }
    }
}

@Composable
private fun SettingToggle(title: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}
