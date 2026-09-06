use axum::{
    body::Body,
    extract::State,
    http::Request,
    middleware::Next,
    response::Response,
};
use shared::cache::{get_key, set_key_w_ttl};
use shared::sqlx::{Error, Pool, Postgres, Row, query};

use crate::{
    AppState,
    models::device_auth::{
        DEVICE_ID_HEADER, DeviceAuthRejection, DeviceCredential, KnownDeviceId, bearer_token,
    },
};

const KNOWN_DEVICE_CACHE_TTL_SECS: u32 = 300; // 5 minutes
const UNKNOWN_DEVICE_CACHE_TTL_SECS: u32 = 60; // 1 minute
/// How often `last_seen_at` may be written for one device. Not a request limit.
const LAST_SEEN_WRITE_INTERVAL_SECS: u32 = 30;

/// The cache entry holding what a device may do.
///
/// Named apart from the boolean this used to store, so a value written by an
/// older build is a miss rather than something that fails to parse.
fn credential_cache_key(device_id: &str) -> String {
    format!("device_credential:{device_id}")
}

pub async fn require_known_device(
    State(state): State<AppState>,
    mut request: Request<Body>,
    next: Next,
) -> Result<Response, DeviceAuthRejection> {
    let device_id = request
        .headers()
        .get(DEVICE_ID_HEADER)
        .and_then(|value| value.to_str().ok())
        .map(str::trim)
        .filter(|value| !value.is_empty())
        .map(str::to_string)
        .ok_or(DeviceAuthRejection::MissingCredentials)?;

    let token = bearer_token(request.headers())
        .ok_or(DeviceAuthRejection::MissingCredentials)?
        .to_string();

    let credential = load_credential(&state, &device_id).await?;

    /*
     * Authenticate before authorising, so that "this device is deactivated" is
     * only ever said to a request that proved it is that device. Answering it
     * any earlier would turn the endpoint into a way of asking which
     * identities exist.
     */
    let Some(credential) = credential else {
        tracing::warn!("No such device: {}", device_id);

        return Err(DeviceAuthRejection::InvalidToken);
    };

    if !credential.accepts(&token) {
        tracing::warn!("Rejected token for device: {}", device_id);

        return Err(DeviceAuthRejection::InvalidToken);
    }

    if !credential.is_active {
        tracing::warn!("Deactivated device: {}", device_id);

        return Err(DeviceAuthRejection::Deactivated);
    }

    request
        .extensions_mut()
        .insert(KnownDeviceId(device_id.clone()));

    record_device_seen(&state, &device_id).await;

    Ok(next.run(request).await)
}

/// Reads what the device may do, from the cache when it can and the database
/// when it cannot.
///
/// `Ok(None)` means there is no such device. The cache holds that answer too,
/// on a shorter lease, so a device repeatedly presenting an unknown identity
/// does not become a stream of queries.
async fn load_credential(
    state: &AppState,
    device_id: &str,
) -> Result<Option<DeviceCredential>, DeviceAuthRejection> {
    let cache_key = credential_cache_key(device_id);

    /*
     * The cache only saves a query, so a cache that cannot answer is treated
     * exactly like one that has nothing to say: ask the database and try to
     * remember the answer. Turning a device away because Valkey is unavailable
     * would lose telemetry for no reason.
     *
     * What it must never do is answer the question the token is supposed to
     * answer. It holds the stored hash rather than a verdict, so the
     * comparison still happens on every request whether or not the lookup was
     * served from here.
     */
    let cached = get_key::<Option<DeviceCredential>>(&state.redis, &cache_key)
        .await
        .unwrap_or_else(|error| {
            tracing::warn!("Device cache lookup failed for {}: {}", device_id, error);

            None
        });

    if let Some(credential) = cached {
        return Ok(credential);
    }

    let credential = fetch_credential(&state.db_pool, device_id)
        .await
        .map_err(|error| {
            tracing::error!("Device lookup failed for {}: {}", device_id, error);

            DeviceAuthRejection::Unavailable
        })?;

    let ttl = if credential.is_some() {
        KNOWN_DEVICE_CACHE_TTL_SECS
    } else {
        UNKNOWN_DEVICE_CACHE_TTL_SECS
    };

    if let Err(error) = set_key_w_ttl(&state.redis, &cache_key, &credential, ttl).await {
        tracing::warn!("Could not cache the device lookup: {}", error);
    }

    Ok(credential)
}

async fn fetch_credential(
    db_pool: &Pool<Postgres>,
    device_id: &str,
) -> Result<Option<DeviceCredential>, Error> {
    let row = query(
        r#"
        SELECT token_hash, is_active
        FROM known_devices
        WHERE device_id = $1
        "#,
    )
    .bind(device_id)
    .fetch_optional(db_pool)
    .await?;

    row.map(|row| {
        Ok(DeviceCredential {
            token_hash: row.try_get("token_hash")?,
            is_active: row.try_get("is_active")?,
        })
    })
    .transpose()
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

/// Records in the database that the device was heard from, writing the column
/// at most once per [`LAST_SEEN_WRITE_INTERVAL_SECS`].
///
/// **This limits a column write, not requests.** Nothing here rejects, delays
/// or sheds an upload - every request is served in full whether or not the
/// write happens, and `ingest` has no rate limiting of any kind. What is being
/// spared is Postgres: a driving device uploads every couple of seconds, and
/// rewriting one timestamp thirty times a minute churns row versions to keep a
/// value fresher than anything reads it, since `www` treats a vehicle as online
/// for two minutes.
///
/// Failures are logged rather than returned: the upload itself is what the
/// device came for, and losing a `last_seen_at` update is not worth failing it.
///
/// This is awaited before the handler runs, so every upload pays for it -
/// normally a single cache read, and once per interval a row update as well.
/// That is affordable against a request that is about to insert a batch, but it
/// is the reason nothing else belongs here.
async fn record_device_seen(state: &AppState, device_id: &str) {
    let recently_written_key = format!("device:last_seen_written:{device_id}");

    /*
     * A cache that cannot answer leaves nothing to measure the interval
     * against, so the write goes ahead. That is the cheaper of the two
     * mistakes: an extra UPDATE per upload is nothing, while skipping the write
     * would freeze `last_seen_at` for as long as the cache stays down, and
     * `www` decides whether a vehicle is online from that column.
     */
    let written_recently = match get_key::<bool>(&state.redis, &recently_written_key).await {
        Ok(marker) => marker.is_some(),
        Err(error) => {
            tracing::warn!("Could not read the last_seen write marker: {}", error);

            false
        }
    };

    if written_recently {
        return;
    }

    if let Err(error) = touch_known_device(&state.db_pool, device_id).await {
        tracing::error!("Could not update last_seen for {}: {}", device_id, error);

        return;
    }

    // Only now: the marker must not suppress writes that never happened.
    if let Err(error) = set_key_w_ttl(
        &state.redis,
        &recently_written_key,
        &true,
        LAST_SEEN_WRITE_INTERVAL_SECS,
    )
    .await
    {
        tracing::warn!("Could not set the last_seen write marker: {}", error);
    }
}
