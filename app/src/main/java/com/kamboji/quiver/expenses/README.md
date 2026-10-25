# Expenses mini-app

A private, offline expense tracker that logs UPI payments **automatically** from
your bank SMS and payment notifications — no bank login, no data leaving the phone.

## The problem
Tracking spending by hand never lasts: you forget, and every "free" tracker
wants your bank credentials or sells your data. But almost every payment already
fires a notification ("₹240 debited…"), so the information is right there — it
just evaporates.

## The solution
Quiver reads bank SMS and payment notifications **on-device** and turns them into
expense entries you can glance at and categorise. Nothing is uploaded; amounts
are stored in paise as integers so money math is never wrong. You still get a
clean month view — total, per-category bars, and a day-grouped list — and Quiver
AI can add expenses or answer "what did I spend on food this month?".

## How it works
### Data
- `data/Expense` — one spend: `amountPaise` (a `Long` — **floats never touch
  money**), category, note, timestamp, plus `auto` (captured vs. typed) and
  `needsReview` (category is still a guess) flags.
- `data/ExpenseCategory` — food / groceries / transport / shopping / bills /
  health / entertainment / other, each with a label + emoji. `parse()` maps
  free-text (incl. common Hindi words) to a category, defaulting to *other*.
- `data/ExpenseMath` — pure, unit-tested money/date helpers: `toPaise`,
  Indian-grouped formatting (`₹1,23,456.50`), month filtering and summaries.
- `data/ExpenseStore` — the JSON store; its `saveAll` also refreshes the spend
  widget.

### UPI auto-capture (`capture/`)
- `SmsCapture` — the primary source. Every debit sends a bank SMS, so it reads
  the **SMS inbox** (`READ_SMS`): unlike notifications it's never redacted
  (Android 15+ hides "sensitive" notification text from listeners — every bank
  SMS arrived as "Sensitive notification content hidden") and it carries the
  real sender. `SmsReceiver` only schedules a scan; an SMS `_id` watermark makes
  scans idempotent. Messages are timestamped with the SMS's own date.
- `CaptureFilter` — decides what may be parsed, so a friend's "I paid Rs 500
  for dinner" never becomes an expense: SMS only from bank DLT sender IDs
  (`AX-HDFCBK`, `JD-SBIUPI-S`), never chat-category notifications, never WhatsApp.
- `PaymentCaptureService` — a `NotificationListenerService` for UPI apps
  (GPay/PhonePe/Paytm…, which don't always notify) and bank apps (must say
  "debited"); SMS-app notifications only as a fallback without SMS permission.
- `ExpenseCapture` — the shared dedup → save → "Logged ₹…" notification path.
- `PaymentParser` — unit-tested and **biased against false positives**: extracts
  amount → paise (no floats), payee/VPA and UTR; rejects credits, refunds,
  promos, requests, failures, OTPs and balance alerts. (An unparsed real payment
  is a bug — add it as a test case and extend the patterns.)
- `CaptureDedup` — the same payment notifying twice (UPI app *and* bank SMS)
  collapses by UTR, or by amount within a 3-minute window.
- Captures land as `auto` + `needsReview`; the screen shows an **enable card**
  (bank SMS first, then UPI-app notifications) and a **review banner** until you confirm.

### UI
- `ExpensesViewModel` + `ui/expenses/ExpensesScreen` — month header (total +
  per-category bars), day-grouped list, and an add/edit sheet (amount, category
  chips, note, date). Editing an entry clears its `needsReview` flag.

## Key files
- `data/` — `Expense`, `ExpenseCategory`, `ExpenseMath`, `ExpenseStore`
- `capture/` — `SmsCapture`, `CaptureFilter`, `PaymentCaptureService`, `PaymentParser`, `CaptureDedup`, `ExpenseCapture`
- `ExpensesViewModel.kt`, `ui/expenses/ExpensesScreen.kt`

## Permissions
`READ_SMS` + `RECEIVE_SMS` (bank-SMS capture) and
`BIND_NOTIFICATION_LISTENER_SERVICE` (UPI-app capture) — both granted from the
enable cards; the app works fully without them, you just type expenses in. SMS
permissions are restricted on Google Play; fine for this sideloaded app.

## How other parts use it
- **Share sheet** "Log expense" and the **Add expense** shortcut/widget write here.
- **Month-spend widget** reads this month's total + top categories.
- **Quiver AI** `add_expense` / `list_expenses` tools use the same store.
