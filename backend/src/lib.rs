//! Local, offline building blocks for the QR Libre backend.
//!
//! The current surface is the bank email ingest module: it turns a manually
//! provided `.eml` file into an unverified [`email::PaymentObservation`].

pub mod email;
