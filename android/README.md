# QR Libre — Android capture app (v0)

An installable debug app that listens to notifications from the apps you pick,
stores their text on the phone, and shows the history. It exists to collect
real bank and wallet notification texts before writing payment rules.

It does **not** detect payments, amounts, banks or rules. It never says a
payment was confirmed.

## What it does

- Detects whether notification access is enabled and links to the system
  setting when it is not.
- **Historial** separates three signals: notification access, the system
  listener binding and a non-empty app selection. "Escuchando" appears only when
  all three hold; otherwise the status card shows a missing permission,
  "Apps que quieres registrar" or "Conectando con el sistema…". On a fresh
  install the screen leads with one compact setup card and a single primary
  action ("Dar acceso" or "Elegir apps"); the today count is a compact inline
  summary only once capture is active. The selected-app count follows changes
  made on the Apps tab.
- `QrLibreNotificationListener` reads `EXTRA_TITLE`, `EXTRA_TEXT`,
  `EXTRA_BIG_TEXT` and `EXTRA_SUB_TEXT` from notifications posted by the
  selected packages only. Its own package is skipped.
- `CapturesDb` (`SQLiteOpenHelper`, no ORM) stores
  `package`, `app`, `title`, `body` and `epoch_ms` in `qr_libre.db`.
- **Historial**: newest first, with app label, local time, title and body.
  A caption states that these are stored notifications, not payment
  confirmations. Saved rows stay visible when permission is revoked, no app is
  selected or the listener is connecting; only the empty-history billboard is
  limited to the active state. "Vaciar historial" asks for confirmation and
  deletes every row. Today's count is a `COUNT(*)` over the whole table in the
  current local day (`epoch_ms >= local midnight` and `< next local midnight`),
  so it does not share the 500-row list limit. It refreshes after local
  midnight, on resume, and when the system time or time zone changes; while the
  count is not read yet, the summary shows an unknown marker instead of a zero.
- **Detalle del aviso**: tapping a card opens a full-height, scrollable detail
  with the complete title and body, the app label, the package name and the full
  local timestamp (`HH:mm:ss dd/MM/yyyy`). The list card remains a 4-line preview
  and keeps its short `HH:mm dd/MM` time. The detail text is selectable. "Copiar
  texto" is the only clipboard action: it copies on tap, never on open, and
  carries the reminder that the text can be pasted in another app while modern
  Android generally keeps background apps from reading the clipboard. There is no
  share sheet and no automatic copy. "Volver" and system back return to the same
  list position, and the detail opens in every listener state, not only when
  capture is active.
- **Apps**: headed "Apps que quieres registrar", with launcher apps, icon, label
  and a checkbox. The selection is a `Set<String>` in the `qr_libre_settings`
  preferences. A search field appears when the list has more than 12 apps.
- **Privacidad**: what it reads, where it stays, how to delete it, and what it
  does not do.
- **Leer en voz alta**: a `TextToSpeech` engine initialised by `MainActivity`
  speaks one fixed Spanish sentence. It is a manual sample shown only when
  capture is active; the app does not announce notifications automatically. A
  failed engine shows a message; speech stops when the app leaves the
  foreground and the engine is shut down in `onDestroy`.

## Requirements

- JDK 17 (Temurin 17 was used).
- Android SDK with `platforms;android-35` and `build-tools;34.0.0`.
- `ANDROID_HOME` pointing at the SDK. There is no `local.properties` in git.
- No Android Studio, no emulator and no global Gradle are required: the
  checked-in wrapper pins Gradle 8.9.

## Build and test

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
cd android
./gradlew assembleDebug
./gradlew test
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

```bash
ls -l app/build/outputs/apk/debug/
"$ANDROID_HOME/build-tools/34.0.0/aapt" dump permissions \
  app/build/outputs/apk/debug/app-debug.apk
```

The `aapt` output must not contain `android.permission.INTERNET`.

## Installing on a phone

