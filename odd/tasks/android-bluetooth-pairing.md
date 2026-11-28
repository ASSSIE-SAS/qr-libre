# Android: Bluetooth pairing (capture phone → speaker phone)

Branch: `feat/android-bluetooth-pairing` (stacked on `feat/android-capture-tools`)
Status: authorized 2026-11-28. TDD ON. User decision: Bluetooth + QR.

## Roles

- **Emisor**: el teléfono del comerciante. Captura notificaciones y las envía.
  No habla en voz alta cuando está emparejado.
- **Receptor**: el teléfono viejo que se queda en el local. Recibe y **suena**.
  Sin historial largo: muestra el último aviso recibido y lo dice.

Un solo peer por emisor. Emparejar otro reemplaza al anterior.

## Transporte (por qué Bluetooth)

No se añade `INTERNET`. La promesa publicada ("nada sale de tu teléfono") sigue
siendo cierta. Alcance de local, sin router.

- Emisor: `BluetoothGattServer` que anuncia un servicio propio, más un
  characteristic de avisos con `notify`.
- Receptor: `BluetoothGatt` cliente, se conecta y se suscribe.
- Cada mensaje va firmado con HMAC-SHA256 usando el secreto del emparejamiento;
  el receptor descarta lo que no verifique.

## Emparejamiento por QR

Payload (texto, versión + campos separados por puntos):

```
qrlibre:1:<deviceName b64url>:<secret b64url 16 bytes>:<serviceUuid>
```

El emisor muestra el QR; el receptor lo escanea, guarda nombre, secreto y UUID,
y busca el anuncio que coincide. El secreto nunca se escribe en el anuncio.

## Permisos nuevos (ninguno es INTERNET)

- Emisor: `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT`.
- Receptor: `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `CAMERA` (solo para escanear).
- En Android 11 o menor, `BLUETOOTH`/`BLUETOOTH_ADMIN` y ubicación para escanear.

## Dependencias nuevas (aprobadas al elegir QR)

- `com.google.zxing:core:3.5.3` para generar el QR.
- `com.journeyapps:zxing-android-embedded:4.3.0` para escanear.

Nada más.

## Tareas

- [ ] **P1** Codec del payload + render del QR a Bitmap. Puro y probado.
- [ ] **P2** Rol y estado de emparejamiento persistidos; pantalla de ajustes con
      "Este teléfono captura" / "Este teléfono suena" y "Desemparejar".
- [ ] **P3** Emisor: GATT server, anuncio, envío firmado al capturar.
- [ ] **P4** Receptor: escáner QR, conexión, verificación HMAC, voz.
- [ ] **P5** Validación con dos teléfonos reales.

## Criterios

- Sin `INTERNET` en el manifiesto.
- Un aviso capturado en el emisor suena en el receptor en menos de ~2 segundos.
- Un mensaje con firma inválida no suena y no se muestra.
- Desemparejar detiene el envío y la escucha.
- La app sin emparejar se comporta exactamente como hoy.
- Pruebas existentes (60) siguen verdes.

## Riesgos

- El modo periférico BLE varía por fabricante; MIUI ya nos dio problemas.
- El escáner añade una actividad externa y permiso de cámara.
- Dos teléfonos son necesarios para la validación real: P5 puede quedar pendiente.
