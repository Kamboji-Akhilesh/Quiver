# Expenses mini-app

A private, offline expense tracker that logs UPI payments **automatically** from
your notifications — no bank login, no data leaving the phone.

## The problem
Tracking spending by hand never lasts: you forget, and every "free" tracker
wants your bank credentials or sells your data. But almost every payment already
fires a notification ("₹240 debited…"), so the information is right there — it
just evaporates.

## The solution
Quiver reads those payment notifications **on-device** and turns them into
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
- `PaymentCaptureService` — a `NotificationListenerService`. UPI and SMS apps are
  parsed in full; any *other* app's notification must contain "debited" (so bank
  apps work without being whitelisted).
- `PaymentParser` — unit-tested and **biased against false positives**: extracts
  amount → paise (no floats), payee/VPA and UTR; rejects credits, refunds,
  promos, requests, failures, OTPs and balance alerts. (An unparsed real payment
  is a bug — add it as a test case and extend the patterns.)
- `CaptureDedup` — the same payment notifying twice (UPI app *and* bank SMS)
  collapses by UTR, or by amount within a 3-minute window.
- Captures land as `auto` + `needsReview`; the screen shows an **enable card**
  (asking for notification access) or a **review banner** until you confirm.

### UI
- `ExpensesViewModel` + `ui/expenses/ExpensesScreen` — month header (total +
  per-category bars), day-grouped list, and an add/edit sheet (amount, category
  chips, note, date). Editing an entry clears its `needsReview` flag.

## Key files
- `data/` — `Expense`, `ExpenseCategory`, `ExpenseMath`, `ExpenseStore`
- `capture/` — `PaymentCaptureService`, `PaymentParser`, `CaptureDedup`
- `ExpensesViewModel.kt`, `ui/expenses/ExpensesScreen.kt`

## Permissions
`BIND_NOTIFICATION_LISTENER_SERVICE` (for auto-capture — the user grants it from
the enable card; the app works fully without it, you just type expenses in).

## How other parts use it
- **Share sheet** "Log expense" and the **Add expense** shortcut/widget write here.
- **Month-spend widget** reads this month's total + top categories.
- **Quiver AI** `add_expense` / `list_expenses` tools use the same store.
