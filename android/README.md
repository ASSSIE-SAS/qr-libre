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
- **Accesibilidad**: app rows grow with the font scale and allow two label lines
  instead of a fixed height; navigation items keep a 48dp minimum touch target;
  landscape three-button navigation bars and display cutouts are respected on
  the left/right edges, including the bottom bar; the bottom bar yields its
  space to the keyboard while the app search field is focused and the Apps
  header/summary collapse so the filtered list stays scrollable, then everything
  returns with the same tab, query and list state. Danger copy uses a dedicated
  token with at least 4.5:1 contrast on the dark surfaces, while the filled
  error color stays `#EB3131` for future error containers.
- **Privacidad**: local-first. The screen opens with where the data stays, then
  reads, limits, deletion and the absence of an account, ads or tracking. The
  sections are borderless and divided instead of repeated cards; it closes with
  a one-line reminder that nothing leaves the phone and the project source.
- **Emparejamiento con dos teléfonos**: one phone chooses "Este teléfono
  captura" and shows a QR with its name, a 16-byte HMAC secret and its GATT
  service UUID. The old phone chooses "Este teléfono suena", scans the code with
  the embedded ZXing scanner and stores the peer. The capture phone advertises
  the service and pushes each stored notice as `JSON\nv1:HMAC-SHA256` on a
  notify characteristic (`8f0a2c1e-4b3d-4a6f-9e21-5c7d8b9a0f13`). The speaker
  connects as a GATT client, requests a 517-byte MTU before discovery,
  subscribes to notifications and verifies every frame before showing or
  speaking it. A frame with a bad signature is counted and dropped; it is never
  displayed and never spoken. "Desemparejar" stops both sides.
- **El receptor suena solo con la app abierta.** There is no background service
  by design: `MainActivity.onStart` starts the GATT client and `onStop` stops
  it. The pairing screen states this in plain Spanish. A notice that arrives
  while the app is closed is not heard, though it is still stored on the capture
  phone and can be read there later.
- **Leer en voz alta**: a `TextToSpeech` engine initialised by `MainActivity`
  reads the manual Spanish sample and, on the speaker role, each verified
  notice as "Aviso de <app>. <cuerpo>" (whitespace collapsed, long bodies cut
  at a word boundary). It never adds a payment confirmation: a notification is
  not a verified payment. A failed engine shows a message and the speaker stays
  silent; speech stops when the app leaves the foreground and the engine is shut
  down in `onDestroy`.

## Requirements

- JDK 17 (Temurin 17 was used).
- Android SDK with `platforms;android-35` and `build-tools;34.0.0`.
- `ANDROID_HOME` pointing at the SDK. There is no `local.properties` in git.
- No Android Studio, no emulator and no global Gradle are required: the
  checked-in wrapper pins Gradle 8.9.
- Speaker pairing by QR needs Android 12 (API 31) or newer. On Android 11 and
  lower the platform requires a location permission to scan for BLE devices,
  which this build deliberately does not declare; capture keeps working there.

## Build and test

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
cd android
./gradlew --offline testDebugUnitTest assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. The JVM suite
has 119 tests: the original 101 plus the pairing payload parser, the spoken
sentence builder and the speaker connection reducer.

```bash
ls -l app/build/outputs/apk/debug/
"$ANDROID_HOME/build-tools/34.0.0/aapt" dump permissions \
  app/build/outputs/apk/debug/app-debug.apk
```

The `aapt` output must contain `CAMERA` and `BLUETOOTH_SCAN` and must not
contain `android.permission.INTERNET`.

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

### Pairing the two phones

1. On the capture phone: Ajustes → Emparejamiento → "Este teléfono captura".
   Allow Bluetooth when asked, then leave the QR on screen.
2. On the speaker phone: install the same APK, Ajustes → Emparejamiento →
   "Este teléfono suena", tap "Permitir Bluetooth" and accept the camera prompt
   when you tap "Escanear QR".
3. Scan the capture phone's QR. The speaker should show the capture phone's
   name and "Buscando al teléfono que captura…", then "Conectado. Escuchando
   avisos." once the link is up.
4. Keep QR Libre open on the speaker. Trigger a selected notification on the
   capture phone: it should appear in "Último aviso" and be read aloud within
   a couple of seconds.
