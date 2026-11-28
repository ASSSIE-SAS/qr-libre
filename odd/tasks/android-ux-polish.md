# Android UX polish — implementation and review record

**Status:** Implemented and code-reviewed; extended device validation pending
**Repository:** `/Users/erik/Desktop/qr-libre`
**Branch:** `feat/android-ux-polish`
**Baseline commit:** `829e1f7 docs(android): note the design tokens and their source`
**Reviewed HEAD before this record:** `cf78b47 docs(odd): record the AUP-5 Apps keyboard-open chrome fix`
**Artifact:** this file (`odd/tasks/android-ux-polish.md`)
**Implementer:** DeepSeek
**Reviewers:** parent session for every task; independent final verifier after AUP-6

This is not a final PASS. All six tasks are implemented and code-reviewed, but
extended physical validation remains pending. No push, PR, default-branch merge
or other remote delivery was performed; `main` is untouched.

**Implementation range.** `6d77533` AUP-1, `a82095c` AUP-2, `a6e3573` AUP-3,
`1c37349` AUP-4, `040be54` AUP-5 with corrections `e6c798d` (horizontal safe
insets) and `c030819` (landscape IME chrome), `2f8ffb9` AUP-6. Review/evidence
docs follow each implementation commit; `cf78b47` is the last review-doc commit
before this record.

**Parent review.** The parent read the actual diffs for every source task and
the three-tab screenshots, and reran each focused test. The latest full parent
build, `./gradlew --offline testDebugUnitTest assembleDebug`, reported **40
Android tests, 0 failures, 0 errors**.

**Independent verification.** A fresh DeepSeek verifier ran the baseline **36
Android tests** and **50 backend tests**, and confirmed no `INTERNET`, no new
dependencies and no schema change. It found the landscape + IME zero-list bug,
which `c030819` corrected, then reverified on the actual phone at 2400x1080 and
font scale 1.0: the IME-open list viewport was 284px with rows and scroll, the
query was retained after closing the keyboard, and it found no defect caused by
the fix. Report:
`/private/var/folders/ff/hrsxp6x120z072qb2t7ccxq40000gp/T/opencode/qr-ux-independent-final.md`.

**History (preparation, kept for provenance).** Preparation was committed as
`d26db05`; it edited no source file, test, Gradle file, README or design token
and made no device operation. The former "no source work occurred" statements
in §1 and §8 describe that preparation baseline, not the current tree.
Implementation evidence is in §10,
`odd/tasks/android-ux-polish-aup5-report.md` and
`odd/tasks/android-ux-polish-aup6-report.md` (the AUP-4 report remains the record
for that task).

Technical prose is English. In-app copy is Spanish, quoted exactly where it is
authorized.

## 0. Status checklist

### Implemented and code-reviewed

All six items below have an implementation commit, an actual parent diff read
and a parent focused-test rerun. This is code acceptance, not a runtime PASS.

- [x] **AUP-1** Truthful listener state — `6d77533`
- [x] **AUP-2** First-run hierarchy and truthful history note — `a82095c`
- [x] **AUP-3** Full capture detail with explicit, truthful copy — `a6e3573`
- [x] **AUP-4** Accurate today aggregate across the whole table — `1c37349`
- [x] **AUP-5** Large text, touch targets, keyboard and contrast — `040be54`,
      with `e6c798d` horizontal safe insets and `c030819` IME chrome
- [x] **AUP-6** Compact local-first privacy — `2f8ffb9`

AUP-5 and AUP-6 are incomplete **only** in the extended device matrix. The
parent's actual 100% font, landscape/current empty-history state visual review
passed; the 200% font path, TalkBack and populated runtime checks are not done.

### Extended validation — unchecked, pending consent

The user has not authorized changing the font scale, permissions or selections,
or injecting sample captures during this wave. Unit tests cover the pure logic
but are not runtime proof. These remain open and must not be reported as PASS:

- [ ] Font scale 200% across the three tabs and the populated History/detail
- [ ] Actual captured-record detail: open, scroll, system/back navigation, copy and clipboard behavior
- [ ] Android `SQLiteOpenHelper` / actual `COUNT(*)` runtime behavior
- [ ] Listener permission and connection transitions on device
- [ ] Physical midnight, time-zone and resume refresh behavior
- [ ] TalkBack labels/reading order and touch-target behavior

---

## 0.1 Review slices

One task per review slice, in execution order; no slice bundles two tasks and
no out-of-order combination was used.

| Slice | Task | Implementation | Review corrections |
| --- | --- | --- | --- |
| S1 | AUP-1 | `6d77533` | Parent diff read and `ListenerStatusTest` rerun |
| S2 | AUP-2 | `a82095c` | Parent diff read and `HistoryPresentationTest` rerun |
| S3 | AUP-3 | `a6e3573` | Parent diff read and `CaptureCopyTest` rerun |
| S4 | AUP-4 | `1c37349` | Parent diff read and `DayWindowTest` rerun; SQL runtime pending |
| S5 | AUP-5 | `040be54` | `e6c798d` horizontal safe insets; `c030819` landscape IME chrome |
| S6 | AUP-6 | `2f8ffb9` | Parent diff read and `PrivacySectionsTest` rerun |

Latest full parent build: **40 Android tests, 0 failures, 0 errors**. Independent
baseline: **36 Android tests, 50 backend tests**, no `INTERNET`, no new
dependencies, schema unchanged. The independent verifier's IME/landscape defect
is the reason for `c030819`; its post-fix phone check at 2400x1080 and font 1.0
passed, with a 284px IME-open list viewport, visible rows and scroll, and the
query retained after closing the keyboard.

### Accepted tactical changes

- Truthful state: zero selected apps can never show "Escuchando"; connection
  comes from the listener lifecycle, not from the permission alone.
- Existing saved records stay visible and their detail stays reachable in every
  permission/connection/selection state; only the empty onboarding billboard is
  hidden.
- Copy is an explicit button with a truthful clipboard reminder; no auto-copy
  and no share action.
- Today's total is a full-table SQL count, independent of the 500-row list, with
  local-day boundaries and midnight/resume/time-zone refresh.
- Accessibility: adaptive row/nav heights, large-text wrapping, contrast-safe
  danger text, safe insets and IME-adaptive Apps chrome.
- Privacy is compact and local-first; the no-payment-confirmation and
  irrecoverable-deletion statements are preserved.
- No new dependency, schema version, permission or manifest receiver was added.

**Costs:** no automatic voice announcement and no payment recognition were
added; the app still only stores notification text and offers a manual voice
test.

## 1. Baseline behavior at preparation (historical)

These are the facts the task split was based on at `829e1f7`, before
implementation; each one was read from the working tree, not inferred. They do
not describe the current tree.

| Area | Current behavior | Locator |
| --- | --- | --- |
| Permission state | `notificationAccess` is only `NotificationAccess.isGranted(this)`, refreshed in `onResume` | `MainActivity.kt:25`, `MainActivity.kt:51-58` |
| Listener connection | The listener has no `onListenerConnected` / `onListenerDisconnected` override; it only captures posts | `QrLibreNotificationListener.kt:18-47` |
| "Escuchando" | Any granted permission renders the hero as `Escuchando`, even with zero selected apps or an unbound listener | `HistoryScreen.kt:185-286`, especially `HistoryScreen.kt:250` |
| Selected apps in history | Read once with `remember { SelectedApps(context).selected().size }`, not keyed to selection changes | `HistoryScreen.kt:84` |
| Today counter | Counts rows in the newest-500 list, not the table | `HistoryScreen.kt:85-88`, `HistoryScreen.kt:415-420`, `CapturesDb.kt:49-74`, `CapturesDb.kt:83` |
| Day boundary refresh | No midnight timer; the count only reloads when `CaptureEvents.version` changes | `HistoryScreen.kt:77-88` |
| Capture cards | Body is capped at 4 lines; cards are not clickable; there is no detail view | `HistoryScreen.kt:361-413` |
| Clear history | Confirmation dialog retained | `HistoryScreen.kt:158-182` |
| App rows | Fixed `56.dp` row, single-line label | `AppsScreen.kt:217-293` |
| Bottom navigation | Fixed `64.dp` row | `QrLibreApp.kt:112-141` |
| Switch semantics | Row is `toggleable`; `Switch` uses `onCheckedChange = null`, so there is one callback | `AppsScreen.kt:225-236`, `AppsScreen.kt:272-283` |
| Privacy screen | Intro plus five large cards plus a large closing surface | `PrivacyScreen.kt:42-48`, `PrivacyScreen.kt:84-127` |
| Error contrast | `error = #EB3131` is used directly as text on cards; contrast is 4.28:1 on `#171717` and 3.98:1 on `#1E1E1E` | `Theme.kt:31`, `Theme.kt:83-86`, `HistoryScreen.kt:148`, `HistoryScreen.kt:308` |
| Latent error tokens | `onError = #F5F4F0` gives 3.80:1 on `#EB3131`; `onErrorContainer = #EB3131` gives 3.98:1 on `#1E1E1E` | `Theme.kt:84`, `Theme.kt:86` |
| Tests | One JUnit4 file; no Robolectric, no Compose UI test, no coroutine test dispatcher | `app/src/test/.../NotificationTextTest.kt:1-62`, `app/build.gradle.kts:53-66` |
| Offline contract | No `INTERNET`; local SQLite and preferences only | `AndroidManifest.xml:4-15`, `README.md` |

## 2. Global constraints

- Preserve: no `INTERNET`, no network code, no analytics, no account, local
  SQLite/preferences only, existing notification-access permission flow,
  existing `TextToSpeech` test behavior, existing clear-history confirmation,
  dark lime/Geist identity, minSdk 26 / compileSdk 35 / targetSdk 35, JDK 17,
  Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.11.00.
- No new dependencies. In particular, do not add Robolectric,
  `ui-test-junit4`, `kotlinx-coroutines-test`, mock frameworks or icon packs.
  All new automated tests must run under the existing JUnit4 test classpath.
- No schema migration unless a task proves it is required. `captures.epoch_ms`
  already has an index (`CapturesDb.kt:24`), so AUP-4 does not need one.
- No payment recognition, amount parsing, bank rules, automatic speech or any
  claim that a payment was confirmed. The app continues to say, in Spanish,
  that these are stored notifications, not payment confirmations.
- No private fixtures and no credential reads anywhere in this work.
- Each task is its own local Conventional work-unit commit on
  `feat/android-ux-polish`, with tests and the affected README/docs in the same
  commit. Substantial implementation work and the user request authorize these
  local commits; the parent may also commit this approved task document before
  source work. No push, PR, merge to the default branch or other remote change
  is authorized. No PR will be opened without the user.
- Parent review is required after every task. The final acceptance still needs
  independent verification; a green Gradle run alone is not a device PASS.

## 3. Preflight decisions — unresolved, must be answered before source changes

### 3.1 TDD mode: **ON**

**Source:** the user answered "Pruebas primero" in this session; the parent
recorded that choice when assigning AUP-1. Every task must show observed
RED → GREEN → REFACTOR evidence for its new tests before implementation, using
the existing JUnit4 classpath and the runner in §6. A minimal skeleton that
returns a wrong stub is allowed only to demonstrate a behavioral RED failure;
compile errors alone are not accepted as RED evidence.

