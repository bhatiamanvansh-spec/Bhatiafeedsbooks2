package com.bhatiafeeds.books.ui

/** Detail screens that open on top of the five bottom tabs. */
sealed interface Screen {
    data class SaleDetail(val id: Long) : Screen
    /** Create or edit a D.O. (one D.O. number, several customers). Blank doNumber = new D.O. */
    data class DoForm(val doNumber: String = "", val companyId: Long = 0, val customerId: Long = 0) : Screen
    /** Add or edit an order that has no D.O. yet. */
    data class PendingForm(val id: Long = 0) : Screen
    data class DoDetail(val doNumber: String, val companyId: Long) : Screen
    /** Add or edit a payment to a company. [id] = 0 for a new payment to [companyId]. */
    data class CompanyPaymentForm(val id: Long = 0, val companyId: Long = 0) : Screen
    /** One company's account: what you owe it, payments, D.O.s. */
    data class Company(val id: Long) : Screen
    /** The list of all companies. */
    data object Companies : Screen
    data class PaymentForm(val id: Long = 0, val customerId: Long = 0) : Screen
    data class CustomerDetail(val id: Long) : Screen
    data object Products : Screen
    data object Drivers : Screen
    data object Reports : Screen
    data object Trash : Screen
    data object Data : Screen
    data object Settings : Screen
}

class Nav(
    val push: (Screen) -> Unit,
    val pop: () -> Unit,
    val goTab: (Int) -> Unit,
    val message: (String) -> Unit,
    val undo: (String, () -> Unit) -> Unit
)
