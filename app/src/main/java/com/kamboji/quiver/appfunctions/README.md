# AppFunctions — Quiver tools inside the OS assistant

Lets the *system* assistant (Gemini, on SDK 36+) create Quiver notes, tasks and
expenses.

## The problem
Quiver's own assistant only works when you're in Quiver. But the phone already
has an assistant one long-press away — and it can't see your Quiver data or do
anything with it.

## The solution
Expose a few Quiver actions through **AndroidX AppFunctions**. The OS assistant
can then call them directly, and because each function writes through the *same
store* the UI and Quiver AI use, an assistant-created item appears in the app
exactly as if you'd typed it.

Everything is isolated in `QuiverAppFunctions.kt`.

## The functions
Annotated `@AppFunction(isDescribedByKDoc = true)`, `suspend`, first parameter
`AppFunctionContext`:

- `createNote(title, body)`
- `addExpense(amount, category, note, date)`
- `createTask(title, date, time)`

Returns are `@AppFunctionSerializable` data classes with inline-KDoc properties.
The class is no-arg so the runtime can instantiate it without DI.

## Two things that will bite you
**Version pin.** `androidx.appfunctions` is pinned to **alpha08**, *not* the
latest. alpha09/alpha10 require **AGP 9.1.0+**; this module is on AGP 8.9.1.
**Bump the two together.**

alpha08 API quirks:
- `@AppFunction` lives in `androidx.appfunctions.service` (the
  `appfunctions-service` artifact); `AppFunctionContext` and
  `@AppFunctionSerializable` live in `androidx.appfunctions`.
- Its KSP does **not** accept `LocalDate`/`LocalTime` parameters (later alphas
  do) — so `createTask` takes `date`/`time` as **strings** and resolves them
  through the same `WhenResolver` the agent's `add_task` uses.

**SDK gating is automatic.** The library's `enablePlatformAppFunctionService`
bool is `false` in `values/` and `true` only in `values-v36/`, so below API 36
the service Gemini binds to is disabled and the app behaves identically on older
devices. Our function code calls no SDK-36 APIs, so no extra guards are needed.

## Build wiring
KSP with `ksp("appfunctions:aggregateAppFunctions" = "true")` generates the
inventory, invokers, serializable factories and `assets/app_functions_v2.xml`.
The service and schema metadata are merged into the manifest by the library — no
manual manifest wiring.

## Status
Build and schema generation are green. **End-to-end execution can't be verified
without an SDK-36 device with Gemini set as the assistant**, so live invocation
still depends on Google's rollout.

## Key files
- `QuiverAppFunctions.kt` — the three functions + their return types
- `app/build.gradle.kts` — the alpha08 pin + KSP arg
