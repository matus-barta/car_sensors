use axum::{
    extract::Json,
    http::{HeaderMap, StatusCode},
    response::IntoResponse,
};

use crate::models::telemetry_sample::TelemetrySample;

pub async fn upload(
    headers: HeaderMap,
    Json(samples): Json<Vec<TelemetrySample>>,
) -> impl IntoResponse {
    if let Some(device_id) = headers.get("X-Device-ID") {
        println!("Device ID: {}", device_id.to_str().unwrap_or(""));
    }

    if samples.is_empty() {
        return StatusCode::BAD_REQUEST;
    }

    println!("Received {} samples", samples.len());

    // Debug example
    if let Some(first) = samples.first() {
        println!("First sample: {:?}", first);
    }

    StatusCode::OK
}