Historical note: preparation found no effective repository test-mode
configuration. Checked: repository root and `android/` for `AGENTS.md`,
`CLAUDE.md`, `.agents`, `openspec`, `sdd*`, and Markdown/JSON/TOML/YAML files
containing `tdd`. There is no project registry and no SDD/OpenSpec tree. The
existence of `NotificationTextTest.kt` was not evidence of the mode.

Baseline observed before AUP-1 source work (offline, foreground):
`./gradlew --offline testDebugUnitTest assembleDebug` → `BUILD SUCCESSFUL`;
`NotificationTextTest` 7 tests, 0 failures, 0 errors, 0 skipped.

### 3.2 Delivery and chaining

- `delivery_strategy`: **auto-chain** (internal). The user chose "Rama
  integradora" in this session.
- `chain_strategy`: **feature-branch-chain**, one work-unit slice per AUP task
  in execution order (§5). Each slice is a local Conventional Commit on
  `feat/android-ux-polish`; tests and the affected README/docs travel in the
  same commit.
- The 400-line budget is advisory per task and not a cap; it is not a license to
  code-golf. A task that honestly exceeds it reports the overage instead of
  shrinking tests, docs or comments.
- No PR, push, merge or other remote change is authorized. Local work-unit
  commits are authorized per §2; there are no remote operations at all.

### 3.3 Environment and memory

- **Preparation:** device checks were not run during preparation. **Execution:**
  device `ace242eb` (POCO F3, Android 13) was used for authorized, read-mostly
  visual checks at 2400x1080 and font scale 1.0, including the three-tab screens
  and the IME-open Apps list.
- **Pending consent:** no font-scale change, permission change, selected-app
  change, orientation/system-setting change or sample-capture injection was
  authorized in this wave. The 200% font, populated-detail, TalkBack, listener
  transition, SQLite runtime and physical midnight/time-zone/resume checks
  remain open.
- Independent verifier report:
  `/private/var/folders/ff/hrsxp6x120z072qb2t7ccxq40000gp/T/opencode/qr-ux-independent-final.md`.
- `gentle-ai` is not installed (`which gentle-ai` is empty). RDD status and
  assessment are therefore unavailable, not PASS. Independent review is not a
  native RDD PASS.
- Engram is bound to `asssie-landing` at `/Users/erik/Desktop/notifications-pay`
  (`mem_current_project`, `project_source: git_remote`). The `qr-libre` project
  is unavailable from this session. No Engram write was made and none may be
  made elsewhere. The full mirror is pending under the intended topic
  `odd/android-ux-polish/tasks`.

## 4. Work-unit overview

| ID | Work unit | Depends on | Forecast (A+D) | Commit type |
| --- | --- | --- | --- | --- |
| AUP-1 | Truthful permission / connection / selection state | — | ~300–340 | `fix(android)` |
| AUP-2 | First-run hierarchy: one setup action, voice demoted | AUP-1 | ~200–230 | `feat(android)` |
| AUP-3 | Full capture detail with explicit copy | AUP-1 | ~300–340 | `feat(android)` |
| AUP-4 | Today count from SQL, with local-day boundaries | AUP-3 | ~190–220 | `fix(android)` |
| AUP-5 | Large text, touch targets, keyboard and contrast | AUP-2 | ~190–230 | `fix(android)` |
| AUP-6 | Compact, local-first privacy screen | AUP-5 | ~200–230 | `refactor(android)` |

Execution order is strictly `AUP-1 → AUP-2 → AUP-3 → AUP-4 → AUP-5 → AUP-6`.
AUP-2, AUP-3 and AUP-4 all touch `HistoryScreen.kt`; running them out of order
would create avoidable conflicts. AUP-6 is independent in code but is last on
purpose: it carries the visual/device checkpoint for the whole branch.

All forecast numbers are authored additions plus deletions, excluding generated
build output. Re-count from `git diff --stat` at each task boundary.

---

## AUP-1 — Truthful listener state

**Objective.** The UI must never equate "permission granted" with "listener
connected" or with "at least one app selected". Show one honest state:
permission missing, no apps, connecting, or active.

**Problem evidence.**
- `MainActivity.kt:25,51-58` tracks only the secure setting.
- `QrLibreNotificationListener.kt:18-47` never reports connection lifecycle, so
  the app cannot distinguish "granted but unbound" (force-stop, reboot, OEM
  kill) from "receiving notifications".
- `HistoryScreen.kt:192-286` early-returns a permission card only when
  `!granted`; every granted render shows `R.string.hero_listening` at line 250
  even when `selectedApps == 0` or the service is not bound. `hero_no_apps` is
  merely a warning line under a card that still says "Escuchando".
- `HistoryScreen.kt:84` reads the selection once and never reacts to changes.

**Authorized scope.**
1. New pure status model in
   `android/app/src/main/java/co/asssie/qrlibre/notifications/ListenerStatus.kt`:
   ```kotlin
   enum class ListenerUiState {
       PERMISSION_MISSING,
       NO_APPS,
       CONNECTING,
       ACTIVE,
   }

   fun resolveListenerUiState(
       accessGranted: Boolean,
       listenerConnected: Boolean,
       selectedApps: Int,
   ): ListenerUiState
   ```
   Precedence: no permission wins; then zero apps wins; then not connected is
   `CONNECTING`; only all three true is `ACTIVE`.
2. New process-local signal in
   `android/app/src/main/java/co/asssie/qrlibre/notifications/ListenerConnection.kt`:
   ```kotlin
   object ListenerConnection {
       val connected: StateFlow<Boolean>
       fun onListenerConnected()
       fun onListenerDisconnected()
   }
   ```
   Default is `false`. It has no Android framework imports; the only
   non-Kotlin import is `kotlinx.coroutines.flow`.
3. Override `onListenerConnected()` and `onListenerDisconnected()` in
   `QrLibreNotificationListener.kt`; call the two methods above. Do not change
   `onNotificationPosted`, normalization or the insert path.
4. `MainActivity.kt`: collect `ListenerConnection.connected` with
   `collectAsStateWithLifecycle()` (already used in the project) and pass it to
   `QrLibreApp`. Keep `NotificationAccess.requestRebind` in `onResume`.
5. New selection signal in
   `android/app/src/main/java/co/asssie/qrlibre/data/SelectionEvents.kt`:
   ```kotlin
   object SelectionEvents {
       val version: StateFlow<Long>
       fun notifyChanged()
   }
   ```
   `SelectedApps.setSelected` calls `SelectionEvents.notifyChanged()` centrally
   after the preference write, so every current and future writer emits the
   signal without UI coordination. `AppsScreen` keeps calling
   `preferences.setSelected`; it must not emit the event itself. `QrLibreApp`
   collects the version, reads `SelectedApps(context).selected().size` keyed by
   it, computes `resolveListenerUiState(...)`, and passes the status plus count
   to `HistoryScreen`. `HistoryScreen` stops reading `SelectedApps` itself.
6. `HistoryScreen.kt`: `HeroCard(status, todayCount, selectedApps, onOpenApps,
   onOpenNotificationSettings)`.
   - `PERMISSION_MISSING`: existing lime permission card.
   - `NO_APPS`: warning card, `R.string.hero_no_apps`, text action "Elegir
     apps"; no `hero_listening`.
   - `CONNECTING`: title "Conectando con el sistema…", body explaining the
     permission is on but the system has not delivered the listener yet; text
     action "Revisar ajustes"; no `hero_listening`.
   - `ACTIVE`: the only branch allowed to render `hero_listening`.
   New strings: `hero_connecting`, `hero_connecting_body`, `hero_no_apps_action`.
7. README `android/README.md`: one short paragraph in "What it does" stating
   that the screen distinguishes access, an unbound listener and an empty app
   selection.

**Non-goals.** Do not restructure the first-run hierarchy (AUP-2), add the
detail view (AUP-3), change the count source (AUP-4), change row/nav sizing or
tokens (AUP-5), or touch privacy (AUP-6). No schema, no permission-flow change,
no automatic rebind loop, no new dependency.

**Acceptance tests.**
- New `app/src/test/java/co/asssie/qrlibre/notifications/ListenerStatusTest.kt`
  covering the complete truth table:
  - `false, true, 2 → PERMISSION_MISSING` (permission wins).
  - `true, true, 0 → NO_APPS` (no apps wins over connected).
  - `false, false, 0 → PERMISSION_MISSING`.
  - `true, false, 0 → NO_APPS`.
  - `true, false, 2 → CONNECTING`.
  - `true, true, 2 → ACTIVE`.
  - Negative app counts are treated as none (or rejected explicitly; choose
    one and test it).
- Static evidence by inspection: `hero_listening` appears in exactly one branch
  (the `ACTIVE` one); no `PERMISSION_MISSING`, `NO_APPS` or `CONNECTING` branch
  renders it.
- `rg -n "resolveListenerUiState|SelectionEvents|onListenerConnected|onListenerDisconnected" android/app/src/main` shows the wiring, and `SelectionEvents.notifyChanged()` appears centrally in `SelectedApps.kt`, not in `AppsScreen.kt`.
- Non-`ACTIVE` statuses must not gate the capture list: saved rows remain
  rendered whenever they exist (the detail requirement is completed in AUP-3).
- Build check: command in §6.

**Runtime harness.** Not yet run as a device state matrix. Device `ace242eb`
(POCO F3, Android 13) is available, but changing permission or selection was not
authorized in this wave. Deferred scenarios: revoke permission while open (saved
rows stay visible); grant permission without a rebind (expect "Conectando");
select apps after the listener connects (expect "Escuchando"); deselect all
(expect "No apps", saved rows still visible); force-stop and reopen (expect a
truthful temporary "Conectando", not an instant "Escuchando").

**Rollback.** Remove `ListenerStatus.kt`, `ListenerConnection.kt`,
`SelectionEvents.kt` and `ListenerStatusTest.kt`; revert the central emit in
`SelectedApps`, the three listener lines, `MainActivity` collection and props,
`QrLibreApp` selection read, and restore `HistoryScreen`'s direct
permission/selection read. No data or schema change to undo.

**Suggested commit.**
`fix(android): report the real listener state instead of the permission`

**Forecast:** ~300–340 authored lines (new model/signal/test ≈ 160–190; UI and
wiring ≈ 130–150; README ≈ 8).

---

## AUP-2 — First-run hierarchy

**Objective.** On a fresh install, lead with one compact actionable card and a
single primary action. Do not feature a giant `0`, and do not put the voice test
before setup.

**Problem evidence.**
- `HistoryScreen.kt:86-88` computes a count even before setup; `HeroCard`
  renders `todayCount` with `displayLarge` at `HistoryScreen.kt:256-265`.
- `HistoryScreen.kt:121-123` puts `VoiceTest` immediately after the hero, before
  the empty-history explanation at line 136.
- `HistoryScreen.kt:288-318` shows a test-speech button with no statement that
  the app does not announce notifications automatically.
- `strings.xml:26` labels the screen "Qué escuchar"; the requested plain phrase
  is "Apps que quieres registrar".

