package com.bhatiafeeds.books.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bhatiafeeds.books.Fmt
import com.bhatiafeeds.books.MainViewModel
import java.time.LocalDate

@Composable
fun HomeScreen(vm: MainViewModel, nav: Nav) {
    val customers by vm.customers.collectAsStateWithLifecycle()
    val sales by vm.sales.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val company by vm.company.collectAsStateWithLifecycle()
    val companyName by vm.companyName.collectAsStateWithLifecycle()

    val owing = remember(customers) { customers.filter { it.balance > 0.005 }.sortedByDescending { it.balance } }
    val totalDue = owing.sumOf { it.balance }
    val monthStart = LocalDate.ofEpochDay(today).withDayOfMonth(1).toEpochDay()
    val todaySales = remember(sales, today) { sales.filter { it.date == today } }
    val monthSales = remember(sales, monthStart, today) { sales.filter { it.date in monthStart..today } }
    val reminderDays = vm.prefs.reminderDays
    val lastBackup = vm.prefs.lastBackupDay
    val backupStale = lastBackup < 0 || today - lastBackup >= 7

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Bhatia Feeds Books")
        LazyColumn(Modifier.fillMaxSize(), contentPadding = ListPadding) {
            item {
                InfoCard(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth()) {
                    Text("Total outstanding", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        Fmt.money(totalDue), fontSize = 32.sp, fontWeight = FontWeight.SemiBold,
                        color = if (totalDue > 0.005) DueRed else Green
                    )
                    Text(
                        if (owing.isEmpty()) "No pending dues" else "${owing.size} customer${if (owing.size == 1) "" else "s"} owe you",
                        color = Muted, fontSize = 13.sp
                    )
                    val creditHeld = customers.filter { it.balance < -0.005 }.sumOf { -it.balance }
                    if (creditHeld > 0.005) {
                        Text("Customer credit held: ${Fmt.money(creditHeld)}", color = Green, fontSize = 13.sp)
                    }
                }
            }
            if (company.lines.isNotEmpty() || company.opening != 0.0) {
                item {
                    InfoCard(
                        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp).fillMaxWidth()
                            .clickable { nav.push(Screen.Company) }
                    ) {
                        Text(
                            if (company.balance < -0.005) "Advance paid to $companyName" else "You owe $companyName",
                            color = Muted, fontSize = 13.sp
                        )
                        CompanyBalanceText(company.balance, fontSize = 20)
                    }
                }
            }
            item {
                Row(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        "Today", Fmt.money(todaySales.sumOf { it.total }),
                        "${todaySales.sumOf { it.bags }} bags", Modifier.weight(1f)
                    )
                    StatCard(
                        "This month", Fmt.money(monthSales.sumOf { it.total }),
                        "${monthSales.sumOf { it.bags }} bags", Modifier.weight(1f)
                    )
                }
            }
            item {
                Row(
                    Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(onClick = { nav.push(Screen.DoForm()) }, modifier = Modifier.weight(1f)) {
                        Text("+ New D.O.")
                    }
                    FilledTonalButton(onClick = { nav.push(Screen.PaymentForm()) }, modifier = Modifier.weight(1f)) {
                        Text("+ Payment")
                    }
                }
            }
            if (backupStale) {
                item {
                    InfoCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                        Text(
                            if (lastBackup < 0) "You haven't saved a backup yet"
                            else "Last backup was ${Fmt.daysAgo(today, lastBackup)}",
                            fontWeight = FontWeight.Medium
                        )
                        Text("Save a copy to Google Drive so your books are never lost.", color = Muted, fontSize = 13.sp)
                        TextButton(onClick = { nav.push(Screen.Data) }) { Text("Back up now") }
                    }
                }
            }
            if (owing.isNotEmpty()) {
                item { SectionTitle("Top dues") }
                items(owing.take(6), key = { it.id }) { c ->
                    val last = c.lastActivity ?: 0L
                    val overdue = last <= 0 || today - last >= reminderDays
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { nav.push(Screen.CustomerDetail(c.id)) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(c.name, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text(
                                if (last > 0) "Last activity ${Fmt.daysAgo(today, last)}" else "No activity yet",
                                color = if (overdue) DueRed else Muted, fontSize = 13.sp
                            )
                        }
                        BalanceText(c.balance, fontSize = 16)
                    }
                    ListRowDivider()
                }
            }
        }
    }
}
