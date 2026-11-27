//! `parse-eml` CLI: print the observation for one `.eml` file.
//!
//! Usage: `parse-eml <path> [--json]`

use std::env;
use std::fs;
use std::process::ExitCode;

use qr_libre_api::email::{observe, parse_eml, Confidence, PaymentObservation};

fn main() -> ExitCode {
    let mut args = env::args().skip(1);
    let path = match args.next() {
        Some(path) => path,
        None => {
            eprintln!("uso: parse-eml <archivo.eml> [--json]");
            return ExitCode::from(1);
        }
    };
    let json = match args.next().as_deref() {
        None => false,
        Some("--json") => true,
        Some(other) => {
            eprintln!("opción desconocida: {other}");
            eprintln!("uso: parse-eml <archivo.eml> [--json]");
            return ExitCode::from(1);
        }
    };

    let raw = match fs::read(&path) {
        Ok(bytes) => String::from_utf8_lossy(&bytes).into_owned(),
        Err(error) => {
            eprintln!("no se pudo leer {path}: {error}");
            return ExitCode::from(1);
        }
    };

    // A readable but unparseable file is not an ingest error: it simply has
    // no observation, same as a message no rule matches.
    let email = parse_eml(&raw).ok();

    let observation = email.as_ref().and_then(observe);

    match observation {
        Some(observation) => {
            if json {
                match serde_json::to_string(&observation) {
                    Ok(json) => println!("{json}"),
                    Err(error) => {
                        eprintln!("no se pudo serializar la observación: {error}");
                        return ExitCode::from(1);
                    }
                }
            } else {
                print_text(&observation);
            }
        }
        None => {
            if json {
                let unknown = serde_json::json!({
                    "bank": "unknown",
                    "confidence": "low",
                });
                println!("{unknown}");
            } else {
                println!("bank: unknown");
                println!("confidence: low");
            }
        }
    }

    ExitCode::SUCCESS
}

fn print_text(observation: &PaymentObservation) {
    println!("bank: {}", observation.bank);
    println!("amount_minor: {}", observation.amount_minor);
    println!("currency: {}", observation.currency);
    let confidence = match observation.confidence {
        Confidence::High => "high",
        Confidence::Low => "low",
    };
    println!("confidence: {confidence}");
    if let Some(occurred_at) = &observation.occurred_at {
        println!("occurred_at: {occurred_at}");
    }
    if let Some(reference) = &observation.reference {
        println!("reference: {reference}");
    }
}
