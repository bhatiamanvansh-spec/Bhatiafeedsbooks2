package com.bhatiafeeds.books.ui

/** Detail screens that open on top of the five bottom tabs. */
sealed interface Screen {
    data class SaleDetail(val id: Long) : Screen
    /** Create or edit a D.O. (one D.O. number, several customers). Blank doNumber = new D.O. */
    data class DoForm(val doNumber: String = "", val customerId: Long = 0) : Screen
    data class DoDetail(val doNumber: String) : Screen
    data class CompanyPaymentForm(val id: Long = 0) : Screen
    data object Company : Screen
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
