package com.bhatiafeeds.books.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Customer::class, Product::class, Driver::class, Sale::class, SaleItem::class, Payment::class,
        CompanyPayment::class, Setting::class, Company::class, PendingOrder::class
    ],
    version = AppDatabase.VERSION,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun customers(): CustomerDao
    abstract fun products(): ProductDao
    abstract fun drivers(): DriverDao
    abstract fun sales(): SaleDao
    abstract fun payments(): PaymentDao
    abstract fun company(): CompanyDao
    abstract fun companies(): CompaniesDao
    abstract fun pending(): PendingDao
    abstract fun settings(): SettingDao

    companion object {
        const val FILE_NAME = "bhatia_books.db"
        const val VERSION = 5

        /**
         * Version 1 -> 2: an invoice can now hold several products and have a driver.
         * Every old sale keeps its data: it becomes an invoice with one product line.
         * The old vehicle number is kept on the invoice so nothing is lost.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `drivers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `phone` TEXT NOT NULL, `vehicleNo` TEXT NOT NULL, `deleted` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sales_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`date` INTEGER NOT NULL, `doNumber` TEXT NOT NULL, `customerId` INTEGER NOT NULL, " +
                        "`driverId` INTEGER NOT NULL, `vehicleNo` TEXT NOT NULL, `received` REAL NOT NULL, " +
                        "`deleted` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sale_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`saleId` INTEGER NOT NULL, `productName` TEXT NOT NULL, `bags` INTEGER NOT NULL, " +
                        "`rate` REAL NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO sales_new (id, date, doNumber, customerId, driverId, vehicleNo, received, deleted) " +
                        "SELECT id, date, doNumber, customerId, 0, vehicleNo, received, deleted FROM sales"
                )
                db.execSQL(
                    "INSERT INTO sale_items (saleId, productName, bags, rate) " +
                        "SELECT id, productName, bags, rate FROM sales"
                )
                db.execSQL("DROP TABLE `sales`")
                db.execSQL("ALTER TABLE `sales_new` RENAME TO `sales`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_customerId` ON `sales` (`customerId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_date` ON `sales` (`date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_doNumber` ON `sales` (`doNumber`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_items_saleId` ON `sale_items` (`saleId`)")
            }
        }

        /**
         * Version 2 -> 3: cost per bag on each product line, payment modes, and the company account.
         * Nothing existing is changed or removed; new columns start empty.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sale_items` ADD COLUMN `costRate` REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `sales` ADD COLUMN `receivedMode` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `payments` ADD COLUMN `mode` TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `company_payments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`date` INTEGER NOT NULL, `amount` REAL NOT NULL, `mode` TEXT NOT NULL, " +
                        "`note` TEXT NOT NULL, `deleted` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_company_payments_date` ON `company_payments` (`date`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `settings` (`name` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`name`))"
                )
            }
        }

        /**
         * Version 3 -> 4: several companies. The one company you already had (name and opening balance
         * from Settings) becomes the first company, and every existing D.O. and payment to the company
         * is linked to it, so all balances stay exactly as they were.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `companies` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `address` TEXT NOT NULL, `phone` TEXT NOT NULL, " +
                        "`openingBalance` REAL NOT NULL, `deleted` INTEGER NOT NULL)"
                )
                db.execSQL("ALTER TABLE `sales` ADD COLUMN `companyId` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `company_payments` ADD COLUMN `companyId` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "INSERT INTO companies (name, address, phone, openingBalance, deleted) " +
                        "SELECT COALESCE(NULLIF((SELECT value FROM settings WHERE name = 'companyName'), ''), 'Company'), " +
                        "'', '', COALESCE(CAST((SELECT value FROM settings WHERE name = 'companyOpening') AS REAL), 0), 0 " +
                        "WHERE EXISTS (SELECT 1 FROM sales) OR EXISTS (SELECT 1 FROM company_payments) " +
                        "OR EXISTS (SELECT 1 FROM settings WHERE name IN ('companyName', 'companyOpening') AND value != '')"
                )
                db.execSQL("UPDATE sales SET companyId = COALESCE((SELECT MIN(id) FROM companies), 0)")
                db.execSQL("UPDATE company_payments SET companyId = COALESCE((SELECT MIN(id) FROM companies), 0)")
            }
        }

        /** Version 4 -> 5: pending orders (orders taken before a D.O. exists). Nothing existing is touched. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pending_orders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`date` INTEGER NOT NULL, `customerId` INTEGER NOT NULL, `productName` TEXT NOT NULL, " +
                        "`bags` INTEGER NOT NULL, `rate` REAL NOT NULL, `note` TEXT NOT NULL, " +
                        "`booked` INTEGER NOT NULL, `deleted` INTEGER NOT NULL)"
                )
            }
        }

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, FILE_NAME
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { instance = it }
            }

        /** Closes the open database so the file can be replaced during a restore. */
        fun close() {
            synchronized(this) {
                instance?.close()
                instance = null
            }
        }
    }
}
