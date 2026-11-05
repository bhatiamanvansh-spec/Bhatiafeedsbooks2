package com.bhatiafeeds.books.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

private const val BALANCE_SQL = """
    SELECT c.id AS id, c.name AS name, c.address AS address, c.phone AS phone,
           c.openingBalance AS openingBalance,
           c.openingBalance
             + COALESCE((SELECT SUM(i.bags * i.rate) FROM sale_items i
                         JOIN sales s ON s.id = i.saleId
                         WHERE s.customerId = c.id AND s.deleted = 0), 0)
             - COALESCE((SELECT SUM(s.received) FROM sales s
                         WHERE s.customerId = c.id AND s.deleted = 0), 0)
             - COALESCE((SELECT SUM(p.amount) FROM payments p
                         WHERE p.customerId = c.id AND p.deleted = 0), 0) AS balance,
           MAX(
             COALESCE((SELECT MAX(s.date) FROM sales s WHERE s.customerId = c.id AND s.deleted = 0), 0),
             COALESCE((SELECT MAX(p.date) FROM payments p WHERE p.customerId = c.id AND p.deleted = 0), 0)
           ) AS lastActivity
    FROM customers c
"""

@Dao
interface CustomerDao {
    @Insert suspend fun insert(c: Customer): Long
    @Update suspend fun update(c: Customer)

    @Query("UPDATE customers SET deleted = :deleted WHERE id = :id")
    suspend fun setDeleted(id: Long, deleted: Boolean)

    @Query("$BALANCE_SQL WHERE c.deleted = 0 ORDER BY c.name COLLATE NOCASE")
    fun balances(): Flow<List<CustomerBalance>>

    @Query("$BALANCE_SQL WHERE c.deleted = 1 ORDER BY c.name COLLATE NOCASE")
    fun deletedBalances(): Flow<List<CustomerBalance>>
}

@Dao
interface ProductDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(p: Product): Long

    @Query("SELECT * FROM products WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): Product?

    @Query("UPDATE products SET deleted = :deleted WHERE id = :id")
    suspend fun setDeleted(id: Long, deleted: Boolean)

    @Query("SELECT * FROM products WHERE deleted = 0 ORDER BY name COLLATE NOCASE")
    fun all(): Flow<List<Product>>
}

@Dao
interface DriverDao {
    @Insert suspend fun insert(d: Driver): Long
    @Update suspend fun update(d: Driver)

    @Query("UPDATE drivers SET deleted = :deleted WHERE id = :id")
    suspend fun setDeleted(id: Long, deleted: Boolean)

    @Query("SELECT * FROM drivers WHERE deleted = 0 ORDER BY name COLLATE NOCASE")
    fun all(): Flow<List<Driver>>

    @Query("SELECT * FROM drivers WHERE deleted = 1 ORDER BY name COLLATE NOCASE")
    fun trashed(): Flow<List<Driver>>
}

private const val SALE_SELECT = """
    SELECT s.id AS id, s.date AS date, s.doNumber AS doNumber,
           s.customerId AS customerId, c.name AS customerName,
           s.companyId AS companyId, COALESCE(co.name, '') AS companyName,
           s.driverId AS driverId, COALESCE(d.name, '') AS driverName,
           COALESCE(d.phone, '') AS driverPhone, COALESCE(d.vehicleNo, '') AS driverVehicle,
           s.vehicleNo AS vehicleNo, s.received AS received, s.receivedMode AS receivedMode,
           s.deleted AS deleted
    FROM sales s
    JOIN customers c ON c.id = s.customerId
    LEFT JOIN companies co ON co.id = s.companyId
    LEFT JOIN drivers d ON d.id = s.driverId
"""

@Dao
interface SaleDao {
    @Insert suspend fun insert(s: Sale): Long
    @Update suspend fun update(s: Sale)

    @Insert suspend fun insertItems(items: List<SaleItem>)

    @Query("DELETE FROM sale_items WHERE saleId = :saleId")
    suspend fun deleteItems(saleId: Long)

    @Query("UPDATE sales SET deleted = :deleted WHERE id = :id")
    suspend fun setDeleted(id: Long, deleted: Boolean)

