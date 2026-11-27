//! Integration tests for the bank email ingest pipeline.

use std::process::Command;

use qr_libre_api::email::{
    observe, parse_amount, parse_eml, registry, Confidence, PaymentObservation, RawEmail,
};

const FIXTURE: &str = include_str!("../fixtures/bank-example/payment.eml");
const BANCOLOMBIA_FIXTURE: &str = include_str!("../fixtures/bancolombia/payment.eml");
const NEQUI_FIXTURE: &str = include_str!("../fixtures/nequi/bre-b-payment.eml");

fn observation_json(observation: &PaymentObservation) -> serde_json::Value {
    serde_json::to_value(observation).expect("serializable observation")
}

#[test]
fn fixture_is_quoted_printable_with_encoded_accents() {
    assert!(FIXTURE.contains("Content-Transfer-Encoding: quoted-printable"));
    assert!(FIXTURE.contains("=C3=B3"));
    assert!(FIXTURE.contains("$ 35.000,00"));
}

#[test]
fn parse_eml_reads_headers_and_quoted_printable_body() {
    let email = parse_eml(FIXTURE).expect("fixture parses");
    assert!(email.from.contains("avisos@bancoejemplo.test"));
    assert_eq!(email.subject, "Aviso de pago recibido en tu cuenta Banco Ejemplo");
    assert!(email.body.contains("está"), "body was: {}", email.body);
    assert!(email.body.contains("información"), "body was: {}", email.body);
    assert!(!email.body.contains("=C3"), "body was not decoded");
}

#[test]
fn observe_extracts_the_fixture_observation() {
    let email = parse_eml(FIXTURE).expect("fixture parses");
    let observation = observe(&email).expect("fixture matches a rule");
    assert_eq!(observation.bank, "Banco Ejemplo");
    assert_eq!(observation.amount_minor, 3_500_000);
    assert_eq!(observation.currency, "COP");
    assert_eq!(observation.confidence, Confidence::High);
    assert_eq!(observation.reference.as_deref(), Some("990001"));
}

#[test]
fn json_uses_contract_keys_and_lowercase_confidence() {
    let email = parse_eml(FIXTURE).expect("fixture parses");
    let observation = observe(&email).expect("fixture matches a rule");
    let json = observation_json(&observation);
    assert_eq!(json["bank"], "Banco Ejemplo");
    assert_eq!(json["amount_minor"], 3_500_000);
    assert_eq!(json["currency"], "COP");
    assert_eq!(json["confidence"], "high");
    assert_eq!(json["reference"], "990001");
    assert!(json["occurred_at"].is_null());
}

#[test]
fn unknown_sender_returns_no_observation() {
    let raw = "From: Otro Banco <avisos@otrobanco.test>\r\n\
               Subject: Aviso de pago\r\n\
               MIME-Version: 1.0\r\n\
               Content-Type: text/plain; charset=\"UTF-8\"\r\n\
               \r\n\
               Recibiste un pago por $ 35.000,00\r\n";
    let email = parse_eml(raw).expect("email parses");
    assert!(observe(&email).is_none());
}

#[test]
fn parse_amount_is_reused_by_the_ingest_layer() {
    assert_eq!(parse_amount("$ 35.000,00"), Some(3_500_000));
}

#[test]
fn base64_body_is_decoded() {
    // "Pago por $ 35.000,00\r\n"
    let raw = "From: Banco <avisos@bancoejemplo.test>\r\n\
               Subject: Aviso\r\n\
               MIME-Version: 1.0\r\n\
               Content-Type: text/plain; charset=\"UTF-8\"\r\n\
               Content-Transfer-Encoding: base64\r\n\
               \r\n\
               UGFnbyBwb3IgJCAzNS4wMDAsMDANCg==\r\n";
    let email = parse_eml(raw).expect("base64 email parses");
    assert!(email.body.contains("Pago por $ 35.000,00"));
    assert_eq!(observe(&email).map(|o| o.amount_minor), Some(3_500_000));
}

#[test]
fn latin1_charset_is_decoded() {
    let raw = "From: Banco <avisos@bancoejemplo.test>\r\n\
               Subject: Aviso\r\n\
               MIME-Version: 1.0\r\n\
               Content-Type: text/plain; charset=\"iso-8859-1\"\r\n\
               Content-Transfer-Encoding: quoted-printable\r\n\
               \r\n\
               La informaci=F3n del pago por $ 35.000,00 est=E1 lista.\r\n";
    let email = parse_eml(raw).expect("latin-1 email parses");
    assert!(email.body.contains("información"), "body was: {}", email.body);
    assert!(email.body.contains("está"), "body was: {}", email.body);
}

