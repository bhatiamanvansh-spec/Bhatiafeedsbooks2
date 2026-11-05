package com.bhatiafeeds.books.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bhatiafeeds.books.Fmt
import com.bhatiafeeds.books.MainViewModel
import com.bhatiafeeds.books.data.CustomerBalance
import com.bhatiafeeds.books.data.Driver
import com.bhatiafeeds.books.data.PAYMENT_MODES
import com.bhatiafeeds.books.data.Repository
import com.bhatiafeeds.books.data.SaleItem
import kotlinx.coroutines.launch

/** What is typed for one product line while the form is open. */
private data class ItemDraft(
    val product: String = "",
    val bags: String = "",
    val rate: String = "",
    val cost: String = ""
)

/** One customer's order on the D.O. [saleId] is 0 for an order that has not been saved yet. */
private data class OrderDraft(
    val saleId: Long = 0,
    val customerId: Long = 0,
    val items: List<ItemDraft> = listOf(ItemDraft()),
    val received: String = "",
    val mode: String = "Cash",
    val legacyVehicle: String = ""
)

private fun ItemDraft.bagCount(): Int = bags.toIntOrNull() ?: 0
private fun ItemDraft.isValid(): Boolean = product.isNotBlank() && bagCount() > 0 && Fmt.parse(rate) > 0.0
private fun OrderDraft.total(): Double = items.sumOf { it.bagCount() * Fmt.parse(it.rate) }
private fun OrderDraft.costTotal(): Double = items.sumOf { it.bagCount() * Fmt.parse(it.cost) }

@Composable
private fun ModeChips(selected: String, onPick: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PAYMENT_MODES.forEach { m ->
            FilterChip(selected = selected == m, onClick = { onPick(m) }, label = { Text(m) })
        }
    }
}

@Composable
private fun ItemBlock(
    index: Int,
    draft: ItemDraft,
    canRemove: Boolean,
    tried: Boolean,
    onPickProduct: () -> Unit,
    onChange: (ItemDraft) -> Unit,
    onRemove: () -> Unit
) {
    val bags = draft.bagCount()
    val rate = Fmt.parse(draft.rate)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Product ${index + 1}", fontWeight = FontWeight.Medium, color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (canRemove) TextButton(onClick = onRemove) { Text("Remove", color = DueRed) }
    }
    PickerField(
        "Product", draft.product, onPickProduct,
        error = if (tried && draft.product.isBlank()) "Choose a product" else null,
        placeholder = "Choose product"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f)) {
            IntField("No. of bags", draft.bags, { onChange(draft.copy(bags = it)) },
                error = if (tried && bags <= 0) "Enter bags" else null)
        }
        Box(Modifier.weight(1f)) {
            MoneyField("Rate per bag", draft.rate, { onChange(draft.copy(rate = it)) },
                error = if (tried && rate <= 0.0) "Enter rate" else null)
        }
    }
    MoneyField("Company cost per bag (optional)", draft.cost, { onChange(draft.copy(cost = it)) })
    if (bags > 0 && rate > 0.0) {
        val cost = Fmt.parse(draft.cost)
        val text = "Amount: ${Fmt.money(bags * rate)}" +
            if (cost > 0.0) " · Profit: ${Fmt.money(bags * (rate - cost))}" else ""
        Text(text, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp, bottom = 4.dp))
    }
}

