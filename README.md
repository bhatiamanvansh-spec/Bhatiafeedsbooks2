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
