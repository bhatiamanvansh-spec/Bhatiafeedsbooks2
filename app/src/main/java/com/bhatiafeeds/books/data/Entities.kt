package com.bhatiafeeds.books.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Dates are stored as epoch-day (days since 1970-01-01) so sorting and range filters are trivial. */

@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val address: String = "",
    val phone: String = "",
    /** Balance the customer already owed when you started using the app. */
    val openingBalance: Double = 0.0,
    val deleted: Boolean = false
)

@Entity(tableName = "products", indices = [Index(value = ["name"], unique = true)])
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val deleted: Boolean = false
)

@Entity(tableName = "drivers")
data class Driver(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val vehicleNo: String = "",
    val deleted: Boolean = false
)

/** One invoice (a delivery order). The products on it live in [SaleItem] rows. */
@Entity(tableName = "sales", indices = [Index("customerId"), Index("date"), Index("doNumber")])
data class Sale(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val doNumber: String,
    val customerId: Long,
    /** 0 = no driver chosen. */
    val driverId: Long = 0,
    /** Only filled on entries made before drivers existed; kept so old data is never lost. */
    val vehicleNo: String = "",
    /** Money received along with this order (can be more than the total; the extra becomes credit). */
    val received: Double = 0.0,
    /** How the money received with this order was paid (see [PAYMENT_MODES]); blank on old entries. */
    @ColumnInfo(defaultValue = "''") val receivedMode: String = "",
    val deleted: Boolean = false
)

@Entity(tableName = "sale_items", indices = [Index("saleId")])
data class SaleItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saleId: Long = 0,
    val productName: String,
    val bags: Int,
    val rate: Double,
    /** What the company charges you per bag for this line (0 = not entered). */
    @ColumnInfo(defaultValue = "0") val costRate: Double = 0.0
) {
    val amount: Double get() = bags * rate
    val cost: Double get() = bags * costRate
}

@Entity(tableName = "payments", indices = [Index("customerId"), Index("date")])
data class Payment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val customerId: Long,
    val amount: Double,
    val note: String = "",
    @ColumnInfo(defaultValue = "''") val mode: String = "",
    val deleted: Boolean = false
)

/** Money you paid to the company (usually in bulk, covering several D.O.s). */
@Entity(tableName = "company_payments", indices = [Index("date")])
data class CompanyPayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val amount: Double,
    val mode: String = "",
    val note: String = "",
    val deleted: Boolean = false
)

/** Small key/value store kept inside the database so it is included in backups (company name, opening balance). */
@Entity(tableName = "settings")
data class Setting(
    @PrimaryKey val name: String,
    val value: String
)

val PAYMENT_MODES = listOf("Cash", "UPI", "Bank transfer")

/** Invoice header joined with customer and driver names (items are attached in the ViewModel). */
data class SaleHead(
    val id: Long,
    val date: Long,
    val doNumber: String,
    val customerId: Long,
    val customerName: String,
    val driverId: Long,
    val driverName: String,
    val driverPhone: String,
    val driverVehicle: String,
    val vehicleNo: String,
    val received: Double,
    val receivedMode: String,
    val deleted: Boolean
)

/** A complete invoice with all its product lines. */
data class SaleRow(
    val id: Long,
    val date: Long,
    val doNumber: String,
    val customerId: Long,
    val customerName: String,
    val driverId: Long,
    val driverName: String,
    val driverPhone: String,
    /** Vehicle number saved with the driver. */
    val driverVehicle: String,
    /** Vehicle number typed on old entries (before drivers existed). */
    val legacyVehicle: String,
    val received: Double,
    val receivedMode: String,
    val deleted: Boolean,
    val items: List<SaleItem>
) {
    val total: Double get() = items.sumOf { it.amount }
    /** What the company charges for the lines that have a cost entered. */
    val cost: Double get() = items.sumOf { it.cost }
    val costComplete: Boolean get() = items.isNotEmpty() && items.all { it.costRate > 0.0 }
    val bags: Int get() = items.sumOf { it.bags }
    /** Positive = still owed on this invoice, negative = paid extra (becomes customer credit). */
    val due: Double get() = total - received
    val vehicle: String get() = driverVehicle.ifBlank { legacyVehicle }

    /** Short text for lists, e.g. "Cattle Feed · 20 bags × ₹1,200" or "Feed A 20, Feed B 40 · 60 bags". */
    fun summary(rate: (Double) -> String): String =
        if (items.size == 1) {
            val i = items[0]
            "${i.productName} · ${i.bags} bags × ${rate(i.rate)}"
        } else {
            items.joinToString(", ") { "${it.productName} ${it.bags}" } + " · $bags bags"
        }
}

