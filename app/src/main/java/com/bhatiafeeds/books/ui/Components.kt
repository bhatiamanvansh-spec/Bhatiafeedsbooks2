package com.bhatiafeeds.books.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bhatiafeeds.books.Fmt
import com.bhatiafeeds.books.data.Customer
import com.bhatiafeeds.books.data.Driver

// ---------- Layout pieces ----------

@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = if (onBack == null) 16.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
            Text(
                title,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            actions()
        }
        HorizontalDivider(color = Hairline)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = Muted,
        letterSpacing = 0.8.sp,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp)
    )
}

@Composable
fun EmptyState(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Muted, fontSize = 15.sp)
    }
}

@Composable
fun InfoCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Hairline),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun LabelValue(label: String, value: String, valueColor: Color = Color.Unspecified, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Text(label, color = Muted, fontSize = 14.sp, modifier = Modifier.weight(0.4f))
        Text(
            value,
            color = valueColor,
            fontSize = 15.sp,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(0.6f)
        )
    }
}

/** Colour-coded balance: red when the customer owes, green when they have paid ahead. */
@Composable
fun BalanceText(balance: Double, fontSize: Int = 15, bold: Boolean = true) {
    val (text, color) = when {
        balance > 0.005 -> Fmt.money(balance) to DueRed
        balance < -0.005 -> ("Credit " + Fmt.money(-balance)) to Green
        else -> "Nil" to Muted
    }
    Text(text, color = color, fontSize = fontSize.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
}

@Composable
fun StatCard(title: String, big: String, small: String, modifier: Modifier = Modifier) {
    InfoCard(modifier) {
        Text(title, color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        Text(big, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(small, color = Muted, fontSize = 13.sp)
    }
}

// ---------- Inputs ----------

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(hint) },
        singleLine = true,
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear")
                }
            }
        } else null,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun AppField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    singleLine: Boolean = true,
    prefix: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = if (error != null) {
            { Text(error) }
        } else null,
        prefix = if (prefix != null) {
            { Text(prefix) }
        } else null,
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, capitalization = capitalization),
        modifier = modifier.fillMaxWidth()
    )
}

private val decimalRegex = Regex("^\\d*\\.?\\d{0,2}$")

/** Digits only (whole numbers), e.g. bags. */
@Composable
fun IntField(label: String, value: String, onChange: (String) -> Unit, error: String? = null) {
    AppField(label, value, { s -> if (s.length <= 7 && s.all { it.isDigit() }) onChange(s) },
        error = error, keyboardType = KeyboardType.Number)
}

/** Rupee amount with up to 2 decimals. */
@Composable
fun MoneyField(label: String, value: String, onChange: (String) -> Unit, error: String? = null) {
    AppField(label, value, { s -> if (s.length <= 12 && decimalRegex.matches(s)) onChange(s) },
        error = error, keyboardType = KeyboardType.Decimal, prefix = "₹ ")
}

/** Looks like a text field but opens a picker when tapped. */
@Composable
fun PickerField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    placeholder: String = "Select"
) {
    Box(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            isError = error != null,
            supportingText = if (error != null) {
                { Text(error) }
            } else null,
            trailingIcon = { Text("▾", color = Muted, modifier = Modifier.padding(end = 12.dp)) },
            modifier = Modifier.fillMaxWidth()
        )
        Box(Modifier.matchParentSize().clickable(onClick = onClick))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickDialog(initial: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial * 86_400_000L)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Math.floorDiv(it, 86_400_000L)) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DatePicker(state = state)
    }
}

@Composable
fun DateField(label: String, epochDay: Long, onChange: (Long) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    PickerField(label, Fmt.date(epochDay), { open = true }, modifier)
    if (open) DatePickDialog(epochDay, onChange) { open = false }
}