**Authorized scope.**
1. Replace the granted hero with a compact status card driven by the AUP-1
   state:
   - `PERMISSION_MISSING`: compact permission card with `Button` "Dar acceso"
     (`permission_grant_action`) as the only primary action.
   - `NO_APPS`: card titled "Apps que quieres registrar", one body line, primary
     `Button` "Elegir apps". This is the main onboarding action.
   - `CONNECTING`: compact status card, "Conectando con el sistema…", secondary
     text action "Revisar ajustes"; no voice test.
   - `ACTIVE`: one compact summary row with a lime dot, "Escuchando" and the
     today count inline (at most `titleLarge`). Remove `displayLarge` from this
     screen so the number cannot dominate first run.
2. Move `VoiceTest` below the history list/empty state and render it only when
   `status == ACTIVE`. Add an explicit line under the button:
   `"La voz solo lee una frase de prueba. La app no anuncia avisos automáticamente."`
   (`R.string.voice_no_auto`). Keep the existing single test sentence and the
   existing failure messages; no automatic speech.
3. Add a caption near the history heading:
   `"Son notificaciones guardadas, no confirmaciones de pago."`
   (`R.string.history_note`). Keep it visible in every state.
4. Suppress only the *empty* onboarding billboard. When `list` is non-empty,
   always render the saved capture rows, including after permission is revoked,
   every app is deselected, or the listener is disconnected. Gate only the
   `EmptyHistory` block on `status == ACTIVE`, and only when `list` is empty, so
   no second "Elegir apps" action or premature "Todavía no hay avisos" message
   appears before setup. Saved records are never hidden by the status card.
5. Change `R.string.apps_headline` to `"Apps que quieres registrar"`. Keep the
   existing explanation text and the selection behavior.
6. README: update the Historial/Apps bullets to describe the compact first-run
   card, the note that these are stored notifications, and the voice test as a
   manual sample only.

**Non-goals.** No change to the state resolver (AUP-1), no detail view (AUP-3),
no count source change (AUP-4), no new illustration, no onboarding carousel, no
permission bypass, no automatic speech. Do not remove the permission card's
system-settings button.

**Acceptance tests.**
- Permission missing: exactly one primary action ("Dar acceso"); no `0` counter;
  no voice test.
- Permission granted, zero apps: card says "Apps que quieres registrar" and
  primary action "Elegir apps"; no `0` counter; no voice test.
- Connecting: no "Escuchando".
- Active: compact summary; voice test appears after the history content with the
  no-auto-announcement line.
- Saved history stays visible and readable with records present and permission
  revoked, zero selected apps, or listener disconnected; tapping a record still
  reaches the AUP-3 detail. Only the empty billboard is suppressed.
- Static checks:
  - `rg -n "displayLarge" android/app/src/main/java/co/asssie/qrlibre/ui/HistoryScreen.kt` → no matches.
  - `rg -n "history_note|voice_no_auto|apps_headline" android/app/src/main/res/values/strings.xml` → present, and `voice_no_auto` is referenced from `HistoryScreen.kt`.
  - `rg -n "VoiceTest|EmptyHistory" android/app/src/main/java/co/asssie/qrlibre/ui/HistoryScreen.kt` → each has one call site guarded by `ListenerUiState.ACTIVE`.
- Build check in §6.
- Deferred visual matrix (AUP-6): fresh install, granted/no-apps, connecting,
  active/empty, active/with captures, at default and large font.

**Runtime harness.** Not yet run as a state matrix. Device `ace242eb` remains
connected; the 1.0 current-state screenshots were reviewed, but changing
permission or selection is not authorized in this wave. The states themselves
are unit-tested in AUP-1; this task changes hierarchy, visibility and copy
only.

**Rollback.** Restore the prior hero/`VoiceTest` call order, the `displayLarge`
count, the old `apps_headline` string and remove `history_note`/`voice_no_auto`.
No behavioral or data migration.

**Suggested commit.**
`feat(android): lead first run with one setup action`

**Forecast:** ~200–230 authored lines.

---

## AUP-3 — Full capture detail

**Objective.** A capture card shows a 4-line preview and opens a readable,
scrollable detail view with the complete body. Copy is an explicit action, with
a clipboard privacy reminder; nothing is copied or shared automatically.

**Problem evidence.**
- `HistoryScreen.kt:402-410` truncates the body to `maxLines = 4`; title is
  capped at 2 lines.
- `HistoryScreen.kt:361-413` has no click target, no detail destination and no
  way to read a long notification in full.
- `Capture.kt:3-10` already carries `id`, `packageName`, `app`, `title`, `body`
  and `epochMs`; no new storage is needed.

**Authorized scope.**
1. New pure copy helper
   `android/app/src/main/java/co/asssie/qrlibre/data/CaptureCopy.kt`:
   ```kotlin
   fun captureCopyText(
       app: String,
       packageName: String,
       title: String,
       body: String,
       timestamp: String,
   ): String
   ```
   The header contains the app label, the package name, and the timestamp. A
   non-empty title and a non-empty body follow as sections. The helper inserts
   exactly one blank line (two newlines) between non-empty header/title/body
   sections and adds no other separator; an empty title or body produces no
   phantom section. The body is appended **verbatim**: do not trim, normalize,
   squeeze, re-indent or strip its internal or edge line breaks. Inherent blank
   lines in the body are data, not separator noise.
2. New `android/app/src/main/java/co/asssie/qrlibre/ui/CaptureDetail.kt`:
   ```kotlin
   @Composable
   internal fun CaptureDetailScreen(
       capture: Capture,
       onBack: () -> Unit,
   )
   ```
   - Full height, `verticalScroll`, `SelectionContainer` around the text; no
     line caps on title or body.
   - Header: app label, full timestamp (`HH:mm:ss dd/MM/yyyy`, local), package
     name in `MonoBody`; a text back action "Volver" and system back both
     return to the list. The list card keeps its existing `HH:mm dd/MM`
     timestamp, so the detail adds precision without changing the list.
   - "Copiar texto" button uses `LocalClipboardManager.setText(AnnotatedString(...))`.
     Copy happens only on click. The persistent caption is
     `"Al copiar, el texto queda en el portapapeles y puedes pegarlo en otra app. En Android moderno, otras apps en segundo plano por lo general no lo leen."`
     (`R.string.capture_detail_copy_hint`). After a click, show
     `"Texto copiado."` (`R.string.capture_detail_copied`).
   - No share `Intent`, no auto-copy on open, no "send" action.
3. `HistoryScreen.kt`: hoist a `rememberLazyListState()` so the list position is
   restored, keep `var openCapture by remember { mutableStateOf<Capture?>(null) }`,
   add `BackHandler(enabled = openCapture != null) { openCapture = null }`, and
   early-return `CaptureDetailScreen` when a capture is open. The detail path
   depends only on a selected record, never on permission, connection or
   selected apps, so records stay readable in every state. The lazy list keeps
   `maxLines = 4` for bodies; do not add inline expansion.
4. Add `Modifier.clickable(onClickLabel = stringResource(R.string.history_open_detail))`
   to the card. A single card tap opens the detail; the clear-history action and
   its confirmation dialog stay exactly as they are.
5. New strings: `history_open_detail`, `capture_detail_back`,
   `capture_detail_copy`, `capture_detail_copy_hint`,
   `capture_detail_copied`.
6. README: document tap-to-open, the explicit copy action, the clipboard hint,
   and that cards remain 4-line previews.

**Non-goals.** No inline expand/collapse, no share sheet, no auto-copy, no
per-row delete, no image/media handling, no rich text parsing, no detection
logic. Keep the existing bulk clear confirmation and all stored fields.

**Acceptance tests.**
- New `app/src/test/java/co/asssie/qrlibre/data/CaptureCopyTest.kt`:
  - a long multi-line body (well over 4 lines) that contains internal blank
    lines is preserved byte-for-byte, including its inherent edge line breaks;
  - an empty title and/or empty body adds no phantom section separator; the
    helper inserts exactly one blank line between non-empty sections, and
    returns the header alone when both are empty;
  - Unicode (accents, `$`, emoji) is preserved;
  - the header includes the app label, package name and timestamp, with the
    package name appearing exactly once in the header.
- Deferred device/manual check for reachability: with saved records and
  permission revoked, zero apps selected, and listener disconnected, the rows
  remain visible and each opens the detail.
- Static evidence:
  - `rg -n "maxLines = 4" android/app/src/main/java/co/asssie/qrlibre/ui/HistoryScreen.kt` → still exactly the card preview.
  - `rg -n "ACTION_SEND|Intent.createChooser|startActivity" android/app/src/main/java/co/asssie/qrlibre/ui/CaptureDetail.kt` → no matches; no sharing.
  - `rg -n "BackHandler|capture_detail_copy_hint|SelectionContainer" .../ui/CaptureDetail.kt .../ui/HistoryScreen.kt`.
- Navigation automation is **not feasible** with the current `junit:junit`
  only test classpath; adding Compose UI test or Robolectric would violate the
  no-new-dependency constraint. Navigation is therefore a code review plus the
  AUP-6 device checklist, explicitly recorded as manual.
- Build check in §6.
- Deferred device checklist: tap a card; scroll a long body; system back and
  "Volver"; copy and verify the hint; confirm clear-history still asks first.

**Runtime harness.** Not yet run on a populated record. Device `ace242eb`
remains connected, but injecting a sample or altering existing data/selection
was not authorized in this wave. Clipboard and navigation are platform
behaviors; the copy builder is the unit-tested part.

**Rollback.** Delete `CaptureDetail.kt`, `CaptureCopy.kt` and
`CaptureCopyTest.kt`; revert the clickable modifier, detail state/back handling,
hoisted list state and new strings. No data changes.

**Suggested commit.**
`feat(android): add a full notification detail view`

**Forecast:** ~300–340 authored lines.

---

## AUP-4 — Accurate today aggregate

**Objective.** "Today" counts every row whose `epoch_ms` falls in the current
local day, independent of the newest-500 list limit, and refreshes after local
midnight, on resume, and when the system time or time zone changes.

**Problem evidence.**
- `HistoryScreen.kt:86-88` uses `list.orEmpty().count { isToday(...) }`, where
  `list` is `recentCaptures()` limited to 500 (`CapturesDb.kt:49-74,83`).
- `HistoryScreen.kt:415-420` compares `Calendar` year/day-of-year, but only when
  the list reloads; there is no midnight refresh.
- `captures.epoch_ms` is already indexed (`CapturesDb.kt:24`), so no schema
  change is needed.

**Authorized scope.**
1. `CapturesDb.kt`: add
   ```kotlin
   fun countCapturesBetween(startMsInclusive: Long, endMsExclusive: Long): Long
   ```
   implemented as `SELECT COUNT(*) FROM captures WHERE epoch_ms >= ? AND epoch_ms < ?`
   on the readable database, with `use { ... }`. No schema version bump, no
   table change.
2. New pure `android/app/src/main/java/co/asssie/qrlibre/data/DayWindow.kt`:
   ```kotlin
   data class LocalDayRange(val startMs: Long, val endMs: Long)

   fun localDayRange(
       nowMs: Long,
       zone: ZoneId = ZoneId.systemDefault(),
   ): LocalDayRange

   fun millisUntilNextLocalMidnight(
       nowMs: Long,
       zone: ZoneId = ZoneId.systemDefault(),
   ): Long
   ```
   Use `java.time` (`Instant`, `ZoneId`, `LocalDate.atStartOfDay`), available at
   minSdk 26 without desugaring. End is exclusive: next local midnight.
