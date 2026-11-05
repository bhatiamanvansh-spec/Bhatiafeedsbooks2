package com.bhatiafeeds.books.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bhatiafeeds.books.Fmt
import com.bhatiafeeds.books.MainViewModel
import com.bhatiafeeds.books.Share
import com.bhatiafeeds.books.pdf.Pdfs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val BY_NAME = 0
private const val BY_BALANCE = 1

@Composable
fun CustomersScreen(vm: MainViewModel, nav: Nav) {
    val customers by vm.customers.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var sortKey by rememberSaveable { mutableStateOf(BY_NAME) }
    var asc by rememberSaveable { mutableStateOf(true) }
    var adding by remember { mutableStateOf(false) }

    val list = remember(customers, query, sortKey, asc) {
        val filtered = customers.filter {
            query.isBlank() || it.name.contains(query.trim(), true) || it.phone.contains(query.trim())
        }
        if (sortKey == BY_NAME) {
            if (asc) filtered.sortedBy { it.name.lowercase() } else filtered.sortedByDescending { it.name.lowercase() }
        } else {
            if (asc) filtered.sortedBy { it.balance } else filtered.sortedByDescending { it.balance }
        }
    }

    fun tapSort(key: Int) {
        if (sortKey == key) asc = !asc
        else { sortKey = key; asc = key == BY_NAME }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Customers")
        SearchField(query, { query = it }, "Search name or phone", Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = sortKey == BY_NAME, onClick = { tapSort(BY_NAME) },
                label = { Text("Name " + if (sortKey == BY_NAME && !asc) "Z–A" else "A–Z") }
            )
            FilterChip(
                selected = sortKey == BY_BALANCE, onClick = { tapSort(BY_BALANCE) },
                label = { Text("Balance " + if (sortKey == BY_BALANCE && asc) "↑" else "↓") }
            )
        }
        Text(
            "${list.size} customers · ${Fmt.money(list.filter { it.balance > 0.005 }.sumOf { it.balance })} outstanding" +
                list.filter { it.balance < -0.005 }.sumOf { -it.balance }.let { cr -> if (cr > 0.005) " · ${Fmt.money(cr)} credit" else "" },
            color = Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Box(Modifier.fillMaxSize()) {
            if (list.isEmpty()) {
                EmptyState(if (customers.isEmpty()) "No customers yet.\nTap + to add one." else "No customers match.")
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = ListPadding) {
                    items(list, key = { it.id }) { c ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { nav.push(Screen.CustomerDetail(c.id)) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(c.name, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                val sub = listOf(c.phone, c.address).filter { it.isNotBlank() }.joinToString(" · ")
                                if (sub.isNotEmpty()) Text(sub, color = Muted, fontSize = 13.sp, maxLines = 1)
                            }
                            BalanceText(c.balance)
                        }
                        ListRowDivider()
                    }
                }
            }
            FloatingActionButton(
                onClick = { adding = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            ) { Icon(Icons.Default.Add, contentDescription = "New customer") }
        }
    }
    if (adding) {
        CustomerDialog(null, onSave = { c -> vm.run { vm.repo.saveCustomer(c) }; nav.message("Customer added") },
            onDismiss = { adding = false })
    }
}

/** One line of a customer's history: either a sale or a payment. */
private class Txn(
    val date: Long,
    val sortId: Long,
    val title: String,
    val detail: String,
    val amountText: String,
    val isPayment: Boolean,
    val open: Screen
)

