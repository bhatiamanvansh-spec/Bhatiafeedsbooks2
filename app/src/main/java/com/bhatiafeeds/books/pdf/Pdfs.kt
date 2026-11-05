package com.bhatiafeeds.books.pdf

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.bhatiafeeds.books.Fmt
import com.bhatiafeeds.books.Prefs
import com.bhatiafeeds.books.R
import com.bhatiafeeds.books.Share
import com.bhatiafeeds.books.data.CompanyAccount
import com.bhatiafeeds.books.data.CustomerBalance
import com.bhatiafeeds.books.data.DeliveryRow
import com.bhatiafeeds.books.data.PaymentRow
import com.bhatiafeeds.books.data.SaleRow
import java.io.File

/** Draws A4 PDFs (invoice and customer ledger) with Android's built-in PdfDocument. No GST, no extra libraries. */
object Pdfs {
    private const val W = 595
    private const val H = 842
    private const val M = 40f
    private const val RIGHT = W - M

    private fun paint(size: Float, bold: Boolean = false, color: Int = Color.BLACK, align: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
            this.color = color
            textAlign = align
        }

    private val NAVY = Color.parseColor("#1F3A8A")
    private val title = paint(26f, bold = true, color = NAVY, align = Paint.Align.CENTER)
    private val heading = paint(14f, bold = true, color = NAVY, align = Paint.Align.CENTER)
    private val h2 = paint(13f, bold = true, color = NAVY)
    private val navyBold = paint(11f, bold = true, color = NAVY)
    private val navyBoldRight = paint(11f, bold = true, color = NAVY, align = Paint.Align.RIGHT)
    private val navyBoldCenter10 = paint(10.5f, bold = true, color = NAVY, align = Paint.Align.CENTER)
    private val mutedCenter10 = paint(10f, color = Color.parseColor("#6B6E68"), align = Paint.Align.CENTER)
    private val body = paint(11f)
    private val bodyBold = paint(11f, bold = true)
    private val bodyRight = paint(11f, align = Paint.Align.RIGHT)
    private val bodyBoldRight = paint(11f, bold = true, align = Paint.Align.RIGHT)
    private val muted = paint(10f, color = Color.parseColor("#6B6E68"))
    private val mutedRight = paint(10f, color = Color.parseColor("#6B6E68"), align = Paint.Align.RIGHT)
    private val mutedCenter = paint(9f, color = Color.parseColor("#6B6E68"), align = Paint.Align.CENTER)
    private val green = paint(11f, bold = true, color = Color.parseColor("#2E6B45"))
    private val line = Paint().apply { color = Color.parseColor("#C9CBC3"); strokeWidth = 0.8f }
    private val shade = Paint().apply { color = Color.parseColor("#F0F1EA") }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    /** Tracks the current page and moves to a new one when content would run off the bottom. */
    private class Writer {
        val doc = PdfDocument()
        private var page: PdfDocument.Page? = null
        lateinit var canvas: Canvas
        var y = M
        private var n = 0

        fun newPage() {
            page?.let { doc.finishPage(it) }
            n++
            val p = doc.startPage(PdfDocument.PageInfo.Builder(W, H, n).create())
            page = p
            canvas = p.canvas
            canvas.drawText("Page $n", W / 2f, H - 20f, mutedCenter)
            y = M
        }

        /** Returns true if a fresh page was started. */
        fun ensure(space: Float): Boolean {
            if (y + space > H - 40f) { newPage(); return true }
            return false
        }

        fun rule() { canvas.drawLine(M, y, RIGHT, y, line) }

        fun save(file: File) {
            page?.let { doc.finishPage(it) }
            file.outputStream().use { doc.writeTo(it) }
            doc.close()
        }
    }

    private fun fit(s: String, p: Paint, maxWidth: Float): String {
        val count = p.breakText(s, true, maxWidth, null)
        return if (count >= s.length) s else s.take((count - 1).coerceAtLeast(0)) + "…"
    }

