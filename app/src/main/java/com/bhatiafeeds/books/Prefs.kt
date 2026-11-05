package com.bhatiafeeds.books

import android.content.Context

/** Small settings store: business details (used on invoices), reminders, last backup day. */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    init {
        // One-time: fill in the shop's phone numbers if none were saved yet.
        if (!sp.getBoolean("phoneSeeded", false)) {
            val editor = sp.edit().putBoolean("phoneSeeded", true)
            if (sp.getString("bizPhone", "").isNullOrBlank()) editor.putString("bizPhone", DEFAULT_PHONE)
            editor.apply()
        }
        // v1.2: new address and bank details (only replaces the old default, never a custom address)
        if (!sp.getBoolean("v12Seeded", false)) {
            val editor = sp.edit().putBoolean("v12Seeded", true)
            val addr = sp.getString("bizAddress", null)
            if (addr == null || addr.trim() == "Dongargarh, Chhattisgarh") editor.putString("bizAddress", DEFAULT_ADDRESS)
            if (sp.getString("bizBank", "").isNullOrBlank()) editor.putString("bizBank", DEFAULT_BANK)
            editor.apply()
        }
    }

    var businessName: String
        get() = sp.getString("bizName", "Bhatia Feeds") ?: "Bhatia Feeds"
        set(v) = sp.edit().putString("bizName", v).apply()

    var businessAddress: String
        get() = sp.getString("bizAddress", DEFAULT_ADDRESS) ?: ""
        set(v) = sp.edit().putString("bizAddress", v).apply()

    var businessPhone: String
        get() = sp.getString("bizPhone", DEFAULT_PHONE) ?: ""
        set(v) = sp.edit().putString("bizPhone", v).apply()

    var bankDetails: String
        get() = sp.getString("bizBank", DEFAULT_BANK) ?: ""
        set(v) = sp.edit().putString("bizBank", v).apply()

    var remindersOn: Boolean
        get() = sp.getBoolean("remindersOn", true)
        set(v) = sp.edit().putBoolean("remindersOn", v).apply()

    /** A due is "old" when the customer has had no sale or payment for this many days. */
    var reminderDays: Int
        get() = sp.getInt("reminderDays", 30)
        set(v) = sp.edit().putInt("reminderDays", v).apply()

    /** Epoch-day of the last manual backup, or -1 if never. */
    var lastBackupDay: Long
        get() = sp.getLong("lastBackupDay", -1L)
        set(v) = sp.edit().putLong("lastBackupDay", v).apply()

    var askedNotificationPermission: Boolean
        get() = sp.getBoolean("askedNotif", false)
        set(v) = sp.edit().putBoolean("askedNotif", v).apply()

    companion object {
        const val DEFAULT_PHONE = "8319721001, 9893397595"
        const val DEFAULT_ADDRESS = "Dhusera, Dongargarh"
        const val DEFAULT_BANK = "Punjab and Sind Bank\nRajnandgaon\nA/C No. 15321100000051\nIFSC Code: PSIB0021532"
    }
}