3. `HistoryScreen.kt`: remove the list-based `isToday` computation and the
   `Calendar` helper. Keep `todayCount` in its own state; load it in the same
   IO coroutine as the list via `countCapturesBetween(range.startMs,
   range.endMs)`. Key the effect on `CaptureEvents.version` and a `dayTick`.
   Increment `dayTick` from three local triggers:
   - a foreground coroutine that delays `millisUntilNextLocalMidnight(...)`
     (plus a small guard, e.g. 1s) and repeats — an in-process timer, not an OS
     scheduled job;
   - the screen's `ON_RESUME` lifecycle (recompute `ZoneId.systemDefault()`, so
     returning from another app or settings after a time/zone change refreshes);
   - a dynamically registered receiver for `Intent.ACTION_TIME_CHANGED` and
     `Intent.ACTION_TIMEZONE_CHANGED`, registered while the screen is active
     and unregistered on dispose. No manifest entry, no new permission.
4. README: note that today's count is a full-table SQL count and refreshes at
   local midnight, on resume, and after a system time or time-zone change,
   while the list stays limited to 500.

**Non-goals.** No timezone setting, no date-range picker, no pagination, no
schema migration, no change to stored timestamps, no aggregation for other days.
No `AlarmManager`, `JobScheduler`, `WorkManager`, background service, manifest
receiver or new permission.

**Acceptance tests.**
- New `app/src/test/java/co/asssie/qrlibre/data/DayWindowTest.kt` using fixed
  zones and instants:
  - `2026-11-28T00:00:00-05:00` is inside the range; `2026-11-27T23:59:59.999-05:00`
    is outside.
  - the range end equals the next local midnight and the next range start;
  - month/year rollover (`2026-12-31` → `2027-01-01`);
  - `millisUntilNextLocalMidnight` is strictly positive and at most 25h at the
    tested instants;
  - a DST-observing zone (e.g. `America/New_York`) still yields midnight-to-
    midnight boundaries (a 23h/25h day is accepted).
- These boundary tests do **not** test the SQL count. The query is reviewed for
  the exclusive upper bound and the existing index; it has no automated test
  without Robolectric/instrumentation, both excluded by the no-new-dependency
  rule. Do not report SQL as tested from boundary tests alone.
- Static checks:
  - `rg -n "isToday|Calendar" android/app/src/main/java/co/asssie/qrlibre/ui/HistoryScreen.kt` → no matches.
  - `rg -n "countCapturesBetween|localDayRange|millisUntilNextLocalMidnight|ACTION_TIMEZONE_CHANGED|ACTION_TIME_CHANGED" android/app/src/main` → wiring present.
  - `rg -n "AlarmManager|JobScheduler|WorkManager" android/app/src/main` → no matches.
- Build check in §6.
- Deferred manual matrix (AUP-6), not run in preparation:
  - after local midnight while the screen is foreground, the count resets
    without a restart;
  - leave and return to the app after changing the device time/date or time
    zone (`ON_RESUME`) and the count matches the new local day;
  - change the time zone while the screen is foreground and the dynamic
    receiver refreshes it;
  - with more than 500 stored rows the count exceeds the visible list length;
  - the SQL result matches an independent on-device count (e.g. `adb`/`run-as`
    `sqlite3` query) when such access is authorized.

**Runtime harness.** Not yet run for SQL or clock transitions. Device
`ace242eb` remains connected, but changing system time/time zone or reading the
app database was not authorized in this wave. The boundary logic is pure and
unit-tested; the SQL count, resume/time-zone refresh and midnight behavior need
the deferred manual matrix.

**Rollback.** Remove `countCapturesBetween`, `DayWindow.kt` and its test; restore
the list-based `isToday` count and delete the timer, resume trigger and
dynamic receiver. No schema or data change to undo.

**Suggested commit.**
`fix(android): count today's captures across the whole table`

**Forecast:** ~210–250 authored lines.

---

## AUP-5 — Large text, touch targets, keyboard and contrast

**Objective.** Rows and navigation grow with font scale instead of clipping;
touch targets stay at least 48dp; search remains usable with the keyboard open;
the switch keeps a single callback; error text meets 4.5:1 on dark surfaces.

**Problem evidence.**
- `AppsScreen.kt:228` fixes every app row at `56.dp`; `AppsScreen.kt:267` limits
  labels to one line.
- `QrLibreApp.kt:126` fixes the bottom bar at `64.dp`, with
  `fillMaxHeight()` children at `QrLibreApp.kt:135`.
- `QrLibreApp.kt:77` applies `imePadding()` to the shell, but the bottom bar
  keeps consuming space while the keyboard is open.
- `AppsScreen.kt:272-283` already uses `Switch(onCheckedChange = null)`; this
  must not regress into a second callback.
- `Theme.kt:84,86` holds the latent low-contrast error tokens. The audit also
  found that `colorScheme.error` is used as text at `HistoryScreen.kt:148,308`
  (4.28:1 on `#171717`), so fixing only the latent tokens would leave visible
  danger copy below 4.5:1.

**Authorized scope.**
1. `AppsScreen.kt` `AppRow`: replace `.height(56.dp)` with
   `.heightIn(min = 56.dp)` plus vertical padding; allow the label
   `maxLines = 2` (ellipsis beyond). Keep the whole row `toggleable`, keep
   `Switch(onCheckedChange = null)`, and keep the existing selected/divider
   behavior.
2. `QrLibreApp.kt` `BottomBar`: replace fixed `.height(64.dp)` with
   `.heightIn(min = 64.dp)`; remove `fillMaxHeight()` from the item modifier and
   give each item `defaultMinSize(minHeight = 48.dp)` with vertical padding, so
   labels can grow at large font scale without a fixed clip. Keep the
   `selectable(role = Role.Tab)` semantics and the 22dp icon.
3. Keyboard/search: read IME visibility from `WindowInsets.ime` (and
   `LocalDensity`) in `QrLibreApp`; hide the bottom bar while the IME is visible
   so the app list keeps vertical space, restore it when the keyboard closes.
   Keep the existing `imePadding()`. Do not change tab state or selection.
4. Contrast (parent-approved, including the visible low-contrast danger text):
   - In `Theme.kt`, extract the tested palette values to an internal object of
     pure `Long` ARGB constants, e.g. `QrLibreColorValues` with `Page`, `Card`,
     `Elevated`, `Divider`, `Danger`, `DangerText`, `OnLime`, `Text`, `Muted`.
     `QrLibreColors` must construct its `Color(...)` values from that object,
     so tests pin the real palette instead of keeping independent copies. This
     is the smallest change; do not build a production contrast engine.
   - `Theme.kt`: `onError = OnLime` (`#0B0B0B`, 4.70:1 on `#EB3131`, replacing
     the 3.80:1 light text) and `onErrorContainer = DangerText` (`#FF7A7A`,
     6.60:1 on `#1E1E1E`, replacing 3.98:1).
   - Add `DangerText = #FF7A7A` to `QrLibreColors`; use it for the two actual
     danger text call sites in `HistoryScreen.kt:148,308` so visible error copy
     also reaches 4.5:1.
   - Keep `error = #EB3131` for any future filled error surface and keep all
     other tokens unchanged.
5. New `app/src/test/java/co/asssie/qrlibre/ui/ContrastTest.kt` with a
   **test-local** `contrastRatio(foreground: Long, background: Long)` helper
   (relative-luminance math belongs in the test utility, not shipping code) and
   assertions against `QrLibreColorValues` for ratios ≥4.5 for normal text.
6. README: no behavior copy change needed unless the keyboard/nav behavior is
   worth one line; add a short accessibility note if it is.

**Non-goals.** No light theme, no dynamic color, no font swap, no icon pack, no
new dependency, no haptics, no redesign of the app list, no change to selection
persistence or switch semantics.

**Acceptance tests.**
- `ContrastTest`: `OnLime` on `Danger` ≥ 4.5 (4.70); `DangerText` on `Page`,
  `Card`, `Elevated`, `Divider` ≥ 4.5 (7.65 / 7.10 / 6.60 / 5.99); baseline
  `Text`/`Muted` ratios still pass on the same surfaces.
- Static checks:
  - `rg -n "\.height\(56\.dp\)|\.height\(64\.dp\)" android/app/src/main/java/co/asssie/qrlibre/ui` → no matches in `AppsScreen.kt` or `QrLibreApp.kt`.
  - `rg -n "onCheckedChange = null" android/app/src/main/java/co/asssie/qrlibre/ui/AppsScreen.kt` → present exactly once.
  - `rg -n "heightIn\(min = 56\.dp\)|heightIn\(min = 64\.dp\)|defaultMinSize\(minHeight = 48\.dp\)" .../ui` → present.
  - `rg -n "contrastRatio" android/app/src/main` → no matches; the ratio math exists only in `app/src/test/.../ContrastTest.kt`.
  - `rg -n "QrLibreColorValues" android/app/src/main/java/co/asssie/qrlibre/ui/Theme.kt app/src/test/java/co/asssie/qrlibre/ui/ContrastTest.kt` → production palette and test pin the same constants.
- Build check in §6.
- Deferred device matrix (AUP-6), at 100%, 150% and 200% font scale:
  - no clipped app labels; long labels wrap to at most 2 lines;
  - rows and nav remain tappable and ≥48dp;
  - search list scrolls to its last row with the keyboard open, and the bottom
    bar returns after dismissal with the tab selection preserved;
  - switch toggles once per tap (no double callback);
  - danger text is legible on cards.

**Runtime harness.** Partially run after implementation. Device `ace242eb` at
2400x1080, font scale 1.0: the three-tab landscape review passed, and the
independent verifier confirmed the IME-open Apps list (284px viewport, visible
rows, scroll, query retained after keyboard close) with no fix-caused defect.
**Not run:** 200% font, TalkBack and the populated-detail/selection matrix;
these require consent to change system settings or inject samples. They are not
PASS.

**Rollback.** Restore the fixed heights, the one-line app label, the always-on
bottom bar and the old error tokens/call-site colors; revert the
`QrLibreColorValues` extraction and delete `ContrastTest.kt`. No data change.

**Suggested commit.**
`fix(android): support large text and accessible contrast`

**Forecast:** ~190–230 authored lines.

---

## AUP-6 — Compact, local-first privacy screen

**Objective.** Replace five repeated large cards with concise sections, lead
with the local-data reassurance, and keep every substantive promise.

**Problem evidence.**
- `PrivacyScreen.kt:42-48` defines five entries; `PrivacyScreen.kt:84-127`
  renders each as a large card with a 40dp icon tile.
- The order starts with "Qué lee"; the local reassurance ("Dónde queda") is
  second.
- `PrivacyScreen.kt:50-81` plus `strings.xml:66-77` hold the copy that must
  survive: reads only selected apps, stays on the phone/no INTERNET, clearing is
  irreversible, no account/ads/tracking, and no payment confirmation.

**Authorized scope.**
1. Rework `PrivacyScreen.kt` into one compact column of borderless sections
   separated by `HorizontalDivider`s, with a smaller inline icon (20dp) and
   title/body typography from the existing scale. Remove the per-entry `Card`
   and the 40dp icon tile.
