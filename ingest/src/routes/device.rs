use axum::{
    Json,
    extract::{Path, State},
    http::StatusCode,
    response::IntoResponse,
};
use serde_json::json;

use crate::AppState;

pub async fn add_device(
    State(state): State<AppState>,
    Path(device_id): Path<String>,
) -> impl IntoResponse {
    let result = sqlx::query(
        r#"
        INSERT INTO known_devices (device_id)
        VALUES ($1)
        ON CONFLICT (device_id) DO NOTHING
        "#,
    )
    .bind(&device_id)
    .execute(&state.db_pool)
    .await;

    match result {
        Ok(result) => {
            let inserted = result.rows_affected() > 0;

            println!(
                "✅ Device add attempt: {}, inserted={}",
                device_id, inserted
            );

            (
                StatusCode::OK,
                Json(json!({
                    "status": "ok",
                    "device_id": device_id,
                    "inserted": inserted
                })),
            )
        }
        Err(err) => {
            eprintln!("❌ Failed to add device: {:?}", err);

            (
                StatusCode::INTERNAL_SERVER_ERROR,
                Json(json!({
                    "status": "error",
                    "message": "failed to add device"
                })),
            )
        }
    }
}