@Composable
fun CustomerDetailScreen(vm: MainViewModel, nav: Nav, id: Long) {
    val customers by vm.customers.collectAsStateWithLifecycle()
    val sales by vm.sales.collectAsStateWithLifecycle()
    val payments by vm.payments.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = customers.firstOrNull { it.id == id }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(c?.name ?: "Customer", onBack = nav.pop) {
            if (c != null) TextButton(onClick = { editing = true }) { Text("Edit") }
        }
        if (c == null) {
            EmptyState("This customer is no longer available.")
            return@Column
        }
        val mySales = remember(sales, id) { sales.filter { it.customerId == id } }
        val myPayments = remember(payments, id) { payments.filter { it.customerId == id } }
        val txns = remember(mySales, myPayments) {
            val a = mySales.map {
                Txn(
                    it.date, it.id * 2,
                    "D.O. ${it.doNumber} · " + it.items.joinToString(", ") { i -> i.productName },
                    (if (it.items.size == 1) "${it.bags} bags × ${Fmt.money(it.items[0].rate)}" else "${it.bags} bags") +
                        if (it.received > 0.005) " · received ${Fmt.money(it.received)}" else "",
                    Fmt.money(it.total), false, Screen.SaleDetail(it.id)
                )
            }
            val b = myPayments.map {
                Txn(
                    it.date, it.id * 2 + 1, "Payment received",
                    it.note, "- " + Fmt.money(it.amount), true, Screen.PaymentForm(it.id)
                )
            }
            (a + b).sortedWith(compareByDescending<Txn> { it.date }.thenByDescending { it.sortId })
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = ListPadding) {
            item {
                InfoCard(Modifier.padding(16.dp).fillMaxWidth()) {
                    Text(if (c.balance < -0.005) "Credit balance (used on next orders)" else "Balance", color = Muted, fontSize = 13.sp)
                    BalanceText(c.balance, fontSize = 30)
                    Spacer(Modifier.height(8.dp))
                    if (c.phone.isNotBlank()) {
                        Text(
                            c.phone, fontSize = 15.sp, color = Green, fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable {
                                ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${c.phone}")))
                            }.padding(vertical = 2.dp)
                        )
                    }
                    if (c.address.isNotBlank()) Text(c.address, fontSize = 14.sp, color = Muted)
                }
            }
            item {
                Row(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(onClick = { nav.push(Screen.DoForm(customerId = id)) }, modifier = Modifier.weight(1f)) {
                        Text("New sale")
                    }
                    FilledTonalButton(onClick = { nav.push(Screen.PaymentForm(customerId = id)) }, modifier = Modifier.weight(1f)) {
                        Text("Payment")
                    }
                }
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val f = withContext(Dispatchers.IO) { Pdfs.ledger(ctx, vm.prefs, c, mySales, myPayments, today) }
                                Share.file(ctx, f, "application/pdf")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Ledger PDF") }
                    OutlinedButton(
                        enabled = c.balance > 0.005,
                        onClick = {
                            Share.text(
                                ctx,
                                "Dear ${c.name}, this is a gentle reminder that ${Fmt.money(c.balance)} is outstanding " +
                                    "with ${vm.prefs.businessName}. Kindly clear it at your earliest convenience. Thank you."
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Send reminder") }
                }
            }
            item { SectionTitle("History") }
            if (txns.isEmpty() && c.openingBalance == 0.0) {
                item { Text("No entries yet.", color = Muted, modifier = Modifier.padding(16.dp)) }
            }
            items(txns, key = { "${it.isPayment}-${it.sortId}" }) { t ->
                Row(
                    Modifier.fillMaxWidth().clickable { nav.push(t.open) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(t.title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(
                            Fmt.date(t.date) + if (t.detail.isNotBlank()) " · ${t.detail}" else "",
                            color = Muted, fontSize = 13.sp
                        )
                    }
                    Text(
                        t.amountText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        color = if (t.isPayment) Green else androidx.compose.ui.graphics.Color.Unspecified
                    )
                }
                ListRowDivider()
            }
            if (c.openingBalance != 0.0) {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("Opening balance", color = Muted, modifier = Modifier.weight(1f))
                        Text(Fmt.money(c.openingBalance), color = Muted)
                    }
                }
            }
            item {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
                    Text("Delete customer", color = DueRed)
                }
            }
        }
    }

    if (editing && c != null) {
        CustomerDialog(c.let { cb ->
            com.bhatiafeeds.books.data.Customer(cb.id, cb.name, cb.address, cb.phone, cb.openingBalance)
        }, onSave = { saved -> vm.run { vm.repo.saveCustomer(saved) }; nav.message("Changes saved") },
            onDismiss = { editing = false })
    }
    if (confirmDelete && c != null) {
        ConfirmDialog(
            "Delete ${c.name}?",
            "Their past sales and payments stay in your records. You can restore the customer from Trash.",
            "Delete", onConfirm = {
                vm.run { vm.repo.deleteCustomer(c.id) }
                nav.pop()
                nav.undo("Customer moved to Trash") { vm.run { vm.repo.restoreCustomer(c.id) } }
            }, onDismiss = { confirmDelete = false }
        )
    }
}
