package com.bhatiafeeds.books

import android.content.Context
import com.bhatiafeeds.books.data.CompanyAccount
import com.bhatiafeeds.books.data.CustomerBalance
import com.bhatiafeeds.books.data.PaymentEntry
import com.bhatiafeeds.books.data.SaleRow
import java.io.File
import java.time.LocalDate

/** CSV files open directly in Excel / Google Sheets. A BOM is added so Excel reads them as UTF-8. */
object Csv {
    private fun cell(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    private fun num(v: Double): String = (Math.round(v * 100) / 100.0).toString().removeSuffix(".0")

    private fun write(ctx: Context, prefix: String, header: List<String>, rows: List<List<String>>): File {
        val f = Share.cacheFile(ctx, "${prefix}_${LocalDate.now()}.csv")
        val sb = StringBuilder("﻿")
        sb.append(header.joinToString(",") { cell(it) }).append("\r\n")
        rows.forEach { r -> sb.append(r.joinToString(",") { cell(it) }).append("\r\n") }
        f.writeText(sb.toString(), Charsets.UTF_8)
        return f
    }

    /** One row per product line. Invoice-level totals appear only on the first line of each invoice. */
    fun sales(ctx: Context, list: List<SaleRow>): File {
        val rows = ArrayList<List<String>>()
        list.sortedWith(compareBy<SaleRow> { it.date }.thenBy { it.id }).forEach { s ->
            s.items.forEachIndexed { index, i ->
                val first = index == 0
                rows.add(
                    listOf(
                        Fmt.isoDate(s.date), s.doNumber, s.companyName, s.customerName, i.productName, i.bags.toString(),
                        num(i.rate), num(i.amount),
                        if (i.costRate > 0.0) num(i.costRate) else "",
                        if (i.costRate > 0.0) num(i.amount - i.cost) else "",
                        if (first) num(s.total) else "",
                        if (first) num(s.received) else "",
                        if (first) s.receivedMode else "",
                        if (first) num(s.due) else "",
                        s.driverName, s.vehicle
                    )
                )
            }
        }
        return write(
            ctx, "Sales",
            listOf(
                "Date", "D.O. No", "Company", "Customer", "Product", "Bags", "Rate per bag", "Line amount",
                "Company cost per bag", "Line profit",
                "Invoice total", "Received", "Payment mode", "Due", "Driver", "Vehicle No"
            ),
            rows
        )
    }

    fun payments(ctx: Context, list: List<PaymentEntry>): File = write(
        ctx, "Payments",
        listOf("Date", "Customer", "Amount", "Mode", "Type", "Note"),
        list.sortedBy { it.date }.map {
            listOf(
                Fmt.isoDate(it.date), it.customerName, num(it.amount), it.mode,
                if (it.saleId != 0L) "With invoice" else "Payment", it.note
            )
        }
    )

    fun customers(ctx: Context, list: List<CustomerBalance>): File = write(
        ctx, "Customers",
        listOf("Name", "Phone", "Address", "Opening balance", "Current balance (negative = credit)"),
        list.map { listOf(it.name, it.phone, it.address, num(it.openingBalance), num(it.balance)) }
    )

    /** The company account, oldest first, with the running balance (positive = you owe the company). */
    fun company(ctx: Context, companyName: String, acct: CompanyAccount): File {
        val rows = ArrayList<List<String>>()
        if (acct.opening != 0.0) rows.add(listOf("", "Opening balance", "", "", num(acct.opening)))
        acct.lines.forEach { l ->
            rows.add(
                listOf(
                    Fmt.isoDate(l.date),
                    l.title + if (l.detail.isNotBlank()) " · ${l.detail}" else "",
                    if (l.doNumber != null) num(l.bill) else "",
                    if (l.doNumber == null) num(l.paid) else "",
                    num(l.balanceAfter)
                )
            )
        }
        return write(
            ctx, "CompanyAccount_${Share.safeName(companyName)}",
            listOf("Date", "Particulars", "Owed to company", "Paid to company", "Balance (you owe)"),
            rows
        )
    }
}
