//! Rule for Nequi Bre-B payment notices.
//!
//! The real notice usually arrives manually forwarded (`Fw:`), so the outer
//! `From` is the forwarder and not Nequi. The product path uses an automatic
//! forward that preserves the original sender, so this rule accepts either
//! the Nequi sender domain or the Bre-B phrase plus `Nequi` in the body.
//!
//! The committed fixture is fully synthetic.

use crate::email::amount::parse_amount;
use crate::email::{BankRule, Confidence, PaymentObservation, RawEmail};

/// Part of the original sender domain, including the tracking subdomain.
const SENDER_MARKER: &str = "nequi.com.co";

/// Phrase used by forwarded copies that lost the original sender.
const FORWARD_PHRASE: &str = "Recibiste plata por Bre-B";

/// Nequi Bre-B payment notice rule.
pub struct Nequi;

impl BankRule for Nequi {
    fn id(&self) -> &'static str {
        "nequi"
    }

    fn matches(&self, email: &RawEmail) -> bool {
        let body = normalized_body(email);
        let sender_matches = email.from.to_ascii_lowercase().contains(SENDER_MARKER);
        let forwarded_matches = body.contains(FORWARD_PHRASE) && body.contains("Nequi");
        sender_matches || forwarded_matches
    }

    fn parse(&self, email: &RawEmail) -> Option<PaymentObservation> {
        if !self.matches(email) {
            return None;
        }

        let text = normalized_body(email);
        let (amount_minor, occurred_at) = payment_parts(&text)?;

        Some(PaymentObservation {
            bank: "Nequi".to_string(),
            amount_minor,
            currency: "COP".to_string(),
            occurred_at: Some(occurred_at),
            // The payer's name is personal data and `reference` is reserved
            // for a bank reference; the notice has neither a reference nor a
            // receipt number.
            reference: None,
            confidence: Confidence::High,
        })
    }
}

/// Collapse line breaks and repeated spaces introduced by HTML conversion.
fn normalized_body(email: &RawEmail) -> String {
    email.body.split_whitespace().collect::<Vec<_>>().join(" ")
}

/// Extract `Recibiste <monto> de <remitente> el <fecha> a las <hora>`.
fn payment_parts(text: &str) -> Option<(i64, String)> {
    let mut search = text;
    while let Some(position) = search.find("Recibiste ") {
        let after = &search[position + "Recibiste ".len()..];
        if let Some((amount_minor, rest)) = amount_prefix(after) {
            if let Some(rest) = rest.strip_prefix(" de ") {
                if let Some((occurred_at, _remitente)) = remitente_and_date(rest) {
                    return Some((amount_minor, occurred_at));
                }
            }
        }
        search = &search[position + "Recibiste ".len()..];
    }
    None
}

/// Parse a leading amount token made of digits and dots, and return the rest.
fn amount_prefix(text: &str) -> Option<(i64, &str)> {
    let end = text
        .find(|c: char| !(c.is_ascii_digit() || c == '.'))
        .unwrap_or(text.len());
    let token = &text[..end];
    if token.is_empty() {
        return None;
    }
    let amount = parse_amount(token)?;
    Some((amount, &text[end..]))
}

/// Split `"<remitente> el <fecha> a las <hora>"` and require a non-empty
/// remitente and a complete date and time.
fn remitente_and_date(text: &str) -> Option<(String, &str)> {
    let mut search = text;
    while let Some(position) = search.find(" el ") {
        let remitente = &text[..text.len() - search.len() + position];
        let candidate = &search[position + " el ".len()..];
        if !remitente.trim().is_empty() {
            if let Some(occurred_at) = spanish_date_time(candidate) {
                return Some((occurred_at, remitente.trim()));
            }
        }
        search = &search[position + " el ".len()..];
    }
    None
}

/// Parse `DD de <mes> de AAAA a las H:MM a.m|p.m` into ISO 8601 at UTC-05:00.
fn spanish_date_time(text: &str) -> Option<String> {
    let (day, rest) = text.split_once(" de ")?;
    let (month_name, rest) = rest.split_once(" de ")?;
    let (year, rest) = rest.split_once(" a las ")?;

    let day_number: u32 = day.trim().parse().ok()?;
    if !(1..=31).contains(&day_number) {
        return None;
    }
    let month = month_number(month_name.trim())?;
    let year: u32 = year.trim().parse().ok()?;
    if year.to_string().len() != 4 {
        return None;
    }

    let mut tokens = rest.split_whitespace();
    let clock = tokens.next()?;
    let meridiem = tokens.next()?;
    let (hour, minute) = split_clock(clock)?;
    let hour = to_24_hour(hour, meridiem)?;

    Some(format!(
        "{year:04}-{month:02}-{day_number:02}T{hour:02}:{minute:02}:00-05:00"
    ))
}

