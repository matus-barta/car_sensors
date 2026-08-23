use std::time::{SystemTime, UNIX_EPOCH};

use axum::{
    body::Body,
    extract::State,
    http::{Request, StatusCode},
    middleware::Next,
    response::Response,
};
use shared::cache::{get_key, set_key_w_ttl};
use shared::sqlx::{Error, Pool, Postgres, query, query_scalar};

use crate::{AppState, models::device_auth::KnownDeviceId};

const KNOWN_DEVICE_CACHE_TTL_SECS: u32 = 300; // 5 minutes
const UNKNOWN_DEVICE_CACHE_TTL_SECS: u32 = 60; // 1 minute
const LAST_SEEN_CACHE_TTL_SECS: u32 = 600; // 10 minutes
const LAST_SEEN_DB_THROTTLE_SECS: u32 = 30; // write to DB at most once per 30 seconds per device

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

    /*
     * The cache only saves a query here, so a cache that cannot answer is
     * treated exactly like one that has nothing to say: ask the database and
     * try to remember the answer. Turning a device away because Valkey is
     * unavailable would lose telemetry for no reason.
     */
    let cached = get_key::<bool>(&state.redis, &cache_key)
        .await
        .unwrap_or_else(|error| {
            tracing::warn!("Device cache lookup failed for {}: {}", device_id, error);

            None
        });

    let is_allowed = match cached {
        Some(is_allowed) => is_allowed,
        None => {
            let exists = is_known_active_device(&state.db_pool, &device_id)
                .await
                .map_err(|error| {
                    tracing::error!("Device lookup failed for {}: {}", device_id, error);

                    StatusCode::INTERNAL_SERVER_ERROR
                })?;

            let ttl = if exists {
                KNOWN_DEVICE_CACHE_TTL_SECS
            } else {
                UNKNOWN_DEVICE_CACHE_TTL_SECS
            };

            if let Err(error) = set_key_w_ttl(&state.redis, &cache_key, &exists, ttl).await {
                tracing::warn!("Could not cache the device lookup: {}", error);
            }

            exists
        }
    };

    if !is_allowed {
        tracing::warn!("Unknown device: {}", device_id);

        return Err(StatusCode::FORBIDDEN);
    }

    // Store validated device ID in request extensions for handlers
    request
        .extensions_mut()
        .insert(KnownDeviceId(device_id.clone()));

    update_last_seen_throttled(&state, &device_id).await;

    Ok(next.run(request).await)
}

async fn is_known_active_device(db_pool: &Pool<Postgres>, device_id: &str) -> Result<bool, Error> {
    let exists: Option<bool> = query_scalar(
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

async fn touch_known_device(db_pool: &Pool<Postgres>, device_id: &str) -> Result<(), Error> {
    query(
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

/// Records that the device was heard from, in the cache always and in the
/// database at most once per [`LAST_SEEN_DB_THROTTLE_SECS`].
///
/// Failures are logged rather than returned: the upload itself is what the
/// device came for, and losing a `last_seen_at` update is not worth failing it.
async fn update_last_seen_throttled(state: &AppState, device_id: &str) {
    let now_epoch = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs() as i64;

    let last_seen_key = format!("device:last_seen:{device_id}");

    if let Err(error) = set_key_w_ttl(
        &state.redis,
        &last_seen_key,
        &now_epoch,
        LAST_SEEN_CACHE_TTL_SECS,
    )
    .await
    {
        tracing::warn!("Could not cache last_seen for {}: {}", device_id, error);
    }

    let throttle_key = format!("device:last_seen_db_throttle:{device_id}");

    /*
     * A cache that cannot answer leaves nothing to throttle against, so the
     * write goes ahead. That is the cheaper of the two mistakes: an extra
     * UPDATE per upload is nothing, while skipping the write would freeze
     * `last_seen_at` for as long as the cache stays down, and `www` decides
     * whether a vehicle is online from that column.
     */
    let is_throttled = match get_key::<bool>(&state.redis, &throttle_key).await {
        Ok(throttle) => throttle.is_some(),
        Err(error) => {
            tracing::warn!("Could not read the last_seen throttle: {}", error);

            false
        }
    };

    if is_throttled {
        return;
    }

    if let Err(error) = touch_known_device(&state.db_pool, device_id).await {
        tracing::error!("Could not update last_seen for {}: {}", device_id, error);

        return;
    }

    // Only now: the throttle must not suppress writes that never happened.
    if let Err(error) = set_key_w_ttl(
        &state.redis,
        &throttle_key,
        &true,
        LAST_SEEN_DB_THROTTLE_SECS,
    )
    .await
    {
        tracing::warn!("Could not set the last_seen throttle: {}", error);
    }
}
