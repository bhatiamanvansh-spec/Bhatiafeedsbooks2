package com.bhatiafeeds.books.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bhatiafeeds.books.Backup
import com.bhatiafeeds.books.Csv
import com.bhatiafeeds.books.Fmt
import com.bhatiafeeds.books.MainViewModel
import com.bhatiafeeds.books.Share
import com.bhatiafeeds.books.data.Driver
import com.bhatiafeeds.books.data.PAYMENT_MODES
import com.bhatiafeeds.books.data.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

// ---------------------------------------------------------------- More menu

@Composable
fun MoreScreen(nav: Nav) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("More")
        MenuRow("Company account", "What you owe the company, your payments to it, profit") { nav.push(Screen.Company) }
        MenuRow("Products", "Names you pick from when making a sale") { nav.push(Screen.Products) }
        MenuRow("Drivers", "Driver names, phones and vehicle numbers") { nav.push(Screen.Drivers) }
        MenuRow("Monthly reports", "Sales by product and by customer") { nav.push(Screen.Reports) }
        MenuRow("Backup & export", "Google Drive backup, restore, Excel/CSV files") { nav.push(Screen.Data) }
        MenuRow("Trash", "Deleted entries you can restore") { nav.push(Screen.Trash) }
        MenuRow("Settings", "Business details for invoices, payment reminders") { nav.push(Screen.Settings) }
    }
}

@Composable
private fun MenuRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(subtitle, fontSize = 13.sp, color = Muted)
            }
            Text("›", fontSize = 22.sp, color = Muted)
        }
        ListRowDivider()
    }
}

// ---------------------------------------------------------------- Products

@Composable
fun ProductsScreen(vm: MainViewModel, nav: Nav) {
    val products by vm.products.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }

    fun add() {
        if (name.isBlank()) return
        val n = name
        scope.launch {
            if (vm.repo.addProduct(n)) { name = ""; nav.message("Product added") }
            else nav.message("\"${n.trim()}\" already exists")
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Products", onBack = nav.pop)
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppField("New product name", name, { name = it }, modifier = Modifier.weight(1f),
                capitalization = KeyboardCapitalization.Words)
            Button(onClick = { add() }, modifier = Modifier.padding(top = 8.dp).height(48.dp)) { Text("Add") }
        }
        if (products.isEmpty()) {
            EmptyState("No products yet.\nAdd the feed names you sell.")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(products, key = { it.id }) { p ->
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(p.name, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(vertical = 14.dp))
                        IconButton(onClick = {
                            vm.run { vm.repo.deleteProduct(p.id) }
                            nav.undo("\"${p.name}\" removed") { vm.run { vm.repo.addProduct(p.name) } }
                        }) { Icon(Icons.Default.Close, contentDescription = "Remove ${p.name}") }
                    }
                    ListRowDivider()
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Drivers

@Composable
fun DriversScreen(vm: MainViewModel, nav: Nav) {
    val drivers by vm.drivers.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Driver?>(null) }
    var adding by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Drivers", onBack = nav.pop) {
            TextButton(onClick = { adding = true }) { Text("+ Add") }
        }
        if (drivers.isEmpty()) {
            EmptyState("No drivers yet.\nTap + Add to save a driver once and pick them on any sale.")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(drivers, key = { it.id }) { d ->
                    Row(
                        Modifier.fillMaxWidth().clickable { editing = d }.padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                            Text(d.name, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            val sub = listOf(d.phone, d.vehicleNo).filter { it.isNotBlank() }.joinToString(" · ")
                            if (sub.isNotEmpty()) Text(sub, color = Muted, fontSize = 13.sp)
                        }
                        IconButton(onClick = {
                            vm.run { vm.repo.deleteDriver(d.id) }
                            nav.undo("\"${d.name}\" removed") { vm.run { vm.repo.restoreDriver(d.id) } }
                        }) { Icon(Icons.Default.Close, contentDescription = "Remove ${d.name}") }
                    }
                    ListRowDivider()
                }
            }
        }
    }
    if (adding) {
        DriverDialog(null, onSave = { d -> vm.run { vm.repo.saveDriver(d) }; nav.message("Driver added") },
            onDismiss = { adding = false })
    }
    editing?.let { d ->
        DriverDialog(d, onSave = { saved -> vm.run { vm.repo.saveDriver(saved) }; nav.message("Changes saved") },
            onDismiss = { editing = null })
    }
}

// ---------------------------------------------------------------- Reports

