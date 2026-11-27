# Backend

Servidor local en Rust (Axum) y utilidades offline del proyecto QR Libre.

## Ingesta de correos bancarios

El módulo `email` convierte un archivo `.eml` exportado a mano en una
observación estructurada y **no verificada** de un pago. No lee buzones, no usa
la red y no guarda nada.

Uso:

```sh
cargo run --bin parse-eml -- fixtures/bank-example/payment.eml
cargo run --bin parse-eml -- fixtures/bank-example/payment.eml --json
```

Salida en texto:

```
bank: Banco Ejemplo
amount_minor: 3500000
currency: COP
confidence: high
reference: 990001
```

El monto siempre va en unidades menores (centavos). `confidence: high` solo
significa que la regla del banco reconoció todos los campos que necesita;
nunca significa que el banco confirmó el pago. Si ninguna regla coincide, la
herramienta imprime `bank: unknown` y `confidence: low`, sin monto, y termina
con código 0. Un archivo ilegible termina con código 1.

Pruebas:

```sh
cargo test
```

## Cómo agregar un banco real

1. Exporta un correo real del banco como `.eml` y guárdalo en
   `fixtures/private/`. Esa carpeta está en `.gitignore`: no se versiona.
2. Anonimiza la muestra antes de usarla en el repositorio: reemplaza nombres,
   correos, números de cuenta, cédulas y referencias reales por datos
   ficticios. Nunca hagas commit de un correo de una persona real.
3. Crea la regla en `src/email/rules/<banco>.rs`. La regla debe:
   - reconocer el banco por el remitente (`from`), no solo por el asunto;
   - extraer el monto con `crate::email::amount::parse_amount`;
   - devolver `None` si falta un campo obligatorio, sin inventar valores;
   - usar `Confidence::High` solo si reconoce el formato completo y
     `Confidence::Low` para coincidencias heurísticas.
4. Registra la regla en `src/email/rules/mod.rs` dentro de `registry()`.
5. Agrega un fixture sintético en `fixtures/<banco>/payment.eml` y una prueba
   de extremo a extremo en `tests/email_ingest.rs` que verifique monto, moneda
   y confianza. Si la muestra real revela un caso límite, conviértelo en un
   caso sintético dentro de la prueba.
6. Ejecuta `cargo test` y confirma que las pruebas anteriores siguen pasando.

Los datos reales de la carpeta `fixtures/private/` se usan solo para escribir
la regla; no entran al repositorio ni a las pruebas.