fn month_number(name: &str) -> Option<u32> {
    const MONTHS: [&str; 12] = [
        "enero",
        "febrero",
        "marzo",
        "abril",
        "mayo",
        "junio",
        "julio",
        "agosto",
        "septiembre",
        "octubre",
        "noviembre",
        "diciembre",
    ];
    let name = name.to_ascii_lowercase();
    MONTHS
        .iter()
        .position(|month| *month == name)
        .map(|index| index as u32 + 1)
}

fn split_clock(clock: &str) -> Option<(u32, u32)> {
    let (hour, minute) = clock.split_once(':')?;
    let hour: u32 = hour.parse().ok()?;
    if !(1..=12).contains(&hour) || minute.len() != 2 {
        return None;
    }
    let minute: u32 = minute.parse().ok()?;
    if minute > 59 {
        return None;
    }
    Some((hour, minute))
}

/// Convert a 12-hour clock value and `a.m`/`p.m` marker to 24 hours.
fn to_24_hour(hour: u32, meridiem: &str) -> Option<u32> {
    let meridiem = meridiem
        .to_ascii_lowercase()
        .trim_matches(|c: char| c == ',' || c == '.' || c.is_whitespace())
        .replace('.', "");
    match meridiem.as_str() {
        "am" => Some(if hour == 12 { 0 } else { hour }),
        "pm" => Some(if hour == 12 { 12 } else { hour + 12 }),
        _ => None,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn email(from: &str, body: &str) -> RawEmail {
        RawEmail {
            from: from.to_string(),
            subject: "Fw: Recibiste plata por Bre-B".to_string(),
            body: body.to_string(),
        }
    }

    const NOTICE: &str = "[Nequi] ¡Recibiste plata por Bre-B! ¡Hola, Cliente de Prueba! \
Recibiste 2.500.000 de Remitente Ficticio el 27 de noviembre de 2026 a las 11:15 a.m, \
desde el banco Banco Ficticio. Revisa el detalle en los movimientos de tu app o descarga \
el comprobante.";

    #[test]
    fn matches_forwarded_body_or_nequi_sender() {
        assert!(Nequi.matches(&email("Persona <persona@example.com>", NOTICE)));
        assert!(Nequi.matches(&email(
            "Nequi <notificaciones@tracking.notificaciones.nequi.com.co>",
            "Hola"
        )));
        assert!(!Nequi.matches(&email(
            "Persona <persona@example.com>",
            "Recibiste plata por Bre-B"
        )));
        assert!(!Nequi.matches(&email("Persona <persona@example.com>", "Hola")));
    }

    #[test]
    fn parses_amount_spanish_date_and_time() {
        let observation = Nequi
            .parse(&email("Persona <persona@example.com>", NOTICE))
            .expect("observation");
        assert_eq!(observation.bank, "Nequi");
        assert_eq!(observation.amount_minor, 250_000_000);
        assert_eq!(observation.currency, "COP");
        assert_eq!(observation.confidence, Confidence::High);
        assert_eq!(
            observation.occurred_at.as_deref(),
            Some("2026-11-27T11:15:00-05:00")
        );
        assert_eq!(observation.reference, None);
    }

    #[test]
    fn rejects_partial_or_missing_sentence() {
        let without_remitente = NOTICE.replace(" de Remitente Ficticio", "");
        assert!(Nequi
            .parse(&email("Persona <persona@example.com>", &without_remitente))
            .is_none());
        assert!(Nequi
            .parse(&email(
                "Nequi <notificaciones@nequi.com.co>",
                "Hola, revisa tu app."
            ))
            .is_none());
    }

    #[test]
    fn supports_all_meridiem_spellings() {
        for marker in ["a.m", "a.m.", "p.m", "p.m.", "A.M.", "P.M"] {
            let body = format!(
                "Recibiste 2.500.000 de Remitente Ficticio el 27 de noviembre de 2026 a las 11:15 {marker}, desde el banco Banco Ficticio."
            );
            assert!(
                Nequi
                    .parse(&email("Nequi <notificaciones@nequi.com.co>", &body))
                    .is_some(),
                "marker {marker} should parse"
            );
        }
    }
}
