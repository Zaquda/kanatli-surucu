package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.ProfileEntity
import com.example.data.ShiftEntity
import com.example.data.TripEntity
import com.example.ui.MartiViewModel
import com.example.ui.theme.MartiGold
import com.example.ui.theme.MartiGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun WeeklyReportScreen(
    viewModel: MartiViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val shifts by viewModel.allShifts.collectAsState()
    val trips by viewModel.allTrips.collectAsState()
    val profile by viewModel.profile.collectAsState()

    // Filter shifts for the past 7 days for the chart and calendar
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DAY_OF_YEAR, -6)
    val startOfPastWeek = calendar.timeInMillis

    val pastWeekShifts = remember(shifts) {
        shifts.filter { it.startTime >= startOfPastWeek }
    }

    val completedShifts = remember(shifts) {
        shifts
    }

    // Days of the week helper for chart
    val weekdays = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")
    val earningsByDay = remember(pastWeekShifts, trips) {
        val earnings = DoubleArray(7) { 0.0 }
        val sdf = SimpleDateFormat("u", Locale.US) // 1 = Monday, 7 = Sunday
        
        pastWeekShifts.forEach { shift ->
            val dayIndex = try {
                val dayNum = sdf.format(Date(shift.startTime)).toInt()
                dayNum - 1 // convert 1-7 to 0-6
            } catch (e: Exception) {
                0
            }
            if (dayIndex in 0..6) {
                val shiftTrips = trips.filter { it.shiftId == shift.id }
                earnings[dayIndex] += shiftTrips.sumOf { it.earnings }
            }
        }
        earnings
    }

    // Edit shift dialog states
    var shiftToEdit by remember { mutableStateOf<ShiftEntity?>(null) }
    var editDistanceInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp, top = 8.dp)
    ) {
        // --- WEEKLY EARNINGS BAR CHART ---
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Haftalık Kazanç Grafiği",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    val maxEarnings = earningsByDay.maxOrNull() ?: 1.0
                    val scaleMax = if (maxEarnings == 0.0) 1000.0 else maxEarnings

                    // Bar Chart Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        earningsByDay.forEachIndexed { index, earn ->
                            val percent = (earn / scaleMax).toFloat()
                            val barHeight = maxOf(4, (percent * 120).toInt()).dp

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (earn > 0.0) {
                                    Text(
                                        text = String.format(Locale.US, "₺%.0f", earn),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = MartiGold
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.6f)
                                        .height(barHeight)
                                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                        .background(
                                            if (earn > 0.0) MaterialTheme.colorScheme.primary 
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = weekdays[index],
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- FUEL CONSUMPTION CALENDAR VIEW ---
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Yakıt Tüketim Takvimi",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = Icons.Default.LocalGasStation,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        text = "Son 7 günlük yakıt sarfiyatı",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Calendar columns
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        weekdays.forEach { day ->
                            Text(
                                text = day,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    // Calendar fuel values
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val avgCons = profile?.avgConsumption ?: 7.0
                        
                        earningsByDay.forEachIndexed { index, _ ->
                            // Calculate fuel for that specific weekday in past 7 days
                            val sdf = SimpleDateFormat("u", Locale.US)
                            val dayShifts = pastWeekShifts.filter { shift ->
                                try {
                                    val dayNum = sdf.format(Date(shift.startTime)).toInt()
                                    (dayNum - 1) == index
                                } catch (e: Exception) {
                                    false
                                }
                            }
                            val dayKm = dayShifts.sumOf { it.totalGpsKm }
                            val dayFuel = (dayKm * avgCons) / 100.0

                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (dayFuel > 0.0) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                                            .padding(vertical = 6.dp, horizontal = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = String.format(Locale.US, "%.1fL", dayFuel),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.error,
                                                fontSize = 9.sp
                                            )
                                        }
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("-", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- EXPORTS ROW ---
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                exportToExcel(context, shifts, trips, profile)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("excel_export_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MartiGreen)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Excel (CSV)", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                exportToPdf(context, shifts, trips, profile)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pdf_export_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("PDF Raporu", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // --- HISTORICAL LOG TITLE ---
        item {
            Text(
                text = "Haftalık Seyir Defteri (Geçmiş Vardiyalar)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // Empty logs fallback
        if (completedShifts.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.EventNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Kayıtlı Seyir Bulunmuyor",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Vardiyayı bitirdiğinizde geçmiş vardiya verileriniz burada listelenecektir.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }

        items(completedShifts) { shift ->
            val shiftTrips = trips.filter { it.shiftId == shift.id }
            val totalEarn = shiftTrips.sumOf { it.earnings }
            val shiftDurationSec = if (shift.endTime != null) {
                (shift.endTime - shift.startTime - shift.accumulatedPauseMillis) / 1000L
            } else {
                (System.currentTimeMillis() - shift.startTime - shift.accumulatedPauseMillis) / 1000L
            }

            val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            val dateStr = sdf.format(Date(shift.startTime))

            // Fuel and other expense calculations for this shift
            val avgCons = profile?.avgConsumption ?: 7.0
            val fuelPrice = profile?.fuelPrice ?: 63.2
            val fuelUsed = (shift.totalGpsKm * avgCons) / 100.0
            val fuelCost = fuelUsed * fuelPrice
            val otherExpenses = shift.generalExpenses
            val netEarnings = totalEarn - fuelCost - otherExpenses

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = dateStr, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        }

                        Row {
                            IconButton(onClick = {
                                shiftToEdit = shift
                                editDistanceInput = shift.totalGpsKm.toString()
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Düzenle", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { viewModel.deleteHistoricalShift(shift.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Sil", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Mesafe: ${String.format(Locale.US, "%.2f km", shift.totalGpsKm)}", style = MaterialTheme.typography.bodySmall)
                            Text("Yolculuk: ${shiftTrips.size} adet", style = MaterialTheme.typography.bodySmall)
                            Text("Süre: ${formatDuration(shiftDurationSec)}", style = MaterialTheme.typography.bodySmall)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Kazanç: ₺${String.format(Locale.US, "%.1f", totalEarn)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MartiGold)
                            Text("Yakıt: -₺${String.format(Locale.US, "%.1f", fuelCost)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            if (otherExpenses > 0.0) {
                                Text("Gider: -₺${String.format(Locale.US, "%.1f", otherExpenses)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                            Text("Net Kar: ₺${String.format(Locale.US, "%.1f", netEarnings)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MartiGreen)
                        }
                    }

                    if (shift.generalExpensesDescription.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Gider Açıklaması: ${shift.generalExpensesDescription}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }

    // --- SHIFT KM EDIT DIALOG ---
    if (shiftToEdit != null) {
        val currentShift = shiftToEdit!!
        AlertDialog(
            onDismissRequest = { shiftToEdit = null },
            title = { Text("Seyir Kilometresi Düzenle", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Lütfen bu vardiyada kaydedilen toplam kilometreyi manuel olarak girin.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editDistanceInput,
                        onValueChange = { editDistanceInput = it },
                        label = { Text("Mesafe (km)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newKm = editDistanceInput.replace(',', '.').toDoubleOrNull() ?: currentShift.totalGpsKm
                        viewModel.updateHistoricalShift(currentShift.copy(totalGpsKm = newKm))
                        shiftToEdit = null
                    }
                ) {
                    Text("Kaydet")
                }
            },
            dismissButton = {
                TextButton(onClick = { shiftToEdit = null }) {
                    Text("İptal")
                }
            }
        )
    }
}

suspend fun exportToExcel(
    context: Context,
    shifts: List<ShiftEntity>,
    trips: List<TripEntity>,
    profile: ProfileEntity?
) {
    withContext(Dispatchers.IO) {
        try {
            val file = File(context.cacheDir, "Kanatli_Surucu_Seyir_Raporu.csv")
            val outputStream = FileOutputStream(file)
            val writer = outputStream.bufferedWriter(Charsets.UTF_8)
            
            // Write BOM for Excel UTF-8 compatibility
            outputStream.write(0xEF)
            outputStream.write(0xBB)
            outputStream.write(0xBF)

            // Header for Shifts
            writer.write("VARDİYA RAPORU\n")
            writer.write("Vardiya ID;Baslangic;Bitis;Duraklama Suresi (dk);Toplam Yol (km);Toplam Kazanc;Yakıt Gideri;Genel Gider;Net Kar\n")

            val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
            val avgCons = profile?.avgConsumption ?: 7.0
            val fuelPrice = profile?.fuelPrice ?: 63.2

            shifts.forEach { shift ->
                val startTimeStr = sdf.format(Date(shift.startTime))
                val endTimeStr = if (shift.endTime != null) sdf.format(Date(shift.endTime)) else "-"
                val pauseMin = shift.accumulatedPauseMillis / 60000.0
                val totalKm = shift.totalGpsKm
                val totalEarn = trips.filter { it.shiftId == shift.id }.sumOf { it.earnings }
                val fuelCost = (totalKm * avgCons / 100.0) * fuelPrice
                val otherExpenses = shift.generalExpenses
                val netKar = totalEarn - fuelCost - otherExpenses

                writer.write(
                    String.format(
                        Locale.US,
                        "%d;%s;%s;%.1f;%.2f;%.2f;%.2f;%.2f;%.2f\n",
                        shift.id, startTimeStr, endTimeStr, pauseMin, totalKm, totalEarn, fuelCost, otherExpenses, netKar
                    )
                )
            }

            writer.write("\n\nYOLCULUK DETAYLARI\n")
            writer.write("Yolculuk ID;Vardiya ID;Mesafe (km);Kazanc;Odeme Tipi;Tarih\n")

            trips.forEach { trip ->
                val tripTimeStr = sdf.format(Date(trip.timestamp))
                writer.write(
                    String.format(
                        Locale.US,
                        "%d;%d;%.2f;%.2f;%s;%s\n",
                        trip.id, trip.shiftId, trip.distanceKm, trip.earnings, trip.paymentMethod, tripTimeStr
                    )
                )
            }

            writer.flush()
            writer.close()

            withContext(Dispatchers.Main) {
                shareFile(context, file, "text/csv")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Hata: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

suspend fun exportToPdf(
    context: Context,
    shifts: List<ShiftEntity>,
    trips: List<TripEntity>,
    profile: ProfileEntity?
) {
    withContext(Dispatchers.IO) {
        try {
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            val paint = Paint()
            val textPaint = Paint().apply {
                isAntiAlias = true
                textSize = 12f
            }

            var y = 40f

            // Title
            textPaint.textSize = 18f
            textPaint.isFakeBoldText = true
            canvas.drawText("KANATLI SÜRÜCÜ ASİSTANI - SEYİR RAPORU", 40f, y, textPaint)
            y += 30f

            // Profile info
            textPaint.textSize = 12f
            textPaint.isFakeBoldText = false
            val name = profile?.driverName ?: "Kanatlı Sürücü"
            val car = profile?.carModel ?: "Belirtilmedi"
            val fuel = profile?.fuelType ?: "Belirtilmedi"
            val price = profile?.fuelPrice ?: 0.0
            val cons = profile?.avgConsumption ?: 7.0

            canvas.drawText("Sürücü: $name", 40f, y, textPaint)
            y += 18f
            canvas.drawText("Araç: $car | Yakıt: $fuel (₺$price/L)", 40f, y, textPaint)
            y += 18f
            canvas.drawText("Ortalama Tüketim: $cons L/100km", 40f, y, textPaint)
            y += 30f

            // Draw a separator line
            paint.strokeWidth = 1f
            paint.color = android.graphics.Color.GRAY
            canvas.drawLine(40f, y, 555f, y, paint)
            y += 25f

            // Section 1: Summary
            textPaint.textSize = 14f
            textPaint.isFakeBoldText = true
            canvas.drawText("Genel Özet", 40f, y, textPaint)
            y += 20f

            val totalShifts = shifts.size
            val totalDistance = shifts.sumOf { it.totalGpsKm }
            val totalEarnings = trips.sumOf { it.earnings }
            val totalFuelUsed = (totalDistance * cons) / 100.0
            val totalFuelCost = totalFuelUsed * price
            val totalGeneralExpenses = shifts.sumOf { it.generalExpenses }
            val netEarnings = totalEarnings - totalFuelCost - totalGeneralExpenses

            textPaint.textSize = 11f
            textPaint.isFakeBoldText = false
            canvas.drawText("Toplam Vardiya Sayısı: $totalShifts", 50f, y, textPaint)
            y += 16f
            canvas.drawText(String.format(Locale.US, "Toplam Kat Edilen Mesafe: %.2f km", totalDistance), 50f, y, textPaint)
            y += 16f
            canvas.drawText(String.format(Locale.US, "Toplam Brüt Kazanç: ₺%.2f", totalEarnings), 50f, y, textPaint)
            y += 16f
            canvas.drawText(String.format(Locale.US, "Tahmini Yakıt Gideri: ₺%.2f (%.2f L)", totalFuelCost, totalFuelUsed), 50f, y, textPaint)
            y += 16f
            canvas.drawText(String.format(Locale.US, "Diğer Genel Giderler: ₺%.2f", totalGeneralExpenses), 50f, y, textPaint)
            y += 16f
            
            // Use different color/style for Net Profit
            textPaint.isFakeBoldText = true
            canvas.drawText(String.format(Locale.US, "Net Kar: ₺%.2f", netEarnings), 50f, y, textPaint)
            y += 30f

            canvas.drawLine(40f, y, 555f, y, paint)
            y += 25f

            // Section 2: Shift Logs
            textPaint.textSize = 14f
            textPaint.isFakeBoldText = true
            canvas.drawText("Vardiya Geçmişi (Son Tamamlananlar)", 40f, y, textPaint)
            y += 20f

            val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

            textPaint.textSize = 10f
            textPaint.isFakeBoldText = true
            canvas.drawText("Tarih / Saat", 40f, y, textPaint)
            canvas.drawText("Mesafe", 170f, y, textPaint)
            canvas.drawText("Yolculuk", 240f, y, textPaint)
            canvas.drawText("Kazanç", 300f, y, textPaint)
            canvas.drawText("Yakıt", 370f, y, textPaint)
            canvas.drawText("Gider", 430f, y, textPaint)
            canvas.drawText("Net Kar", 490f, y, textPaint)
            y += 15f

            textPaint.isFakeBoldText = false
            val pdfShifts = shifts.take(15)

            pdfShifts.forEach { shift ->
                val dateStr = sdf.format(Date(shift.startTime))
                val shiftTrips = trips.filter { it.shiftId == shift.id }
                val earn = shiftTrips.sumOf { it.earnings }
                val km = shift.totalGpsKm
                val fuelCostForShift = (km * cons / 100.0) * price
                val otherExpenses = shift.generalExpenses
                val netKarForShift = earn - fuelCostForShift - otherExpenses

                canvas.drawText(dateStr, 40f, y, textPaint)
                canvas.drawText(String.format(Locale.US, "%.1f km", km), 170f, y, textPaint)
                canvas.drawText("${shiftTrips.size} ad", 240f, y, textPaint)
                canvas.drawText(String.format(Locale.US, "₺%.0f", earn), 300f, y, textPaint)
                canvas.drawText(String.format(Locale.US, "₺%.0f", fuelCostForShift), 370f, y, textPaint)
                canvas.drawText(String.format(Locale.US, "₺%.0f", otherExpenses), 430f, y, textPaint)
                canvas.drawText(String.format(Locale.US, "₺%.0f", netKarForShift), 490f, y, textPaint)
                y += 18f
            }

            pdfDocument.finishPage(page)

            val file = File(context.cacheDir, "Kanatli_Surucu_Haftalik_Rapor.pdf")
            val outputStream = FileOutputStream(file)
            pdfDocument.writeTo(outputStream)
            pdfDocument.close()
            outputStream.flush()
            outputStream.close()

            withContext(Dispatchers.Main) {
                shareFile(context, file, "application/pdf")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Hata: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

fun shareFile(context: Context, file: File, mimeType: String) {
    try {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "com.aistudio.martitagasistan.xwqzpt.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Raporu Paylaş"))
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Dosya paylaşılamadı: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

