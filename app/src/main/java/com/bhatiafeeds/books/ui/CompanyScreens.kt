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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bhatiafeeds.books.Fmt
import com.bhatiafeeds.books.MainViewModel
import com.bhatiafeeds.books.Share
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

@Composable
fun CompanyScreen(vm: MainViewModel, nav: Nav) {
    val acct by vm.company.collectAsStateWithLifecycle()
    val name by vm.companyName.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val shown = remember(acct) { acct.lines.reversed() }
    val missing = remember(acct) { acct.lines.count { it.costMissing } }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(name, onBack = nav.pop)
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
                    Button(onClick = { nav.push(Screen.CompanyPaymentForm()) }, modifier = Modifier.weight(1f)) {
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
                            if (l.doNumber != null) nav.push(Screen.DoDetail(l.doNumber))
                            else nav.push(Screen.CompanyPaymentForm(l.paymentId))
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
        }
    }
}

@Composable
fun CompanyPaymentFormScreen(vm: MainViewModel, nav: Nav, id: Long) {
    val payments by vm.companyPayments.collectAsStateWithLifecycle()
    val acct by vm.company.collectAsStateWithLifecycle()
    val name by vm.companyName.collectAsStateWithLifecycle()
    val existing = remember(id) { payments.firstOrNull { it.id == id } }

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
                                CompanyPayment(id = id, date = date, amount = amt, mode = mode, note = note.trim())
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
