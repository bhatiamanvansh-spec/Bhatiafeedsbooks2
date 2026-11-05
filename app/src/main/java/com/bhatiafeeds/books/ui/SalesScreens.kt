package com.bhatiafeeds.books.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bhatiafeeds.books.Fmt
import com.bhatiafeeds.books.MainViewModel
import com.bhatiafeeds.books.Share
import com.bhatiafeeds.books.data.DeliveryRow
import com.bhatiafeeds.books.data.Driver
import com.bhatiafeeds.books.data.Sale
import com.bhatiafeeds.books.data.SaleItem
import com.bhatiafeeds.books.data.SaleRow
import com.bhatiafeeds.books.pdf.Pdfs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val BY_DATE = 0
private const val BY_NAME = 1

/** Sorts by date (newest first by default) or by customer name; ties are always newest first. */
fun sortSales(list: List<SaleRow>, key: Int, asc: Boolean): List<SaleRow> {
    val cmp = Comparator<SaleRow> { a, b ->
        if (key == BY_DATE) {
            val d = a.date.compareTo(b.date)
            val dd = if (asc) d else -d
            if (dd != 0) dd else if (asc) a.id.compareTo(b.id) else b.id.compareTo(a.id)
        } else {
            val c = a.customerName.compareTo(b.customerName, ignoreCase = true)
            val cc = if (asc) c else -c
            if (cc != 0) cc else {
                val d = b.date.compareTo(a.date)
                if (d != 0) d else b.id.compareTo(a.id)
            }
        }
    }
    return list.sortedWith(cmp)
}

private fun SaleRow.matches(q: String): Boolean {
    val s = q.trim()
    return doNumber.contains(s, true) || vehicle.contains(s, true) || driverName.contains(s, true) ||
        customerName.contains(s, true) || items.any { it.productName.contains(s, true) }
}

