//! Rule for Bancolombia payment notices.
//!
//! The notice is HTML-only (`multipart/alternative` with a single
//! `text/html` part), uses the Anglo number style (`35,000,000.00`) and
//! states the local date and time in the body. The fixture committed with
//! this rule is fully synthetic.

use crate::email::amount::{parse_amount_with, AmountStyle};
use crate::email::{BankRule, Confidence, PaymentObservation, RawEmail};

/// Sender domain of the real notification service. Matching on this domain
/// plus the payment sentence keeps debit and marketing mail out.
const SENDER_MARKER: &str = "notificacionesbancolombia.com";

/// Sentence that must be present for a payment notice.
const PAYMENT_PHRASE: &str = "Recibiste un pago";

/// Bancolombia payment notice rule.
pub struct Bancolombia;

impl BankRule for Bancolombia {
    fn id(&self) -> &'static str {
        "bancolombia"
    }

    fn matches(&self, email: &RawEmail) -> bool {
        email
            .from
            .to_ascii_lowercase()
            .contains(SENDER_MARKER)
            && normalized_body(email).contains(PAYMENT_PHRASE)
    }

    fn parse(&self, email: &RawEmail) -> Option<PaymentObservation> {
        if !self.matches(email) {
            return None;
        }

        let text = normalized_body(email);
        let amount_minor = amount(&text)?;

        Some(PaymentObservation {
            bank: "Bancolombia".to_string(),
            amount_minor,
            currency: "COP".to_string(),
            occurred_at: occurred_at(&text),
            // The payer and entity descriptors would need new fields in
            // `PaymentObservation`; `reference` is reserved for a bank
            // reference, so both are intentionally left uncaptured.
            reference: None,
            confidence: Confidence::High,
        })
    }
}

/// Collapse line breaks and repeated spaces introduced by HTML conversion.
fn normalized_body(email: &RawEmail) -> String {
    email.body.split_whitespace().collect::<Vec<_>>().join(" ")
}

/// Read the amount after `por $` using the Anglo style.
fn amount(text: &str) -> Option<i64> {
    let after_por = &text[text.find("por $")? + "por $".len()..];
    let start = after_por.find(|c: char| c.is_ascii_digit())?;
    let token: String = after_por[start..]
        .chars()
        .take_while(|c| c.is_ascii_digit() || *c == '.' || *c == ',')
        .collect();
    // Drop sentence punctuation such as a trailing period.
    let token = token.trim_end_matches(['.', ',']);
    parse_amount_with(token, AmountStyle::Anglo)
}

/// Convert `el DD/MM/YYYY a las HH:MM` to ISO 8601 in Colombia time.
fn occurred_at(text: &str) -> Option<String> {
    let mut rest = text;
    while let Some(position) = rest.find(" el ") {
        let after = &rest[position + " el ".len()..];
        if let Some(date) = after.get(..10) {
            if let Some((year, month, day)) = split_date(date) {
                let time = after
                    .get(10..)
                    .and_then(|tail| tail.strip_prefix(" a las "))
                    .and_then(|tail| tail.get(..5));
                if let Some((hour, minute)) = time.and_then(split_time) {
                    return Some(format!(
                        "{year}-{month}-{day}T{hour}:{minute}:00-05:00"
                    ));
                }
            }
        }
        rest = &rest[position + " el ".len()..];
    }
    None
}

fn split_date(value: &str) -> Option<(&str, &str, &str)> {
    let mut parts = value.split('/');
    let day = parts.next()?;
    let month = parts.next()?;
    let year = parts.next()?;
    if parts.next().is_some()
        || day.len() != 2
        || month.len() != 2
        || year.len() != 4
        || !value.chars().all(|c| c.is_ascii_digit() || c == '/')
    {
        return None;
    }
    let day_number: u32 = day.parse().ok()?;
    let month_number: u32 = month.parse().ok()?;
    if !(1..=31).contains(&day_number) || !(1..=12).contains(&month_number) {
        return None;
    }
    Some((year, month, day))
}

fn split_time(value: &str) -> Option<(&str, &str)> {
    let mut parts = value.split(':');
    let hour = parts.next()?;
    let minute = parts.next()?;
    if parts.next().is_some() || hour.len() != 2 || minute.len() != 2 {
        return None;
    }
    let hour_number: u32 = hour.parse().ok()?;
    let minute_number: u32 = minute.parse().ok()?;
    if hour_number > 23 || minute_number > 59 {
        return None;
    }
    Some((hour, minute))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn email(from: &str, body: &str) -> RawEmail {
        RawEmail {
            from: from.to_string(),
            subject: "Alertas y Notificaciones".to_string(),
            body: body.to_string(),
        }
    }

    const NOTICE: &str = "Bancolombia: Recibiste un pago Persona de Prueba de Entidad Ficticia \
por $1,250,000.00 en tu cuenta de Ahorros el 26/11/2026 a las 17:23. \
Si tienes dudas, llamanos al 601 000 0000. A tu lado siempre.";

    #[test]
    fn matches_domain_and_payment_phrase() {
        assert!(Bancolombia.matches(&email(
            "Bancolombia <notificaciones@ayn.notificacionesbancolombia.com>",
            NOTICE
        )));
        assert!(!Bancolombia.matches(&email(
            "Bancolombia <notificaciones@example.com>",
            NOTICE
        )));
        assert!(!Bancolombia.matches(&email(
            "Bancolombia <notificaciones@ayn.notificacionesbancolombia.com>",
            "Tu compra fue aprobada por $1,250,000.00"
        )));
    }

    #[test]
    fn parses_amount_and_colombia_time() {
        let observation = Bancolombia
            .parse(&email(
                "Bancolombia <notificaciones@ayn.notificacionesbancolombia.com>",
                NOTICE,
            ))
            .expect("observation");
        assert_eq!(observation.bank, "Bancolombia");
        assert_eq!(observation.amount_minor, 125_000_000);
        assert_eq!(observation.currency, "COP");
        assert_eq!(observation.confidence, Confidence::High);
        assert_eq!(
            observation.occurred_at.as_deref(),
            Some("2026-11-26T17:23:00-05:00")
        );
        assert_eq!(observation.reference, None);
    }

    #[test]
    fn does_not_invent_an_amount() {
        assert!(Bancolombia
            .parse(&email(
                "Bancolombia <notificaciones@ayn.notificacionesbancolombia.com>",
                "Bancolombia: Recibiste un pago sin monto en tu cuenta de Ahorros."
            ))
            .is_none());
    }

    #[test]
    fn missing_or_invalid_date_leaves_occurred_at_empty() {
        let observation = Bancolombia
            .parse(&email(
                "Bancolombia <notificaciones@ayn.notificacionesbancolombia.com>",
                "Bancolombia: Recibiste un pago de Entidad Ficticia por $1,250,000.00.",
            ))
            .expect("observation");
        assert_eq!(observation.occurred_at, None);
    }
}