#[test]
fn multipart_alternative_prefers_text_plain() {
    let raw = "From: Banco <avisos@bancoejemplo.test>\r\n\
               Subject: Aviso\r\n\
               MIME-Version: 1.0\r\n\
               Content-Type: multipart/alternative; boundary=\"alt\"\r\n\
               \r\n\
               --alt\r\n\
               Content-Type: text/plain; charset=\"UTF-8\"\r\n\
               \r\n\
               Texto plano: $ 35.000,00\r\n\
               --alt\r\n\
               Content-Type: text/html; charset=\"UTF-8\"\r\n\
               \r\n\
               <p>HTML: $ 99.000,00</p>\r\n\
               --alt--\r\n";
    let email = parse_eml(raw).expect("multipart email parses");
    assert!(email.body.contains("Texto plano"), "body was: {}", email.body);
    assert!(!email.body.contains("HTML:"), "body was: {}", email.body);
    assert_eq!(observe(&email).map(|o| o.amount_minor), Some(3_500_000));
}

#[test]
fn multipart_without_plain_part_falls_back_to_html() {
    let raw = "From: Banco <avisos@bancoejemplo.test>\r\n\
               Subject: Aviso\r\n\
               MIME-Version: 1.0\r\n\
               Content-Type: text/html; charset=\"UTF-8\"\r\n\
               \r\n\
               <html><body><p>Pago por $ 35.000,00</p></body></html>\r\n";
    let email = parse_eml(raw).expect("html email parses");
    assert!(email.body.contains("$ 35.000,00"), "body was: {}", email.body);
}

#[test]
fn folded_header_is_unfolded() {
    let raw = concat!(
        "From: Banco <avisos@bancoejemplo.test>\r\n",
        "Subject: Aviso de pago\r\n",
        " del Banco Ejemplo\r\n",
        "MIME-Version: 1.0\r\n",
        "Content-Type: text/plain; charset=\"UTF-8\"\r\n",
        "\r\n",
        "Pago por $ 35.000,00\r\n"
    );
    let email = parse_eml(raw).expect("folded email parses");
    assert_eq!(email.subject, "Aviso de pago del Banco Ejemplo");
}

#[test]
fn registry_ids_are_unique_and_non_empty() {
    let rules = registry();
    assert!(!rules.is_empty());
    let mut ids: Vec<_> = rules.iter().map(|rule| rule.id()).collect();
    let count = ids.len();
    ids.sort_unstable();
    ids.dedup();
    assert_eq!(ids.len(), count);
}

#[test]
fn raw_email_without_rule_fields_does_not_panic() {
    let email = RawEmail {
        from: String::new(),
        subject: String::new(),
        body: String::new(),
    };
    assert!(observe(&email).is_none());
}

#[test]
fn nequi_forwarded_fixture_yields_high_confidence_observation() {
    let email = parse_eml(NEQUI_FIXTURE).expect("nequi fixture parses");
    assert!(email.from.contains("example.com"));
    assert!(
        email.body.contains("Recibiste plata por Bre-B"),
        "body: {}",
        email.body
    );

    let observation = observe(&email).expect("nequi rule matches the forwarded copy");
    assert_eq!(observation.bank, "Nequi");
    assert_eq!(observation.amount_minor, 250_000_000);
    assert_eq!(observation.currency, "COP");
    assert_eq!(observation.confidence, Confidence::High);
    assert_eq!(observation.reference, None);

    let occurred_at = observation
        .occurred_at
        .as_deref()
        .expect("occurred_at is present");
    assert_eq!(occurred_at, "2026-11-27T11:15:00-05:00");
    assert!(occurred_at.ends_with("-05:00"));
}

#[test]
fn nequi_body_phrase_without_nequi_returns_none() {
    let raw = concat!(
        "From: Otro Banco <avisos@otrobanco.test>\r\n",
        "Subject: Aviso\r\n",
        "MIME-Version: 1.0\r\n",
        "Content-Type: text/plain; charset=\"UTF-8\"\r\n",
        "\r\n",
        "Recibiste plata por Bre-B por 2.500.000 el 27 de noviembre de 2026.\r\n"
    );
    let email = parse_eml(raw).expect("email parses");
    assert!(observe(&email).is_none());
}

#[test]
fn nequi_sender_without_payment_sentence_returns_none() {
    let raw = concat!(
        "From: Nequi <notificaciones@nequi.com.co>\r\n",
        "Subject: Hola\r\n",
        "MIME-Version: 1.0\r\n",
        "Content-Type: text/plain; charset=\"UTF-8\"\r\n",
        "\r\n",
        "Hola, revisa tu app y tus movimientos.\r\n"
    );
    let email = parse_eml(raw).expect("email parses");
    assert!(observe(&email).is_none());
}

#[test]
fn bancolombia_fixture_yields_high_confidence_observation() {
    let email = parse_eml(BANCOLOMBIA_FIXTURE).expect("bancolombia fixture parses");
    assert!(email.body.contains("Recibiste un pago"), "body: {}", email.body);
    assert!(!email.body.contains('<'), "HTML was not stripped: {}", email.body);

    let observation = observe(&email).expect("bancolombia rule matches");
    assert_eq!(observation.bank, "Bancolombia");
    assert_eq!(observation.amount_minor, 125_000_000);
    assert_eq!(observation.currency, "COP");
    assert_eq!(observation.confidence, Confidence::High);
    assert_eq!(observation.reference, None);

    let occurred_at = observation
        .occurred_at
        .as_deref()
        .expect("occurred_at is present");
    assert_eq!(occurred_at, "2026-11-26T17:23:00-05:00");
    assert!(occurred_at.ends_with("-05:00"));

    let json = observation_json(&observation);
    assert_eq!(json["bank"], "Bancolombia");
    assert_eq!(json["amount_minor"], 125_000_000);
    assert_eq!(json["occurred_at"], "2026-11-26T17:23:00-05:00");
}

