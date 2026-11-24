use axum::{extract::State, http::StatusCode, routing::{get, post}, Json, Router};
use serde::{Deserialize, Serialize};
use std::sync::{Arc, Mutex};
use uuid::Uuid;

#[derive(Clone, Debug, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
enum ObservationSource { AndroidNotification }

#[derive(Clone, Debug, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
enum ObservationStatus { Unverified }

#[derive(Debug, Deserialize)]
struct NewObservation {
    source: ObservationSource,
    provider: String,
    amount_minor: i64,
    currency: String,
    external_reference: Option<String>,
}

#[derive(Clone, Debug, Serialize)]
struct Observation {
    id: Uuid,
    source: ObservationSource,
    provider: String,
    amount_minor: i64,
    currency: String,
    external_reference: Option<String>,
    status: ObservationStatus,
}

type Store = Arc<Mutex<Vec<Observation>>>;

async fn health() -> &'static str { "ok" }

async fn create_observation(
    State(store): State<Store>,
    Json(payload): Json<NewObservation>,
) -> Result<(StatusCode, Json<Observation>), (StatusCode, &'static str)> {
    if payload.amount_minor <= 0 || payload.provider.trim().is_empty() || payload.currency != "COP" {
        return Err((StatusCode::BAD_REQUEST, "invalid observation"));
    }
    let observation = Observation {
        id: Uuid::new_v4(),
        source: payload.source,
        provider: payload.provider,
        amount_minor: payload.amount_minor,
        currency: payload.currency,
        external_reference: payload.external_reference,
        status: ObservationStatus::Unverified,
    };
    store.lock().map_err(|_| (StatusCode::INTERNAL_SERVER_ERROR, "lock error"))?
        .push(observation.clone());
    Ok((StatusCode::CREATED, Json(observation)))
}

async fn list_observations(State(store): State<Store>) -> Result<Json<Vec<Observation>>, StatusCode> {
    Ok(Json(store.lock().map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?.clone()))
}

#[tokio::main]
async fn main() -> std::io::Result<()> {
    let store: Store = Arc::new(Mutex::new(Vec::new()));
    let app = Router::new()
        .route("/health", get(health))
        .route("/v1/observations", post(create_observation).get(list_observations))
        .with_state(store);
    // Localhost only: no authentication or persistence yet.
    let listener = tokio::net::TcpListener::bind("127.0.0.1:3000").await?;
    axum::serve(listener, app).await
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn notifications_are_unverified_by_design() {
        assert_eq!(serde_json::to_string(&ObservationStatus::Unverified).unwrap(), "\"unverified\"");
    }

    #[test]
    fn observation_source_is_distinct_from_verified_payment() {
        assert_eq!(serde_json::to_string(&ObservationSource::AndroidNotification).unwrap(), "\"android_notification\"");
    }
}
