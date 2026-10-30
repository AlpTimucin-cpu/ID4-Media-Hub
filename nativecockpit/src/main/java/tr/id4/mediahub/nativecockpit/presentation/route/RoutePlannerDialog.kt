package tr.id4.mediahub.nativecockpit.presentation.route

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import tr.id4.mediahub.nativecockpit.domain.model.RoutePlace

/** Route and charging planner: one IME inset, one scroll container, fixed close button.
 * State and callback contracts remain unchanged.
 */
@Composable
fun RoutePlannerDialog(
    state: RoutePlannerUiState,
    onDismiss: () -> Unit,
    onOriginChange: (String) -> Unit,
    onDestinationChange: (String) -> Unit,
    onOriginSelected: (RoutePlace) -> Unit,
    onDestinationSelected: (RoutePlace) -> Unit,
    onGps: () -> Unit,
    onPlan: () -> Unit,
    onChooseAlternative: (Int) -> Unit,
    onClear: () -> Unit
) {
    val scrollState = rememberScrollState()
    val muted = Color(0xFFABC5D5)
    val accent = Color(0xFF62E7EE)
    val success = Color(0xFF74F2BD)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedPlaceholderColor = muted,
        unfocusedPlaceholderColor = muted,
        focusedBorderColor = accent,
        unfocusedBorderColor = Color(0xFF658194),
        cursorColor = accent
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // Full-window scrim; no IME inset on this outer container.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.80f))
                .statusBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.90f)
                        .fillMaxHeight(0.85f),
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xFF0A1728),
                    tonalElevation = 4.dp,
                    shadowElevation = 18.dp
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // This header is outside the scroll area, so X never moves.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 18.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Rota ve şarj planı",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White
                            )
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Pencereyi kapat",
                                    tint = Color.White
                                )
                            }
                        }
                        HorizontalDivider(color = Color(0xFF27465B))

                        // Every form field, suggestion, alternative and action can scroll.
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .imePadding()
                                .verticalScroll(scrollState)
                                .padding(horizontal = 18.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Photon / OSM adresleri · OSRM gerçek rota · canlı trafik değil",
                                style = MaterialTheme.typography.bodySmall,
                                color = muted
                            )

                            // Manual heading replaces the TextField's floating label.
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "NEREDEN",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White
                                )
                                OutlinedTextField(
                                    value = state.originText,
                                    onValueChange = onOriginChange,
                                    placeholder = { Text("Başlangıç adresi veya şehir") },
                                    singleLine = true,
                                    colors = fieldColors,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                TextButton(
                                    onClick = onGps,
                                    enabled = !state.locatingGps,
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text(
                                        if (state.locatingGps) "GPS konumu alınıyor…"
                                        else "Güncel GPS konumumdan başlat"
                                    )
                                }
                                if (state.locatingGps) {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                                if (state.startLocation != null) {
                                    Text("Başlangıç konumu seçildi", color = success)
                                }
                                if (state.searchingOrigin) {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                                Suggestions(state.originMatches, onOriginSelected)
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "NEREYE",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White
                                )
                                OutlinedTextField(
                                    value = state.destinationText,
                                    onValueChange = onDestinationChange,
                                    placeholder = { Text("Varış adresi veya şehir") },
                                    singleLine = true,
                                    colors = fieldColors,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (state.destinationLocation != null) {
                                    Text("Varış konumu seçildi", color = success)
                                }
                                if (state.searchingDestination) {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                                Suggestions(state.destinationMatches, onDestinationSelected)
                            }

                            state.error?.let { error ->
                                Text(
                                    text = error,
                                    color = Color(0xFFFFB17C),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            if (state.options.isNotEmpty()) {
                                HorizontalDivider(color = Color(0xFF27465B))
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        text = "OSRM alternatifleri (" +
                                            state.options.size + " doğrulanmış)",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color.White
                                    )
                                    state.options.forEachIndexed { index, item ->
                                        val selected = state.selectedIndex == index
                                        OutlinedButton(
                                            onClick = { onChooseAlternative(index) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 52.dp)
                                        ) {
                                            Text(
                                                (if (selected) "✓ " else "") +
                                                    "Rota " + (index + 1) + " · " +
                                                    "%.1f km · %d dk".format(
                                                        item.distanceKm,
                                                        item.durationMinutesWithoutTraffic
                                                    )
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Tahminler trafiksizdir. GPS ilerlemesi olmadan " +
                                            "varış SoC hesaplanmaz.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = muted
                                    )
                                }
                            }

                            HorizontalDivider(color = Color(0xFF27465B))

                            // Keep all actions reachable with an open keyboard.
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (state.planning) {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                                Button(
                                    onClick = onPlan,
                                    enabled = state.canCalculate,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 52.dp)
                                ) {
                                    Text("Gerçek yol rotasını hesapla")
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = onClear,
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                    ) { Text("Rotayı temizle") }
                                    TextButton(
                                        onClick = onDismiss,
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                    ) { Text("Haritaya dön") }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
        }
    }
}

@Composable
private fun Suggestions(items: List<RoutePlace>, onSelect: (RoutePlace) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { place ->
            Surface(
                color = Color(0xFF19374B),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable { onSelect(place) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = place.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                }
            }
        }
    }
}
