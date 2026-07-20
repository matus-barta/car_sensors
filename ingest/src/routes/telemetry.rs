use axum::{
    Extension,
    extract::{Json, State, rejection::JsonRejection},
    http::StatusCode,
    response::IntoResponse,
};

use crate::{
    AppState, db::telemetry_sample::insert_telemetry_batch, helpers::device_auth::KnownDeviceId,
    models::telemetry_sample::TelemetrySample,
};

pub async fn upload(
    State(state): State<AppState>,
    Extension(known_device): Extension<KnownDeviceId>,
    result: Result<Json<Vec<TelemetrySample>>, JsonRejection>,
) -> impl IntoResponse {
    let device_id = known_device.0;
    println!("Validated device: {}", &device_id);

    match result {
        Ok(Json(samples)) => {
            println!("Received {} samples", samples.len());

            match insert_telemetry_batch(&state.db_pool, Some(&device_id), &samples).await {
                Ok(rows) => {
                    println!("Inserted {} rows", rows);
                    StatusCode::OK
                }
                Err(err) => {
                    eprintln!("DB insert error: {:?}", err);
                    StatusCode::INTERNAL_SERVER_ERROR
                }
            }
        }
        Err(err) => {
            eprintln!("JSON ERROR: {:?}", err);
            StatusCode::BAD_REQUEST
        }
    }
}
