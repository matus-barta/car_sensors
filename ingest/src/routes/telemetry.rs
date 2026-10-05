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

/// Store a batch of samples from one device.
///
/// The body is a JSON array, normally gzipped with `Content-Encoding: gzip`;
/// an uncompressed body is accepted too. It may be at most 4 MiB as sent and
/// 32 MiB once decompressed.
///
/// The batch is written in one transaction. A sample whose `id` this device
/// has already uploaded is skipped rather than refused, so a device that never
/// received an answer can send the same batch again.
#[utoipa::path(
    post,
    path = "/api/telemetry/upload",
    tag = "telemetry",
    request_body(content = Vec<TelemetrySample>, content_type = "application/json"),
    security(("device_id" = [], "device_token" = [])),
    responses(
        (status = 200, description = "The batch is stored, or was already"),
        (status = 400, description = "The body is not valid JSON"),
        (status = 401, description = "No credentials were presented, or the token was refused or \
            belongs to no known device. The `WWW-Authenticate` challenge says which: \
            `error=\"invalid_token\"` for a refused token, no error for missing credentials."),
        (status = 403, description = "The credentials are good and the device has been \
            deactivated. The challenge carries `error=\"insufficient_scope\"`."),
        (status = 413, description = "The body is larger than the limits above"),
        (status = 415, description = "The request has no `Content-Type: application/json`"),
        (status = 422, description = "The body is JSON but not an array of samples - a field of \
            the wrong type, or one of `id`, `event` and `timestamp` missing"),
        (status = 500, description = "The device lookup or the database write failed"),
    ),
)]
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
