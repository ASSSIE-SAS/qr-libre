//! Registry of available bank rules.
//!
//! Rules are tried in registration order. Add new banks here once an
//! anonymized sample and its test exist.

mod bancolombia;
mod example_bank;
mod nequi;

use super::BankRule;

/// All rules known to this build, in priority order.
pub fn registry() -> Vec<Box<dyn BankRule>> {
    vec![
        Box::new(bancolombia::Bancolombia),
        Box::new(nequi::Nequi),
        Box::new(example_bank::ExampleBank),
    ]
}