@Composable
private fun OrderCard(
    index: Int,
    order: OrderDraft,
    customerName: String,
    hasCustomer: Boolean,
    prevBalance: Double,
    canRemove: Boolean,
    tried: Boolean,
    onPickCustomer: () -> Unit,
    onPickProduct: (Int) -> Unit,
    onItemChange: (Int, ItemDraft) -> Unit,
    onAddItem: () -> Unit,
    onRemoveItem: (Int) -> Unit,
    onReceived: (String) -> Unit,
    onMode: (String) -> Unit,
    onRemove: () -> Unit
) {
    val total = order.total()
    val received = Fmt.parse(order.received)
    val due = total - received
    InfoCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Customer ${index + 1}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (canRemove) TextButton(onClick = onRemove) { Text("Remove customer", color = DueRed) }
        }
        PickerField(
            "Customer", customerName, onPickCustomer,
            error = if (tried && order.customerId == 0L) "Choose a customer" else null,
            placeholder = "Choose customer"
        )
        order.items.forEachIndexed { i, draft ->
            ItemBlock(
                index = i, draft = draft, canRemove = order.items.size > 1, tried = tried,
                onPickProduct = { onPickProduct(i) },
                onChange = { onItemChange(i, it) },
                onRemove = { onRemoveItem(i) }
            )
        }
        OutlinedButton(onClick = onAddItem, modifier = Modifier.fillMaxWidth()) { Text("+ Add another product") }
        Spacer(Modifier.height(8.dp))
        MoneyField("Received from this customer", order.received, onReceived)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { onReceived(Fmt.plain(total)) }, label = { Text("Full amount") })
            AssistChip(onClick = { onReceived("") }, label = { Text("None") })
        }
        if (received > 0.0) {
            Text("Paid by", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
            ModeChips(order.mode, onMode)
        }
        if (received > total + 0.005 && total > 0.0) {
            Text(
                "${Fmt.money(received - total)} extra is saved as credit for this customer's future orders.",
                color = Green, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        LabelValue("Order total", Fmt.money(total), bold = true)
        if (hasCustomer) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Balance before", color = Muted, fontSize = 14.sp, modifier = Modifier.weight(0.5f))
                Box(Modifier.weight(0.5f)) { BalanceText(prevBalance) }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Balance after", color = Muted, fontSize = 14.sp, modifier = Modifier.weight(0.5f))
                Box(Modifier.weight(0.5f)) { BalanceText(prevBalance + due) }
            }
        } else {
            LabelValue(
                "Due", if (due <= 0.005) "Nil" else Fmt.money(due),
                valueColor = if (due > 0.005) DueRed else Green, bold = true
            )
        }
    }
}

/**
 * One D.O. number with several customers. Each customer still gets their own invoice and ledger entry.
 * [originalDo] is blank for a new D.O.; otherwise every customer already on that D.O. is loaded for editing.
 */