// ---------- Dialogs ----------

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun TextEntryDialog(title: String, label: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            AppField(label, text, { text = it }, capitalization = KeyboardCapitalization.Words)
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = { onDone(text.trim()); onDismiss() }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

class PickItem(val id: Long, val title: String, val subtitle: String = "")

/** A searchable list in a dialog, with an optional "add new" action at the bottom. */
@Composable
fun PickDialog(
    title: String,
    items: List<PickItem>,
    onPick: (PickItem) -> Unit,
    onDismiss: () -> Unit,
    addLabel: String? = null,
    onAdd: (() -> Unit)? = null,
    searchable: Boolean = true
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(items, query) {
        if (query.isBlank()) items
        else items.filter { it.title.contains(query.trim(), ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (searchable) {
                    SearchField(query, { query = it }, "Search")
                    Spacer(Modifier.height(8.dp))
                }
                if (shown.isEmpty()) {
                    Text("Nothing found", color = Muted, modifier = Modifier.padding(vertical = 16.dp))
                } else {
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(shown, key = { it.id }) { item ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(item); onDismiss() }
                                    .padding(vertical = 12.dp)
                            ) {
                                Text(item.title, fontSize = 16.sp)
                                if (item.subtitle.isNotEmpty()) {
                                    Text(item.subtitle, fontSize = 13.sp, color = Muted)
                                }
                            }
                            HorizontalDivider(color = Hairline)
                        }
                    }
                }
                if (addLabel != null && onAdd != null) {
                    TextButton(onClick = { onDismiss(); onAdd() }) { Text("+ $addLabel") }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

/** Add or edit a customer. Used from the Customers screen and from the sale/payment pickers. */
@Composable
fun CustomerDialog(initial: Customer?, onSave: (Customer) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var address by remember { mutableStateOf(initial?.address ?: "") }
    var balance by remember { mutableStateOf(Fmt.plain(initial?.openingBalance ?: 0.0)) }
    var nameError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New customer" else "Edit customer") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AppField("Name", name, { name = it; nameError = null }, error = nameError,
                    capitalization = KeyboardCapitalization.Words)
                AppField("Phone", phone, { s -> if (s.length <= 15 && s.all { it.isDigit() || it == '+' || it == ' ' }) phone = s },
                    keyboardType = KeyboardType.Phone)
                AppField("Address", address, { address = it }, capitalization = KeyboardCapitalization.Words,
                    singleLine = false)
                MoneyField("Balance already owed (opening)", balance, { balance = it })
                Text(
                    "Enter what this customer owed you before you started using the app. Leave blank if nothing.",
                    fontSize = 12.sp, color = Muted
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) {
                    nameError = "Enter a name"
                } else {
                    onSave(
                        (initial ?: Customer(name = "")).copy(
                            name = name.trim(),
                            phone = phone.trim(),
                            address = address.trim(),
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

/** Add or edit a driver (name, phone, vehicle number). */
@Composable
fun DriverDialog(initial: Driver?, onSave: (Driver) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var vehicle by remember { mutableStateOf(initial?.vehicleNo ?: "") }
    var nameError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New driver" else "Edit driver") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AppField("Driver name", name, { name = it; nameError = null }, error = nameError,
                    capitalization = KeyboardCapitalization.Words)
                AppField("Phone (optional)", phone, { s -> if (s.length <= 15 && s.all { it.isDigit() || it == '+' || it == ' ' }) phone = s },
                    keyboardType = KeyboardType.Phone)
                AppField("Vehicle reg. no. (optional)", vehicle, { vehicle = it },
                    capitalization = KeyboardCapitalization.Characters)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) {
                    nameError = "Enter the driver's name"
                } else {
                    onSave(
                        (initial ?: Driver(name = "")).copy(
                            name = name.trim(), phone = phone.trim(), vehicleNo = vehicle.trim().uppercase()
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
fun ListRowDivider() {
    HorizontalDivider(color = Hairline, modifier = Modifier.padding(horizontal = 16.dp))
}

val ListPadding = PaddingValues(bottom = 96.dp)

@Composable
fun Dot(color: Color) {
    Box(Modifier.size(8.dp).background(color, shape = androidx.compose.foundation.shape.CircleShape))
}