    /** Logo on both sides, shop name / address / phone centred between them, document title below. */
    private fun header(ctx: Context, w: Writer, prefs: Prefs, docTitle: String) {
        val size = 58f
        val top = w.y
        val logo = BitmapFactory.decodeResource(ctx.resources, R.drawable.logo)
        if (logo != null) {
            w.canvas.drawBitmap(logo, null, RectF(M, top, M + size, top + size), bitmapPaint)
            w.canvas.drawBitmap(logo, null, RectF(RIGHT - size, top, RIGHT, top + size), bitmapPaint)
        }
        val cx = W / 2f
        val ik = BitmapFactory.decodeResource(ctx.resources, R.drawable.ik_onkar)
        if (ik != null) {
            val ih = 24f
            val iw = ih * ik.width / ik.height
            w.canvas.drawBitmap(ik, null, RectF(cx - iw / 2, top, cx + iw / 2, top + ih), bitmapPaint)
        }
        w.canvas.drawText(prefs.businessName, cx, top + 52f, title)
        var ty = top + 68f
        prefs.businessAddress.lines().filter { it.isNotBlank() }.forEach {
            w.canvas.drawText(it, cx, ty, navyBoldCenter10); ty += 13f
        }
        if (prefs.businessPhone.isNotBlank()) {
            w.canvas.drawText("Mob. ${prefs.businessPhone}", cx, ty, navyBoldCenter10); ty += 13f
        }
        w.y = maxOf(ty, top + size) + 8f
        w.rule()
        w.y += 22f
        w.canvas.drawText(docTitle, cx, w.y, heading)
        w.y += 26f
    }

    // ------------------------------------------------------------------ Invoice

    fun invoice(ctx: Context, prefs: Prefs, sale: SaleRow, customer: CustomerBalance?): File {
        val w = Writer()
        w.newPage()
        header(ctx, w, prefs, "INVOICE")

        // Billed-to (left) and invoice details (right)
        val top = w.y
        w.canvas.drawText("Billed to", M, top, navyBold)
        w.canvas.drawText(fit(sale.customerName, h2, 270f), M, top + 16f, h2)
        var ly = top + 32f
        customer?.address?.lines()?.filter { it.isNotBlank() }?.forEach {
            w.canvas.drawText(fit(it, body, 270f), M, ly, body); ly += 14f
        }
        if (!customer?.phone.isNullOrBlank()) { w.canvas.drawText("Phone: ${customer?.phone}", M, ly, body); ly += 14f }

        var ry = top
        fun meta(label: String, value: String, bold: Boolean = false) {
            w.canvas.drawText(label, RIGHT - 130f, ry, mutedRight)
            w.canvas.drawText(fit(value, bodyRight, 125f), RIGHT, ry, if (bold) bodyBoldRight else bodyRight)
            ry += 18f
        }
        meta("Invoice / D.O. no.", sale.doNumber, bold = true)
        meta("Date", Fmt.date(sale.date))
        if (sale.driverName.isNotBlank()) meta("Driver", sale.driverName)
        if (sale.driverPhone.isNotBlank()) meta("Driver phone", sale.driverPhone)
        if (sale.vehicle.isNotBlank()) meta("Vehicle no.", sale.vehicle)
        w.y = maxOf(ly, ry) + 10f

        // Product table
        val cDesc = M + 6f
        val cBags = 345f
        val cRate = 440f
        val cAmt = RIGHT - 6f
        fun tableHeader() {
            w.canvas.drawRect(M, w.y - 14f, RIGHT, w.y + 8f, shade)
            w.canvas.drawText("Product", cDesc, w.y, navyBold)
            w.canvas.drawText("Bags", cBags, w.y, navyBoldRight)
            w.canvas.drawText("Rate (₹)", cRate, w.y, navyBoldRight)
            w.canvas.drawText("Amount (₹)", cAmt, w.y, navyBoldRight)
            w.y += 26f
        }
        tableHeader()
        for (item in sale.items) {
            if (w.ensure(34f)) { w.y += 14f; tableHeader() }
            w.canvas.drawText(fit(item.productName, body, cBags - 70f - cDesc), cDesc, w.y, body)
            w.canvas.drawText(item.bags.toString(), cBags, w.y, bodyRight)
            w.canvas.drawText(Fmt.number(item.rate), cRate, w.y, bodyRight)
            w.canvas.drawText(Fmt.number(item.amount), cAmt, w.y, bodyRight)
            w.y += 8f
            w.rule()
            w.y += 16f
        }

        // Totals
        w.ensure(120f)
        w.canvas.drawText("Total", cDesc, w.y, bodyBold)
        w.canvas.drawText(sale.bags.toString(), cBags, w.y, bodyBoldRight)
        w.canvas.drawText(Fmt.number(sale.total), cAmt, w.y, bodyBoldRight)
        w.y += 24f

        fun totalRow(label: String, value: String, bold: Boolean = false, p: Paint? = null) {
            w.canvas.drawText(label, 330f, w.y, p ?: if (bold) bodyBold else body)
            w.canvas.drawText(value, cAmt, w.y, if (bold) bodyBoldRight else bodyRight)
            w.y += 20f
        }
        totalRow("Received", Fmt.money(sale.received))
        w.rule()
        w.y += 18f
        if (sale.due < -0.005) {
            totalRow("Extra paid (added to your credit)", Fmt.money(-sale.due), bold = true, p = green)
        } else {
            totalRow("Balance due on this invoice", Fmt.money(maxOf(sale.due, 0.0)), bold = true)
        }
        w.ensure(110f)
        val fy = maxOf(w.y + 30f, H - 100f)
        var by = fy
        val bank = prefs.bankDetails.lines().filter { it.isNotBlank() }
        if (bank.isNotEmpty()) {
            w.canvas.drawText("Bank details", M, by, navyBold); by += 14f
            bank.take(4).forEach { w.canvas.drawText(fit(it, navyBold, 260f), M, by, navyBold); by += 14f }
        }
        w.canvas.drawText("For ${prefs.businessName}", RIGHT, fy + 40f, navyBoldRight)

        val file = Share.cacheFile(ctx, "Invoice_${Share.safeName(sale.doNumber)}_${Share.safeName(sale.customerName)}.pdf")
        w.save(file)
        return file
    }