@Composable
fun SaleStatus(row: SaleRow) {
    when {
        row.due < -0.005 ->
            Text("Paid · credit ${Fmt.money(-row.due)}", color = Green, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        row.due <= 0.005 -> Text("Paid", color = Green, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        row.received <= 0.005 ->
            Text("Due ${Fmt.money(row.due)}", color = DueRed, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        else -> Text("Part · due ${Fmt.money(row.due)}", color = DueRed, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

// ---------------------------------------------------------------- list

private const val VIEW_DO = 0
private const val VIEW_ENTRIES = 1

@Composable
fun SalesScreen(vm: MainViewModel, nav: Nav) {
    val sales by vm.sales.collectAsStateWithLifecycle()
    val deliveries by vm.deliveries.collectAsStateWithLifecycle()
    var view by rememberSaveable { mutableStateOf(VIEW_DO) }
    var query by rememberSaveable { mutableStateOf("") }
    var sortKey by rememberSaveable { mutableStateOf(BY_DATE) }
    var asc by rememberSaveable { mutableStateOf(false) }
    var from by rememberSaveable { mutableStateOf<Long?>(null) }
    var to by rememberSaveable { mutableStateOf<Long?>(null) }
    var pickFrom by remember { mutableStateOf(false) }
    var pickTo by remember { mutableStateOf(false) }

    val list = remember(sales, query, sortKey, asc, from, to) {
        val filtered = sales.filter { r ->
            (from == null || r.date >= from!!) && (to == null || r.date <= to!!) &&
                (query.isBlank() || r.matches(query))
        }
        sortSales(filtered, sortKey, asc)
    }
    val doList = remember(deliveries, query, asc, from, to) {
        val q = query.trim()
        val filtered = deliveries.filter { d ->
            (from == null || d.date >= from!!) && (to == null || d.date <= to!!) &&
                (q.isEmpty() || d.doNumber.contains(q, true) || d.orders.any { it.matches(q) })
        }
        if (asc) filtered.sortedWith(compareBy<DeliveryRow>({ it.date }, { it.lastId })) else filtered
    }

    fun tapSort(key: Int) {
        if (sortKey == key) asc = !asc
        else { sortKey = key; asc = key == BY_NAME }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Sales")
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = view == VIEW_DO, onClick = { view = VIEW_DO; sortKey = BY_DATE }, label = { Text("By D.O.") })
            FilterChip(selected = view == VIEW_ENTRIES, onClick = { view = VIEW_ENTRIES }, label = { Text("By customer") })
        }
        SearchField(query, { query = it }, "Search D.O., customer, product, driver",
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = sortKey == BY_DATE, onClick = { tapSort(BY_DATE) },
                label = { Text("Date " + if (sortKey == BY_DATE && asc) "↑" else "↓") }
            )
            if (view == VIEW_ENTRIES) {
                FilterChip(
                    selected = sortKey == BY_NAME, onClick = { tapSort(BY_NAME) },
                    label = { Text("Customer " + if (sortKey == BY_NAME && !asc) "Z–A" else "A–Z") }
                )
            }
            AssistChip(onClick = { pickFrom = true }, label = { Text(from?.let { "From ${Fmt.date(it)}" } ?: "From date") })
            AssistChip(onClick = { pickTo = true }, label = { Text(to?.let { "To ${Fmt.date(it)}" } ?: "To date") })
            if (from != null || to != null) {
                TextButton(onClick = { from = null; to = null }) { Text("Clear dates") }
            }
        }
        Text(
            if (view == VIEW_DO)
                "${doList.size} D.O.s · ${doList.sumOf { it.bags }} bags · ${Fmt.money(doList.sumOf { it.total })}"
            else
                "${list.size} entries · ${list.sumOf { it.bags }} bags · ${Fmt.money(list.sumOf { it.total })}",
            color = Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Box(Modifier.fillMaxSize()) {
            if (view == VIEW_DO) {
                if (doList.isEmpty()) {
                    EmptyState(if (deliveries.isEmpty()) "No D.O.s yet.\nTap + to add your first one." else "Nothing matches.")
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = ListPadding) {
                        items(doList, key = { it.doNumber.lowercase() }) { d ->
                            DoItemRow(d) { nav.push(Screen.DoDetail(d.doNumber)) }
                            ListRowDivider()
                        }
                    }
                }
            } else {
                if (list.isEmpty()) {
                    EmptyState(if (sales.isEmpty()) "No sales yet.\nTap + to add your first entry." else "No entries match.")
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = ListPadding) {
                        items(list, key = { it.id }) { r ->
                            SaleItemRow(r) { nav.push(Screen.SaleDetail(r.id)) }
                            ListRowDivider()
                        }
                    }
                }
            }
            FloatingActionButton(
                onClick = { nav.push(Screen.DoForm()) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            ) { Icon(Icons.Default.Add, contentDescription = "New D.O.") }
        }
    }
    if (pickFrom) DatePickDialog(from ?: vm.today.value, { from = it }) { pickFrom = false }
    if (pickTo) DatePickDialog(to ?: vm.today.value, { to = it }) { pickTo = false }
}

@Composable
fun SaleItemRow(r: SaleRow, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.weight(1f)) {
            Text(r.customerName, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text("${Fmt.date(r.date)} · D.O. ${r.doNumber}", color = Muted, fontSize = 13.sp)
            Text(r.summary { Fmt.money(it) }, color = Muted, fontSize = 13.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Fmt.money(r.total), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            SaleStatus(r)
        }
    }
}

/** One D.O. in the list: number, date, how many customers, bags, total and whether it is paid. */
@Composable
fun DoItemRow(d: DeliveryRow, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text("D.O. ${d.doNumber}", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(
                "${Fmt.date(d.date)} · ${d.orders.size} customer${if (d.orders.size == 1) "" else "s"} · ${d.bags} bags",
                color = Muted, fontSize = 13.sp
            )
            Text(
                d.orders.joinToString(", ") { it.customerName },
                color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Fmt.money(d.total), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            val due = d.dueTotal
            when {
                due <= 0.005 -> Text("Paid", color = Green, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                d.received <= 0.005 ->
                    Text("Due ${Fmt.money(due)}", color = DueRed, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                else -> Text("Part · due ${Fmt.money(due)}", color = DueRed, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** A customer's order inside the D.O. detail screen. */
@Composable
private fun OrderRow(r: SaleRow, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.weight(1f)) {
            Text(r.customerName, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(r.summary { Fmt.money(it) }, color = Muted, fontSize = 13.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Fmt.money(r.total), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            SaleStatus(r)
        }
    }
}

// ---------------------------------------------------------------- D.O. detail

@Composable
fun DoDetailScreen(vm: MainViewModel, nav: Nav, doNumber: String) {
    val deliveries by vm.deliveries.collectAsStateWithLifecycle()
    val customers by vm.customers.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val d = deliveries.firstOrNull { it.doNumber.equals(doNumber.trim(), ignoreCase = true) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("D.O. $doNumber", onBack = nav.pop)
        if (d == null) {
            EmptyState("This D.O. is no longer available.")
            return@Column
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            InfoCard(Modifier.fillMaxWidth()) {
                LabelValue("D.O. number", d.doNumber, bold = true)
                LabelValue("Date", Fmt.date(d.date))
                LabelValue(
                    "Driver",
                    if (d.driverName.isBlank()) "—"
                    else d.driverName + if (d.driverPhone.isNotBlank()) " · ${d.driverPhone}" else ""
                )
                if (d.vehicle.isNotBlank()) LabelValue("Vehicle no.", d.vehicle)
            }
            Spacer(Modifier.height(12.dp))
            InfoCard(Modifier.fillMaxWidth()) {
                Text("Customers on this D.O.", color = Muted, fontSize = 13.sp)
                d.orders.forEach { r -> OrderRow(r) { nav.push(Screen.SaleDetail(r.id)) } }
            }
            Spacer(Modifier.height(12.dp))
            InfoCard(Modifier.fillMaxWidth()) {
                LabelValue("Total bags", d.bags.toString())
                LabelValue("Sales total", Fmt.money(d.total), bold = true)
                LabelValue("Received", Fmt.money(d.received))
                LabelValue(
                    "Still due", if (d.dueTotal <= 0.005) "Nil" else Fmt.money(d.dueTotal),
                    valueColor = if (d.dueTotal > 0.005) DueRed else Green, bold = true
                )
                if (d.cost > 0.005) {
                    LabelValue("Company cost", Fmt.money(d.cost))
                    if (d.costComplete) {
                        LabelValue("Profit", Fmt.money(d.profit), valueColor = if (d.profit >= 0) Green else DueRed, bold = true)
                    } else {
                        LabelValue("Profit", "Add cost on every line to see profit", valueColor = Muted)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { nav.push(Screen.DoForm(d.doNumber)) }, modifier = Modifier.weight(1f)) { Text("Edit D.O.") }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val file = withContext(Dispatchers.IO) { Pdfs.deliverySlip(ctx, vm.prefs, d, customers) }
                            Share.file(ctx, file, "application/pdf")
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Delivery slip") }
            }
            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.padding(top = 8.dp)) {
                Text("Delete whole D.O.", color = DueRed)
            }
        }
    }
    if (confirmDelete && d != null) {
        val ids = d.orders.map { it.id }
        ConfirmDialog(
            "Delete this D.O.?",
            "All ${d.orders.size} customer entries on it move to Trash, balances are adjusted and the company account is reduced. You can undo.",
            "Delete", onConfirm = {
                vm.run { vm.repo.setDeliveryDeleted(ids, true) }
                nav.pop()
                nav.undo("D.O. moved to Trash") { vm.run { vm.repo.setDeliveryDeleted(ids, false) } }
            }, onDismiss = { confirmDelete = false }
        )
    }
}

// ---------------------------------------------------------------- sale detail

@Composable
fun SaleDetailScreen(vm: MainViewModel, nav: Nav, id: Long) {
    val sales by vm.sales.collectAsStateWithLifecycle()
    val customers by vm.customers.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val row = sales.firstOrNull { it.id == id }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Sale", onBack = nav.pop)
        if (row == null) {
            EmptyState("This entry is no longer available.")
            return@Column
        }
        val cust = customers.firstOrNull { it.id == row.customerId }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            InfoCard(Modifier.fillMaxWidth()) {
                LabelValue("Date", Fmt.date(row.date))
                LabelValue("D.O. number", row.doNumber)
                LabelValue("Customer", row.customerName, bold = true)
                LabelValue(
                    "Driver",
                    if (row.driverName.isBlank()) "—"
                    else row.driverName + if (row.driverPhone.isNotBlank()) " · ${row.driverPhone}" else ""
                )
                if (row.vehicle.isNotBlank()) LabelValue("Vehicle no.", row.vehicle)
            }
            Spacer(Modifier.height(12.dp))
            InfoCard(Modifier.fillMaxWidth()) {
                row.items.forEach { i ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(i.productName, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text("${i.bags} bags × ${Fmt.money(i.rate)}", color = Muted, fontSize = 13.sp)
                        }
                        Text(Fmt.money(i.amount), fontSize = 15.sp)
                    }
                }
                if (row.items.size > 1) {
                    LabelValue("Total bags", row.bags.toString())
                }
            }
            Spacer(Modifier.height(12.dp))
            InfoCard(Modifier.fillMaxWidth()) {
                LabelValue("Total", Fmt.money(row.total), bold = true)
                LabelValue(
                    "Received",
                    Fmt.money(row.received) + if (row.received > 0.005 && row.receivedMode.isNotBlank()) " · ${row.receivedMode}" else ""
                )
                if (row.due < -0.005) {
                    LabelValue("Extra paid (credit)", Fmt.money(-row.due), valueColor = Green, bold = true)
                } else {
                    LabelValue(
                        "Due on this entry", if (row.due <= 0.005) "Nil" else Fmt.money(row.due),
                        valueColor = if (row.due > 0.005) DueRed else Green, bold = true
                    )
                }
                if (row.cost > 0.005) {
                    LabelValue("Company cost", Fmt.money(row.cost))
                    if (row.costComplete) {
                        val profit = row.total - row.cost
                        LabelValue("Profit", Fmt.money(profit), valueColor = if (profit >= 0) Green else DueRed, bold = true)
                    }
                }
                if (cust != null) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Customer balance", color = Muted, fontSize = 14.sp, modifier = Modifier.weight(0.4f))
                        Box(Modifier.weight(0.6f)) { BalanceText(cust.balance) }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { nav.push(Screen.DoForm(row.doNumber)) }, modifier = Modifier.weight(1f)) { Text("Edit") }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val file = withContext(Dispatchers.IO) { Pdfs.invoice(ctx, vm.prefs, row, cust) }
                            Share.file(ctx, file, "application/pdf")
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Invoice") }
            }
            TextButton(onClick = { nav.push(Screen.DoDetail(row.doNumber)) }, modifier = Modifier.padding(top = 8.dp)) {
                Text("View whole D.O.")
            }
            TextButton(onClick = { confirmDelete = true }) {
                Text("Delete this customer's entry", color = DueRed)
            }
        }
    }
    if (confirmDelete && row != null) {
        ConfirmDialog(
            "Delete this entry?", "It will move to Trash and the customer's balance will be adjusted. You can restore it any time.",
            "Delete", onConfirm = {
                vm.run { vm.repo.deleteSale(row.id) }
                nav.pop()
                nav.undo("Entry moved to Trash") { vm.run { vm.repo.restoreSale(row.id) } }
            }, onDismiss = { confirmDelete = false }
        )
    }
}

