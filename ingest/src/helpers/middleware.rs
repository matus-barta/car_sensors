use std::time::{SystemTime, UNIX_EPOCH};

use axum::{
    body::Body,
    extract::State,
    http::{Request, StatusCode},
    middleware::Next,
    response::Response,
};
use shared::redis::*;
use sqlx::{Pool, Postgres};

use crate::{AppState, helpers::device_auth::KnownDeviceId};

const KNOWN_DEVICE_CACHE_TTL_SECS: u32 = 300; // 5 minutes
const UNKNOWN_DEVICE_CACHE_TTL_SECS: u32 = 60; // 1 minute
const LAST_SEEN_CACHE_TTL_SECS: u32 = 600; // 10 minutes
const LAST_SEEN_DB_THROTTLE_SECS: u32 = 30; // write to DB at most once per minute per device

pub async fn require_known_device(
    State(state): State<AppState>,
    mut request: Request<Body>,
    next: Next,
) -> Result<Response, StatusCode> {
    let device_id: String = request
        .headers()
        .get("X-Device-ID")
        .and_then(|v| v.to_str().ok())
        .map(str::trim)
        .filter(|v| !v.is_empty())
        .map(str::to_string)
        .ok_or(StatusCode::UNAUTHORIZED)?;

    let cache_key = format!("known_device:{device_id}");

    // 1) Try Redis cache first
    let is_allowed = if let Some(cached) = get_key::<bool>(&state.redis, &cache_key).await {
        cached
    } else {
        // 2) Fallback to DB
        let exists = is_known_active_device(&state.db_pool, &device_id)
            .await
            .map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;

        let ttl = if exists {
            KNOWN_DEVICE_CACHE_TTL_SECS
        } else {
            UNKNOWN_DEVICE_CACHE_TTL_SECS
        };

        set_key_w_ttl(&state.redis, &cache_key, &exists, ttl).await;
        exists
    };

    if !is_allowed {
        tracing::warn!("Unknow device: {}", device_id);
        return Err(StatusCode::FORBIDDEN);
    }

    // Store validated device ID in request extensions for handlers
    request
        .extensions_mut()
        .insert(KnownDeviceId(device_id.clone()));

    // 3) Update Redis last_seen on every request, but throttle DB update
    let _ = update_last_seen_throttled(&state, &device_id).await;

    Ok(next.run(request).await)
}

async fn is_known_active_device(
    db_pool: &Pool<Postgres>,
    device_id: &str,
) -> Result<bool, sqlx::Error> {
    let exists: Option<bool> = sqlx::query_scalar(
        r#"
        SELECT EXISTS (
            SELECT 1
            FROM known_devices
            WHERE device_id = $1
              AND is_active = TRUE
        )
        "#,
    )
    .bind(device_id)
    .fetch_one(db_pool)
    .await?;

    Ok(exists.unwrap_or(false))
}

async fn touch_known_device(db_pool: &Pool<Postgres>, device_id: &str) -> Result<(), sqlx::Error> {
    sqlx::query(
        r#"
        UPDATE known_devices
        SET last_seen_at = NOW()
        WHERE device_id = $1
        "#,
    )
    .bind(device_id)
    .execute(db_pool)
    .await?;

    Ok(())
}

async fn update_last_seen_throttled(state: &AppState, device_id: &str) -> Result<(), sqlx::Error> {
    // Always keep a fresh last_seen value in Redis
    let now_epoch = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs() as i64;

    let last_seen_key = format!("device:last_seen:{device_id}");
    set_key_w_ttl(
        &state.redis,
        &last_seen_key,
        &now_epoch,
        LAST_SEEN_CACHE_TTL_SECS,
    )
    .await;

    // Throttle DB writes using a Redis flag
    let throttle_key = format!("device:last_seen_db_throttle:{device_id}");

    let should_write_db = get_key::<bool>(&state.redis, &throttle_key).await.is_none();

    if should_write_db {
        // Only set throttle if DB update succeeds
        touch_known_device(&state.db_pool, device_id).await?;

        set_key_w_ttl(
            &state.redis,
            &throttle_key,
            &true,
            LAST_SEEN_DB_THROTTLE_SECS,
        )
        .await;
    }

    Ok(())
}