    // ------------------------------------------------------------------ Ledger

    private class LedgerLine(
        val date: Long, val order: Long, val text: String, val bill: Double?, val received: Double?
    )

    fun ledger(
        ctx: Context, prefs: Prefs, customer: CustomerBalance,
        sales: List<SaleRow>, payments: List<PaymentRow>, today: Long
    ): File {
        val lines = ArrayList<LedgerLine>()
        sales.forEach { s ->
            val what = if (s.items.size == 1) {
                val i = s.items[0]
                "${i.productName} · ${i.bags} bags × ${Fmt.number(i.rate)}"
            } else {
                s.items.joinToString(", ") { "${it.productName} ${it.bags}" } + " · ${s.bags} bags"
            }
            lines.add(
                LedgerLine(
                    s.date, s.id * 2, "D.O. ${s.doNumber} · $what",
                    s.total, if (s.received > 0.005) s.received else null
                )
            )
        }
        payments.forEach {
            lines.add(LedgerLine(it.date, it.id * 2 + 1, "Payment received" + if (it.note.isNotBlank()) " · ${it.note}" else "", null, it.amount))
        }
        lines.sortWith(compareBy<LedgerLine> { it.date }.thenBy { it.order })

        val xDate = M
        val xText = 112f
        val rBill = 385f
        val rRecv = 465f
        val rBal = RIGHT

        val w = Writer()
        w.newPage()
        header(ctx, w, prefs, "STATEMENT OF ACCOUNT")

        w.canvas.drawText(customer.name, M, w.y, h2)
        w.canvas.drawText("As on ${Fmt.date(today)}", RIGHT, w.y, bodyRight)
        w.y += 15f
        if (customer.phone.isNotBlank()) { w.canvas.drawText("Phone: ${customer.phone}", M, w.y, body); w.y += 14f }
        customer.address.lines().filter { it.isNotBlank() }.forEach { w.canvas.drawText(fit(it, body, 300f), M, w.y, body); w.y += 14f }
        w.y += 14f

        fun tableHeader() {
            w.canvas.drawRect(M, w.y - 13f, RIGHT, w.y + 7f, shade)
            w.canvas.drawText("Date", xDate + 4f, w.y, navyBold)
            w.canvas.drawText("Particulars", xText, w.y, navyBold)
            w.canvas.drawText("Bill (₹)", rBill, w.y, navyBoldRight)
            w.canvas.drawText("Received (₹)", rRecv, w.y, navyBoldRight)
            w.canvas.drawText("Balance (₹)", rBal - 4f, w.y, navyBoldRight)
            w.y += 20f
        }
        tableHeader()

        var balance = customer.openingBalance
        if (customer.openingBalance != 0.0) {
            w.canvas.drawText("Opening balance", xText, w.y, body)
            w.canvas.drawText(Fmt.number(balance), rBal - 4f, w.y, bodyRight)
            w.y += 17f
        }
        for (l in lines) {
            if (w.ensure(30f)) { tableHeader() }
            balance += (l.bill ?: 0.0) - (l.received ?: 0.0)
            w.canvas.drawText(Fmt.date(l.date), xDate + 4f, w.y, body)
            w.canvas.drawText(fit(l.text, body, rBill - 100f - xText), xText, w.y, body)
            if (l.bill != null) w.canvas.drawText(Fmt.number(l.bill), rBill, w.y, bodyRight)
            if (l.received != null) w.canvas.drawText(Fmt.number(l.received), rRecv, w.y, bodyRight)
            w.canvas.drawText(Fmt.number(balance), rBal - 4f, w.y, bodyRight)
            w.y += 6f
            w.rule()
            w.y += 12f
        }

        w.ensure(60f)
        w.y += 10f
        val closing = customer.balance
        val label = when {
            closing > 0.005 -> "Balance due"
            closing < -0.005 -> "Credit balance"
            else -> "Balance"
        }
        w.canvas.drawText(label, 380f, w.y, bodyBold)
        w.canvas.drawText(Fmt.money(kotlin.math.abs(closing)), rBal - 4f, w.y, bodyBoldRight)
        if (closing < -0.005) {
            w.y += 16f
            w.canvas.drawText("Credit is adjusted automatically against future orders.", M, w.y, muted)
        }

        val file = Share.cacheFile(ctx, "Ledger_${Share.safeName(customer.name)}_${Fmt.isoDate(today)}.pdf")
        w.save(file)
        return file
    }

