use axum::{
    Router,
    routing::{get, post},
};

use crate::AppState;

mod health;
mod telemetry;

pub fn router() -> Router<AppState> {
    Router::new()
        .route("/health", get(health::health_check))
        .route("/telemetry/upload", post(telemetry::upload))
}