2. Order: local reassurance first ("Dónde queda"), then "Qué lee", "Qué no
   hace", "Puedes borrarlo", "Sin cuenta y sin anuncios". Put a short
   local-first line in the intro, e.g.
   `"Tus avisos se quedan en este teléfono. Esto es lo que la app hace y lo que no."`
   (new/edited `privacy_intro`).
3. Preserve the substance exactly: clearing is irrecoverable; the app does not
   confirm payments or query the bank; no INTERNET; captures are only from
   selected apps; no account, ads, tracking.
4. Keep the closing reassurance ("Nada sale de tu teléfono.") but make it a
   compact line/surface, not another large card. Keep the source footer.
5. Add the final visual/device validation checkpoint here: this task is not
   accepted on build alone. The deferred matrix covers the reordered privacy
   screen and the whole branch at default and large font, including scroll to
   the last section and no clipped text.

**Non-goals.** No legal-policy expansion, no settings toggles, no data-export
feature, no new permissions, no behavior change to deletion or capture. Do not
remove the "no payment confirmation" statement or the irrecoverable-deletion
statement.

**Acceptance tests.**
- Copy review confirms both required statements are present and unweakened:
  `privacy_delete_body` (irrecoverable) and `privacy_limit_body` (no payment
  confirmation / no bank query).
- Static checks:
  - `rg -n "Card\(" android/app/src/main/java/co/asssie/qrlibre/ui/PrivacyScreen.kt` → no per-entry cards; at most a compact closing surface if needed.
  - `rg -n "privacy_stays_body|privacy_delete_body|privacy_limit_body" android/app/src/main/java/co/asssie/qrlibre/ui/PrivacyScreen.kt` → all are rendered in the new order.
  - `rg -n "Todo se queda|Nada sale|No se puede recuperar|no es un pago verificado" android/app/src/main/res/values/strings.xml` → substance present.
- Build check in §6.
- Final checkpoint, partially run: at 1.0 font and landscape on `ace242eb`
  (POCO F3, Android 13), the five sections were reviewed in order and the screen
  scrolled to the closing/footer. **Still open:** 200% font, TalkBack,
  populated-detail and the full branch matrix from AUP-1, AUP-3, AUP-4 and
  AUP-5. Those require consent to change system settings or inject samples and
  are not PASS.

**Runtime harness.** Partially run: the 1.0 landscape privacy review and the
IME/landscape Apps check passed; the 200% and populated matrices were not run.
The artifact remains **not PASS** until those authorized checks are completed.

**Rollback.** Restore the five-card layout and entry order from the prior
commit; restore the previous `privacy_intro` string. No data change.

**Suggested commit.**
`refactor(android): make the privacy screen concise and local-first`

**Forecast:** ~200–230 authored lines.

---

## 5. Delivery forecast and candidate slices

Honest authored additions + deletions, based on the files and behaviors above:

| Task | Low | High |
| --- | ---: | ---: |
| AUP-1 | 300 | 340 |
| AUP-2 | 200 | 230 |
| AUP-3 | 300 | 340 |
| AUP-4 | 210 | 250 |
| AUP-5 | 190 | 230 |
| AUP-6 | 200 | 230 |
| **Total** | **~1,400** | **~1,620** |

Planning number: **~1,480 authored lines**. Each individual work unit is below
the advisory 400, but the total is far above it. This is a forecast, not a
promise; re-count at every task boundary.

Candidate slices: the selected plan is **one task per slice, in execution order**,
with no bundling and no out-of-order combination:

- S1: AUP-1 (~320) — truthful state only.
- S2: AUP-2 (~215) — first-run hierarchy.
- S3: AUP-3 (~320) — detail view.
- S4: AUP-4 (~230) — today aggregate.
- S5: AUP-5 (~210) — accessibility and contrast.
- S6: AUP-6 (~215) — privacy and final validation.

The 400-line advisory is not a cap, and coherent behaviors are not chopped to
meet it. `chain_strategy` is **feature-branch-chain**, selected by the user in
this session ("Rama integradora"). Local work-unit commits are authorized by
§2; no PR, push or remote change is authorized, and no PR will be opened without
the user.

## 6. Checks

These were the commands used during implementation. The latest parent full run
was:

```bash
cd android && ANDROID_HOME=/Users/erik/Library/Android/sdk ./gradlew --offline testDebugUnitTest assembleDebug
```

Result: `BUILD SUCCESSFUL`, **40 Android tests, 0 failures, 0 errors**. A green
build is not the extended runtime PASS described in §0.

Focused examples:

```bash
cd android && ANDROID_HOME=/Users/erik/Library/Android/sdk ./gradlew testDebugUnitTest --tests "co.asssie.qrlibre.notifications.ListenerStatusTest"
cd android && ANDROID_HOME=/Users/erik/Library/Android/sdk ./gradlew testDebugUnitTest --tests "co.asssie.qrlibre.data.CaptureCopyTest"
cd android && ANDROID_HOME=/Users/erik/Library/Android/sdk ./gradlew testDebugUnitTest --tests "co.asssie.qrlibre.data.DayWindowTest"
cd android && ANDROID_HOME=/Users/erik/Library/Android/sdk ./gradlew testDebugUnitTest --tests "co.asssie.qrlibre.ui.ContrastTest"
```

Expected: `BUILD SUCCESSFUL`, all tests green, APK at
`app/build/outputs/apk/debug/app-debug.apk`. Also keep the existing offline
check in the final review:

```bash
/Users/erik/Library/Android/sdk/build-tools/34.0.0/aapt dump permissions android/app/build/outputs/apk/debug/app-debug.apk
```

The output must not contain `android.permission.INTERNET`.

## 7. Risks

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Total change is ~1,480 lines | Delivery/review overload | `auto-chain` + `feature-branch-chain`, one slice per task; small work-unit commits; re-count per task |
| No Compose/Robolectric test deps | Navigation, IME, font-scale and clipboard cannot be automated now | Pure logic tests; explicit AUP-6 device checkpoint; independent final verification |
| Three tasks touch `HistoryScreen.kt` | Merge/conflict churn | Strict order AUP-2 → AUP-3 → AUP-4; re-read the file before each edit |
| Listener callback timing / OEM rebind | A transient "Conectando" even when things work | Truthful state is the requirement; default disconnected; test the matrix on device |
| Contrast changes touch real palette tokens | Visual identity drift | Parent-approved; tests read the production `QrLibreColorValues`, ratio math stays test-local; review the token diff |
| Midnight / resume / time-zone edge cases | Wrong "today" count | Pure `DayWindow` tests incl. month/year/DST; in-process timer plus resume and dynamic time/time-zone receiver; manual matrix |
| Clipboard privacy wording | Overclaiming insecurity | Say the user can paste elsewhere and that modern Android does not let background apps freely read the clipboard; no auto-copy |
| Extended device validation not run | Runtime regressions remain unproven | Code review plus 1.0 landscape/current-state checks passed; 200% font, populated detail, TalkBack, SQLite runtime, listener transitions and midnight/time-zone/resume await authorized checks |

## 8. Preparation record (historical, superseded)

_This section records only the planning phase before `6d77533`. It is not a
current status. Current status is §0, review slices are §0.1, and implementation
evidence is §10._

**Done during preparation**
- Read the required skills and the actual Android sources, strings, Gradle
  config, tests, manifest, README and design tokens.
- Verified the baseline was clean (`git status --porcelain` empty).
- Produced this artifact before any source change.

**Not done during preparation**
- No source, test, resource, Gradle or README edit.
- No build, test run, install, device command or device state change, commit,
  push, branch or subagent.
- No Engram write; no RDD assessment.

The planning requests from this section were subsequently authorized and
executed; the current pending work is the extended validation checklist in §0.

## 9. Skill resolution

- **Loaded:** `/Users/erik/.agents/skills/work-unit-commits/SKILL.md` and
  `/Users/erik/.agents/skills/impeccable/reference/product.md` (preparation);
  for AUP-1 through AUP-5 also
  `/Users/erik/.cache/opencode/npm/git-superpowers-0958a5557860/1793134404581/node_modules/superpowers/skills/test-driven-development/SKILL.md`
  and
  `/Users/erik/.cache/opencode/npm/git-superpowers-0958a5557860/1793134404581/node_modules/superpowers/skills/test-driven-development/writing-good-tests.md`
  (paths injected by the parent).
- **Project registry:** none found (no `AGENTS.md`, `CLAUDE.md`, `.agents`,
  `openspec` or `sdd*`). No SDD/OpenSpec artifacts exist for this repo.
- **gentle-ai:** not installed (`which gentle-ai` empty). RDD status and
  assessment are unavailable; this is **not** a PASS.
- **Engram:** `mem_current_project` resolves to `asssie-landing` at
  `/Users/erik/Desktop/notifications-pay` from `git_remote`. `qr-libre` is not
  available and no memory was written. Full mirror remains pending under the
  intended topic `odd/android-ux-polish/tasks`; do not save it to another
  project.

## 10. Implementation log

### AUP-1 — accepted by the parent

**TDD evidence (JUnit4 only, offline).** RED was observed before the real
implementation with deliberately wrong skeletons, and it was behavioral, not a
compile error: 7 focused tests ran, 5 failed on assertions —
`expected:<PERMISSION_MISSING> but was:<ACTIVE>`,
`expected:<NO_APPS> but was:<ACTIVE>` (three resolver tests), and two
`assertTrue` failures for the connection signal. GREEN after implementing the
resolver and the signal: 7/7 focused tests. Full verification:
`./gradlew --offline testDebugUnitTest assembleDebug` → `BUILD SUCCESSFUL`,
14/14 tests (`NotificationTextTest` 7, `ListenerStatusTest` 3,
`ListenerConnectionTest` 4), 0 failures, 0 errors, 0 skipped; no compiler
warnings; `aapt dump permissions` lists no `android.permission.INTERNET`.

**What shipped**
- `notifications/ListenerStatus.kt`: pure resolver with precedence missing
  permission > zero/negative selection > connecting > active, and the
  `ListenerUiState` enum.
- `notifications/ListenerConnection.kt`: process-local `StateFlow<Boolean>`,
  default `false`, no persisted "active" preference.
- `notifications/QrLibreNotificationListener.kt`: `onListenerConnected`,
  `onListenerDisconnected` and `onDestroy` keep the signal truthful (destruction
  clears it); `super` is called; the notification capture path is unchanged.
- `data/SelectionEvents.kt`: version signal emitted centrally from
  `SelectedApps.setSelected`, which also skips no-op writes/emissions.
- `MainActivity` collects the connection flow with
  `collectAsStateWithLifecycle`; `QrLibreApp` reads the selected-app count keyed
  by the selection version and computes the state.
- `ui/HistoryScreen.kt`: `HeroCard` has four truthful branches; `hero_listening`
  renders only in `ACTIVE`; saved rows are never gated by the status.
- Strings `hero_connecting`, `hero_connecting_body`, `hero_no_apps_action` and
  the README paragraph/layout rows.

**Limits.** Service lifecycle timing and the visual result need the deferred
device matrix (AUP-6); `onDestroy` clearing is verified by inspection, not by an
automated test. Authored lines: **395** (345 additions + 50 deletions) across
the source commit.

**Commits:** `d26db05` docs(odd) plan; `6d77533` fix(android) AUP-1
implementation, tests, README and checklist; this docs update records both.

