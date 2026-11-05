package com.bhatiafeeds.books.data

/** Rate you last gave this customer for this product, or 0 if none. [sales] must be newest first. */
fun lastRate(sales: List<SaleRow>, customerId: Long, product: String): Double {
    val name = product.trim()
    if (customerId == 0L || name.isEmpty()) return 0.0
    for (s in sales) {
        if (s.customerId != customerId) continue
        val line = s.items.firstOrNull { it.productName.trim().equals(name, ignoreCase = true) && it.rate > 0.0 }
        if (line != null) return line.rate
    }
    return 0.0
}

/** Cost per bag this company last charged you for this product, or 0 if none. [sales] must be newest first. */
fun lastCost(sales: List<SaleRow>, companyId: Long, product: String): Double {
    val name = product.trim()
    if (companyId == 0L || name.isEmpty()) return 0.0
    for (s in sales) {
        if (s.companyId != companyId) continue
        val line = s.items.firstOrNull { it.productName.trim().equals(name, ignoreCase = true) && it.costRate > 0.0 }
        if (line != null) return line.costRate
    }
    return 0.0
}

/** What customers owe, split by how old the unpaid invoices are. */
class Ageing(val recent: Double, val mid: Double, val old: Double) {
    val total: Double get() = recent + mid + old
}

/**
 * Payments always settle the oldest charges first, so the part of a balance that is still unpaid is made up of the
 * newest invoices. Walks each owing customer's invoices from newest to oldest until the balance is used up; anything
 * left over is the opening balance (the oldest of all). 0-7 days, 8-30 days, over 30 days.
 */
fun dueAgeing(customers: List<CustomerBalance>, sales: List<SaleRow>, today: Long): Ageing {
    val byCustomer = sales.groupBy { it.customerId }
    var recent = 0.0
    var mid = 0.0
    var old = 0.0
    for (c in customers) {
        var remaining = c.balance
        if (remaining <= 0.005) continue
        val invoices = byCustomer[c.id].orEmpty()
            .sortedWith(compareByDescending<SaleRow> { it.date }.thenByDescending { it.id })
        for (s in invoices) {
            if (remaining <= 0.005) break
            val part = minOf(s.total, remaining)
            if (part <= 0.0) continue
            remaining -= part
            val age = today - s.date
            when {
                age <= 7 -> recent += part
                age <= 30 -> mid += part
                else -> old += part
            }
        }
        if (remaining > 0.005) old += remaining
    }
    return Ageing(recent, mid, old)
}
