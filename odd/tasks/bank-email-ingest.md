# Feature: ingesta de correos bancarios (semana 1, local)

## Objetivo

Convertir un correo de aviso de pago del banco en una observación estructurada
(`monto`, moneda, fecha, referencia, banco) usando una regla por banco, sin
servidor, sin usuarios y sin credenciales de nadie.

Esta fase **no** lee el buzón de nadie. Trabaja sobre archivos `.eml` que se le
pasan a mano. Es la base sobre la que después se monta el reenvío.

## Problema

No sabemos cómo es el texto real de un aviso bancario colombiano. Escribir un
parser adivinando produce falsos positivos, y en una herramienta que anuncia
dinero un falso positivo es peor que no tener herramienta. Primero se captura el
formato; después se escribe la regla.

## Alcance

Dentro:

- Lectura de un archivo `.eml` (MIME, quoted-printable, base64, latin-1).
- Extracción de remitente, asunto y cuerpo de texto.
- Contrato `BankRule` + registro de reglas.
- Parser de montos en formato colombiano (`$ 35.000,00`).
- CLI `parse-eml` que imprime la observación en texto o JSON.
- Una regla de ejemplo sobre un banco ficticio, con su fixture sintético.
- Pruebas de montos, de MIME y de extremo a extremo.

Fuera:

- IMAP, OAuth, Gmail API.
- Webhook, subdominio, tokens de comercio.
- Almacenamiento, base de datos, servidor.
- Cualquier regla de un banco real: eso entra cuando exista un correo real.

## Restricciones

- Rust, dentro del crate `backend` existente. Sin servicios externos.
- Ningún correo real de un tercero en el repositorio. Las muestras reales van en
  `fixtures/private/`, que está en `.gitignore`.
- La salida distingue `confidence: high` (regla completa) de `low` (heurística).
  Una observación nunca se presenta como pago confirmado.

## Tareas

- [ ] **T1** Contrato `BankRule`, `RawEmail`, `PaymentObservation`, `Confidence` y registro de reglas.
- [ ] **T2** Lector `.eml` → `RawEmail`, con MIME y codificaciones.
- [ ] **T3** Parser de montos colombianos con pruebas de casos límite.
- [ ] **T4** Regla de ejemplo sobre banco ficticio y su fixture sintético.
- [ ] **T5** CLI `parse-eml` con salida texto y `--json`.
- [ ] **T6** Documentar en el README del crate cómo agregar un banco con una muestra real.

## Criterios de aceptación

- `cargo test` pasa en `backend/`.
- `cargo run --bin parse-eml -- fixtures/bank-example/payment.eml` devuelve monto `3500000`, moneda `COP` y confianza `high`.
- `--json` devuelve JSON válido con las mismas claves.
- Un `.eml` sin regla conocida devuelve `unknown` con confianza `low` y no inventa monto.
- Ningún archivo versionado contiene datos de una persona real.

## Verificación

```
cd backend
cargo test
cargo run --bin parse-eml -- fixtures/bank-example/payment.eml --json
```

## Siguiente paso (fuera de esta fase)

Cuando exista un correo real del banco del comerciante, se agrega una regla con
su fixture anonimizado y una prueba que lo cubra. Solo entonces se evalúa el
subdominio y el webhook.