**Parent acceptance.** The parent read the actual AUP-1 diff and reran
`./gradlew --offline testDebugUnitTest --tests "co.asssie.qrlibre.notifications.ListenerStatusTest"`
→ `BUILD SUCCESSFUL`. The acceptance evidence is commit `6d77533` and 14 total
tests green: 7 `NotificationTextTest`, 3 `ListenerStatusTest`, 4
`ListenerConnectionTest` (writer run plus the parent focused rerun). AUP-1 is
accepted; the branch still needs the deferred device matrix before any PASS.

### AUP-2 — accepted by the parent

**TDD evidence (JUnit4 only, offline).** A small pure presentation policy,
`ui/HistoryPresentation.kt`, was introduced first so the gating decisions the
screen consumes are testable without Compose UI dependencies: `showCaptures`,
`showEmptyHistory` and `showVoiceTest` from `status` plus `hasCaptures`. RED was
observed with a deliberately wrong stub (all `false`/`true` inverted) after the
new test compiled: 3 focused tests ran, 3 failed on assertions, not compile
errors:

```
HistoryPresentationTest > voiceTestOnlyAppearsWhenCaptureIsActive FAILED
    java.lang.AssertionError at HistoryPresentationTest.kt:41
HistoryPresentationTest > emptyHistoryIsOnlyOfferedInTheActiveState FAILED
    java.lang.AssertionError at HistoryPresentationTest.kt:29
HistoryPresentationTest > savedCapturesStayVisibleInEveryListenerState FAILED
    java.lang.AssertionError at HistoryPresentationTest.kt:19

3 tests completed, 3 failed
BUILD FAILED
```

Exact assertion messages from the JUnit XML:
`no voice test before capture is active for PERMISSION_MISSING expected:<false> but was:<true>`;
`no empty billboard before setup for PERMISSION_MISSING expected:<false> but was:<true>`;
`saved captures must stay visible for PERMISSION_MISSING expected:<true> but was:<false>`.
GREEN after the minimal policy implementation: focused
`HistoryPresentationTest` → `BUILD SUCCESSFUL`, 3/3 tests. Full verification after
the UI, strings and README edits:
`./gradlew --offline testDebugUnitTest assembleDebug` → `BUILD SUCCESSFUL`,
17/17 tests (7 `NotificationTextTest`, 3 `ListenerStatusTest`, 4
`ListenerConnectionTest`, 3 `HistoryPresentationTest`), 0 failures, 0 errors,
0 skipped; no compiler warnings; `aapt dump permissions` lists no
`android.permission.INTERNET`.

**What shipped**
- `ui/HistoryPresentation.kt`: pure visibility policy. Saved captures are never
  gated by status; the empty billboard and the voice test are active-only.
- `ui/HistoryScreen.kt`: compact `PermissionCard` (single "Dar acceso" action),
  compact `SetupCard` ("Apps que quieres registrar", primary "Elegir apps"),
  compact `CONNECTING` status card ("Revisar ajustes"), and a compact
  `ActiveSummary` with a lime dot, "Escuchando", and the today count inline at
  `titleLarge` (no `displayLarge`). The history note is always rendered; the
  voice sample moved below the list/empty content and only renders when active;
  saved rows render for every status.
- `res/values/strings.xml`: `history_note`
  ("Son notificaciones guardadas, no confirmaciones de pago."), `voice_no_auto`
  ("La voz solo lee una frase de prueba. La app no anuncia avisos
  automáticamente.") and `apps_headline` changed to
  "Apps que quieres registrar".
- README: first-run hierarchy, stored-notifications note, saved-row persistence,
  app heading and manual-only voice sample.
- The AUP-1 resolver, connection signal and selection signal are unchanged.

**Static evidence.** No `displayLarge` remains in `HistoryScreen.kt`;
`history_note`, `voice_no_auto` and `apps_headline` are present and referenced;
`VoiceTest` and `EmptyHistory` each have one call site reached only through the
tested `HistoryPresentation` fields (the `ListenerUiState.ACTIVE` guard lives in
the policy, not duplicated at the call sites); `aapt` shows no `INTERNET`.

**Limits.** Visual hierarchy, the compact summary layout and font-scale behavior
still need the deferred device matrix (AUP-6). No Compose UI test is possible
without new dependencies, so element presence is covered by the pure policy plus
code review. The `todayCount` source is intentionally unchanged until AUP-4.
Authored lines: **367** (265 additions + 102 deletions), above the 200–230
forecast because the card was split into three focused composables and the test
is included; below the 400 advisory.

**Commit:** `a82095c` feat(android): lead first run with one setup action
(source, test, strings and README). The following docs commit
`6883df1` records the implementation evidence.

**Parent acceptance.** The parent read the actual full AUP-2 diff and reran
`./gradlew --offline testDebugUnitTest --tests "co.asssie.qrlibre.ui.HistoryPresentationTest"`
→ focused PASS. The writer's full run was 17/17 tests green (7
`NotificationTextTest`, 3 `ListenerStatusTest`, 4 `ListenerConnectionTest`,
3 `HistoryPresentationTest`). AUP-2 is accepted. The parent parked the
`ActiveSummary` fixed Row/ellipsis large-text count concern for AUP-5; it is not
fixed in AUP-3. The branch still needs the deferred device matrix before any
PASS.

### AUP-3 — accepted by the parent

**TDD evidence (JUnit4 only, offline).** The pure clipboard payload builder
`data/CaptureCopy.kt` was first written as a deliberately wrong stub that
always returned `""`, after the real `CaptureCopyTest` compiled, so RED was
behavioral rather than a compile error. Focused RED: 5 tests completed, 5 failed
with `org.junit.ComparisonFailure` / `java.lang.AssertionError`:

```
CaptureCopyTest > longBodyKeepsInternalBlankLinesAndEdgeBreaksVerbatim FAILED
    org.junit.ComparisonFailure at CaptureCopyTest.kt:34
CaptureCopyTest > emptyTitleAndBodyProduceOnlyTheHeader FAILED
    org.junit.ComparisonFailure at CaptureCopyTest.kt:40
CaptureCopyTest > oneEmptySectionAddsNoPhantomSeparator FAILED
    org.junit.ComparisonFailure at CaptureCopyTest.kt:45
CaptureCopyTest > unicodeAccentsCurrencyAndEmojiArePreserved FAILED
    java.lang.AssertionError at CaptureCopyTest.kt:56
CaptureCopyTest > headerListsAppPackageAndTimestampOnce FAILED
    java.lang.AssertionError at CaptureCopyTest.kt:72

5 tests completed, 5 failed
BUILD FAILED
```

Exact XML assertion messages include
`expected:<[Banco Uno\nco.banco.uno\n09:41:07 28/11/2026\n\nAviso de pago\n\n\nPrimera línea con ñ y tildes…]> but was:<[]>`
and `title must keep accents, currency and emoji`. GREEN after the minimal
helper: focused `CaptureCopyTest` → `BUILD SUCCESSFUL`, 5/5 tests. Full
verification after the UI, strings and README edits:
`./gradlew --offline testDebugUnitTest assembleDebug --rerun-tasks` →
`BUILD SUCCESSFUL`, 41/41 tasks executed, 22/22 tests (7 `NotificationTextTest`,
3 `ListenerStatusTest`, 4 `ListenerConnectionTest`, 3
`HistoryPresentationTest`, 5 `CaptureCopyTest`), 0 failures, 0 errors, 0
skipped; no compiler warnings; `aapt dump permissions` lists no
`android.permission.INTERNET`.

**What shipped**
- `data/CaptureCopy.kt`: pure payload builder. The header is the app label, the
  package name and the timestamp joined by newlines; non-empty title and body
  are separate sections separated by exactly one blank line; the body is
  appended verbatim and its internal/edge line breaks are never trimmed,
  squeezed or normalised.
- `ui/CaptureDetail.kt`: full-height `verticalScroll` detail with
  `SelectionContainer`; app label, package in `MonoBody`, and the full local
  `HH:mm:ss dd/MM/yyyy` timestamp; no line caps on title or body; "Volver" text
  action. "Copiar texto" calls `LocalClipboardManager.setText(AnnotatedString)`
  only inside its `onClick`, shows the persistent clipboard caption and, after a
  tap, "Texto copiado." with `LiveRegionMode.Polite`. No share intent, no send
  action, no copy on open.
- `ui/HistoryScreen.kt`: hoisted `rememberLazyListState()` retains list position
  across detail open/close; `openCapture` is a plain `remember` (not saveable),
  so leaving the tab disposes the hidden detail; `BackHandler` consumes system
  back only while a detail is open; the card is clickable with the
  `history_open_detail` label. The list card keeps `maxLines = 4`; clear-history
  and its confirmation are unchanged. The detail opens regardless of permission,
  connection or selection state.
- `res/values/strings.xml`: `history_open_detail`, `capture_detail_back`,
  `capture_detail_copy`, `capture_detail_copy_hint`, `capture_detail_copied`
  with the authorized Spanish copy.
- README: tap-to-open, full timestamp/package, scroll/selection, explicit copy
  with the clipboard reminder, no share/auto-copy, and the 4-line preview.

**Static evidence.** `maxLines = 4` appears exactly once (the list preview); no
`ACTION_SEND`, `Intent.createChooser` or `startActivity` in
`CaptureDetail.kt`; `BackHandler`, `capture_detail_copy_hint` and
`SelectionContainer` are present; `clipboard.setText` appears only inside the
Button `onClick`; the copied payload is the tested `captureCopyText` output.

**Limits.** Navigation, scrolling, system back, the clipboard write and the
live-region announcement are platform behaviors that the JUnit4-only classpath
cannot automate; they remain deferred to the AUP-6 device matrix. List position
retention is structural and reviewed, not automated. No device pass was run.
Authored lines: **303** (297 additions + 6 deletions), inside the 300–340
forecast and below the 400 advisory.

**Commit:** `a6e3573` feat(android): add a full notification detail view
(source, test, strings and README); `5ac4f37` records this evidence.

**Parent acceptance.** The parent read the actual AUP-3 diff and reran the
focused `CaptureCopyTest` → PASS. The writer's full run was 22/22 tests green
(7 `NotificationTextTest`, 3 `ListenerStatusTest`, 4 `ListenerConnectionTest`,
3 `HistoryPresentationTest`, 5 `CaptureCopyTest`). AUP-3 is accepted. The
parent also accepted the copy helper taking primitive fields instead of a
`Capture` object: it is pure and the UI uses the actual helper. The branch
still needs the deferred device matrix before any PASS.

### AUP-4 — accepted by the parent

**TDD evidence (JUnit4 only, offline).** `data/DayWindow.kt` was first written
as a deliberately wrong stub (`LocalDayRange(0, 0)` and a zero delay) after the
real `DayWindowTest` compiled, so RED was behavioral. Focused RED: 7 tests
completed, 7 failed with `java.lang.AssertionError`, e.g.
`expected:<1772946000000> but was:<0>`,
`delay must be positive at 2026-11-28T00:00:00` and
`Colombia has no daylight saving time on 2026-03-08T12:00:00
expected:<86400000> but was:<0>`. GREEN after the `java.time` implementation:
focused `DayWindowTest` → `BUILD SUCCESSFUL`, 7/7 tests. Full verification after
the SQL and UI edits:
`./gradlew --offline testDebugUnitTest assembleDebug --rerun-tasks` →
`BUILD SUCCESSFUL`, 41/41 tasks executed, 29/29 tests (7 `NotificationTextTest`,
3 `ListenerStatusTest`, 4 `ListenerConnectionTest`, 3
`HistoryPresentationTest`, 5 `CaptureCopyTest`, 7 `DayWindowTest`), 0 failures,
0 errors, 0 skipped; no compiler warnings; `aapt dump permissions` lists no
`android.permission.INTERNET`. The complete RED/GREEN output is in
`odd/tasks/android-ux-polish-aup4-report.md`.

