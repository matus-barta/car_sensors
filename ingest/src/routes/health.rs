use axum::{http::StatusCode, response::IntoResponse};

/// Whether the service is up.
///
/// Answers as long as the process is serving requests. It does not check the
/// database or the cache.
#[utoipa::path(
    get,
    path = "/api/health",
    tag = "health",
    responses(
        (status = 200, description = "The service is up", body = String, content_type = "text/plain",
            example = "Service is healthy"),
    ),
)]
pub async fn health_check() -> impl IntoResponse {
    (StatusCode::OK, "Service is healthy")
}