5. "Desemparejar" on either phone stops the link. Changing roles also forgets
   the peer.

The speaker is intentionally in-app only. It does not run a background
service, so locking the phone or leaving QR Libre stops the audio until the app
is open again.

## Versions

| Piece | Version |
| --- | --- |
| Gradle | 8.9 (wrapper) |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.0.21 |
| Compose compiler plugin | 2.0.21 |
| Compose BOM | 2024.11.00 (Material 3) |
| ZXing core | 3.5.3 |
| ZXing Android Embedded (scanner) | 4.3.0 |
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
    data/PairingStore.kt     persisted role, single peer and local QR payload
    pairing/PairingPayload.kt QR codec, QR matrix and secret minting
    pairing/CaptureMessage.kt signed frame, HMAC verify and JSON encoder
    pairing/CapturePayloadParser.kt flat JSON reader for incoming frames
    pairing/SpokenText.kt    pure sentence builder for the speaker
    pairing/SpeakerState.kt  connection state machine and speaker UI state
    pairing/PairingState.kt  pairing summary for the screens
    pairing/BluetoothSendPolicy.kt advertise/send gates
    bluetooth/CaptureBroadcaster.kt emitter GATT server and advertisement
    bluetooth/CaptureReceiver.kt    speaker GATT client and verified StateFlow
    bluetooth/BluetoothPermissions.kt runtime permission sets
    ui/PairingScreen.kt      role picker, QR, embedded scanner, speaker panel
    notifications/QrLibreNotificationListener.kt
    notifications/NotificationText.kt
    notifications/NotificationAccess.kt
    notifications/ListenerStatus.kt
    notifications/ListenerConnection.kt
  app/src/test/java/co/asssie/qrlibre/notifications/
    NotificationTextTest.kt  normalizeNotificationText
    ListenerStatusTest.kt    permission/apps/connection precedence
    ListenerConnectionTest.kt listener binding signal
  app/src/test/java/co/asssie/qrlibre/pairing/
    PairingPayloadTest.kt     QR codec and QR matrix
    CaptureMessageTest.kt     known-answer HMAC and signed frames
    BluetoothSendPolicyTest.kt advertise/send gates
    PairingStateTest.kt       pairing summary and first-peer rule
    CapturePayloadParserTest.kt frame payload parser
    SpokenTextTest.kt         spoken sentence and truncation
    SpeakerStateTest.kt       speaker connection reducer
  app/src/test/java/co/asssie/qrlibre/ui/
    HistoryPresentationTest.kt first-run and voice visibility policy
    ContrastTest.kt         real palette contrast on the dark surfaces
    PrivacySectionsTest.kt  privacy section order and bodies
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
- `BLUETOOTH_ADVERTISE`/`BLUETOOTH_CONNECT` let the capture phone announce and
  send; `BLUETOOTH_SCAN` (with `neverForLocation`) and `BLUETOOTH_CONNECT` let
  the speaker find and receive. `CAMERA` is only for the embedded QR scanner
  activity. Legacy `BLUETOOTH`/`BLUETOOTH_ADMIN` are capped at API 30.
- Captures stay in the app's private SQLite database and preferences. The
  pairing secret lives in private preferences, travels only inside the QR and is
  never written to an advertisement or a log.
- `android:allowBackup="false"` keeps both out of cloud backups.
- Clearing the history deletes the rows. Uninstalling the app deletes the
  database.

## Limits of this version

- **Two-device validation (P5) is pending.** One phone was available for UI and
  permission checks; a real capture-to-speaker transfer could not be completed
  here. The scanner, the permission prompts and the speaker role screen were
  exercised on hardware, but the GATT link and the audio have not been verified
  between two physical phones.
- Speaker pairing needs Android 12+. On Android 11 and lower the platform
  requires a location permission to scan for BLE, which this build does not
  declare.
- The speaker is **in-app only**. It stops in `onStop`; there is no foreground
  service, so the phone must stay unlocked with QR Libre open to sound.
- Peripheral mode (advertising) varies by vendor. MIUI has caused problems
  before; a capture phone that cannot advertise cannot reach the speaker.
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
