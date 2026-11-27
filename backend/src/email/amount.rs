//! Colombian amount parsing.
//!
//! Colombian peso amounts use `.` as the thousands separator and `,` as the
//! decimal separator: `$ 35.000,00`. Amounts are returned in minor units
//! (centavos) so no floating point is involved.

/// Parse a Colombian-formatted amount into minor units (centavos).
///
/// Accepted examples:
///
/// - `$ 35.000,00` → `3_500_000`
/// - `35.000` → `3_500_000`
/// - `$1.234.567,50` → `123_456_750`
/// - `1,5` → `150`
///
/// Returns `None` when the text has no digit, when it is zero or negative,
/// when the grouping is ambiguous, or when the value overflows `i64`.
pub fn parse_amount(text: &str) -> Option<i64> {
    let text = text.trim();
    if !text.chars().any(|c| c.is_ascii_digit()) {
        return None;
    }

    // Reject a negative sign placed before the amount (`-35.000`,
    // `-$ 35.000`). A hyphen after the first digit is not a sign and is
    // simply ignored.
    let before_first_digit = text
        .split_once(|c: char| c.is_ascii_digit())
        .map(|(prefix, _)| prefix)
        .unwrap_or(text);
    if before_first_digit.contains('-') || before_first_digit.contains('\u{2212}') {
        return None;
    }

    let mut decimal_part = String::new();

    let mut pieces = text.split(',');
    let raw_integer = pieces.next().unwrap_or_default();
    let raw_decimal = pieces.next();
    if pieces.next().is_some() {
        // More than one decimal comma is ambiguous.
        return None;
    }

    // Keep only digits and dot separators in the integer part; currency
    // symbols, spaces and letters are ignored.
    let cleaned_integer: String = raw_integer
        .chars()
        .filter(|c| c.is_ascii_digit() || *c == '.')
        .collect();
    let integer_part: String = cleaned_integer.chars().filter(|c| c.is_ascii_digit()).collect();

    // Validate dot grouping so that `1.5` (ambiguous dot decimal) is not
    // silently read as 15 pesos. The first group may have 1 to 3 digits and
    // every following group must have exactly 3.
    if cleaned_integer.contains('.') {
        let groups: Vec<&str> = cleaned_integer.split('.').collect();
        let first_is_valid = groups
            .first()
            .map(|group| !group.is_empty() && group.len() <= 3)
            .unwrap_or(false);
        let rest_are_valid = groups
            .iter()
            .skip(1)
            .all(|group| group.len() == 3);
        if !(first_is_valid && rest_are_valid) {
            return None;
        }
    }

    if let Some(raw_decimal) = raw_decimal {
        for c in raw_decimal.chars() {
            if c.is_ascii_digit() {
                decimal_part.push(c);
            }
        }
        // Pad `,5` to `,50` and ignore precision beyond centavos.
        while decimal_part.len() < 2 {
            decimal_part.push('0');
        }
        decimal_part.truncate(2);
    }

    let integer_part: i64 = integer_part.parse().ok()?;
    let decimal_part: i64 = if decimal_part.is_empty() {
        0
    } else {
        decimal_part.parse().ok()?
    };

    let minor = integer_part
        .checked_mul(100)?
        .checked_add(decimal_part)?;

    if minor <= 0 {
        None
    } else {
        Some(minor)
    }
}

#[cfg(test)]
mod tests {
    use super::parse_amount;

    #[test]
    fn parses_colombian_formatting() {
        assert_eq!(parse_amount("$ 35.000,00"), Some(3_500_000));
        assert_eq!(parse_amount("35.000"), Some(3_500_000));
        assert_eq!(parse_amount("$1.234.567,50"), Some(123_456_750));
        assert_eq!(parse_amount("1,5"), Some(150));
    }

    #[test]
    fn parses_plain_values() {
        assert_eq!(parse_amount("35000"), Some(3_500_000));
        assert_eq!(parse_amount("$ 1.500"), Some(150_000));
        assert_eq!(parse_amount("0,01"), Some(1));
        assert_eq!(parse_amount("  $ 12.000,00 COP "), Some(1_200_000));
    }

    #[test]
    fn rejects_missing_digit_zero_and_negative() {
        assert_eq!(parse_amount(""), None);
        assert_eq!(parse_amount("$"), None);
        assert_eq!(parse_amount("COP"), None);
        assert_eq!(parse_amount("0"), None);
        assert_eq!(parse_amount("$ 0,00"), None);
        assert_eq!(parse_amount("-35.000"), None);
        assert_eq!(parse_amount("-$ 35.000,00"), None);
    }

    #[test]
    fn rejects_ambiguous_or_malformed_grouping() {
        assert_eq!(parse_amount("1.5"), None);
        assert_eq!(parse_amount("1.23.456,00"), None);
        assert_eq!(parse_amount("35.000,00,00"), None);
    }

    #[test]
    fn rejects_overflow() {
        assert_eq!(
            parse_amount("99.000.000.000.000.000,00"),
            None,
            "amount beyond i64 centavos must not wrap"
        );
    }
}
