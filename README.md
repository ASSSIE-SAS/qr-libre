# QR Libre

**Tu pago suena. Tu negocio sigue.**

Una app de Android, gratuita y de código abierto, que avisa en voz alta cuando entra un pago por QR. Para el comerciante que no puede dejar de atender para mirar el celular.

Un proyecto de [ASSSIE SAS](https://www.asssie.co) · [asssie.co/qr-libre](https://www.asssie.co/qr-libre)

---

## Estado

**En construcción.** Aquí todavía no hay una app instalable.

Lo que existe hoy, en este repositorio:

- Un backend de ejemplo en Rust (Axum) que registra *observaciones*, no pagos.
- La documentación del pipeline de Android que se va a construir.
- Nada de código Android todavía.

Lo que **no** existe: la app, un APK, parsers de notificaciones por banco, ni ninguna integración con entidades financieras.

Si llegaste buscando el APK, todavía no está. Cuando esté, va a estar publicado aquí y en la página del proyecto.

## Qué va a hacer

- Escuchar los avisos de pago que ya llegan al teléfono, **solo de las apps que tú actives**.
- Convertir ese aviso en una frase corta y decirla en voz alta.
- Guardar un historial local de avisos.

## Qué no va a hacer

- **No confirma pagos.** Un aviso del banco no es un pago verificado.
- **No se conecta a tu cuenta bancaria** ni consulta saldos ni movimientos.
- **No reemplaza el comprobante del banco.** El comprobante y el saldo siguen siendo los del banco.
- No pide cuenta, no muestra anuncios y no envía el contenido de los mensajes a ningún servidor.

## Cómo va a funcionar

```
App Android (Kotlin + Compose)
  └─ NotificationListenerService
       └─ parser por app de origen (banco / billetera)
            └─ clasificación: abono, débito, desconocido
                 └─ deduplicación
                      └─ Room (historial local)
                           └─ TextToSpeech
```

El backend es opcional y nunca convierte una observación en un pago confirmado.

## El backend de ejemplo

Requiere Rust y Cargo.

```bash
cd backend
cargo test
cargo run
# en otra terminal
curl http://localhost:3000/health
```

Escucha solo en `127.0.0.1:3000`. **No tiene autenticación ni persistencia**: está para probar el modelo de datos, no para producción. No lo expongas a internet ni le mandes datos reales.

## Roadmap

**Milestone 0 — investigación**

- [ ] Confirmar los textos reales de notificaciones de los bancos y billeteras que usan los comerciantes, con muestras anonimizadas y consentimiento.
- [ ] Auditar licencias de los proyectos de referencia.

**Milestone 1 — MVP solo Android**

- [ ] `NotificationListenerService` y onboarding explícito del permiso.
- [ ] Parsers por app de origen, clasificador abono/débito, deduplicación.
- [ ] Historial local con Room, aviso por voz.
- [ ] Pruebas: falsos positivos, notificaciones perdidas, latencia, reinicios.

**Milestone 2 — sincronización opcional**

- [ ] Registro de dispositivos, HTTPS, credenciales revocables.
- [ ] Almacén multi-comercio, idempotencia.
- [ ] Sincronización que el usuario habilita a mano.

**Milestone 3 — confirmaciones oficiales**

- [ ] Proveedor contratado con sandbox y firmas documentadas.
- [ ] Webhooks firmados, conciliación por referencia.
- [ ] Conservar la procedencia: una observación nunca asciende a pago confirmado sin evidencia oficial.

## Privacidad

La política completa está en [asssie.co/qr-libre/privacidad](https://www.asssie.co/qr-libre/privacidad). Aplica cuando la app esté publicada.

En corto: los avisos se leen en el teléfono, se guardan en el teléfono, y no se envían a ningún servidor.

## Contribuir

Todavía es temprano. Si trabajas en Android, en accesibilidad o conoces de cerca cómo llegan estos avisos en Colombia, abre un issue contando qué ves. Especialmente útil: textos reales de notificaciones, anonimizados.

## Licencia

[MIT](LICENSE). Un proyecto de ASSSIE SAS.