```bash
"$ANDROID_HOME/platform-tools/adb" install -r \
  app/build/outputs/apk/debug/app-debug.apk
```

1. Open the app. The Historial tab shows whether notification access is on.
2. Tap "Abrir ajustes de acceso", find QR Libre and enable it.
3. Go to Apps and check the bank or wallet app you want to sample.
4. Trigger a notification, then return to Historial. Its title and text
   should be there.
5. "Leer en voz alta" plays the fixed test sentence.

## Versions

| Piece | Version |
| --- | --- |
| Gradle | 8.9 (wrapper) |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.0.21 |
| Compose compiler plugin | 2.0.21 |
| Compose BOM | 2024.11.00 (Material 3) |
| compileSdk / targetSdk | 35 |
| minSdk | 26 |
| Java toolchain | 17 |
| applicationId / namespace | `co.asssie.qrlibre` |

## Project layout

```
android/
  settings.gradle.kts
  build.gradle.kts
  gradle/wrapper/            pinned Gradle 8.9 wrapper
  app/src/main/java/co/asssie/qrlibre/
    MainActivity.kt          tabs, permission state, TextToSpeech lifecycle
    ui/QrLibreApp.kt         app shell, tab row, listener status
    ui/HistoryScreen.kt      status card, history note, voice sample, capture list
    ui/HistoryPresentation.kt first-run and voice visibility policy
    ui/CaptureDetail.kt      full capture detail and explicit copy
    ui/AppsScreen.kt         app picker and search
    ui/PrivacyScreen.kt      privacy copy
    ui/Components.kt         shared empty-state card
    ui/Theme.kt              small Material 3 theme
    data/Capture.kt          row model
    data/CaptureCopy.kt      pure clipboard payload builder
    data/CapturesDb.kt       SQLiteOpenHelper
    data/SelectedApps.kt     SharedPreferences set
    data/SelectionEvents.kt  in-process selection change signal
    data/CaptureEvents.kt    in-process refresh signal
    notifications/QrLibreNotificationListener.kt
    notifications/NotificationText.kt
    notifications/NotificationAccess.kt
    notifications/ListenerStatus.kt
    notifications/ListenerConnection.kt
  app/src/test/java/co/asssie/qrlibre/notifications/
    NotificationTextTest.kt  normalizeNotificationText
    ListenerStatusTest.kt    permission/apps/connection precedence
    ListenerConnectionTest.kt listener binding signal
  app/src/test/java/co/asssie/qrlibre/ui/
    HistoryPresentationTest.kt first-run and voice visibility policy
  app/src/test/java/co/asssie/qrlibre/data/
    CaptureCopyTest.kt       verbatim clipboard payload
    DayWindowTest.kt         local-day boundaries and midnight delay
```

`normalizeNotificationText` is a pure function: it trims each line, collapses
internal whitespace runs, drops empty lines and returns `""` for null or blank
input.

## Privacy

- No `INTERNET` permission is declared, intentionally.
- No account, no ads, no analytics, no network code.
- Captures stay in the app's private SQLite database and preferences.
- `android:allowBackup="false"` keeps both out of cloud backups.
- Clearing the history deletes the rows. Uninstalling the app deletes the
  database.

## Limits of this version

- No device or emulator was available while building this. The logic is
  covered by unit tests for `normalizeNotificationText`, the listener state, the
  history visibility policy and the clipboard payload; install, notification
  capture, detail navigation and the clipboard are unverified on hardware.
- A notification update posts again and can create a second row. There is no
  deduplication key yet.
- The history screen loads the newest 500 rows. The table keeps the rest until
  the user clears it. Today's count is not limited to those rows; the boundary
  logic is unit-tested, but the SQL count itself has no automated test because
  the JUnit-only classpath cannot run `SQLiteOpenHelper`.
- App labels come from `PackageManager`; a source whose package is not visible
  to the app falls back to the package name.
- Debug APK only. No release signing or Play Store packaging.

## License

MIT, as the rest of the repository. A project of ASSSIE SAS.