    // ------------------------------------------------------------------ Delivery slip

    /** One page for the driver: which customer gets how many bags of what, and how much to collect on delivery. */
    fun deliverySlip(ctx: Context, prefs: Prefs, d: DeliveryRow, customers: List<CustomerBalance>): File {
        val w = Writer()
        w.newPage()
        header(ctx, w, prefs, "DELIVERY SLIP")

        w.canvas.drawText("D.O. no.", M, w.y, muted)
        w.canvas.drawText(d.doNumber, M + 60f, w.y, h2)
        w.canvas.drawText("Date: ${Fmt.date(d.date)}", RIGHT, w.y, bodyRight)
        w.y += 17f
        if (d.driverName.isNotBlank()) {
            w.canvas.drawText(
                "Driver: ${d.driverName}" + if (d.driverPhone.isNotBlank()) " · ${d.driverPhone}" else "",
                M, w.y, body
            )
            if (d.vehicle.isNotBlank()) w.canvas.drawText("Vehicle: ${d.vehicle}", RIGHT, w.y, bodyRight)
            w.y += 16f
        }
        w.y += 8f

        w.canvas.drawRect(M, w.y - 14f, RIGHT, w.y + 8f, shade)
        w.canvas.drawText("Customer / product", M + 6f, w.y, navyBold)
        w.canvas.drawText("Bags", RIGHT - 110f, w.y, navyBoldRight)
        w.canvas.drawText("To collect (₹)", RIGHT - 6f, w.y, navyBoldRight)
        w.y += 28f

        d.orders.forEachIndexed { index, o ->
            val cust = customers.firstOrNull { it.id == o.customerId }
            val extra = listOfNotNull(
                cust?.address?.lines()?.firstOrNull { it.isNotBlank() },
                cust?.phone?.takeIf { it.isNotBlank() }?.let { "Phone: $it" }
            ).joinToString(" · ")
            w.ensure(46f + o.items.size * 15f)
            w.canvas.drawText(fit("${index + 1}. ${o.customerName}", bodyBold, 300f), M + 6f, w.y, bodyBold)
            w.canvas.drawText(o.bags.toString(), RIGHT - 110f, w.y, bodyBoldRight)
            val collect = maxOf(o.due, 0.0)
            w.canvas.drawText(if (collect > 0.005) Fmt.number(collect) else "Paid", RIGHT - 6f, w.y, bodyBoldRight)
            w.y += 14f
            if (extra.isNotBlank()) {
                w.canvas.drawText(fit(extra, muted, 340f), M + 18f, w.y, muted)
                w.y += 13f
            }
            o.items.forEach { i ->
                w.canvas.drawText(fit(i.productName, body, 300f), M + 18f, w.y, body)
                w.canvas.drawText(i.bags.toString(), RIGHT - 110f, w.y, bodyRight)
                w.y += 14f
            }
            w.y += 4f
            w.rule()
            w.y += 16f
        }

        w.ensure(50f)
        w.canvas.drawText("Total bags", M + 6f, w.y, bodyBold)
        w.canvas.drawText(d.bags.toString(), RIGHT - 110f, w.y, bodyBoldRight)
        w.canvas.drawText(Fmt.number(d.dueTotal), RIGHT - 6f, w.y, bodyBoldRight)

        val file = Share.cacheFile(ctx, "DeliverySlip_${Share.safeName(d.doNumber)}.pdf")
        w.save(file)
        return file
    }

