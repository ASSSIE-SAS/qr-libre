//! Amount parsing for the number styles found in bank notices.
//!
//! Colombian peso amounts use `.` as the thousands separator and `,` as the
//! decimal separator (`$ 35.000,00`). Some banks also send the Anglo style,
//! with `,` as the thousands separator and `.` as decimals
//! (`35,000,000.00`). Amounts are returned in minor units (centavos) so no
//! floating point is involved.

/// Separator conventions used by an amount string.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum AmountStyle {
    /// `.` for thousands and `,` for decimals, as used in Colombia.
    Colombian,
    /// `,` for thousands and `.` for decimals.
    Anglo,
}

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
    parse_amount_with(text, AmountStyle::Colombian)
}

/// Parse an amount using the separators of `style`.
///
/// See [`AmountStyle`] for the two conventions. Both styles reject grouping
/// that is not unambiguous and never invent a decimal part.
pub fn parse_amount_with(text: &str, style: AmountStyle) -> Option<i64> {
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

    // Keep digits and both separator characters; currency symbols, spaces and
    // letters are ignored.
    let cleaned: String = text
        .chars()
        .filter(|c| c.is_ascii_digit() || *c == '.' || *c == ',')
        .collect();

    let minor = match style {
        AmountStyle::Colombian => parse_colombian(&cleaned)?,
        AmountStyle::Anglo => parse_anglo(&cleaned)?,
    };

    if minor <= 0 {
        None
    } else {
        Some(minor)
    }
}

/// Colombian: dot thousands, comma decimals.
fn parse_colombian(cleaned: &str) -> Option<i64> {
    let mut pieces = cleaned.split(',');
    let raw_integer = pieces.next().unwrap_or_default();
    let raw_decimal = pieces.next();
    if pieces.next().is_some() {
        // More than one decimal comma is ambiguous.
        return None;
    }

    if !valid_grouping(raw_integer, '.') {
        return None;
    }
    let integer_digits: String = raw_integer.chars().filter(|c| c.is_ascii_digit()).collect();

    let decimal = match raw_decimal {
        None => 0,
        Some(raw_decimal) => {
            if !raw_decimal.chars().all(|c| c.is_ascii_digit()) {
                return None;
            }
            // Pad `,5` to `,50` and ignore precision beyond centavos.
            let mut digits = raw_decimal.to_string();
            while digits.len() < 2 {
                digits.push('0');
            }
            digits.truncate(2);
            digits.parse().ok()?
        }
    };

    build_minor(&integer_digits, decimal)
}

/// Anglo: comma thousands, dot decimals.
fn parse_anglo(cleaned: &str) -> Option<i64> {
    let mut pieces = cleaned.split('.');
    let raw_integer = pieces.next().unwrap_or_default();
    let raw_decimal = pieces.next();
    if pieces.next().is_some() {
        // A second dot is ambiguous.
        return None;
    }

    if !valid_grouping(raw_integer, ',') {
        return None;
    }
    let integer_digits: String = raw_integer.chars().filter(|c| c.is_ascii_digit()).collect();

    let decimal = match raw_decimal {
        None => 0,
        // `1.234`: a fractional part longer than two digits cannot be
        // represented exactly in centavos. Treating it as `1234.00` would
        // multiply the value by ten; truncating or rounding would silently
        // change it. Reject the ambiguous input instead of guessing.
        Some(raw_decimal) if raw_decimal.is_empty() || raw_decimal.len() > 2 => return None,
        Some(raw_decimal) => {
            if !raw_decimal.chars().all(|c| c.is_ascii_digit()) {
                return None;
            }
            let mut digits = raw_decimal.to_string();
            if digits.len() == 1 {
                digits.push('0');
            }
            digits.parse().ok()?
        }
    };

    build_minor(&integer_digits, decimal)
}

/// Validate thousands grouping: the first group may have one to three
/// digits and every following group must have exactly three.
fn valid_grouping(integer: &str, separator: char) -> bool {
    if integer.is_empty() {
        return false;
    }
    if !integer.contains(separator) {
        return integer.chars().all(|c| c.is_ascii_digit());
    }

    let mut groups = integer.split(separator);
    let first = groups.next().unwrap_or_default();
    if first.is_empty() || first.len() > 3 || !first.chars().all(|c| c.is_ascii_digit()) {
        return false;
    }
    groups.all(|group| group.len() == 3 && group.chars().all(|c| c.is_ascii_digit()))
}

fn build_minor(integer_digits: &str, decimal: i64) -> Option<i64> {
    let integer: i64 = integer_digits.parse().ok()?;
    integer.checked_mul(100)?.checked_add(decimal)
}

#[cfg(test)]
mod tests {
    use super::{parse_amount, parse_amount_with, AmountStyle};

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

    #[test]
    fn parses_anglo_formatting() {
        assert_eq!(
            parse_amount_with("35,000,000.00", AmountStyle::Anglo),
            Some(3_500_000_000)
        );
        assert_eq!(
            parse_amount_with("$1,250,000.00", AmountStyle::Anglo),
            Some(125_000_000)
        );
        assert_eq!(
            parse_amount_with("35,000", AmountStyle::Anglo),
            Some(3_500_000)
        );
        assert_eq!(
            parse_amount_with("1.5", AmountStyle::Anglo),
            Some(150)
        );
        assert_eq!(
            parse_amount_with("35000000", AmountStyle::Anglo),
            Some(3_500_000_000)
        );
    }

    #[test]
    fn anglo_rejects_ambiguous_inputs() {
        // A thousands group must be exactly three digits.
        assert_eq!(parse_amount_with("1,5", AmountStyle::Anglo), None);
        assert_eq!(parse_amount_with("1,23,456.00", AmountStyle::Anglo), None);
        // More than two fractional digits would be ten times off if guessed.
        assert_eq!(parse_amount_with("1.234", AmountStyle::Anglo), None);
        assert_eq!(parse_amount_with("1,000.234", AmountStyle::Anglo), None);
        // No digits, zero and negative values stay invalid.
        assert_eq!(parse_amount_with("", AmountStyle::Anglo), None);
        assert_eq!(parse_amount_with("$0.00", AmountStyle::Anglo), None);
        assert_eq!(parse_amount_with("-1,000.00", AmountStyle::Anglo), None);
    }

    #[test]
    fn the_same_characters_mean_different_values_per_style() {
        // Colombian: comma starts the decimals, so `234` is truncated to 23.
        assert_eq!(parse_amount("1,234"), Some(123));
        // Anglo: comma groups thousands, so the same text is 1234.00.
        assert_eq!(
            parse_amount_with("1,234", AmountStyle::Anglo),
            Some(123_400)
        );

        // Colombian: dot groups thousands, so 1.234 is 1234.00.
        assert_eq!(parse_amount("1.234"), Some(123_400));
        // Anglo: dot starts the decimals with three digits, which is rejected.
        assert_eq!(parse_amount_with("1.234", AmountStyle::Anglo), None);
    }
}
