package com.bhatiafeeds.books.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.bhatiafeeds.books.data.PendingOrder
import com.bhatiafeeds.books.data.PendingRow
import com.bhatiafeeds.books.data.lastRate
import kotlinx.coroutines.launch

/** Add or edit an order that has no D.O. yet. Nothing here changes any balance until it is put on a D.O. */
@Composable
fun PendingFormScreen(vm: MainViewModel, nav: Nav, id: Long) {
    val pending by vm.pending.collectAsStateWithLifecycle()
    val customers by vm.customers.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    val sales by vm.sales.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val existing = remember(id) { pending.firstOrNull { it.id == id } }

    var date by remember { mutableStateOf(existing?.date ?: vm.today.value) }
    var customerId by remember { mutableStateOf(existing?.customerId ?: 0L) }
    var product by remember { mutableStateOf(existing?.productName ?: "") }
    var bags by remember { mutableStateOf(existing?.bags?.toString() ?: "") }
    var rate by remember { mutableStateOf(Fmt.plain(existing?.rate ?: 0.0)) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var tried by remember { mutableStateOf(false) }
    var pickCustomer by remember { mutableStateOf(false) }
    var newCustomer by remember { mutableStateOf(false) }
    var pickProduct by remember { mutableStateOf(false) }
    var newProduct by remember { mutableStateOf(false) }

    val customerName = customers.firstOrNull { it.id == customerId }?.name
        ?: existing?.takeIf { it.customerId == customerId }?.customerName ?: ""
    val bagCount = bags.toIntOrNull() ?: 0

    /** Fills the rate with what this customer paid last time for this product (only if the rate is still blank). */
    fun suggestRate() {
        if (rate.isBlank()) {
            val r = lastRate(sales, customerId, product)
            if (r > 0.0) rate = Fmt.plain(r)
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (id == 0L) "New order" else "Edit order", onBack = nav.pop)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "An order taken before you have the D.O. number. Later, add it to a D.O. from the New D.O. screen.",
                color = Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp)
            )
            DateField("Date", date, { date = it })
            PickerField(
                "Customer", customerName, { pickCustomer = true },
                error = if (tried && customerId == 0L) "Choose a customer" else null,
                placeholder = "Choose customer"
            )
            PickerField(
                "Product", product, { pickProduct = true },
                error = if (tried && product.isBlank()) "Choose a product" else null,
                placeholder = "Choose product"
            )
            IntField("No. of bags", bags, { bags = it }, error = if (tried && bagCount <= 0) "Enter bags" else null)
            MoneyField("Rate per bag (optional)", rate, { rate = it })
            AppField(
                "Delivery area / note (optional)", note, { note = it },
                capitalization = KeyboardCapitalization.Words
            )
            if (bagCount > 0 && Fmt.parse(rate) > 0.0) {
                Text(
                    "Amount: ${Fmt.money(bagCount * Fmt.parse(rate))}",
                    color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    tried = true
                    if (customerId != 0L && product.isNotBlank() && bagCount > 0) {
                        vm.run {
                            vm.repo.savePending(
                                PendingOrder(
                                    id = id, date = date, customerId = customerId, productName = product.trim(),
                                    bags = bagCount, rate = Fmt.parse(rate), note = note.trim()
                                )
                            )
                        }
                        nav.pop()
                        nav.message(if (id == 0L) "Order saved" else "Changes saved")
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Save order", fontSize = 16.sp) }
            if (id != 0L) {
                TextButton(onClick = {
                    vm.run { vm.repo.deletePending(id) }
                    nav.pop()
                    nav.undo("Order removed") { vm.run { vm.repo.restorePending(id) } }
                }) { Text("Delete order", color = DueRed) }
            }
        }
    }

    if (pickCustomer) {
        PickDialog(
            "Choose customer",
            customers.map {
                PickItem(
                    it.id, it.name,
                    when {
                        it.balance > 0.005 -> "Due ${Fmt.money(it.balance)}"
                        it.balance < -0.005 -> "Credit ${Fmt.money(-it.balance)}"
                        else -> ""
                    }
                )
            },
            onPick = { customerId = it.id; suggestRate() },
            onDismiss = { pickCustomer = false },
            addLabel = "New customer", onAdd = { newCustomer = true }
        )
    }
    if (newCustomer) {
        CustomerDialog(null, onSave = { c ->
            scope.launch { customerId = vm.repo.addCustomer(c); suggestRate() }
        }, onDismiss = { newCustomer = false })
    }
    if (pickProduct) {
        PickDialog(
            "Choose product", products.map { PickItem(it.id, it.name) },
            onPick = { product = it.title; suggestRate() },
            onDismiss = { pickProduct = false },
            addLabel = "New product", onAdd = { newProduct = true }
        )
    }
    if (newProduct) {
        TextEntryDialog("New product", "Product name", onDone = { name ->
            vm.run { vm.repo.addProduct(name) }
            product = name.trim()
            suggestRate()
        }, onDismiss = { newProduct = false })
    }
}

/** One pending order in the Sales > Pending list. */
@Composable
fun PendingItemRow(r: PendingRow, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(r.customerName, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(
                "${r.bags} bags · ${r.productName}" + if (r.rate > 0.0) " × ${Fmt.money(r.rate)}" else "",
                color = Muted, fontSize = 13.sp
            )
            val sub = listOf(r.note, Fmt.date(r.date)).filter { it.isNotBlank() }.joinToString(" · ")
            Text(sub, color = Muted, fontSize = 13.sp)
        }
        if (r.rate > 0.0) Text(Fmt.money(r.amount), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        else Text("Rate not set", color = Muted, fontSize = 12.sp)
    }
}

/** Tick the pending orders to put on this D.O. Sorted by area/note so orders for one place sit together. */
@Composable
fun PendingPickDialog(rows: List<PendingRow>, onAdd: (List<PendingRow>) -> Unit, onDismiss: () -> Unit) {
    var chosen by remember { mutableStateOf(setOf<Long>()) }
    val sorted = remember(rows) {
        rows.sortedWith(compareBy<PendingRow>({ it.note.trim().lowercase() }, { it.customerName.lowercase() }))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add pending orders") },
        text = {
            Column {
                Text(
                    "Tick the orders to deliver together. Sorted by area.",
                    color = Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(sorted, key = { it.id }) { r ->
                        val on = r.id in chosen
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { chosen = if (on) chosen - r.id else chosen + r.id }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = on, onCheckedChange = { chosen = if (on) chosen - r.id else chosen + r.id })
                            Column(Modifier.weight(1f)) {
                                Text("${r.customerName} · ${r.bags} bags", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    listOf(r.productName, r.note).filter { it.isNotBlank() }.joinToString(" · "),
                                    color = Muted, fontSize = 13.sp
                                )
                            }
                        }
                        HorizontalDivider(color = Hairline)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = chosen.isNotEmpty(),
                onClick = { onAdd(sorted.filter { it.id in chosen }); onDismiss() }
            ) { Text(if (chosen.isEmpty()) "Add" else "Add ${chosen.size}") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
