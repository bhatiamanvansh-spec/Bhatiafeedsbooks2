package com.bhatiafeeds.books

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bhatiafeeds.books.data.CompanyAccount
import com.bhatiafeeds.books.data.DeliveryRow
import com.bhatiafeeds.books.data.PaymentEntry
import com.bhatiafeeds.books.data.buildCompanyAccount
import com.bhatiafeeds.books.data.groupDeliveries
import com.bhatiafeeds.books.data.Repository
import com.bhatiafeeds.books.data.SaleHead
import com.bhatiafeeds.books.data.SaleItem
import com.bhatiafeeds.books.data.SaleRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val repo = Repository(app)
    val prefs = Prefs(app)

    private fun <T> Flow<T>.keep(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.Eagerly, initial)

    private fun attach(heads: List<SaleHead>, items: List<SaleItem>): List<SaleRow> {
        val byInvoice = items.groupBy { it.saleId }
        return heads.map { h ->
            SaleRow(
                id = h.id, date = h.date, doNumber = h.doNumber,
                customerId = h.customerId, customerName = h.customerName,
                companyId = h.companyId, companyName = h.companyName,
                driverId = h.driverId, driverName = h.driverName, driverPhone = h.driverPhone,
                driverVehicle = h.driverVehicle, legacyVehicle = h.vehicleNo,
                received = h.received, receivedMode = h.receivedMode, deleted = h.deleted,
                items = byInvoice[h.id].orEmpty()
            )
        }
    }

    val customers = repo.customers.balances().keep(emptyList())
    val deletedCustomers = repo.customers.deletedBalances().keep(emptyList())
    val products = repo.products.all().keep(emptyList())
    val drivers = repo.drivers.all().keep(emptyList())
    val deletedDrivers = repo.drivers.trashed().keep(emptyList())
    val sales = combine(repo.sales.all(), repo.sales.items()) { h, i -> attach(h, i) }.keep(emptyList())
    val trashedSales = combine(repo.sales.trashed(), repo.sales.items()) { h, i -> attach(h, i) }.keep(emptyList())
    val payments = repo.payments.all().keep(emptyList())
    val trashedPayments = repo.payments.trashed().keep(emptyList())
    val companyPayments = repo.company.all().keep(emptyList())
    val trashedCompanyPayments = repo.company.trashed().keep(emptyList())

    val pending = repo.pending.open().keep(emptyList())
    /** Which list the Sales tab shows (so Home can open it on Pending). */
    val salesView = MutableStateFlow(0)

    val companies = repo.companies.all().keep(emptyList())
    val deletedCompanies = repo.companies.trashed().keep(emptyList())

    /** Invoices grouped by D.O. number: one D.O. can carry several customers. */
    val deliveries: StateFlow<List<DeliveryRow>> = sales.map { groupDeliveries(it) }.keep(emptyList())

    /** One account per company (by company id): D.O. costs minus your payments, with a running balance. */
    val companyAccounts: StateFlow<Map<Long, CompanyAccount>> = combine(companies, deliveries, companyPayments) { cs, d, p ->
        cs.associate { c ->
            c.id to buildCompanyAccount(
                d.filter { it.companyId == c.id }, p.filter { it.companyId == c.id }, c.openingBalance
            )
        }
    }.keep(emptyMap())

    /**
     * Everything the customer has paid: payments recorded in the Payments section
     * plus the money received along with each invoice.
     */
    val paymentEntries: StateFlow<List<PaymentEntry>> = combine(payments, sales) { pays, invoices ->
        val standalone = pays.map {
            PaymentEntry("p${it.id}", it.date, it.customerId, it.customerName, it.amount, it.note, it.id, 0L, it.mode)
        }
        val withInvoice = invoices.filter { it.received > 0.005 }.map {
            PaymentEntry(
                "s${it.id}", it.date, it.customerId, it.customerName, it.received,
                "With invoice D.O. ${it.doNumber}", 0L, it.id, it.receivedMode
            )
        }
        (standalone + withInvoice).sortedWith(compareByDescending<PaymentEntry> { it.date }.thenByDescending { it.key })
    }.keep(emptyList())

    private val _today = MutableStateFlow(Repository.today())
    /** Today's epoch-day; refreshed when the app returns to the foreground so "Today" stays right after midnight. */
    val today: StateFlow<Long> = _today

    fun refreshDay() {
        _today.value = Repository.today()
    }

    fun run(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
