//! Bank payment notice ingest from `.eml` files.
//!
//! This module never touches the network or a mailbox: it receives the raw
//! text of an email that a human exported, extracts the plain text body and
//! lets a per-bank [`BankRule`] propose a [`PaymentObservation`].
//!
//! Observations are always unverified. [`Confidence::High`] only means the
//! matching rule recognized every field it needs, never that the bank
//! confirmed a payment.

use mail_parser::MessageParser;

pub mod amount;
pub use amount::parse_amount;

/// The parts of an email that a bank rule is allowed to inspect.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct RawEmail {
    /// Sender as written in the `From` header, for example
    /// `"Banco Ejemplo <avisos@bancoejemplo.test>"`.
    pub from: String,
    /// Decoded `Subject` header, with folding removed.
    pub subject: String,
    /// Best-effort plain text body. Multipart messages prefer their
    /// `text/plain` part; HTML-only messages are converted to text.
    pub body: String,
}

/// How much a rule trusts its own observation.
///
/// `High` means the rule found a complete, bank-specific match. `Low` is
/// reserved for heuristic matches such as a generic amount search.
#[derive(Debug, Clone, Copy, PartialEq, Eq, serde::Serialize)]
#[serde(rename_all = "lowercase")]
pub enum Confidence {
    /// The rule matched the bank and parsed every field it requires.
    High,
    /// A partial or heuristic match. Never treated as a confirmed payment.
    Low,
}

/// A structured, unverified payment notice extracted from an email.
#[derive(Debug, Clone, PartialEq, Eq, serde::Serialize)]
pub struct PaymentObservation {
    /// Display name of the bank, as reported by the rule.
    pub bank: String,
    /// Amount in minor units (centavos for COP).
    pub amount_minor: i64,
    /// ISO 4217 currency code.
    pub currency: String,
    /// ISO 8601 timestamp when the rule could recover one.
    pub occurred_at: Option<String>,
    /// Bank reference or transaction id when the rule could recover one.
    pub reference: Option<String>,
    /// Rule confidence; an observation is never a confirmed payment.
    pub confidence: Confidence,
}

/// One bank's recognition and parsing logic.
pub trait BankRule {
    /// Stable, unique identifier used in logs and tests.
    fn id(&self) -> &'static str;

    /// Whether this rule claims the email. Must be specific enough to avoid
    /// matching unrelated senders.
    fn matches(&self, email: &RawEmail) -> bool;

    /// Extract an observation, or `None` when required fields are missing.
    /// Never invent an amount.
    fn parse(&self, email: &RawEmail) -> Option<PaymentObservation>;
}

/// Parse the raw text of an `.eml` file into the fields rules can inspect.
///
/// Handles CRLF line endings, header folding, `quoted-printable` and `base64`
/// transfer encodings, UTF-8 and latin-1 charsets, and multipart messages
/// (preferring `text/plain` over HTML).
///
/// `raw` is expected to be UTF-8. A file that is itself latin-1 should be
/// decoded lossily by the caller; charset handling inside MIME parts is done
/// by the MIME parser.
pub fn parse_eml(raw: &str) -> Result<RawEmail, String> {
    let message = MessageParser::new()
        .parse(raw.as_bytes())
        .ok_or_else(|| "could not parse the email headers".to_string())?;

    let from = message
        .from()
        .and_then(|list| list.first())
        .map(|mailbox| match (mailbox.name(), mailbox.address()) {
            (Some(name), Some(address)) => format!("{name} <{address}>"),
            (None, Some(address)) => address.to_string(),
            (Some(name), None) => name.to_string(),
            (None, None) => String::new(),
        })
        .unwrap_or_default();

    let subject = message.subject().unwrap_or_default().to_string();
    let body = plain_text_body(&message);

    Ok(RawEmail {
        from,
        subject,
        body,
    })
}

/// Collect `text/plain` parts, falling back to HTML converted to text.
fn plain_text_body(message: &mail_parser::Message<'_>) -> String {
    let mut text = String::new();
    for part in message.text_body() {
        if let Some(part_text) = part.text() {
            if !text.is_empty() && !text.ends_with('\n') {
                text.push('\n');
            }
            text.push_str(&part_text);
        }
    }

    if text.trim().is_empty() {
        for part in message.html_body() {
            if let Some(html) = part.text() {
                if !text.is_empty() && !text.ends_with('\n') {
                    text.push('\n');
                }
                text.push_str(&mail_parser::html_to_text(&html));
            }
        }
    }

    text
}