**What shipped**
- `data/DayWindow.kt`: pure half-open `LocalDayRange` between local midnight and
  the next local midnight, built with `java.time` so month/year rollover,
  Bogota's fixed UTC-5 offset and DST zones (23h/25h days) stay correct;
  `millisUntilNextLocalMidnight` is clamped to at least 1 ms.
- `data/CapturesDb.kt`: `countCapturesBetween(startMsInclusive, endMsExclusive):
  Long` runs `SELECT COUNT(*) FROM captures WHERE epoch_ms >= ? AND epoch_ms < ?`
  on the readable database with `use`. No schema bump; `DB_VERSION` stays 1 and
  the existing `epoch_ms` index is reused.
- `ui/HistoryScreen.kt`: the list-based `isToday`/`Calendar` count is gone. One
  IO coroutine loads the newest-500 list and the whole-table count;
  `todayCount: Long?` is null (rendered `—`) until the read completes and after
  a failed read, so an unread total is never shown as a known zero. Refreshes
  are keyed on `CaptureEvents.version` and `dayTick`, fed by an in-process
  midnight timer (`millisUntilNextLocalMidnight(...) + 1 s`, restarted after
  each refresh), `LifecycleEventEffect(ON_RESUME)` and a runtime
  `ACTION_TIME_CHANGED`/`ACTION_TIMEZONE_CHANGED` receiver registered with
  `RECEIVER_NOT_EXPORTED` and unregistered on dispose. No OS job, service,
  manifest receiver or new permission. Saved rows, the detail view and the
  4-line card body are unchanged in every listener state.
- `android/README.md`: full-table today count, the three refresh triggers, the
  unknown marker and the SQL-test limitation; `DayWindowTest` in the test map.

**Static evidence.** No `isToday` or `Calendar` remains in `HistoryScreen.kt`;
`countCapturesBetween`, `localDayRange`, `millisUntilNextLocalMidnight`,
`LifecycleEventEffect`, `registerReceiver`, `unregisterReceiver` and both time
actions are wired in `app/src/main`; no `AlarmManager`, `JobScheduler` or
`WorkManager`; `DB_VERSION` is unchanged. The
`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` visible in `aapt` comes
unconditionally from the pre-existing `androidx.core:core:1.13.1` manifest
(manifest-merger blame), not from this change, and `INTERNET` stays absent.

**Limits.** The pure boundary tests do not test the SQL count. The JUnit-only
classpath cannot execute `SQLiteOpenHelper`; adding Robolectric or
instrumentation is forbidden by the no-new-dependency rule. The query's
inclusive lower bound, exclusive upper bound and index use are source-inspected
only, which is **not** SQL runtime PASS; an independent verifier may later run
it against an isolated database without touching real device records. The
midnight timer, resume refresh and dynamic receiver are deferred to the AUP-6
manual matrix. Authored lines: **286** (268 additions + 18 deletions), above
the 210–250 forecast because the boundary suite is included; below the 400
advisory.

**Commit:** `1c37349` fix(android): count today's captures across the whole
table (source, test and README). A following docs commit records this evidence;
the detailed RED/GREEN output is in the local, git-ignored
`odd/tasks/android-ux-polish-aup4-report.md`.

**Parent acceptance.** The parent read the actual AUP-4 diff and reran
`./gradlew --offline testDebugUnitTest --tests "co.asssie.qrlibre.data.DayWindowTest"`
→ focused PASS. The writer's full run was 29/29 tests green (7
`NotificationTextTest`, 3 `ListenerStatusTest`, 4 `ListenerConnectionTest`, 3
`HistoryPresentationTest`, 5 `CaptureCopyTest`, 7 `DayWindowTest`), recorded at
`HEAD 6d612c1`. AUP-4 is accepted for its implemented code, tests and README.
The SQL platform/runtime behavior (`SQLiteOpenHelper`, the actual `COUNT(*)`
execution, the midnight timer, resume refresh and dynamic receiver) remains
**explicitly pending**; it is not accepted or claimed as a runtime PASS. The
branch still needs the deferred device matrix before any PASS.

### AUP-5 — implemented, corrected, accepted in code review; extended device matrix pending

**TDD evidence (JUnit4 only, offline).** `ui/ContrastTest.kt` was written first
against the real theme. To keep RED behavioral instead of a compile error,
`QrLibreColorValues` and `QrLibreDarkColors` were introduced as a minimal
skeleton with the old values and mappings (`onError = Text`,
`onErrorContainer = Danger`, `DangerText = Danger`). Focused RED: 5 tests
completed, 4 failed with `java.lang.AssertionError`:
`onError on error must be at least 4.5:1, was 3.80:1`,
`DangerText on Card must be at least 4.5:1, was 4.28:1`,
`onErrorContainer on errorContainer must be at least 4.5:1, was 3.98:1`, and
`onError must use the dark text intended for the filled danger surface
expected:<4278913803> but was:<4294309104>`. GREEN after the palette fix:
focused `ContrastTest` → `BUILD SUCCESSFUL`, 5/5 tests. Full verification after
the layout, keyboard, strings and README edits:
`./gradlew --offline testDebugUnitTest assembleDebug --rerun-tasks` →
`BUILD SUCCESSFUL`, 41/41 tasks executed, 34/34 tests (29 prior +
5 `ContrastTest`), 0 failures, 0 errors, 0 skipped; `aapt` lists no
`android.permission.INTERNET`. The complete RED/GREEN output, contrast table
and static checks are in `odd/tasks/android-ux-polish-aup5-report.md`.

**What shipped**
- `ui/AppsScreen.kt`: app rows use `heightIn(min = 56.dp)` with vertical
  padding instead of a fixed 56dp height; labels wrap to `maxLines = 2` with
  ellipsis beyond. The whole row stays `toggleable`, the `Switch` keeps
  `onCheckedChange = null` (one callback), and divider/selection behavior is
  unchanged.
- `ui/QrLibreApp.kt`: bottom bar uses `heightIn(min = 64.dp)`, each item has
  `defaultMinSize(minHeight = 48.dp)` plus vertical padding and no
  `fillMaxHeight`; `selectable(role = Role.Tab)` and the 22dp icon remain.
  `WindowInsets.ime.getBottom(density) > 0` hides only the bar while the
  keyboard is visible; `imePadding()` stays and `tab` is untouched.
- `ui/HistoryScreen.kt`: `ActiveSummary` uses a `FlowRow` so status and the
  `todayCount` + suffix can wrap instead of clipping; the number has no
  `maxLines`, and the count/apps copy is a single merged accessibility
  description (`hero_active_summary`). The two visible danger call sites (clear
  action and voice error) use `QrLibreColors.DangerText`. `CaptureDetail` and
  the 4-line card preview are untouched, so the full detail remains readable.
- `ui/Theme.kt`: pure ARGB `QrLibreColorValues` (`Long`) now backs every
  `QrLibreColors` `Color`; `DangerText = #FF7A7A`, `onError = OnLime` and
  `onErrorContainer = DangerText`; `error` stays `#EB3131`.
- `res/values/strings.xml`: `hero_active_summary`
  ("Escuchando. %1$s %2$s. %3$s.") for the merged semantics.
- `android/README.md`: accessibility behavior, new test-map entry and the
  unverified-on-hardware limitation.

**Static evidence.** No `.height(56.dp)` or `.height(64.dp)` remains under
`ui/`; `heightIn(min = 56.dp)`, `heightIn(min = 64.dp)` and
`defaultMinSize(minHeight = 48.dp)` are present; `onCheckedChange = null` appears
exactly once in `AppsScreen.kt`; `contrastRatio` appears only in the test
source; `QrLibreColorValues` is referenced by both `Theme.kt` and
`ContrastTest.kt`. No dependency, SDK, Gradle or Compose version changed.

**Contrast.** `OnLime` on `Danger` = 4.70:1; `DangerText` on `Page` / `Card` /
`Elevated` / `Divider` = 7.65 / 7.10 / 6.60 / 5.99:1; baseline `Text` and
`Muted` remain 13.75:1 or higher / 5.92:1 or higher on those surfaces.

**Limits.** The JUnit-only classpath cannot render Compose, so font-scale
wrapping, the 48dp touch targets, the IME show/hide transition, TalkBack and
the visual result are structural/static evidence only, not a visual PASS. They
are deferred to the AUP-6 device matrix. Authored lines: **332** (257 additions
+ 75 deletions), above the ~190–230 forecast because the contrast suite and
adaptive summary are included and no test/docs were removed; below the 400
advisory.

**Commit:** `040be54` fix(android): support large text and accessible contrast
(source, test, strings and README). The detailed RED/GREEN output is in the
local, git-ignored `odd/tasks/android-ux-polish-aup5-report.md`. Accepted in
code review by the parent; the extended device matrix remains pending.

**Reopened correction — landscape horizontal safe insets.** The parent's visual
review after the AUP-6 install found a real AUP-5 acceptance gap: with the
device in landscape and Android's three-button navigation on the left, the
first characters of `Tus avisos`, `Privacidad` and `Apps que quieres registrar`
and the left hero/button edge were under the side navigation bar. The root shell
consumed only `statusBarsPadding()` plus a bottom `navigationBars` spacer, so
left/right safe-drawing insets were never applied.

