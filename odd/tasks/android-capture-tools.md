# Android: capture tools (nav, export, marking)

Branch: `feat/android-capture-tools` (stacked on `feat/android-ux-polish`)
Status: authorized by the user on 2026-11-28. TDD ON (user choice, same project).

## Why

The app records notification text but cannot hand those samples to anyone, and
its bottom bar spends 64dp of permanent height moving between one product screen
and two configuration surfaces. The owner also cannot say which captures are
real payments, so there is no way to measure a future rule.

## Scope

**T1 — App shell.** Remove the bottom navigation. One visible product screen
(Historial) plus a settings screen reached from a gear in the top-right of the
history header. Settings lists: apps to register, voice test, privacy, export.
Apps and privacy stay full screens; the IME handling for the app list must keep
working and the bottom-bar/IME coupling must disappear.

**T2 — Export.** A pure builder turns the loaded captures into a deterministic
JSON document (newest first, stable key order, escaped strings). Settings gets
"Exportar avisos", which opens `ACTION_CREATE_DOCUMENT` (text/plain) and writes
that document through `ContentResolver`. No new dependency, no storage
permission, no network. Empty history disables the action.

**T3 — Verdict.** Schema v2: `captures.verdict TEXT NULL` with values
`payment`, `not_payment` or null, migrated with `ALTER TABLE`. Detail view gets
two toggles ("Es un pago" / "No es un pago") with a visible selected state; the
history card shows a small marker when set; export includes the verdict. A pure
model owns the values and their serialization.

## Constraints

- No new dependencies, no INTERNET, no account, no analytics.
- Existing data must survive the migration; never drop the table.
- Keep the honest wording: the app does not confirm payments.
- `cargo`/backend untouched.
- Existing 40 tests stay green.

## Acceptance

- No bottom bar; settings reachable in one tap and back returns to Historial.
- Export produces valid JSON, includes package, app, title, body, timestamp and
  verdict; user chooses the destination; nothing is shared automatically.
- Marking survives app restart and is visible in the list.
- `./gradlew --offline testDebugUnitTest assembleDebug` passes; `aapt` shows no
  INTERNET.
