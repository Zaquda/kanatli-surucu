package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MartiViewModel
import com.example.ui.theme.MartiGold
import com.example.ui.theme.MartiGreen
import java.util.*

@Composable
fun AnalysisScreen(
    viewModel: MartiViewModel,
    modifier: Modifier = Modifier
) {
    val shifts by viewModel.allShifts.collectAsState()
    val trips by viewModel.allTrips.collectAsState()
    val profile by viewModel.profile.collectAsState()

    var selectedInterval by remember { mutableStateOf("Haftalık") } // "Haftalık" or "Aylık"

    // Filter shifts based on chosen interval
    val filteredShifts = remember(shifts, selectedInterval) {
        val calendar = Calendar.getInstance()
        val limit = if (selectedInterval == "Haftalık") -7 else -30
        calendar.add(Calendar.DAY_OF_YEAR, limit)
        val cutoff = calendar.timeInMillis
        shifts.filter { it.startTime >= cutoff }
    }

    // Calculations
    val totalDistance = filteredShifts.sumOf { it.totalGpsKm }
    val totalHours = filteredShifts.sumOf {
        val end = it.endTime ?: System.currentTimeMillis()
        val durationMs = (end - it.startTime - it.accumulatedPauseMillis)
        durationMs / 3600000.0
    }

    // Trips belonging to the filtered shifts
    val filteredShiftIds = filteredShifts.map { it.id }.toSet()
    val filteredTrips = trips.filter { it.shiftId in filteredShiftIds }

    val totalGrossEarnings = filteredTrips.sumOf { it.earnings }

    val avgConsumption = profile?.avgConsumption ?: 7.0
    val fuelPrice = profile?.fuelPrice ?: 63.2
    val totalFuelLiters = (totalDistance * avgConsumption) / 100.0
    val totalFuelCost = totalFuelLiters * fuelPrice
    val totalGeneralExpenses = filteredShifts.sumOf { it.generalExpenses }
    val netProfit = totalGrossEarnings - totalFuelCost - totalGeneralExpenses

    val avgHourlyEarnings = if (totalHours > 0.0) totalGrossEarnings / totalHours else 0.0

    // Cash vs Card ratios
    val cashEarnings = filteredTrips.filter { it.paymentMethod == "Nakit" }.sumOf { it.earnings }
    val cardEarnings = filteredTrips.filter { it.paymentMethod == "K.Kartı" }.sumOf { it.earnings }
    val totalTripEarnings = cashEarnings + cardEarnings
    val cashRatio = if (totalTripEarnings > 0.0) (cashEarnings / totalTripEarnings).toFloat() else 0.5f
    val cardRatio = if (totalTripEarnings > 0.0) (cardEarnings / totalTripEarnings).toFloat() else 0.5f

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp, top = 8.dp)
    ) {
        // --- INTERVAL SELECTOR TABS ---
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Haftalık", "Aylık").forEach { interval ->
                        val isSelected = selectedInterval == interval
                        Button(
                            onClick = { selectedInterval = interval },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("${interval.lowercase()}_analysis_tab")
                        ) {
                            Text(interval, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // --- CORE KPI OVERVIEWS ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Gross Earnings Card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Icon(Icons.Default.TrendingUp, contentDescription = null, tint = MartiGreen)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Toplam Brüt", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(String.format(Locale.US, "₺%.1f", totalGrossEarnings), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }

                // Net Profit Card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = MartiGold)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Net Kar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(String.format(Locale.US, "₺%.1f", netProfit), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MartiGreen)
                    }
                }
            }
        }

        // --- EFFICIENCY STATS CARD ---
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Verimlilik Analizi",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Toplam Mesafe", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(String.format(Locale.US, "%.1f KM", totalDistance), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Toplam Sürüş", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(String.format(Locale.US, "%.1f Saat", totalHours), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Vardiya Sayısı", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${filteredShifts.size} Vardiya", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Saatlik Ortalama", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(String.format(Locale.US, "₺%.1f/sa", avgHourlyEarnings), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MartiGreen)
                        }
                    }
                }
            }
        }

        // --- EXPENSES & FUEL IN-DEPTH STATS ---
        item {
            val totalGeneralExpenses = filteredShifts.sumOf { it.generalExpenses }
            val overallExpenses = totalFuelCost + totalGeneralExpenses

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Gider & Sarfiyat Detayları",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(Icons.Default.LocalGasStation, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Yakıt Tüketimi", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(String.format(Locale.US, "%.1f Litre", totalFuelLiters), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Ort. Yakıt Fiyatı", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(String.format(Locale.US, "₺%.2f", fuelPrice), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Yakıt Gideri", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(String.format(Locale.US, "₺%.1f", totalFuelCost), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Diğer Genel Giderler", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(String.format(Locale.US, "₺%.1f", totalGeneralExpenses), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Toplam Giderler:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text(String.format(Locale.US, "₺%.1f", overallExpenses), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        // --- CASH VS CARD RATIO ---
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Ödeme Yöntemi Oranı",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Progress bar representing Cash vs Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            if (cashRatio > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .weight(maxOf(0.01f, cashRatio))
                                        .background(MartiGreen)
                                )
                            }
                            if (cardRatio > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .weight(maxOf(0.01f, cardRatio))
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(MartiGreen))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = String.format(Locale.US, "Nakit: ₺%.1f (%%%.0f)", cashEarnings, cashRatio * 100),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = String.format(Locale.US, "K.Kartı: ₺%.1f (%%%.0f)", cardEarnings, cardRatio * 100),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}
