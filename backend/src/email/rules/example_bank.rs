//! Rule for the fictional "Banco Ejemplo".
//!
//! The bank, its domain and every sample are synthetic. This rule exists to
//! exercise the ingest pipeline and to show the shape of a real rule; it must
//! never be used with a real bank's text.

use crate::email::amount::parse_amount;
use crate::email::{BankRule, Confidence, PaymentObservation, RawEmail};

/// Sender domain used by the synthetic fixture.
const SENDER_DOMAIN: &str = "@bancoejemplo.test";
/// Bank name reported in observations.
const BANK_NAME: &str = "Banco Ejemplo";

/// Synthetic rule for the example fixture.
pub struct ExampleBank;

impl BankRule for ExampleBank {
    fn id(&self) -> &'static str {
        "example-bank"
    }

    fn matches(&self, email: &RawEmail) -> bool {
        // Match on the sender domain only. Guessing from the subject alone
        // would accept any forwarded or spoofed message.
        email.from.to_ascii_lowercase().contains(SENDER_DOMAIN)
    }

    fn parse(&self, email: &RawEmail) -> Option<PaymentObservation> {
        if !self.matches(email) {
            return None;
        }

        let amount_minor = first_amount(&email.body)?;

        Some(PaymentObservation {
            bank: BANK_NAME.to_string(),
            amount_minor,
            currency: "COP".to_string(),
            occurred_at: None,
            reference: reference(&email.body),
            confidence: Confidence::High,
        })
    }
}

/// Find the first `$`-prefixed amount on its own line.
fn first_amount(body: &str) -> Option<i64> {
    for line in body.lines() {
        let Some(dollar) = line.find('$') else {
            continue;
        };
        let rest = &line[dollar + 1..];
        let Some(start) = rest.find(|c: char| c.is_ascii_digit()) else {
            continue;
        };
        let token: String = rest[start..]
            .chars()
            .take_while(|c| c.is_ascii_digit() || *c == '.' || *c == ',')
            .collect();
        // Drop sentence punctuation such as the final period.
        let token = token.trim_end_matches(['.', ',']);
        if let Some(amount) = parse_amount(token) {
            return Some(amount);
        }
    }
    None
}

/// Read a `Referencia:` line, if present.
fn reference(body: &str) -> Option<String> {
    for line in body.lines() {
        let line = line.trim();
        let Some((label, value)) = line.split_once(':') else {
            continue;
        };
        if label.trim().eq_ignore_ascii_case("referencia") {
            let value = value.trim();
            if !value.is_empty() {
                return Some(value.split_whitespace().next()?.to_string());
            }
        }
    }
    None
}

#[cfg(test)]
mod tests {
    use super::*;

    fn email(from: &str, body: &str) -> RawEmail {
        RawEmail {
            from: from.to_string(),
            subject: "Aviso de pago".to_string(),
            body: body.to_string(),
        }
    }

    #[test]
    fn matches_only_the_example_domain() {
        assert!(ExampleBank.matches(&email("avisos@bancoejemplo.test", "")));
        assert!(!ExampleBank.matches(&email("avisos@otrobanco.test", "")));
        assert!(!ExampleBank.matches(&email("", "Banco Ejemplo")));
    }

    #[test]
    fn parses_amount_and_reference() {
        let observation = ExampleBank
            .parse(&email(
                "Avisos <avisos@bancoejemplo.test>",
                "Recibiste un pago por $ 35.000,00\r\nReferencia: 990001\r\n",
            ))
            .expect("observation");
        assert_eq!(observation.amount_minor, 3_500_000);
        assert_eq!(observation.reference.as_deref(), Some("990001"));
        assert_eq!(observation.confidence, Confidence::High);
    }

    #[test]
    fn does_not_invent_an_amount() {
        assert!(ExampleBank
            .parse(&email("avisos@bancoejemplo.test", "Sin monto"))
            .is_none());
    }
}
