use axum::{http::StatusCode, response::IntoResponse};

/// GET -> /health
/// returns JSON OK
pub async fn health_check() -> impl IntoResponse {
    (StatusCode::OK, "Service is healthy")
}
