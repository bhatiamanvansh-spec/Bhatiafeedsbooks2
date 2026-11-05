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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bhatiafeeds.books.Fmt
import com.bhatiafeeds.books.MainViewModel
import com.bhatiafeeds.books.Share
import com.bhatiafeeds.books.data.Company
import com.bhatiafeeds.books.data.CompanyPayment
import com.bhatiafeeds.books.data.PAYMENT_MODES
import com.bhatiafeeds.books.pdf.Pdfs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Balance with the company in words and colour. Positive = you owe them; negative = you have paid ahead. */
@Composable
fun CompanyBalanceText(balance: Double, fontSize: Int = 15, bold: Boolean = true) {
    val (text, color) = when {
        balance > 0.005 -> Fmt.money(balance) to DueRed
        balance < -0.005 -> ("Advance " + Fmt.money(-balance)) to Green
        else -> "Nil" to Muted
    }
    Text(text, color = color, fontSize = fontSize.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
}

/** The list of companies you buy from, each with what you owe it. Works like the Drivers and Customers lists. */
@Composable
fun CompaniesScreen(vm: MainViewModel, nav: Nav) {
    val companies by vm.companies.collectAsStateWithLifecycle()
    val accounts by vm.companyAccounts.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    val list = remember(companies, query) {
        if (query.isBlank()) companies else companies.filter { it.name.contains(query.trim(), true) || it.phone.contains(query.trim()) }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Companies", onBack = nav.pop) {
            TextButton(onClick = { adding = true }) { Text("+ Add") }
        }
        if (companies.isEmpty()) {
            EmptyState("No companies yet.\nTap + Add to save a company once, then pick it on each D.O.")
        } else {
            if (companies.size > 6) {
                SearchField(query, { query = it }, "Search company", Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            val owed = accounts.values.sumOf { maxOf(it.balance, 0.0) }
            val advance = accounts.values.sumOf { maxOf(-it.balance, 0.0) }
            Text(
                "${companies.size} compan${if (companies.size == 1) "y" else "ies"} · ${Fmt.money(owed)} owed" +
                    if (advance > 0.005) " · ${Fmt.money(advance)} advance" else "",
                color = Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            if (list.isEmpty()) {
                EmptyState("No companies match.")
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = ListPadding) {
                    items(list, key = { it.id }) { c ->
                        Row(
                            Modifier.fillMaxWidth().clickable { nav.push(Screen.Company(c.id)) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(c.name, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                val sub = listOf(c.phone, c.address).filter { it.isNotBlank() }.joinToString(" · ")
                                if (sub.isNotEmpty()) Text(sub, color = Muted, fontSize = 13.sp, maxLines = 1)
                            }
                            CompanyBalanceText(accounts[c.id]?.balance ?: 0.0)
                        }
                        ListRowDivider()
                    }
                }
            }
        }
    }
    if (adding) {
        CompanyDialog(null, onSave = { c -> vm.run { vm.repo.saveCompany(c) }; nav.message("Company added") },
            onDismiss = { adding = false })
    }
}

/** Add or edit a company. Used from the Companies list and from the D.O. form. */
@Composable
fun CompanyDialog(initial: Company?, onSave: (Company) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var address by remember { mutableStateOf(initial?.address ?: "") }
    var balance by remember { mutableStateOf(Fmt.plain(initial?.openingBalance ?: 0.0)) }
    var nameError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New company" else "Edit company") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AppField("Company name", name, { name = it; nameError = null }, error = nameError,
                    capitalization = KeyboardCapitalization.Words)
                AppField("Phone (optional)", phone, { s -> if (s.length <= 15 && s.all { it.isDigit() || it == '+' || it == ' ' }) phone = s },
                    keyboardType = KeyboardType.Phone)
                AppField("Address (optional)", address, { address = it }, capitalization = KeyboardCapitalization.Words,
                    singleLine = false)
                MoneyField("Amount you already owed them (opening)", balance, { balance = it })
                Text(
                    "Enter what you owed this company before you started using the app. Leave blank if nothing.",
                    fontSize = 12.sp, color = Muted
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) {
                    nameError = "Enter the company name"
                } else {
                    onSave(
                        (initial ?: Company(name = "")).copy(
                            name = name.trim(), phone = phone.trim(), address = address.trim(),
                            openingBalance = Fmt.parse(balance)
                        )
                    )
                    onDismiss()
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun CompanyScreen(vm: MainViewModel, nav: Nav, id: Long) {
    val companies by vm.companies.collectAsStateWithLifecycle()
    val accounts by vm.companyAccounts.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val company = companies.firstOrNull { it.id == id }
    val acct = accounts[id]
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (company == null || acct == null) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Company", onBack = nav.pop)
            EmptyState("This company is no longer available.")
        }
        return
    }
    val name = company.name
    val shown = remember(acct) { acct.lines.reversed() }
    val missing = remember(acct) { acct.lines.count { it.costMissing } }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(name, onBack = nav.pop) {
            TextButton(onClick = { editing = true }) { Text("Edit") }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = ListPadding) {
            item {
                InfoCard(Modifier.padding(16.dp).fillMaxWidth()) {
                    Text(
                        when {
                            acct.balance > 0.005 -> "You owe $name"
                            acct.balance < -0.005 -> "Advance paid to $name (adjusts against future D.O.s)"
                            else -> "Account with $name"
                        },
                        color = Muted, fontSize = 13.sp
                    )
                    CompanyBalanceText(acct.balance, fontSize = 30)
                    if (company.phone.isNotBlank()) {
                        Text(
                            company.phone, fontSize = 15.sp, color = Green, fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable {
                                ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${company.phone}")))
                            }.padding(top = 8.dp, bottom = 2.dp)
                        )
                    }
                    if (company.address.isNotBlank()) Text(company.address, fontSize = 14.sp, color = Muted)
                    if (missing > 0) {
                        Text(
                            "$missing D.O.${if (missing == 1) " has" else "s have"} no cost entered yet, so the amount above may be too low.",
                            color = DueRed, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { nav.push(Screen.CompanyPaymentForm(companyId = id)) }, modifier = Modifier.weight(1f)) {
                        Text("+ Payment to company")
                    }
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                val f = withContext(Dispatchers.IO) { Pdfs.companyStatement(ctx, vm.prefs, name, acct, today) }
                                Share.file(ctx, f, "application/pdf")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Statement") }
                }
            }
            if (shown.isEmpty() && acct.opening == 0.0) {
                item {
                    Text(
                        "Nothing yet.\nCost you enter on each D.O. line and payments you make to the company appear here.",
                        color = Muted, fontSize = 14.sp, modifier = Modifier.padding(24.dp)
                    )
                }
            } else {
                item { SectionTitle("Account (newest first)") }
            }
            items(shown, key = { (it.doNumber ?: "p") + "|" + it.paymentId + "|" + it.date + "|" + it.title }) { l ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (l.doNumber != null) nav.push(Screen.DoDetail(l.doNumber, id))
                            else nav.push(Screen.CompanyPaymentForm(l.paymentId, id))
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(l.title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text(
                            Fmt.date(l.date) + if (l.detail.isNotBlank()) " · ${l.detail}" else "",
                            color = Muted, fontSize = 13.sp
                        )
                        if (l.costMissing) Text("Cost not (fully) entered", color = DueRed, fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        if (l.doNumber != null) {
                            Text(Fmt.money(l.bill), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        } else {
                            Text("- " + Fmt.money(l.paid), color = Green, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Row {
                            Text("Balance ", color = Muted, fontSize = 12.sp)
                            CompanyBalanceText(l.balanceAfter, fontSize = 12, bold = false)
                        }
                    }
                }
                ListRowDivider()
            }
            if (acct.opening != 0.0) {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("Opening balance", color = Muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        CompanyBalanceText(acct.opening, bold = false)
                    }
                }
            }
            item {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
                    Text("Delete company", color = DueRed)
                }
            }
        }
    }
    if (editing) {
        CompanyDialog(company, onSave = { saved -> vm.run { vm.repo.saveCompany(saved) }; nav.message("Changes saved") },
            onDismiss = { editing = false })
    }
    if (confirmDelete) {
        ConfirmDialog(
            "Delete ${company.name}?",
            "Its D.O.s and payments stay in your records. You can restore the company from Trash.",
            "Delete", onConfirm = {
                vm.run { vm.repo.deleteCompany(company.id) }
                nav.pop()
                nav.undo("Company moved to Trash") { vm.run { vm.repo.restoreCompany(company.id) } }
            }, onDismiss = { confirmDelete = false }
        )
    }
}

@Composable
fun CompanyPaymentFormScreen(vm: MainViewModel, nav: Nav, id: Long, presetCompanyId: Long) {
    val payments by vm.companyPayments.collectAsStateWithLifecycle()
    val companies by vm.companies.collectAsStateWithLifecycle()
    val accounts by vm.companyAccounts.collectAsStateWithLifecycle()
    val existing = remember(id) { payments.firstOrNull { it.id == id } }
    val companyId = existing?.companyId ?: presetCompanyId
    val name = companies.firstOrNull { it.id == companyId }?.name ?: "company"
    val acct = accounts[companyId] ?: com.bhatiafeeds.books.data.CompanyAccount(0.0, 0.0, emptyList())

    var date by remember { mutableStateOf(existing?.date ?: vm.today.value) }
    var amount by remember { mutableStateOf(Fmt.plain(existing?.amount ?: 0.0)) }
    var mode by remember { mutableStateOf(existing?.mode ?: "Bank transfer") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var amtErr by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val amt = Fmt.parse(amount)
    // Balance before this payment (leave this payment out when editing it).
    val before = acct.balance + (existing?.amount ?: 0.0)

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (id == 0L) "Payment to $name" else "Edit payment", onBack = nav.pop)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            DateField("Date", date, { date = it })
            Row(Modifier.padding(start = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("You owe now: ", color = Muted, fontSize = 13.sp)
                CompanyBalanceText(before, fontSize = 13)
            }
            MoneyField("Amount paid", amount, { amount = it; amtErr = null }, error = amtErr)
            if (before > 0.005) {
                AssistChip(
                    onClick = { amount = Fmt.plain(before); amtErr = null },
                    label = { Text("Full balance ${Fmt.money(before)}") }
                )
            }
            if (amt > before + 0.005 && amt > 0.0) {
                Text(
                    "${Fmt.money(amt - maxOf(before, 0.0))} is more than you owe. The extra stays as advance with the company " +
                        "and is adjusted automatically against D.O.s you take later.",
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
                    amtErr = if (amt <= 0.0) "Enter the amount" else null
                    if (amtErr == null) {
                        vm.run {
                            vm.repo.saveCompanyPayment(
                                CompanyPayment(id = id, companyId = companyId, date = date, amount = amt, mode = mode, note = note.trim())
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
    if (confirmDelete) {
        ConfirmDialog(
            "Delete this payment?", "It will move to Trash and the amount you owe the company will go back up.",
            "Delete", onConfirm = {
                vm.run { vm.repo.deleteCompanyPayment(id) }
                nav.pop()
                nav.undo("Payment moved to Trash") { vm.run { vm.repo.restoreCompanyPayment(id) } }
            }, onDismiss = { confirmDelete = false }
        )
    }
}