@Composable
fun DoFormScreen(vm: MainViewModel, nav: Nav, originalDo: String, presetCustomerId: Long) {
    val sales by vm.sales.collectAsStateWithLifecycle()
    val customers by vm.customers.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    val drivers by vm.drivers.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val existing = remember(originalDo) {
        if (originalDo.isBlank()) emptyList()
        else sales.filter { it.doNumber.trim().equals(originalDo.trim(), ignoreCase = true) }.sortedBy { it.id }
    }

    var date by remember { mutableStateOf(existing.minOfOrNull { it.date } ?: vm.today.value) }
    var doNo by remember { mutableStateOf(existing.firstOrNull()?.doNumber?.trim() ?: "") }
    var driverId by remember { mutableStateOf(existing.map { it.driverId }.firstOrNull { it != 0L } ?: 0L) }
    val orders = remember {
        mutableStateListOf<OrderDraft>().apply {
            if (existing.isNotEmpty()) {
                addAll(existing.map { s ->
                    OrderDraft(
                        saleId = s.id, customerId = s.customerId,
                        items = s.items.map { ItemDraft(it.productName, it.bags.toString(), Fmt.plain(it.rate), Fmt.plain(it.costRate)) }
                            .ifEmpty { listOf(ItemDraft()) },
                        received = Fmt.plain(s.received),
                        mode = s.receivedMode.ifBlank { "Cash" },
                        legacyVehicle = s.legacyVehicle
                    )
                })
            } else add(OrderDraft(customerId = presetCustomerId))
        }
    }
    var tried by remember { mutableStateOf(false) }

    var pickCustomerFor by remember { mutableIntStateOf(-1) }
    var newCustomerFor by remember { mutableIntStateOf(-1) }
    var productOrder by remember { mutableIntStateOf(-1) }
    var productItem by remember { mutableIntStateOf(0) }
    var newProduct by remember { mutableStateOf(false) }
    var newProductOrder by remember { mutableIntStateOf(-1) }
    var newProductItem by remember { mutableIntStateOf(0) }
    var pickDriver by remember { mutableStateOf(false) }
    var newDriver by remember { mutableStateOf(false) }
    var dupWarning by remember { mutableStateOf(false) }

    fun updateOrder(i: Int, f: (OrderDraft) -> OrderDraft) {
        if (i in orders.indices) orders[i] = f(orders[i])
    }
    fun updateItem(oi: Int, ii: Int, f: (ItemDraft) -> ItemDraft) {
        updateOrder(oi) { o -> o.copy(items = o.items.mapIndexed { k, d -> if (k == ii) f(d) else d }) }
    }

    val driverName = drivers.firstOrNull { it.id == driverId }?.name
        ?: existing.firstOrNull { driverId != 0L && it.driverId == driverId }?.driverName ?: ""

    val ordersValid = orders.isNotEmpty() && orders.all { o -> o.customerId != 0L && o.items.all { d -> d.isValid() } }
    val totalBags = orders.sumOf { o -> o.items.sumOf { it.bagCount() } }
    val totalSales = orders.sumOf { it.total() }
    val totalReceived = orders.sumOf { Fmt.parse(it.received) }
    val totalCost = orders.sumOf { it.costTotal() }
    val allCostEntered = orders.all { o -> o.items.all { Fmt.parse(it.cost) > 0.0 } }

    fun doSave() {
        val inputs = orders.map { o ->
            val recv = Fmt.parse(o.received)
            Repository.OrderInput(
                saleId = o.saleId, customerId = o.customerId, received = recv,
                mode = if (recv > 0.0) o.mode else "",
                legacyVehicle = o.legacyVehicle,
                items = o.items.map { d ->
                    SaleItem(
                        productName = d.product.trim(), bags = d.bagCount(),
                        rate = Fmt.parse(d.rate), costRate = Fmt.parse(d.cost)
                    )
                }
            )
        }
        val keep = orders.map { it.saleId }.toSet()
        val removed = existing.map { it.id }.filter { it !in keep }
        val newNumber = doNo.trim()
        val renamed = originalDo.isNotBlank() && !newNumber.equals(originalDo.trim(), ignoreCase = true)
        vm.run { vm.repo.saveDelivery(date, newNumber, driverId, inputs, removed) }
        nav.pop()
        if (renamed) nav.pop()
        nav.message(if (originalDo.isBlank()) "D.O. saved" else "Changes saved")
    }

    fun validateAndSave() {
        tried = true
        if (doNo.isBlank() || !ordersValid) return
        val clash = sales.any {
            it.doNumber.trim().equals(doNo.trim(), ignoreCase = true) &&
                !it.doNumber.trim().equals(originalDo.trim(), ignoreCase = true)
        }
        if (clash) dupWarning = true else doSave()
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (originalDo.isBlank()) "New D.O." else "Edit D.O.", onBack = nav.pop)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            DateField("Date", date, { date = it })
            AppField(
                "D.O. number (from the company)", doNo, { doNo = it },
                error = if (tried && doNo.isBlank()) "Enter the D.O. number" else null
            )
            PickerField("Driver", driverName, { pickDriver = true }, placeholder = "Optional")

            Spacer(Modifier.height(8.dp))
            Text("Customers on this D.O.", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            orders.forEachIndexed { index, order ->
                val cust = customers.firstOrNull { it.id == order.customerId }
                val oldSale = existing.firstOrNull { it.id == order.saleId && order.saleId != 0L }
                val customerName = cust?.name
                    ?: existing.firstOrNull { it.customerId == order.customerId }?.customerName ?: ""
                val prevBalance = when {
                    cust == null -> 0.0
                    oldSale != null && oldSale.customerId == order.customerId -> cust.balance - (oldSale.total - oldSale.received)
                    else -> cust.balance
                }
                OrderCard(
                    index = index, order = order, customerName = customerName, hasCustomer = cust != null,
                    prevBalance = prevBalance, canRemove = orders.size > 1, tried = tried,
                    onPickCustomer = { pickCustomerFor = index },
                    onPickProduct = { itemIndex -> productOrder = index; productItem = itemIndex },
                    onItemChange = { itemIndex, d -> updateItem(index, itemIndex) { d } },
                    onAddItem = { updateOrder(index) { o -> o.copy(items = o.items + ItemDraft()) } },
                    onRemoveItem = { itemIndex ->
                        updateOrder(index) { o -> o.copy(items = o.items.filterIndexed { k, _ -> k != itemIndex }) }
                    },
                    onReceived = { v -> updateOrder(index) { o -> o.copy(received = v) } },
                    onMode = { m -> updateOrder(index) { o -> o.copy(mode = m) } },
                    onRemove = { orders.removeAt(index) }
                )
                Spacer(Modifier.height(8.dp))
            }
            OutlinedButton(onClick = { orders.add(OrderDraft()) }, modifier = Modifier.fillMaxWidth()) {
                Text("+ Add another customer")
            }

            Spacer(Modifier.height(8.dp))
            InfoCard(Modifier.fillMaxWidth()) {
                LabelValue("Customers", orders.size.toString())
                LabelValue("Total bags on D.O.", totalBags.toString())
                LabelValue("Sales total", Fmt.money(totalSales), bold = true)
                LabelValue("Received now", Fmt.money(totalReceived))
                if (totalCost > 0.005) {
                    LabelValue("Company cost", Fmt.money(totalCost))
                    if (allCostEntered) {
                        LabelValue(
                            "Profit", Fmt.money(totalSales - totalCost),
                            valueColor = if (totalSales >= totalCost) Green else DueRed, bold = true
                        )
                    } else {
                        LabelValue("Profit", "Add cost on every line to see profit", valueColor = Muted)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { validateAndSave() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Save D.O.", fontSize = 16.sp)
            }
        }
    }

    if (pickCustomerFor in orders.indices) {
        val target = pickCustomerFor
        val usedElsewhere = orders.filterIndexed { k, _ -> k != target }.map { it.customerId }.toSet()
        PickDialog(
            "Choose customer",
            customers.filter { it.id !in usedElsewhere }.map {
                PickItem(
                    it.id, it.name,
                    when {
                        it.balance > 0.005 -> "Due ${Fmt.money(it.balance)}"
                        it.balance < -0.005 -> "Credit ${Fmt.money(-it.balance)}"
                        else -> ""
                    }
                )
            },
            onPick = { picked -> updateOrder(target) { o -> o.copy(customerId = picked.id) } },
            onDismiss = { pickCustomerFor = -1 },
            addLabel = "New customer", onAdd = { newCustomerFor = target }
        )
    }
    if (newCustomerFor in orders.indices) {
        val target = newCustomerFor
        CustomerDialog(null, onSave = { c ->
            scope.launch {
                val id = vm.repo.addCustomer(c)
                updateOrder(target) { o -> o.copy(customerId = id) }
            }
        }, onDismiss = { newCustomerFor = -1 })
    }
    if (productOrder in orders.indices) {
        val target = productOrder
        val itemIndex = productItem
        PickDialog(
            "Choose product", products.map { PickItem(it.id, it.name) },
            onPick = { p -> updateItem(target, itemIndex) { d -> d.copy(product = p.title) } },
            onDismiss = { productOrder = -1 },
            addLabel = "New product",
            onAdd = { newProductOrder = target; newProductItem = itemIndex; newProduct = true }
        )
    }
    if (newProduct) {
        TextEntryDialog("New product", "Product name", onDone = { name ->
            vm.run { vm.repo.addProduct(name) }
            updateItem(newProductOrder, newProductItem) { d -> d.copy(product = name.trim()) }
        }, onDismiss = { newProduct = false })
    }
    if (pickDriver) {
        PickDialog(
            "Choose driver",
            listOf(PickItem(0, "No driver")) + drivers.map {
                PickItem(it.id, it.name, listOf(it.phone, it.vehicleNo).filter { s -> s.isNotBlank() }.joinToString(" · "))
            },
            onPick = { driverId = it.id },
            onDismiss = { pickDriver = false },
            addLabel = "New driver", onAdd = { newDriver = true },
            searchable = drivers.size > 6
        )
    }
    if (newDriver) {
        DriverDialog(null, onSave = { d: Driver ->
            scope.launch { driverId = vm.repo.addDriver(d) }
        }, onDismiss = { newDriver = false })
    }
    if (dupWarning) {
        ConfirmDialog(
            "D.O. number already exists",
            "D.O. \"${doNo.trim()}\" is already in your books. These customers will be added to that same D.O. Continue?",
            "Continue", onConfirm = { doSave() }, onDismiss = { dupWarning = false }
        )
    }
}
