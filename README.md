# Bhatia Feeds Books

A simple offline Android app for keeping your sales books: customers, products, drivers, invoices with several products, payments, credit balances, customer ledgers, invoice PDFs, reports, backup and reminders.

## Version 1.1 changes
- One invoice can hold several products.
- Driver name replaces the vehicle number; drivers are saved once (More > Drivers) and picked on each sale.
- Money received with an invoice also appears in the Payments tab.
- Extra money paid (with an invoice or as a payment) is saved as the customer's credit and is used automatically on their next orders.
- Invoice shows the logo (top left) and your phone numbers.

## Updating from version 1.0 (do this once, in this order)
The first version was signed with a temporary key, so Android will not install this one over it. Your data is safe if you follow these steps:
1. In the OLD app: More > Backup & export > **Save backup to Google Drive**. Wait for "Backup saved".
2. Upload the new project files to GitHub (replace the old ones), let the build finish, download the new APK.
3. Uninstall the old Bhatia Feeds Books from the phone.
4. Install the new APK.
5. Open it: More > Backup & export > **Restore from a backup** > pick the file you saved in step 1. The app restarts with all your old data (old sales become invoices with one product).
From now on every future update installs directly over the top, with no uninstalling.

## Get the APK
### Option A: build it free on GitHub (no installs)
1. Open your repository on github.com, click **Add file** > **Upload files**.
2. Drag everything inside the unzipped `BhatiaFeedsBooks` folder onto the page (replace the old files), then click **Commit changes**.
3. Open the **Actions** tab. The "Build APK" job runs by itself (about 5 to 8 minutes).
4. Open the finished run, scroll to **Artifacts**, download **BhatiaFeedsBooks-apk**, unzip it to get `app-debug.apk`.

### Option B: Android Studio
File > Open > this folder, wait for Gradle sync, then Build > Build APK(s).

## Your data
- Stored on the phone in a local database; works with no internet.
- Backup: More > Backup & export. Android also backs up to your Google account if Settings > Google > Backup is on.

## Business details and logo
- Name, address and phone numbers on invoices: More > Settings (phones default to 8319721001, 9893397595).
- Logo: `app/src/main/res/drawable-nodpi/logo.png` (the editable drawing is in `logo-source/khanda.svg`). To use a different logo, replace that PNG with your own square PNG of the same name and rebuild.

## What's new in v2.0
- **One D.O., several customers.** Create a D.O. once (number, date, driver) and add each customer's order inside it. Each customer still gets their own invoice and ledger entry. Sales tab: "By D.O." and "By customer" views.
- **Company account.** Enter the company's cost per bag on each product line (optional). The Company account (More menu and Home) shows what you owe, your bulk payments to the company, advance paid, and a running balance. Statement PDF and CSV available.
- **Payment modes.** Cash, UPI or Bank transfer on every customer payment, payment received with an order, and company payment. Filter the Payments tab by mode; Monthly report shows the split and profit.
- **Delivery slip.** Per-D.O. PDF for the driver: customers, bags and how much to collect.
- Set the company name and any opening balance owed under More > Settings > Company account.
- Existing data is upgraded automatically (database v3). Old entries that share a D.O. number are grouped together.

## What's new in v2.1
- **Several companies.** More > Companies works like Customers and Drivers: add as many companies as you buy from, each with its own name, phone, address and opening balance. Each company has its own account (what you owe, payments, statement PDF and CSV).
- **Pick the company on each D.O.** The D.O. form has a Company field (it fills itself in when you only have one company, or uses the company of your last D.O.). You can add a new company right from there. Two companies may use the same D.O. number.
- **Home** shows the total you owe across companies (or the single company's balance if you have just one).
- **Your existing data is upgraded automatically** (database v4): the company you already had (name and opening balance from Settings) becomes your first company, and all its D.O.s and payments are linked to it, so every balance stays the same. No uninstall needed.
- The old "Company account" fields in Settings are replaced by a shortcut to Companies.

## What's new in v2.2
- **Pending orders.** Note a phone order before you have the D.O. number: Home > "+ New order", or Sales > Pending > +. Customer, product, bags, an optional rate, and a free-text delivery area. Pending orders change no balance and no company account.
- **Make a D.O. from pending orders.** On the New D.O. screen tap "Add from pending orders", tick the orders to deliver together (sorted by area) and they appear as customer cards. Once the D.O. is saved they leave the Pending list.
- **Less typing.** The rate fills with what that customer last paid for the product; the company cost fills with what that company last charged for it. Both are only suggestions, you can overwrite them.
- **Company filter** on the Sales list and on the Monthly report (shown once you have more than one company).
- **Dues by age** card on Home: 0-7 days, 8-30 days, over 30 days (payments are counted against the oldest invoices first).
- **WhatsApp buttons**: send the invoice (customer) or the delivery slip (driver) straight to WhatsApp.
- Existing data is upgraded automatically (database v5). No uninstall needed.
