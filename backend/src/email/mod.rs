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
pub mod rules;

pub use amount::parse_amount;
pub use rules::registry;

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
///
/// `text/plain` wins whenever at least one such part exists, even if it is
/// empty. HTML is used only when the message has no plain text body at all;
/// `mail-parser`'s `html_to_text` strips tags, decodes named and numeric
/// entities (for example `&aacute;` → `á`, `&nbsp;` → space, `&amp;` → `&`)
/// and collapses whitespace.
fn plain_text_body(message: &mail_parser::Message<'_>) -> String {
    // `text_body` may contain an HTML part as a stand-in when there is no
    // plain version, so check the content type before treating a part as
    // plain text.
    let plain_parts: Vec<String> = message
        .text_body()
        .filter(|part| !part.is_content_type("text", "html"))
        .filter_map(|part| part.text().map(|text| text.into_owned()))
        .collect();
    if !plain_parts.is_empty() {
        return join_parts(plain_parts.into_iter());
    }

    let html_parts: Vec<String> = message
        .html_body()
        .filter_map(|part| part.text())
        .map(|html| html_to_plain_text(&html))
        .collect();
    join_parts(html_parts.into_iter())
}

/// Strip HTML and normalize whitespace, including the non-breaking space
/// that `&nbsp;` decodes to.
fn html_to_plain_text(html: &str) -> String {
    let text = mail_parser::html_to_text(html);
    text.split_whitespace().collect::<Vec<_>>().join(" ")
}

/// Join decoded body parts, keeping paragraphs separated by a newline.
fn join_parts(parts: impl Iterator<Item = String>) -> String {
    let mut text = String::new();
    for part_text in parts {
        if !text.is_empty() && !text.ends_with('\n') {
            text.push('\n');
        }
        text.push_str(&part_text);
    }
    text
}

/// Try every registered rule in order and return the first observation.
///
/// Returns `None` when no rule claims the email. No generic guess is made:
/// a missing rule is a missing observation, not a zero amount.
pub fn observe(email: &RawEmail) -> Option<PaymentObservation> {
    registry()
        .iter()
        .filter(|rule| rule.matches(email))
        .find_map(|rule| rule.parse(email))
}

#[cfg(test)]
mod tests {
    use super::parse_eml;

    #[test]
    fn html_only_email_is_converted_to_text() {
        let raw = concat!(
            "From: Banco <avisos@bancoejemplo.test>\r\n",
            "Subject: Aviso\r\n",
            "MIME-Version: 1.0\r\n",
            "Content-Type: text/html; charset=\"UTF-8\"\r\n",
            "Content-Transfer-Encoding: quoted-printable\r\n",
            "\r\n",
            "<html><body><p>Recibiste un pago por $1,250,000.00</p></body></html>\r\n"
        );
        let email = parse_eml(raw).expect("html-only email parses");
        assert!(
            email.body.contains("Recibiste un pago por $1,250,000.00"),
            "body was: {}",
            email.body
        );
        assert!(!email.body.contains('<'), "body still has tags: {}", email.body);
    }

    #[test]
    fn html_named_entities_are_decoded() {
        let raw = concat!(
            "From: Banco <avisos@bancoejemplo.test>\r\n",
            "Subject: Aviso\r\n",
            "MIME-Version: 1.0\r\n",
            "Content-Type: text/html; charset=\"UTF-8\"\r\n",
            "\r\n",
            "<p>Informaci&oacute;n &amp; m&aacute;s&nbsp;texto</p>\r\n"
        );
        let email = parse_eml(raw).expect("html email parses");
        assert_eq!(email.body.trim(), "Información & más texto");
    }

    #[test]
    fn multipart_prefers_plain_over_html() {
        let raw = concat!(
            "From: Banco <avisos@bancoejemplo.test>\r\n",
            "Subject: Aviso\r\n",
            "MIME-Version: 1.0\r\n",
            "Content-Type: multipart/alternative; boundary=\"alt\"\r\n",
            "\r\n",
            "--alt\r\n",
            "Content-Type: text/plain; charset=\"UTF-8\"\r\n",
            "\r\n",
            "Texto plano\r\n",
            "--alt\r\n",
            "Content-Type: text/html; charset=\"UTF-8\"\r\n",
            "\r\n",
            "<p>Texto HTML</p>\r\n",
            "--alt--\r\n"
        );
        let email = parse_eml(raw).expect("multipart email parses");
        assert!(email.body.contains("Texto plano"), "body was: {}", email.body);
        assert!(!email.body.contains("Texto HTML"), "body was: {}", email.body);
    }

    #[test]
    fn html_only_multipart_alternative_falls_back_to_html() {
        let raw = concat!(
            "From: Banco <avisos@bancoejemplo.test>\r\n",
            "Subject: Aviso\r\n",
            "MIME-Version: 1.0\r\n",
            "Content-Type: multipart/alternative; boundary=\"alt\"\r\n",
            "\r\n",
            "--alt\r\n",
            "Content-Type: text/html; charset=\"UTF-8\"\r\n",
            "\r\n",
            "<p>Recibiste un pago</p>\r\n",
            "--alt--\r\n"
        );
        let email = parse_eml(raw).expect("html-only multipart parses");
        assert!(email.body.contains("Recibiste un pago"), "body was: {}", email.body);
    }
}