Manual visual RED: the recorded parent captures
`aup6-history-current-102001.png`, `aup6-apps-default-102029.png` and
`aup6-privacy-default-102016.png` (2400x1080, landscape) show the overlap. This
is a manual visual failing check, **not** a JUnit reproduction of the OS
overlap. Manual visual GREEN after `e6c798d`: the APK was reinstalled with
`adb -s ace242eb install -r` (data and user state preserved, no permission,
selection, orientation, font-scale or system-setting change), the app was
opened and the three tabs were captured as
`aup5-insets-history.png`, `aup5-insets-apps.png` and
`aup5-insets-privacy.png`. `uiautomator dump` after the fix shows the tab
content starting at x=130 (the device's 130px left inset) instead of x=0; the
headline bounds are `Tus avisos [185,174]` and `Privacidad [185,124]`, clear of
the side bar. The Apps list was allowed to finish loading before its capture.

**What shipped in the correction.** `ui/QrLibreApp.kt` adds
`windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))`
to the root column, between the existing `statusBarsPadding()` and
`imePadding()`. Only horizontal safe-drawing is consumed, so the top status bar,
bottom navigation-bar spacer and IME bottom padding are not doubled; no
orientation lock, hardcoded pixel margin, dependency, state, permission or
database change was made. `android/README.md` notes the landscape side-bar and
cutout behavior. Authored lines: **21** (17 additions + 4 deletions).

**Correction verification.** `./gradlew --offline testDebugUnitTest assembleDebug`
→ `BUILD SUCCESSFUL`, all **36** existing tests preserved (0 failures, 0
errors, 0 skipped); `aapt` still lists no `android.permission.INTERNET`. The
correction passed the parent's 1.0 landscape visual review (three tabs). The
AUP-6 200% font matrix remains pending. Not a final PASS.

**Reopened correction — Apps chrome under the landscape IME.** The independent
final verifier reproduced a blocking AUP-5 defect at 2400x1080 landscape and
font scale 1.0: the IME started at y=529, the search field ended at y=495, and
the fixed heading/description/summary left the weighted list with no visible
row and no scrollable node. Hiding the bottom bar did not recover the fixed
chrome height (report:
`/private/var/folders/ff/hrsxp6x120z072qb2t7ccxq40000gp/T/opencode/qr-ux-independent-final.md`).

**TDD evidence.** `ui/AppsChromePolicyTest.kt` was written first against a real
production policy. A `FULL`-only skeleton compiled and failed behaviorally:
4 tests completed, 3 failed with
`imeWinsEvenOnTallViewportsAndLargeFont expected:<SEARCH_ONLY> but was:<FULL>`,
`shortLandscapeUsesCompactChrome expected:<COMPACT> but was:<FULL>` and
`largeFontFallsBackToCompactEvenInTallPortrait expected:<COMPACT> but was:<FULL>`.
GREEN after `ui/AppsChromePolicy.kt`: focused suite 4/4, `BUILD SUCCESSFUL`.

**What shipped.** The pure policy returns `SEARCH_ONLY` whenever the IME is
visible, `COMPACT` below 360dp viewport height or from font scale 1.5, and
`FULL` for ample portrait heights. `AppsScreen` consumes it with
`BoxWithConstraints.maxHeight` and `LocalDensity.fontScale`; `QrLibreApp` passes
the IME signal captured before `imePadding()` consumes the insets. `SEARCH_ONLY`
hides the header and fixed summary, tightens the search top padding and keeps
the filtered `LazyColumn` weighted and scrollable. `COMPACT` uses a compact
title, omits the long description and moves `SelectionSummary` into a list
footer. `FULL` keeps the original heading, description, search and summary.
Query (`rememberSaveable`), selection/preferences and the single
`Switch(onCheckedChange = null)` callback are unchanged; there is no nested
vertical scroll, orientation lock or new dependency.

**Device evidence.** The updated APK was installed with
`adb -s ace242eb install -r` (data preserved; no permission, selected-app,
font-scale, rotation or system-setting change). Device stayed in its existing
landscape orientation (2400x1080, rotation 270). The Apps tab was opened, the
search field focused, the harmless query `a` typed, and `dumpsys input_method`
showed `mInputShown=true`. With the IME open, `uiautomator` showed
`EditText "a" [185,91][2190,179]`, a scrollable list
`[130,245][2190,529]`, and rows `AliExpress [317,305][2111,362]` and
`Amazon compras [317,462][2111,519]`; after one swipe both rows were complete
(`AliExpress [317,245][2111,284]`, `Amazon compras [317,384][2111,441]`). No
switch was tapped. Closing the IME restored the compact header
`[185,102][750,163]`, kept `EditText "a" [185,202][2190,300]`, restored the
bottom tabs `[434,1010]`, `[1188,1010]`, `[1877,1010]` and enlarged the list to
`[130,356][2190,901]`. The app was left on the History tab and no user DB,
clipboard, permission, selection or unrelated app was touched.

Screenshots: `aup5-apps-ime-landscape.png`,
`aup5-apps-ime-scrolled.png` and `aup5-apps-query-restored.png` under
`/private/var/folders/ff/hrsxp6x120z072qb2t7ccxq40000gp/T/opencode/`.

**Verification.** `./gradlew --offline testDebugUnitTest assembleDebug` and the
`--rerun-tasks` variant → `BUILD SUCCESSFUL`, 41/41 tasks executed, **40/40
tests** (36 prior preserved + 4 `AppsChromePolicyTest`), 0 failures, 0 errors,
0 skipped; `aapt` still has no `android.permission.INTERNET`. Authored lines:
**241** (208 additions + 33 deletions).

**Commit:** `c030819` fix(android): keep the apps list usable with the
landscape keyboard.

**Limits.** The 200% font path is covered only by the pure policy test
(`fontScale = 2.0` → `COMPACT`); changing the phone font was not authorized, so
this is not a fake 200% device PASS. The IME row/scroll evidence is manual
UI-hierarchy and screenshot evidence, not an automated Compose layout test. The
writer has no image input; hierarchy bounds and local pixel sampling were used
for the GREEN claim. AUP-6's extended matrix remains pending; this is not a
final PASS.

**Parent review — final for AUP-5.** The parent read the actual diffs for
`040be54`, `e6c798d` and `c030819`, reviewed the three-tab 1.0 landscape
screenshots and reran each focused AUP-5 test. The latest full build was 40
Android tests with 0 failures/0 errors. The independent verifier separately
confirmed the post-`c030819` phone behavior: 2400x1080, font 1.0, 284px IME-open
list viewport, visible rows and scroll, query retained after closing the
keyboard, no fix-caused defect. AUP-5 is accepted at the code/review level;
200% font and TalkBack remain unchecked pending consent.

### AUP-6 — implemented, accepted in code review; extended device validation pending

**TDD evidence (JUnit4 only, offline).** The production screen now renders an
`internal` ordered `privacySections` list. To keep RED behavioral rather than a
compile error, a refactor-only skeleton moved the existing entries into that
list with the **old** order and the old cards. `ui/PrivacySectionsTest.kt` was
written against the required order and failed on an assertion:

```text
PrivacySectionsTest > localReassuranceComesFirstAndNoSectionIsDropped FAILED
    java.lang.AssertionError at PrivacySectionsTest.kt:19
2 tests completed, 1 failed
```

The XML message showed the expected title ids (stays, reads, limit, delete,
identity) against the skeleton's actual ids (reads, stays, delete, identity,
limit). The second test, `eachSectionKeepsItsOwnBody`, was green in the
skeleton by design and guards title→body swaps. GREEN after reordering the real
list, the compact layout and the new `privacy_intro`: focused
`PrivacySectionsTest` → 2/2, `BUILD SUCCESSFUL`.

**What shipped**
- `ui/PrivacyScreen.kt`: `PrivacySection` + `privacySections` (Dónde queda, Qué
  lee, Qué no hace, Puedes borrarlo, Sin cuenta y sin anuncios). Borderless
  rows with a 20dp inline icon, `titleSmall` bold title and `bodyMedium` muted
  body; `HorizontalDivider` between sections; no per-entry `Card` and no 40dp
  tile. The closing "Nada sale de tu teléfono." is one compact row above the
  unchanged source footer.
- `res/values/strings.xml`: only `privacy_intro` changed to "Tus avisos se
  quedan en este teléfono. Esto es lo que la app hace y lo que no." All
  disclosure strings are preserved verbatim, including the irrecoverable
  deletion and the no-payment-confirmation / no-bank-query statements.
- `ui/PrivacySectionsTest.kt`: order/size plus title→body pairing, reading the
  real production list the screen consumes.
- `android/README.md`: local-first privacy note, test-map entry and coverage
  sentence.

**Static evidence.** `grep -n "Card(" PrivacyScreen.kt` → none; all five
`privacy_*_body` resources appear in `privacySections` in the new order;
`strings.xml` still contains "Nada sale de tu teléfono", "No se puede
recuperar", "permiso de internet" and "no es un pago verificado".

**Full verification (foreground, offline).**
`./gradlew --offline testDebugUnitTest assembleDebug --rerun-tasks` →
`BUILD SUCCESSFUL`, 41/41 tasks executed, **36/36 tests** (34 prior + 2
`PrivacySectionsTest`), 0 failures, 0 errors, 0 skipped. `aapt` lists only
`co.asssie.qrlibre.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, no
`android.permission.INTERNET`.

**Device evidence.** `adb -s ace242eb install -r app-debug.apk` → `Success`; app
data was preserved and no selection, permission or system setting was changed.
Device `ace242eb` (POCO F3, Android 13) was in landscape, font scale 1.0 (read
only). Captures under
`/private/var/folders/ff/hrsxp6x120z072qb2t7ccxq40000gp/T/opencode/`:
`aup6-history-current-102001.png`, `aup6-privacy-default-102016.png`,
`aup6-privacy-scrolled-default-102023.png`, `aup6-apps-default-102029.png`.
`uiautomator dump` confirmed the five privacy sections in the new order plus
"Nada sale de tu teléfono." and the source footer after scrolling.

**Limits.** The 200% font scale and the full branch device matrix were not run:
changing the font scale would modify a system setting, which this task forbids.
The parent reviewed the actual 1.0 landscape/current-state screenshots; the
200% font, TalkBack and populated-runtime checks remain pending consent, so
this is **not** a device/visual PASS. Authored lines: **189** (115 additions +
74 deletions, including the new 45-line test), inside the ~200–230 forecast and
below the 400 advisory.

**Commit:** `2f8ffb9` refactor(android): make the privacy screen concise and
local-first (source, test, strings and README). Detailed RED/GREEN output and
screenshot paths are in the local, git-ignored
`odd/tasks/android-ux-polish-aup6-report.md`. Accepted in code review by the
parent; extended device validation remains pending.

---

## Runtime acceptance (authorized device pass)

Device `ace242eb` (POCO F3, Android 13, 2400x1080), branch head `6c0d948`,
font scale restored to 1.0. Full report:
`/private/var/folders/ff/hrsxp6x120z072qb2t7ccxq40000gp/T/opencode/qr-ux-device-acceptance.md`
(31 screenshots beside it; temporary proof workspace only).

| Check | Result |
| --- | --- |
| Font 100% and 200%, all three tabs, scroll, long labels, bottom nav, short-landscape search with IME | PASS |
| Listener states: no permission → "Dar acceso"; no apps → "Elegir apps"; selected app → "Conectando…" then "Escuchando"; unset → "Elegir apps" | PASS (MIUI needed a listener re-toggle to rebind; the UI stayed truthful) |
| Synthetic notice → History → full uncapped detail → system Back → explicit copy → clipboard read → clear dialog cancelled | PASS (470-char body preserved; 553-char copy contained only synthetic text) |
| Production `CapturesDb` `COUNT` on an isolated database | PASS (601 in-day rows vs 500-row list; start inclusive, end exclusive; real `qr_libre.db` unchanged) |
| Live midnight/time-zone rollover, TalkBack | NOT EXECUTED (out of approved scope); covered structurally and by unit tests only |
| No `INTERNET` permission, no new dependencies, `DB_VERSION` unchanged | CONFIRMED |

**Restoration.** Font scale 1.0, rotation/accelerometer originals, QR Libre
listener enabled with the two other listeners preserved, selection back to its
original package (`shared_prefs` SHA identical), real history 0 rows with
`PRAGMA integrity_check=ok`, no harness package installed, repository clean.

**Known side effect.** The authorized copy test overwrote the device clipboard
and MIUI provides no supported way to clear it, so it still holds the
synthetic text. No private content was read or placed there.

**Still open (not a defect, not a PASS).** Live midnight/time-zone rollover,
TalkBack reading order, and a human eyeball on the 200% screenshots. A MIUI
rebind quirk after `cmd notification allow_listener` is environmental, not
app-side.