private class ReportRow(val label: String, val bags: Int, val amount: Double)

@Composable
fun ReportsScreen(vm: MainViewModel, nav: Nav) {
    val sales by vm.sales.collectAsStateWithLifecycle()
    val entries by vm.paymentEntries.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    var monthStart by remember { mutableStateOf(LocalDate.ofEpochDay(today).withDayOfMonth(1)) }
    var byCustomer by remember { mutableStateOf(false) }

    val from = monthStart.toEpochDay()
    val to = monthStart.plusMonths(1).minusDays(1).toEpochDay()
    val monthSales = remember(sales, from, to) { sales.filter { it.date in from..to } }
    val monthEntries = remember(entries, from, to) { entries.filter { it.date in from..to } }
    val costedLines = remember(monthSales) { monthSales.flatMap { it.items }.filter { it.costRate > 0.0 } }
    val rows = remember(monthSales, byCustomer) {
        val result: List<ReportRow> = if (byCustomer) {
            monthSales.groupBy { it.customerName.trim().lowercase() }.values.map { g ->
                ReportRow(g.first().customerName, g.sumOf { it.bags }, g.sumOf { it.total })
            }
        } else {
            monthSales.flatMap { it.items }.groupBy { it.productName.trim().lowercase() }.values.map { g ->
                ReportRow(g.first().productName, g.sumOf { it.bags }, g.sumOf { it.amount })
            }
        }
        result.sortedByDescending { it.amount }
    }
    val totalAmount = monthSales.sumOf { it.total }
    val collected = monthEntries.sumOf { it.amount }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Monthly report", onBack = nav.pop)
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { monthStart = monthStart.minusMonths(1) }) { Text("‹ Prev") }
            Text(
                Fmt.monthTitle(from), fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            TextButton(onClick = { monthStart = monthStart.plusMonths(1) }) { Text("Next ›") }
        }
        InfoCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
            LabelValue("Sales", Fmt.money(totalAmount), bold = true)
            LabelValue("Bags sold", monthSales.sumOf { it.bags }.toString())
            LabelValue("Entries", monthSales.size.toString())
            LabelValue("Money collected", Fmt.money(collected), valueColor = Green)
            (PAYMENT_MODES + "").forEach { m ->
                val sum = monthEntries.filter { it.mode == m }.sumOf { it.amount }
                if (sum > 0.005) LabelValue(if (m.isEmpty()) "  Mode not recorded" else "  $m", Fmt.money(sum))
            }
            if (costedLines.isNotEmpty()) {
                val profit = costedLines.sumOf { it.bags * (it.rate - it.costRate) }
                LabelValue("Profit (lines with cost)", Fmt.money(profit), valueColor = if (profit >= 0) Green else DueRed, bold = true)
            }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !byCustomer, onClick = { byCustomer = false }, label = { Text("By product") })
            FilterChip(selected = byCustomer, onClick = { byCustomer = true }, label = { Text("By customer") })
        }
        if (rows.isEmpty()) {
            EmptyState("No sales in this month.")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(rows) { r ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text("${r.bags} bags", color = Muted, fontSize = 13.sp)
                        }
                        Text(Fmt.money(r.amount), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                    ListRowDivider()
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Trash

@Composable
fun TrashScreen(vm: MainViewModel, nav: Nav) {
    val sales by vm.trashedSales.collectAsStateWithLifecycle()
    val payments by vm.trashedPayments.collectAsStateWithLifecycle()
    val customers by vm.deletedCustomers.collectAsStateWithLifecycle()
    val drivers by vm.deletedDrivers.collectAsStateWithLifecycle()
    val companyPays by vm.trashedCompanyPayments.collectAsStateWithLifecycle()
    val empty = sales.isEmpty() && payments.isEmpty() && customers.isEmpty() && drivers.isEmpty() && companyPays.isEmpty()

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Trash", onBack = nav.pop)
        if (empty) {
            EmptyState("Trash is empty.")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                if (sales.isNotEmpty()) item { SectionTitle("Sales") }
                items(sales, key = { "s${it.id}" }) { r ->
                    TrashRow(
                        "${r.customerName} · ${Fmt.money(r.total)}",
                        "${Fmt.date(r.date)} · D.O. ${r.doNumber} · " + r.items.joinToString(", ") { it.productName }
                    ) { vm.run { vm.repo.restoreSale(r.id) }; nav.message("Sale restored") }
                }
                if (payments.isNotEmpty()) item { SectionTitle("Payments") }
                items(payments, key = { "p${it.id}" }) { p ->
                    TrashRow(
                        "${p.customerName} · ${Fmt.money(p.amount)}", Fmt.date(p.date)
                    ) { vm.run { vm.repo.restorePayment(p.id) }; nav.message("Payment restored") }
                }
                if (companyPays.isNotEmpty()) item { SectionTitle("Payments to company") }
                items(companyPays, key = { "cp${it.id}" }) { p ->
                    TrashRow(
                        "${Fmt.money(p.amount)} · ${Fmt.date(p.date)}", p.mode
                    ) { vm.run { vm.repo.restoreCompanyPayment(p.id) }; nav.message("Payment restored") }
                }
                if (customers.isNotEmpty()) item { SectionTitle("Customers") }
                items(customers, key = { "c${it.id}" }) { c ->
                    TrashRow(c.name, c.phone) { vm.run { vm.repo.restoreCustomer(c.id) }; nav.message("Customer restored") }
                }
                if (drivers.isNotEmpty()) item { SectionTitle("Drivers") }
                items(drivers, key = { "d${it.id}" }) { d ->
                    TrashRow(d.name, d.phone) { vm.run { vm.repo.restoreDriver(d.id) }; nav.message("Driver restored") }
                }
            }
        }
    }
}