data class PaymentRow(
    val id: Long,
    val date: Long,
    val customerId: Long,
    val customerName: String,
    val amount: Double,
    val note: String,
    val mode: String,
    val deleted: Boolean
)

/**
 * One line in the Payments list: either a payment recorded in the Payments section,
 * or money received along with an invoice (then [saleId] is set and [paymentId] is 0).
 */
data class PaymentEntry(
    val key: String,
    val date: Long,
    val customerId: Long,
    val customerName: String,
    val amount: Double,
    val note: String,
    val paymentId: Long,
    val saleId: Long,
    val mode: String
)

/** Customer with computed balance. Positive = they owe you, negative = credit they hold with you. */
data class CustomerBalance(
    val id: Long,
    val name: String,
    val address: String,
    val phone: String,
    val openingBalance: Double,
    val balance: Double,
    val lastActivity: Long?
)

data class MonthlyReportRow(
    val label: String,
    val bags: Int,
    val amount: Double
)

/**
 * One delivery order (D.O.) as the company knows it: a single D.O. number that carries
 * the orders of several customers. Each customer's order is still its own invoice ([SaleRow]).
 */
data class DeliveryRow(
    val doNumber: String,
    val date: Long,
    val orders: List<SaleRow>
) {
    val bags: Int get() = orders.sumOf { it.bags }
    val total: Double get() = orders.sumOf { it.total }
    val received: Double get() = orders.sumOf { it.received }
    /** Sum of what each customer still owes on this D.O. (one customer's extra payment never hides another's due). */
    val dueTotal: Double get() = orders.sumOf { maxOf(it.due, 0.0) }
    val cost: Double get() = orders.sumOf { it.cost }
    val costComplete: Boolean get() = orders.isNotEmpty() && orders.all { it.costComplete }
    val profit: Double get() = total - cost
    val driverName: String get() = orders.map { it.driverName }.firstOrNull { it.isNotBlank() } ?: ""
    val driverPhone: String get() = orders.map { it.driverPhone }.firstOrNull { it.isNotBlank() } ?: ""
    val vehicle: String get() = orders.map { it.vehicle }.firstOrNull { it.isNotBlank() } ?: ""
    val lastId: Long get() = orders.maxOf { it.id }
}

/** Groups invoices that share a D.O. number. Newest first. */
fun groupDeliveries(sales: List<SaleRow>): List<DeliveryRow> =
    sales.groupBy { it.doNumber.trim().lowercase() }.values.map { g ->
        val orders = g.sortedBy { it.id }
        DeliveryRow(orders.first().doNumber.trim(), orders.minOf { it.date }, orders)
    }.sortedWith(compareByDescending<DeliveryRow> { it.date }.thenByDescending { it.lastId })

/** One line of the company account: a D.O. you owe for, or a payment you made. */
data class CompanyLine(
    val date: Long,
    val title: String,
    val detail: String,
    /** Amount you owe the company for this D.O. (0 on payment lines). */
    val bill: Double,
    /** Amount you paid (0 on D.O. lines). */
    val paid: Double,
    val balanceAfter: Double,
    val doNumber: String?,
    val paymentId: Long,
    val costMissing: Boolean
)

/** Positive balance = you owe the company. Negative = you have paid ahead (advance). */
data class CompanyAccount(
    val opening: Double,
    val balance: Double,
    /** Oldest first, each with its running balance. */
    val lines: List<CompanyLine>
)

fun buildCompanyAccount(deliveries: List<DeliveryRow>, payments: List<CompanyPayment>, opening: Double): CompanyAccount {
    val raw = ArrayList<CompanyLine>()
    deliveries.forEach { d ->
        raw.add(
            CompanyLine(
                date = d.date, title = "D.O. ${d.doNumber}",
                detail = "${d.orders.size} customer${if (d.orders.size == 1) "" else "s"} · ${d.bags} bags",
                bill = d.cost, paid = 0.0, balanceAfter = 0.0,
                doNumber = d.doNumber, paymentId = 0L, costMissing = !d.costComplete
            )
        )
    }
    payments.forEach { p ->
        raw.add(
            CompanyLine(
                date = p.date, title = "Payment to company",
                detail = listOf(p.mode, p.note).filter { it.isNotBlank() }.joinToString(" · "),
                bill = 0.0, paid = p.amount, balanceAfter = 0.0,
                doNumber = null, paymentId = p.id, costMissing = false
            )
        )
    }
    val sorted = raw.sortedWith(
        compareBy<CompanyLine>({ it.date }, { if (it.doNumber != null) 0 else 1 }, { it.paymentId }, { it.doNumber ?: "" })
    )
    var bal = opening
    val lines = sorted.map { l ->
        bal += l.bill - l.paid
        l.copy(balanceAfter = bal)
    }
    return CompanyAccount(opening, bal, lines)
}
