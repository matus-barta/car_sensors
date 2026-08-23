use axum::{
    Extension,
    extract::{Json, State, rejection::JsonRejection},
    http::StatusCode,
    response::{IntoResponse, Response},
};

use crate::{
    AppState,
    db::telemetry_sample::insert_telemetry_batch,
    live::telemetry_sample::publish_live_sample,
    models::{device_auth::KnownDeviceId, telemetry_sample::TelemetrySample},
};

pub async fn upload(
    State(state): State<AppState>,
    Extension(known_device): Extension<KnownDeviceId>,
    result: Result<Json<Vec<TelemetrySample>>, JsonRejection>,
) -> Response {
    let device_id = known_device.0;

    tracing::debug!("Validated device: {}", device_id);

    match result {
        Ok(Json(samples)) => {
            tracing::debug!("Received {} samples", samples.len());

            match insert_telemetry_batch(&state.db_pool, &device_id, &samples).await {
                Ok(rows) => {
                    tracing::info!("Inserted {} rows for {}", rows, device_id);

                    /*
                     * Only after the batch is committed, and only when it added
                     * something: a re-uploaded batch stores no rows and has no
                     * newer position to announce.
                     */
                    if rows > 0 {
                        publish_live_sample(&state, &device_id, &samples).await;
                    }

                    StatusCode::OK.into_response()
                }
                Err(error) => {
                    tracing::error!("Could not store the batch from {}: {}", device_id, error);

                    StatusCode::INTERNAL_SERVER_ERROR.into_response()
                }
            }
        }
        Err(rejection) => {
            tracing::warn!("Rejected an upload from {}: {}", device_id, rejection);

            /*
             * The rejection already carries the right status: 413 when the body
             * outgrew a limit, 400 when the JSON is malformed. A device that
             * retries on failure needs that difference - one says "send smaller
             * batches", the other says "this batch will never be accepted".
             */
            rejection.into_response()
        }
    }
}