@Composable
private fun TrashRow(title: String, subtitle: String, onRestore: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                if (subtitle.isNotBlank()) Text(subtitle, color = Muted, fontSize = 13.sp)
            }
            TextButton(onClick = onRestore) { Text("Restore") }
        }
        ListRowDivider()
    }
}

// ---------------------------------------------------------------- Backup & export

@Composable
fun DataScreen(vm: MainViewModel, nav: Nav) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val today by vm.today.collectAsStateWithLifecycle()
    var lastBackup by remember { mutableStateOf(vm.prefs.lastBackupDay) }
    var pendingRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    var busy by remember { mutableStateOf(false) }

    val customers by vm.customers.collectAsStateWithLifecycle()
    val sales by vm.sales.collectAsStateWithLifecycle()
    val payments by vm.paymentEntries.collectAsStateWithLifecycle()
    val company by vm.company.collectAsStateWithLifecycle()

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                val ok = withContext(Dispatchers.IO) { Backup.write(ctx, uri) }
                busy = false
                if (ok) {
                    vm.prefs.lastBackupDay = Repository.today()
                    lastBackup = vm.prefs.lastBackupDay
                    nav.message("Backup saved")
                } else nav.message("Backup failed. Please try again.")
            }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestore = uri
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Backup & export", onBack = nav.pop)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            InfoCard(Modifier.fillMaxWidth()) {
                Text("Backup", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (lastBackup < 0) "No backup saved yet" else "Last backup: ${Fmt.date(lastBackup)} (${Fmt.daysAgo(today, lastBackup)})",
                    color = if (lastBackup < 0 || today - lastBackup >= 7) DueRed else Muted, fontSize = 14.sp
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { backupLauncher.launch(Backup.fileName()) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Save backup to Google Drive")
                }
                Text(
                    "In the screen that opens, choose Drive (or any folder) and tap Save.",
                    fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 6.dp)
                )
                OutlinedButton(
                    onClick = { restoreLauncher.launch(arrayOf("*/*")) }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) { Text("Restore from a backup") }
            }
            Spacer(Modifier.height(12.dp))
            InfoCard(Modifier.fillMaxWidth()) {
                Text("Automatic backup", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(
                    "Android also backs up this app's data to your Google account by itself, about once a day, " +
                        "when the phone is idle on Wi-Fi. Make sure Settings › Google › Backup is switched on. " +
                        "This restores automatically when you set up a new phone with the same Google account.",
                    fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            InfoCard(Modifier.fillMaxWidth()) {
                Text("Export to Excel (CSV)", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text("Opens in Excel, Google Sheets or any spreadsheet app.", fontSize = 13.sp, color = Muted)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val f = withContext(Dispatchers.IO) { Csv.sales(ctx, sales) }
                            Share.file(ctx, f, "text/csv")
                        }
                    }, modifier = Modifier.fillMaxWidth()
                ) { Text("Sales") }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val f = withContext(Dispatchers.IO) { Csv.payments(ctx, payments) }
                            Share.file(ctx, f, "text/csv")
                        }
                    }, modifier = Modifier.fillMaxWidth()
                ) { Text("Payments") }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val f = withContext(Dispatchers.IO) { Csv.customers(ctx, customers) }
                            Share.file(ctx, f, "text/csv")
                        }
                    }, modifier = Modifier.fillMaxWidth()
                ) { Text("Customers & balances") }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val f = withContext(Dispatchers.IO) { Csv.company(ctx, company) }
                            Share.file(ctx, f, "text/csv")
                        }
                    }, modifier = Modifier.fillMaxWidth()
                ) { Text("Company account") }
            }
        }
    }

    pendingRestore?.let { uri ->
        ConfirmDialog(
            "Restore this backup?",
            "This replaces ALL current data in the app with the contents of the backup file. " +
                "Entries made after that backup was saved will be lost. The app will restart.",
            "Restore", onConfirm = {
                scope.launch {
                    val error = withContext(Dispatchers.IO) { Backup.restore(ctx, uri) }
                    if (error == null) Backup.restartApp(ctx) else nav.message(error)
                }
            }, onDismiss = { pendingRestore = null }
        )
    }
}

