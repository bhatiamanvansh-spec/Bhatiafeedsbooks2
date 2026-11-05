package com.bhatiafeeds.books.data

import android.content.Context
import androidx.room.withTransaction
import java.time.LocalDate

/**
 * Single place for all data operations. Balances are never stored as a loose number:
 * they are always computed from opening balance + invoice totals - money received - payments,
 * so editing or deleting any entry can never leave a balance out of sync.
 * A negative balance is credit the customer holds with you; it is used up automatically by the next invoices.
 */
class Repository(context: Context) {
    private val db = AppDatabase.get(context)
    val customers = db.customers()
    val products = db.products()
    val drivers = db.drivers()
    val sales = db.sales()
    val payments = db.payments()
    val company = db.company()
    val settings = db.settings()

    // ---- Customers ----
    suspend fun saveCustomer(c: Customer) {
        if (c.id == 0L) customers.insert(c) else customers.update(c)
    }
    /** Adds a customer and returns its new id (used by quick-add inside pickers). */
    suspend fun addCustomer(c: Customer): Long = customers.insert(c)
    suspend fun deleteCustomer(id: Long) = customers.setDeleted(id, true)
    suspend fun restoreCustomer(id: Long) = customers.setDeleted(id, false)

    // ---- Drivers ----
    suspend fun saveDriver(d: Driver) {
        if (d.id == 0L) drivers.insert(d) else drivers.update(d)
    }
    suspend fun addDriver(d: Driver): Long = drivers.insert(d)
    suspend fun deleteDriver(id: Long) = drivers.setDeleted(id, true)
    suspend fun restoreDriver(id: Long) = drivers.setDeleted(id, false)

    // ---- Products ----
    /** Returns false if a product with this name already exists. */
    suspend fun addProduct(name: String): Boolean {
        val clean = name.trim()
        if (clean.isEmpty()) return false
        val existing = products.findByName(clean)
        if (existing != null) {
            if (existing.deleted) { products.setDeleted(existing.id, false); return true }
            return false
        }
        return products.insert(Product(name = clean)) != -1L
    }
    suspend fun deleteProduct(id: Long) = products.setDeleted(id, true)

    // ---- Sales (invoices) ----
    /** Saves the invoice and all its product lines together, or nothing at all. */
    suspend fun saveSale(s: Sale, items: List<SaleItem>) {
        db.withTransaction {
            val saleId: Long
            if (s.id == 0L) {
                saleId = sales.insert(s)
            } else {
                sales.update(s)
                sales.deleteItems(s.id)
                saleId = s.id
            }
            sales.insertItems(items.map { it.copy(id = 0, saleId = saleId) })
        }
    }
    suspend fun deleteSale(id: Long) = sales.setDeleted(id, true)
    suspend fun restoreSale(id: Long) = sales.setDeleted(id, false)
    suspend fun isDuplicateDo(doNumber: String, excludeId: Long): Boolean =
        doNumber.isNotBlank() && sales.countDo(doNumber.trim(), excludeId) > 0

    /** One customer's order inside a D.O. [saleId] is 0 for a new order. */
    class OrderInput(
        val saleId: Long,
        val customerId: Long,
        val received: Double,
        val mode: String,
        val legacyVehicle: String,
        val items: List<SaleItem>
    )

    /**
     * Saves a whole D.O.: every customer's order together, or nothing at all.
     * Orders in [removedSaleIds] (customers taken off the D.O. while editing) go to Trash.
     */
    suspend fun saveDelivery(
        date: Long, doNumber: String, driverId: Long,
        orders: List<OrderInput>, removedSaleIds: List<Long>
    ) {
        db.withTransaction {
            removedSaleIds.forEach { sales.setDeleted(it, true) }
            orders.forEach { o ->
                val sale = Sale(
                    id = o.saleId, date = date, doNumber = doNumber, customerId = o.customerId,
                    driverId = driverId, vehicleNo = o.legacyVehicle, received = o.received,
                    receivedMode = o.mode
                )
                val id: Long
                if (o.saleId == 0L) {
                    id = sales.insert(sale)
                } else {
                    sales.update(sale)
                    sales.deleteItems(o.saleId)
                    id = o.saleId
                }
                sales.insertItems(o.items.map { it.copy(id = 0, saleId = id) })
            }
        }
    }

    suspend fun setDeliveryDeleted(saleIds: List<Long>, deleted: Boolean) {
        db.withTransaction { saleIds.forEach { sales.setDeleted(it, deleted) } }
    }

    // ---- Company ----
    suspend fun saveCompanyPayment(p: CompanyPayment) {
        if (p.id == 0L) company.insert(p) else company.update(p)
    }
    suspend fun deleteCompanyPayment(id: Long) = company.setDeleted(id, true)
    suspend fun restoreCompanyPayment(id: Long) = company.setDeleted(id, false)
    suspend fun putSetting(name: String, value: String) = settings.put(Setting(name, value))

    // ---- Payments ----
    suspend fun savePayment(p: Payment) {
        if (p.id == 0L) payments.insert(p) else payments.update(p)
    }
    suspend fun deletePayment(id: Long) = payments.setDeleted(id, true)
    suspend fun restorePayment(id: Long) = payments.setDeleted(id, false)

    companion object {
        fun today(): Long = LocalDate.now().toEpochDay()
    }
}
