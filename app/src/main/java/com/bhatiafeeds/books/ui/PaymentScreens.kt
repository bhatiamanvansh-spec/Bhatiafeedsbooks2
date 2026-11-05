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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bhatiafeeds.books.Fmt
import com.bhatiafeeds.books.MainViewModel
import com.bhatiafeeds.books.data.PAYMENT_MODES
import com.bhatiafeeds.books.data.Payment
import com.bhatiafeeds.books.data.PaymentEntry
import kotlinx.coroutines.launch

private const val BY_DATE = 0
private const val BY_NAME = 1

private fun sortPayments(list: List<PaymentEntry>, key: Int, asc: Boolean): List<PaymentEntry> {
    val cmp = Comparator<PaymentEntry> { a, b ->
        if (key == BY_DATE) {
            val d = a.date.compareTo(b.date)
            val dd = if (asc) d else -d
            if (dd != 0) dd else if (asc) a.key.compareTo(b.key) else b.key.compareTo(a.key)
        } else {
            val c = a.customerName.compareTo(b.customerName, ignoreCase = true)
            val cc = if (asc) c else -c
            if (cc != 0) cc else {
                val d = b.date.compareTo(a.date)
                if (d != 0) d else b.key.compareTo(a.key)
            }
        }
    }
    return list.sortedWith(cmp)
}