    // ------------------------------------------------------------------ Company statement

    fun companyStatement(ctx: Context, prefs: Prefs, companyName: String, acct: CompanyAccount, today: Long): File {
        val xDate = M
        val xText = 112f
        val rBill = 385f
        val rPaid = 465f
        val rBal = RIGHT

        val w = Writer()
        w.newPage()
        header(ctx, w, prefs, "COMPANY ACCOUNT")
        w.canvas.drawText(companyName, M, w.y, h2)
        w.canvas.drawText("As on ${Fmt.date(today)}", RIGHT, w.y, bodyRight)
        w.y += 26f

        fun tableHeader() {
            w.canvas.drawRect(M, w.y - 13f, RIGHT, w.y + 7f, shade)
            w.canvas.drawText("Date", xDate + 4f, w.y, navyBold)
            w.canvas.drawText("Particulars", xText, w.y, navyBold)
            w.canvas.drawText("D.O. cost (₹)", rBill, w.y, navyBoldRight)
            w.canvas.drawText("Paid (₹)", rPaid, w.y, navyBoldRight)
            w.canvas.drawText("Balance (₹)", rBal - 4f, w.y, navyBoldRight)
            w.y += 20f
        }
        tableHeader()

        if (acct.opening != 0.0) {
            w.canvas.drawText("Opening balance", xText, w.y, body)
            w.canvas.drawText(Fmt.number(acct.opening), rBal - 4f, w.y, bodyRight)
            w.y += 17f
        }
        for (l in acct.lines) {
            if (w.ensure(30f)) tableHeader()
            val text = l.title + if (l.detail.isNotBlank()) " · ${l.detail}" else ""
            w.canvas.drawText(Fmt.date(l.date), xDate + 4f, w.y, body)
            w.canvas.drawText(fit(text, body, rBill - 100f - xText), xText, w.y, body)
            if (l.doNumber != null) w.canvas.drawText(Fmt.number(l.bill), rBill, w.y, bodyRight)
            else w.canvas.drawText(Fmt.number(l.paid), rPaid, w.y, bodyRight)
            w.canvas.drawText(Fmt.number(l.balanceAfter), rBal - 4f, w.y, bodyRight)
            w.y += 6f
            w.rule()
            w.y += 12f
        }

        w.ensure(60f)
        w.y += 10f
        val closing = acct.balance
        val label = when {
            closing > 0.005 -> "You owe"
            closing < -0.005 -> "Advance paid"
            else -> "Balance"
        }
        w.canvas.drawText(label, 380f, w.y, bodyBold)
        w.canvas.drawText(Fmt.money(kotlin.math.abs(closing)), rBal - 4f, w.y, bodyBoldRight)

        val file = Share.cacheFile(ctx, "CompanyAccount_${Share.safeName(companyName)}_${Fmt.isoDate(today)}.pdf")
        w.save(file)
        return file
    }
}
