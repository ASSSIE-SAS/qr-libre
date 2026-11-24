# Android integration guide

Recommended: Kotlin + Jetpack Compose, Room, Android TextToSpeech.

SDK QR colombiano (from upstream README; verify published version before shipping):

```kotlin
implementation("dev.code93:emvdecoder:2.1.0")
```

Pipeline:
1. Explicitly request notification-access permission (not SMS permissions by default).
2. Whitelist specific bank app package IDs *after device verification*.
3. Normalize notification text, parse with provider-specific rules, classify `CREDIT/DEBIT/UNKNOWN`.
4. Record event as `UNVERIFIED`; never announce 'confirmed' from notification alone.
5. Deduplicate per provider + transaction reference when available; fallback to short-window fingerprint and manual review.
6. Save locally, announce via TTS, and sync only on user opt-in.

Example spoken notice: "Se recibió una notificación de abono de treinta y cinco mil pesos. Verifica el movimiento en tu banco."

Do not copy a UPI parser and assume Colombian notifications have identical formats.
