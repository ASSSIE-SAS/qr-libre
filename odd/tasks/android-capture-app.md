# Feature: app Android — captura de notificaciones (v0)

## Objetivo

Una app instalable que escuche las notificaciones de las apps que el usuario
elija, las guarde en el teléfono y las muestre. Con un botón que lea en voz alta
un texto de prueba.

No interpreta pagos todavía. Su trabajo es **capturar los textos reales** de las
notificaciones bancarias colombianas, que es lo que falta para escribir las
reglas. Es el mismo camino que funcionó con el correo: primero el formato real,
después la regla.

## Problema

No conocemos el texto real de una notificación push de Nequi, Daviplata o
Bancolombia. Escribir el detector adivinando produce falsos positivos, y en una
herramienta que anuncia dinero un falso positivo es peor que no tener
herramienta.

## Alcance

Dentro:

- Proyecto Gradle que compila un APK de depuración sin Android Studio.
- Kotlin + Jetpack Compose, una sola actividad.
- `NotificationListenerService` que guarda el texto de las notificaciones de las
  apps seleccionadas.
- Pantalla de permiso con explicación, con enlace a los ajustes del sistema.
- Selector de apps instaladas para elegir cuáles escuchar.
- Historial local en SQLite del sistema (`SQLiteOpenHelper`), sin ORM ni
  generación de código.
- Botón "leer en voz alta" con `TextToSpeech`.
- Pantalla de privacidad dentro de la app, corta y en español.

Fuera:

- Detección de pagos, montos, bancos o reglas.
- Sincronización, red, backend, cuentas.
- Room, KSP, KAPT, Hilt, navegación multi-pantalla.
- Publicación en Play Store.

## Restricciones

- `applicationId` `co.asssie.qrlibre`, nombre visible `QR Libre`.
- `minSdk 26`, `compileSdk 35`, `targetSdk 35`, JDK 17.
- Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21, plugin `org.jetbrains.kotlin.plugin.compose`, Compose BOM 2024.11.00.
- Sin dependencias fuera de AndroidX, Compose y Material 3.
- Nada sale del teléfono. Sin permiso de internet en el manifiesto.
- El texto de la notificación se guarda completo, porque es la muestra que se va a
  analizar; el usuario puede borrar el historial desde la app.
- La app nunca afirma que un pago se confirmó.

## Tareas

- [x] **T1** Proyecto Gradle que compila `assembleDebug` y produce un APK.
- [x] **T2** Onboarding del permiso `BIND_NOTIFICATION_LISTENER_SERVICE`, con estado y atajo a ajustes.
- [x] **T3** `NotificationListenerService` que se suscribe a las apps elegidas y guarda `paquete`, `app`, `titulo`, `texto`, `epoch_ms`.
- [x] **T4** Historial en pantalla: lista con app, hora y texto, y botón para vaciar.
- [x] **T5** Selector de apps: lista de apps instaladas con casilla, persistida en preferencias.
- [x] **T6** `TextToSpeech`: botón que lee una frase de prueba y detiene la lectura al salir.
- [x] **T7** Pantalla de privacidad en la app y README del módulo `android/`.

## Criterios de aceptación

- `./gradlew assembleDebug` termina sin errores y deja el APK en `app/build/outputs/apk/debug/`.
- El APK instala en Android 8 o superior.
- Con el permiso concedido y una app seleccionada, una notificación de esa app aparece en el historial con su texto.
- Con el permiso denegado, la app explica qué falta y no falla.
- "Leer en voz alta" dice la frase de prueba; sin el motor TTS instalado, la app lo informa sin caerse.
- El manifiesto no declara `android.permission.INTERNET`.

## Verificación

```
cd android
./gradlew assembleDebug
./gradlew test
~/Library/Android/sdk/build-tools/34.0.0/aapt dump permissions \
  app/build/outputs/apk/debug/app-debug.apk
```

## Siguiente paso (fuera de esta fase)

Con dos o tres notificaciones reales capturadas, se escriben las reglas de
detección y se reemplaza el botón de prueba por el aviso automático.