// ---------------------------------------------------------------- Settings

@Composable
fun SettingsScreen(vm: MainViewModel, nav: Nav) {
    val prefs = vm.prefs
    var name by remember { mutableStateOf(prefs.businessName) }
    var address by remember { mutableStateOf(prefs.businessAddress) }
    var phone by remember { mutableStateOf(prefs.businessPhone) }
    var bank by remember { mutableStateOf(prefs.bankDetails) }
    var companyName by remember { mutableStateOf(vm.companyName.value.takeIf { it != "Company" } ?: "") }
    var companyOpening by remember { mutableStateOf(Fmt.plain(vm.companyOpening.value)) }
    var reminders by remember { mutableStateOf(prefs.remindersOn) }
    var days by remember { mutableIntStateOf(prefs.reminderDays) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Settings", onBack = nav.pop)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Shown on invoices and ledgers", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
            AppField("Business name", name, { name = it; prefs.businessName = it }, capitalization = KeyboardCapitalization.Words)
            AppField("Address", address, { address = it; prefs.businessAddress = it },
                capitalization = KeyboardCapitalization.Words, singleLine = false)
            AppField("Phone number(s)", phone, { s ->
                if (s.length <= 40 && s.all { it.isDigit() || it in "+ ,/-" }) { phone = s; prefs.businessPhone = s }
            }, keyboardType = KeyboardType.Phone)
            Text("Separate two numbers with a comma, e.g. 8319721001, 9893397595", fontSize = 12.sp, color = Muted)
            AppField("Bank details (shown at bottom of invoice, up to 4 lines)", bank, { bank = it; prefs.bankDetails = it },
                capitalization = KeyboardCapitalization.Words, singleLine = false)

            SectionTitle("Company account", Modifier.padding(start = 0.dp, end = 0.dp, top = 24.dp))
            Text("The company you buy feed from. Used for the account of what you owe them.", fontSize = 12.sp, color = Muted)
            AppField("Company name", companyName, { companyName = it }, capitalization = KeyboardCapitalization.Words)
            MoneyField("Amount you already owed them before using this app", companyOpening, { companyOpening = it })
            OutlinedButton(
                onClick = {
                    val n = companyName.trim()
                    val o = Fmt.parse(companyOpening)
                    vm.run {
                        vm.repo.putSetting("companyName", n)
                        vm.repo.putSetting("companyOpening", o.toString())
                    }
                    nav.message("Company details saved")
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text("Save company details") }

            SectionTitle("Payment reminders", Modifier.padding(start = 0.dp, end = 0.dp, top = 24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Remind me about old dues", fontSize = 16.sp)
                    Text("A daily notification at 10 AM when customers owe money and have had no activity for a while.",
                        fontSize = 13.sp, color = Muted)
                }
                Switch(checked = reminders, onCheckedChange = { on ->
                    reminders = on; prefs.remindersOn = on
                    if (on && Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                })
            }
            if (reminders) {
                Text("Treat a due as old after", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7, 15, 30, 45, 60, 90).forEach { d ->
                        FilterChip(selected = days == d, onClick = { days = d; prefs.reminderDays = d }, label = { Text("$d days") })
                    }
                }
            }
            SectionTitle("About", Modifier.padding(start = 0.dp, end = 0.dp, top = 24.dp))
            Text("Bhatia Feeds Books · version 2.0", color = Muted, fontSize = 14.sp)
            Text("All data is stored on this phone and works without internet.", color = Muted, fontSize = 13.sp)
        }
    }
}