    /** Used by the duplicate D.O. warning. excludeId = 0 when adding a new entry. */
    @Query("SELECT COUNT(*) FROM sales WHERE doNumber = :doNumber COLLATE NOCASE AND companyId = :companyId AND deleted = 0 AND id != :excludeId")
    suspend fun countDo(doNumber: String, companyId: Long, excludeId: Long): Int

    @Query("$SALE_SELECT WHERE s.deleted = 0 ORDER BY s.date DESC, s.id DESC")
    fun all(): Flow<List<SaleHead>>

    @Query("$SALE_SELECT WHERE s.deleted = 1 ORDER BY s.date DESC, s.id DESC")
    fun trashed(): Flow<List<SaleHead>>

    @Query("SELECT * FROM sale_items ORDER BY id")
    fun items(): Flow<List<SaleItem>>
}

private const val PAYMENT_SELECT = """
    SELECT p.id AS id, p.date AS date, p.customerId AS customerId, c.name AS customerName,
           p.amount AS amount, p.note AS note, p.mode AS mode, p.deleted AS deleted
    FROM payments p JOIN customers c ON c.id = p.customerId
"""

@Dao
interface PaymentDao {
    @Insert suspend fun insert(p: Payment): Long
    @Update suspend fun update(p: Payment)

    @Query("UPDATE payments SET deleted = :deleted WHERE id = :id")
    suspend fun setDeleted(id: Long, deleted: Boolean)

    @Query("$PAYMENT_SELECT WHERE p.deleted = 0 ORDER BY p.date DESC, p.id DESC")
    fun all(): Flow<List<PaymentRow>>

    @Query("$PAYMENT_SELECT WHERE p.deleted = 1 ORDER BY p.date DESC, p.id DESC")
    fun trashed(): Flow<List<PaymentRow>>
}

@Dao
interface CompaniesDao {
    @Insert suspend fun insert(c: Company): Long
    @Update suspend fun update(c: Company)

    @Query("UPDATE companies SET deleted = :deleted WHERE id = :id")
    suspend fun setDeleted(id: Long, deleted: Boolean)

    @Query("SELECT * FROM companies WHERE deleted = 0 ORDER BY name COLLATE NOCASE")
    fun all(): Flow<List<Company>>

    @Query("SELECT * FROM companies WHERE deleted = 1 ORDER BY name COLLATE NOCASE")
    fun trashed(): Flow<List<Company>>
}

/** Payments made to a company. */
@Dao
interface CompanyDao {
    @Insert suspend fun insert(p: CompanyPayment): Long
    @Update suspend fun update(p: CompanyPayment)

    @Query("UPDATE company_payments SET deleted = :deleted WHERE id = :id")
    suspend fun setDeleted(id: Long, deleted: Boolean)

    @Query("SELECT * FROM company_payments WHERE deleted = 0 ORDER BY date DESC, id DESC")
    fun all(): Flow<List<CompanyPayment>>

    @Query("SELECT * FROM company_payments WHERE deleted = 1 ORDER BY date DESC, id DESC")
    fun trashed(): Flow<List<CompanyPayment>>
}

@Dao
interface SettingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(s: Setting)

    @Query("SELECT * FROM settings")
    fun all(): Flow<List<Setting>>
}

@Dao
interface PendingDao {
    @Insert suspend fun insert(p: PendingOrder): Long
    @Update suspend fun update(p: PendingOrder)

    @Query("UPDATE pending_orders SET deleted = :deleted WHERE id = :id")
    suspend fun setDeleted(id: Long, deleted: Boolean)

    @Query("UPDATE pending_orders SET booked = 1 WHERE id = :id")
    suspend fun markBooked(id: Long)

    /** Orders still waiting for a D.O. */
    @Query(
        """
        SELECT p.id AS id, p.date AS date, p.customerId AS customerId, c.name AS customerName,
               p.productName AS productName, p.bags AS bags, p.rate AS rate, p.note AS note
        FROM pending_orders p JOIN customers c ON c.id = p.customerId
        WHERE p.deleted = 0 AND p.booked = 0
        ORDER BY p.date DESC, p.id DESC
        """
    )
    fun open(): Flow<List<PendingRow>>
}