@Composable
fun PaymentsScreen(vm: MainViewModel, nav: Nav) {
    val payments by vm.paymentEntries.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var sortKey by rememberSaveable { mutableStateOf(BY_DATE) }
    var asc by rememberSaveable { mutableStateOf(false) }
    var from by rememberSaveable { mutableStateOf<Long?>(null) }
    var to by rememberSaveable { mutableStateOf<Long?>(null) }
    var pickFrom by remember { mutableStateOf(false) }
    var pickTo by remember { mutableStateOf(false) }
    var modeFilter by rememberSaveable { mutableStateOf("") }

    val list = remember(payments, query, sortKey, asc, from, to, modeFilter) {
        val filtered = payments.filter { p ->
            (modeFilter.isEmpty() || p.mode == modeFilter) &&
                (from == null || p.date >= from!!) && (to == null || p.date <= to!!) &&
                (query.isBlank() || p.customerName.contains(query.trim(), true) || p.note.contains(query.trim(), true))
        }
        sortPayments(filtered, sortKey, asc)
    }

    fun tapSort(key: Int) {
        if (sortKey == key) asc = !asc
        else { sortKey = key; asc = key == BY_NAME }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Payments received")
        SearchField(query, { query = it }, "Search customer, D.O. or note", Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = sortKey == BY_DATE, onClick = { tapSort(BY_DATE) },
                label = { Text("Date " + if (sortKey == BY_DATE && asc) "↑" else "↓") }
            )
            FilterChip(
                selected = sortKey == BY_NAME, onClick = { tapSort(BY_NAME) },
                label = { Text("Customer " + if (sortKey == BY_NAME && !asc) "Z–A" else "A–Z") }
            )
            AssistChip(onClick = { pickFrom = true }, label = { Text(from?.let { "From ${Fmt.date(it)}" } ?: "From date") })
            AssistChip(onClick = { pickTo = true }, label = { Text(to?.let { "To ${Fmt.date(it)}" } ?: "To date") })
            if (from != null || to != null) {
                TextButton(onClick = { from = null; to = null }) { Text("Clear dates") }
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(selected = modeFilter.isEmpty(), onClick = { modeFilter = "" }, label = { Text("All") })
            PAYMENT_MODES.forEach { m ->
                FilterChip(
                    selected = modeFilter == m,
                    onClick = { modeFilter = if (modeFilter == m) "" else m },
                    label = { Text(m) }
                )
            }
        }
        Text(
            "${list.size} payments · ${Fmt.money(list.sumOf { it.amount })} received",
            color = Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Box(Modifier.fillMaxSize()) {
            if (list.isEmpty()) {
                EmptyState(if (payments.isEmpty()) "No payments yet.\nMoney received with a sale also shows here.\nTap + when a customer pays you later." else "No payments match.")
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = ListPadding) {
                    items(list, key = { it.key }) { p ->
                        PaymentItem(p) {
                            if (p.saleId != 0L) nav.push(Screen.SaleDetail(p.saleId)) else nav.push(Screen.PaymentForm(p.paymentId))
                        }
                        ListRowDivider()
                    }
                }
            }
            FloatingActionButton(
                onClick = { nav.push(Screen.PaymentForm()) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            ) { Icon(Icons.Default.Add, contentDescription = "New payment") }
        }
    }
    if (pickFrom) DatePickDialog(from ?: vm.today.value, { from = it }) { pickFrom = false }
    if (pickTo) DatePickDialog(to ?: vm.today.value, { to = it }) { pickTo = false }
}

@Composable
fun PaymentItem(p: PaymentEntry, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(p.customerName, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(
                Fmt.date(p.date) + (if (p.mode.isNotBlank()) " · ${p.mode}" else "") +
                    if (p.note.isNotBlank()) " · ${p.note}" else "",
                color = Muted, fontSize = 13.sp
            )
        }
        Text("+ " + Fmt.money(p.amount), color = Green, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun PaymentFormScreen(vm: MainViewModel, nav: Nav, id: Long, presetCustomerId: Long) {
    val payments by vm.payments.collectAsStateWithLifecycle()
    val customers by vm.customers.collectAsStateWithLifecycle()
    val existing = remember(id) { payments.firstOrNull { it.id == id } }
    val scope = rememberCoroutineScope()

    var date by remember { mutableStateOf(existing?.date ?: vm.today.value) }
    var customerId by remember { mutableStateOf(existing?.customerId ?: presetCustomerId) }
    var amount by remember { mutableStateOf(Fmt.plain(existing?.amount ?: 0.0)) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var mode by remember { mutableStateOf(existing?.mode ?: "Cash") }
    var custErr by remember { mutableStateOf<String?>(null) }
    var amtErr by remember { mutableStateOf<String?>(null) }
    var pickCustomer by remember { mutableStateOf(false) }
    var newCustomer by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val cust = customers.firstOrNull { it.id == customerId }
    val customerName = cust?.name ?: existing?.takeIf { it.customerId == customerId }?.customerName ?: ""
    val amt = Fmt.parse(amount)

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (id == 0L) "Payment received" else "Edit payment", onBack = nav.pop)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            DateField("Date", date, { date = it })
            PickerField("Customer", customerName, { pickCustomer = true }, error = custErr, placeholder = "Choose customer")
            if (cust != null) {
                Row(Modifier.padding(start = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Current balance: ", color = Muted, fontSize = 13.sp)
                    BalanceText(cust.balance, fontSize = 13)
                }
            }
            MoneyField("Amount received", amount, { amount = it; amtErr = null }, error = amtErr)
            // Balance before this payment (leave this payment out when editing it).
            val balanceBefore = when {
                cust == null -> 0.0
                existing != null && existing.customerId == customerId -> cust.balance + existing.amount
                else -> cust.balance
            }
            if (cust != null && balanceBefore > 0.005) {
                AssistChip(
                    onClick = { amount = Fmt.plain(balanceBefore); amtErr = null },
                    label = { Text("Full balance ${Fmt.money(balanceBefore)}") }
                )
            }
            if (cust != null && amt > balanceBefore + 0.005) {
                Text(
                    "${Fmt.money(amt - maxOf(balanceBefore, 0.0))} is more than what's due. " +
                        "The extra is saved as credit and used automatically on this customer's next orders.",
                    color = Green, fontSize = 13.sp
                )
            }
            Text("Paid by", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PAYMENT_MODES.forEach { m ->
                    FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m) })
                }
            }
            AppField("Note (optional)", note, { note = it }, capitalization = KeyboardCapitalization.Sentences)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    custErr = if (customerId == 0L) "Choose a customer" else null
                    amtErr = if (amt <= 0.0) "Enter the amount" else null
                    if (custErr == null && amtErr == null) {
                        vm.run {
                            vm.repo.savePayment(
                                Payment(id = id, date = date, customerId = customerId, amount = amt, note = note.trim(), mode = mode)
                            )
                        }
                        nav.pop()
                        nav.message(if (id == 0L) "Payment saved" else "Changes saved")
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Save", fontSize = 16.sp) }
            if (id != 0L) {
                TextButton(onClick = { confirmDelete = true }) { Text("Delete payment", color = DueRed) }
            }
        }
    }

    if (pickCustomer) {
        PickDialog(
            "Choose customer",
            customers.map { PickItem(it.id, it.name, if (it.balance > 0.005) "Due ${Fmt.money(it.balance)}" else "") },
            onPick = { customerId = it.id; custErr = null },
            onDismiss = { pickCustomer = false },
            addLabel = "New customer", onAdd = { newCustomer = true }
        )
    }
    if (newCustomer) {
        CustomerDialog(null, onSave = { c ->
            scope.launch { customerId = vm.repo.addCustomer(c); custErr = null }
        }, onDismiss = { newCustomer = false })
    }
    if (confirmDelete) {
        ConfirmDialog(
            "Delete this payment?", "It will move to Trash and the customer's balance will go back up.",
            "Delete", onConfirm = {
                vm.run { vm.repo.deletePayment(id) }
                nav.pop()
                nav.undo("Payment moved to Trash") { vm.run { vm.repo.restorePayment(id) } }
            }, onDismiss = { confirmDelete = false }
        )
    }
}