#[test]
fn bancolombia_notice_without_amount_returns_none() {
    let raw = concat!(
        "From: Bancolombia <notificaciones@ayn.notificacionesbancolombia.com>\r\n",
        "Subject: Alertas y Notificaciones\r\n",
        "MIME-Version: 1.0\r\n",
        "Content-Type: text/html; charset=\"UTF-8\"\r\n",
        "\r\n",
        "<p>Bancolombia: Recibiste un pago de Entidad Ficticia en tu cuenta ",
        "de Ahorros el 26/11/2026 a las 17:23.</p>\r\n"
    );
    let email = parse_eml(raw).expect("email parses");
    assert!(observe(&email).is_none());
}

#[test]
fn other_domain_mentioning_the_payment_phrase_returns_none() {
    let raw = concat!(
        "From: Otro Banco <avisos@otrobanco.test>\r\n",
        "Subject: Aviso\r\n",
        "MIME-Version: 1.0\r\n",
        "Content-Type: text/plain; charset=\"UTF-8\"\r\n",
        "\r\n",
        "Recibiste un pago por $1,250,000.00 en tu cuenta.\r\n"
    );
    let email = parse_eml(raw).expect("email parses");
    assert!(observe(&email).is_none());
}

#[test]
fn cli_prints_text_and_json() {
    let fixture = concat!(
        env!("CARGO_MANIFEST_DIR"),
        "/fixtures/bank-example/payment.eml"
    );

    let text = Command::new(env!("CARGO_BIN_EXE_parse-eml"))
        .arg(fixture)
        .output()
        .expect("cli runs");
    assert!(text.status.success());
    let stdout = String::from_utf8(text.stdout).expect("utf-8 output");
    assert!(stdout.contains("bank: Banco Ejemplo"), "stdout: {stdout}");
    assert!(stdout.contains("amount_minor: 3500000"), "stdout: {stdout}");
    assert!(stdout.contains("currency: COP"), "stdout: {stdout}");
    assert!(stdout.contains("confidence: high"), "stdout: {stdout}");
    assert!(stdout.contains("reference: 990001"), "stdout: {stdout}");

    let json = Command::new(env!("CARGO_BIN_EXE_parse-eml"))
        .arg(fixture)
        .arg("--json")
        .output()
        .expect("cli runs");
    assert!(json.status.success());
    let stdout = String::from_utf8(json.stdout).expect("utf-8 output");
    let value: serde_json::Value = serde_json::from_str(stdout.trim()).expect("valid json");
    assert_eq!(value["amount_minor"], 3_500_000);
    assert_eq!(value["confidence"], "high");
}

#[test]
fn cli_reports_unknown_without_amount() {
    let path = std::env::temp_dir().join("qr-libre-unknown-test.eml");
    std::fs::write(
        &path,
        "From: Otro <avisos@otrobanco.test>\r\nSubject: Nada\r\n\r\nSin movimientos\r\n",
    )
    .expect("write temp email");

    let output = Command::new(env!("CARGO_BIN_EXE_parse-eml"))
        .arg(&path)
        .output()
        .expect("cli runs");
    let _ = std::fs::remove_file(&path);

    assert!(output.status.success());
    let stdout = String::from_utf8(output.stdout).expect("utf-8 output");
    assert!(stdout.contains("bank: unknown"), "stdout: {stdout}");
    assert!(stdout.contains("confidence: low"), "stdout: {stdout}");
    assert!(!stdout.contains("amount_minor"), "stdout: {stdout}");
}

#[test]
fn cli_treats_garbage_as_unknown() {
    let path = std::env::temp_dir().join("qr-libre-garbage-test.eml");
    std::fs::write(&path, "not an email\n").expect("write temp email");

    let output = Command::new(env!("CARGO_BIN_EXE_parse-eml"))
        .arg(&path)
        .output()
        .expect("cli runs");
    let _ = std::fs::remove_file(&path);

    assert!(output.status.success());
    let stdout = String::from_utf8(output.stdout).expect("utf-8 output");
    assert!(stdout.contains("bank: unknown"), "stdout: {stdout}");
    assert!(stdout.contains("confidence: low"), "stdout: {stdout}");
}

#[test]
fn cli_fails_on_unreadable_file() {
    let output = Command::new(env!("CARGO_BIN_EXE_parse-eml"))
        .arg("/definitely/not/a/real/path.eml")
        .output()
        .expect("cli runs");
    assert!(!output.status.success());
    assert!(!output.stderr.is_empty());
}
