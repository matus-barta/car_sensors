//! Drives the real router, middleware included.
//!
//! `build_app` is what `main` serves, so these exercise the same stack a device
//! meets: the auth middleware, the JSON extractor, the insert and the live
//! announcement. They need Postgres and Valkey - point `TEST_DATABASE_URL` and
//! `TEST_REDIS_URL` at throwaway instances - and report that they were skipped
//! when either is missing.
//!
//! ```sh
//! cd tools && docker compose up -d postgres valkey
//! TEST_DATABASE_URL=postgres://postgres:postgres@127.0.0.1:5432/ingest_test \
//!   TEST_REDIS_URL=redis://127.0.0.1:6379 cargo test -p ingest
//! ```

use axum::body::Body;
use axum::http::{
    Request, StatusCode, header::AUTHORIZATION, header::CONTENT_LENGTH, header::CONTENT_TYPE,
};
use ingest::{AppState, build_app};
use shared::cache::init_redis;
use shared::pg::init_pg;
use shared::redis::AsyncTypedCommands;
use shared::sqlx::{Pool, Postgres, query, query_scalar};
use shared::time::{now_ms, since_epoch};
use shared::tokio;
use tower::ServiceExt;

/// A device id nothing else in the run will use.
fn unique_device_id(name: &str) -> String {
    let nanos = since_epoch().as_nanos();

    format!("test-{name}-{nanos}")
}

async fn state() -> Option<AppState> {
    let (Ok(db_url), Ok(redis_url)) = (
        std::env::var("TEST_DATABASE_URL"),
        std::env::var("TEST_REDIS_URL"),
    ) else {
        eprintln!("skipped: set TEST_DATABASE_URL and TEST_REDIS_URL to run the api tests");

        return None;
    };

    Some(AppState {
        db_pool: init_pg(&db_url)
            .await
            .expect("the database should be ready"),
        redis: init_redis(&redis_url)
            .await
            .expect("the cache should be ready"),
    })
}

macro_rules! state_or_skip {
    () => {
        match state().await {
            Some(state) => state,
            None => return,
        }
    };
}

/// A token whose SHA-256 is known independently of this crate.
///
/// Thirty-two bytes as unpadded base64url, exactly the shape `www` mints. The
/// digest below came from `shasum -a 256` rather than from the code under
/// test, so these agree with the standard rather than merely with themselves -
/// which is the part that decides whether `www` and `ingest` can interoperate
/// at all.
const DEVICE_TOKEN: &str = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8";

const DEVICE_TOKEN_SHA256: &str =
    "ea866a757e4c38babfa8127cbe9a409d3e1f93a00ff1488ff735fcf917afffd0";

async fn register_device(db_pool: &Pool<Postgres>, device_id: &str) {
    register_device_with_hash(db_pool, device_id, Some(DEVICE_TOKEN_SHA256)).await;
}

async fn register_device_with_hash(
    db_pool: &Pool<Postgres>,
    device_id: &str,
    token_hash: Option<&str>,
) {
    query(
        "INSERT INTO known_devices (device_id, name, is_active, token_hash)
         VALUES ($1, $2, TRUE, $3)",
    )
    .bind(device_id)
    .bind("api test")
    .bind(token_hash)
    .execute(db_pool)
    .await
    .expect("the device should be registrable");
}

/// The `error` code out of an RFC 6750 challenge, if it carries one.
fn challenge_error(response: &axum::http::Response<Body>) -> Option<String> {
    let challenge = response
        .headers()
        .get(axum::http::header::WWW_AUTHENTICATE)?
        .to_str()
        .ok()?;

    let after = challenge.split("error=\"").nth(1)?;

    after.split('"').next().map(str::to_string)
}

async fn forget_device(state: &AppState, device_id: &str) {
    for statement in [
        "DELETE FROM telemetry_samples WHERE device_id = $1",
        "DELETE FROM known_devices WHERE device_id = $1",
    ] {
        query(statement)
            .bind(device_id)
            .execute(&state.db_pool)
            .await
            .expect("cleanup should succeed");
    }

    let keys = [
        format!("device_credential:{device_id}"),
        format!("device:live:{device_id}"),
        format!("device:last_seen_written:{device_id}"),
    ];

    for key in keys {
        state.redis.clone().del(&key).await.expect("cleanup");
    }
}

fn upload_request(device_id: Option<&str>, body: &str) -> Request<Body> {
    authenticated_upload_request(device_id, Some(DEVICE_TOKEN), body)
}

fn authenticated_upload_request(
    device_id: Option<&str>,
    token: Option<&str>,
    body: &str,
) -> Request<Body> {
    let mut builder = Request::builder()
        .method("POST")
        .uri("/api/telemetry/upload")
        .header(CONTENT_TYPE, "application/json");

    if let Some(device_id) = device_id {
        builder = builder.header("Device-Id", device_id);
    }

    if let Some(token) = token {
        builder = builder.header(AUTHORIZATION, format!("Bearer {token}"));
    }

    builder
        .header(CONTENT_LENGTH, body.len())
        .body(Body::from(body.to_string()))
        .expect("the request should build")
}

/// Compresses a body the way the device does before uploading it.
fn gzip(body: &str) -> Vec<u8> {
    use std::io::Write;

    use flate2::{Compression, write::GzEncoder};

    let mut encoder = GzEncoder::new(Vec::new(), Compression::default());

    encoder
        .write_all(body.as_bytes())
        .expect("the body should compress");

    encoder.finish().expect("the stream should finish")
}

fn gzipped_upload_request(device_id: &str, body: &str) -> Request<Body> {
    let compressed = gzip(body);

    Request::builder()
        .method("POST")
        .uri("/api/telemetry/upload")
        .header(CONTENT_TYPE, "application/json")
        .header("Content-Encoding", "gzip")
        .header("Device-Id", device_id)
        .header(AUTHORIZATION, format!("Bearer {DEVICE_TOKEN}"))
        .header(CONTENT_LENGTH, compressed.len())
        .body(Body::from(compressed))
        .expect("the request should build")
}

fn batch(timestamp: i64) -> String {
    batch_with(1, timestamp)
}

/// A batch whose sample id is chosen by the caller, so a second upload can
/// carry rows the database has not already stored.
fn batch_with(id: i64, timestamp: i64) -> String {
    format!(
        r#"[{{"id":{id},"event":"telemetry_sample","timestamp":{timestamp},
             "latitude":48.15,"longitude":17.11,"speedKmh":42.5,"bearing":90}}]"#
    )
}

async fn last_seen_at(state: &AppState, device_id: &str) -> Option<String> {
    query_scalar::<_, Option<String>>(
        "SELECT last_seen_at::text FROM known_devices WHERE device_id = $1",
    )
    .bind(device_id)
    .fetch_one(&state.db_pool)
    .await
    .expect("the device row should be readable")
}

async fn stored_samples(state: &AppState, device_id: &str) -> i64 {
    query_scalar::<_, i64>("SELECT COUNT(*) FROM telemetry_samples WHERE device_id = $1")
        .bind(device_id)
        .fetch_one(&state.db_pool)
        .await
        .expect("the count should be readable")
}

#[tokio::test]
async fn health_should_report_that_the_service_is_up() {
    let state = state_or_skip!();

    let response = build_app(state)
        .oneshot(
            Request::builder()
                .uri("/api/health")
                .body(Body::empty())
                .expect("the request should build"),
        )
        .await
        .expect("the service should answer");

    assert_eq!(response.status(), StatusCode::OK);
}

#[tokio::test]
async fn upload_without_a_device_header_should_be_unauthorized() {
    let state = state_or_skip!();

    let response = build_app(state)
        .oneshot(upload_request(None, &batch(1_000)))
        .await
        .expect("the service should answer");

    assert_eq!(response.status(), StatusCode::UNAUTHORIZED);
}

#[tokio::test]
async fn upload_without_a_token_should_be_unauthorized() {
    let state = state_or_skip!();
    let device_id = unique_device_id("tokenless");

    register_device(&state.db_pool, &device_id).await;

    let response = build_app(state.clone())
        .oneshot(authenticated_upload_request(
            Some(&device_id),
            None,
            &batch(1_000),
        ))
        .await
        .expect("the service should answer");

    forget_device(&state, &device_id).await;

    assert_eq!(response.status(), StatusCode::UNAUTHORIZED);
    assert_eq!(
        challenge_error(&response),
        None,
        "RFC 6750 asks for a bare challenge when a request simply carries no credentials"
    );
}

#[tokio::test]
async fn upload_from_an_unknown_device_should_look_exactly_like_a_bad_token() {
    let state = state_or_skip!();
    let device_id = unique_device_id("stranger");

    let response = build_app(state.clone())
        .oneshot(upload_request(Some(&device_id), &batch(1_000)))
        .await
        .expect("the service should answer");

    forget_device(&state, &device_id).await;

    /*
     * Not "forbidden", which is what this used to answer. Saying that an
     * identity is unknown would make the endpoint a way of discovering which
     * identities exist, so it is answered exactly as a rejected token is.
     */
    assert_eq!(response.status(), StatusCode::UNAUTHORIZED);
    assert_eq!(challenge_error(&response).as_deref(), Some("invalid_token"));
}

#[tokio::test]
async fn upload_with_the_wrong_token_should_be_unauthorized() {
    let state = state_or_skip!();
    let device_id = unique_device_id("wrong-token");

    register_device(&state.db_pool, &device_id).await;

    let response = build_app(state.clone())
        .oneshot(authenticated_upload_request(
            Some(&device_id),
            Some("PGqBHmJmMDNmZTQ4LTk5ZDgtNGYyYy1hMWMzLTAwMDAwMDAwMA"),
            &batch(1_000),
        ))
        .await
        .expect("the service should answer");

    forget_device(&state, &device_id).await;

    assert_eq!(response.status(), StatusCode::UNAUTHORIZED);
    assert_eq!(challenge_error(&response).as_deref(), Some("invalid_token"));
}

#[tokio::test]
async fn a_device_registered_before_tokens_existed_should_not_be_able_to_upload() {
    let state = state_or_skip!();
    let device_id = unique_device_id("hashless");

    // The shape every row had before this scheme: an identity and nothing else.
    register_device_with_hash(&state.db_pool, &device_id, None).await;

    let response = build_app(state.clone())
        .oneshot(upload_request(Some(&device_id), &batch(1_000)))
        .await
        .expect("the service should answer");

    forget_device(&state, &device_id).await;

    assert_eq!(
        response.status(),
        StatusCode::UNAUTHORIZED,
        "a row with no hash must not fall back to accepting the identity alone"
    );
}

#[tokio::test]
async fn upload_of_malformed_json_should_be_rejected() {
    let state = state_or_skip!();
    let device_id = unique_device_id("malformed");

    register_device(&state.db_pool, &device_id).await;

    let response = build_app(state.clone())
        .oneshot(upload_request(Some(&device_id), r#"[{"id": 1,"#))
        .await
        .expect("the service should answer");

    forget_device(&state, &device_id).await;

    assert_eq!(response.status(), StatusCode::BAD_REQUEST);
}

#[tokio::test]
async fn upload_of_the_wrong_json_shape_should_be_unprocessable() {
    let state = state_or_skip!();
    let device_id = unique_device_id("wrong-shape");

    register_device(&state.db_pool, &device_id).await;

    // Valid JSON, but an object where the upload is an array of samples.
    let response = build_app(state.clone())
        .oneshot(upload_request(Some(&device_id), r#"{"id": 1}"#))
        .await
        .expect("the service should answer");

    forget_device(&state, &device_id).await;

    assert_eq!(
        response.status(),
        StatusCode::UNPROCESSABLE_ENTITY,
        "a device sending the wrong shape should hear something other than \"malformed\""
    );
}

#[tokio::test]
async fn upload_from_a_known_device_should_be_stored_and_announced() {
    let state = state_or_skip!();
    let device_id = unique_device_id("accepted");

    register_device(&state.db_pool, &device_id).await;

    let timestamp = now_ms();

    let response = build_app(state.clone())
        .oneshot(upload_request(Some(&device_id), &batch(timestamp)))
        .await
        .expect("the service should answer");

    let stored =
        query_scalar::<_, i64>("SELECT COUNT(*) FROM telemetry_samples WHERE device_id = $1")
            .bind(&device_id)
            .fetch_one(&state.db_pool)
            .await
            .expect("the count should be readable");

    let live: Option<String> = state
        .redis
        .clone()
        .get(format!("device:live:{device_id}"))
        .await
        .expect("the live key should be readable");

    let was_seen: Option<bool> =
        query_scalar("SELECT last_seen_at IS NOT NULL FROM known_devices WHERE device_id = $1")
            .bind(&device_id)
            .fetch_optional(&state.db_pool)
            .await
            .expect("the device row should be readable");

    forget_device(&state, &device_id).await;

    assert_eq!(response.status(), StatusCode::OK);
    assert_eq!(stored, 1, "the sample should be in the database");
    assert!(
        live.is_some_and(|snapshot| snapshot.contains("48.15")),
        "the position should have been published as the live snapshot"
    );
    assert_eq!(
        was_seen,
        Some(true),
        "the device should have been marked as seen"
    );
}

#[tokio::test]
async fn a_gzipped_upload_should_be_accepted() {
    let state = state_or_skip!();
    let device_id = unique_device_id("gzipped");

    register_device(&state.db_pool, &device_id).await;

    let response = build_app(state.clone())
        .oneshot(gzipped_upload_request(&device_id, &batch(1_000)))
        .await
        .expect("the service should answer");

    let stored =
        query_scalar::<_, i64>("SELECT COUNT(*) FROM telemetry_samples WHERE device_id = $1")
            .bind(&device_id)
            .fetch_one(&state.db_pool)
            .await
            .expect("the count should be readable");

    forget_device(&state, &device_id).await;

    assert_eq!(
        (response.status(), stored),
        (StatusCode::OK, 1),
        "devices always gzip their uploads, so this is the ordinary path"
    );
}

#[tokio::test]
async fn an_upload_beyond_the_wire_limit_should_be_rejected() {
    let state = state_or_skip!();
    let device_id = unique_device_id("too-large");

    register_device(&state.db_pool, &device_id).await;

    // Uncompressed, so it is the limit on the wire that has to stop this.
    let oversized = "x".repeat(5 * 1024 * 1024);

    let response = build_app(state.clone())
        .oneshot(upload_request(Some(&device_id), &oversized))
        .await
        .expect("the service should answer");

    forget_device(&state, &device_id).await;

    assert_eq!(response.status(), StatusCode::PAYLOAD_TOO_LARGE);
}

#[tokio::test]
async fn a_body_that_expands_beyond_the_memory_limit_should_be_rejected() {
    let state = state_or_skip!();
    let device_id = unique_device_id("bomb");

    register_device(&state.db_pool, &device_id).await;

    /*
     * Small on the wire, far too large once decompressed: exactly what the
     * limit inside the decompression layer exists for. Telemetry repeats
     * itself, so this needs no exotic construction to compress by orders of
     * magnitude - a real upload already manages 28x.
     */
    let expanded = batch(1_000).repeat(400_000);
    let compressed = gzip(&expanded);

    assert!(
        expanded.len() > 32 * 1024 * 1024 && compressed.len() < 4 * 1024 * 1024,
        "the payload must pass the wire limit and fail the memory one,          got {} bytes compressed and {} bytes expanded",
        compressed.len(),
        expanded.len()
    );

    let response = build_app(state.clone())
        .oneshot(gzipped_upload_request(&device_id, &expanded))
        .await
        .expect("the service should answer");

    forget_device(&state, &device_id).await;

    assert_eq!(response.status(), StatusCode::PAYLOAD_TOO_LARGE);
}

#[tokio::test]
async fn upload_from_a_deactivated_device_should_be_forbidden() {
    let state = state_or_skip!();
    let device_id = unique_device_id("deactivated");

    register_device(&state.db_pool, &device_id).await;

    query("UPDATE known_devices SET is_active = FALSE WHERE device_id = $1")
        .bind(&device_id)
        .execute(&state.db_pool)
        .await
        .expect("the device should be deactivable");

    let response = build_app(state.clone())
        .oneshot(upload_request(Some(&device_id), &batch(now_ms())))
        .await
        .expect("the service should answer");

    forget_device(&state, &device_id).await;

    assert_eq!(
        response.status(),
        StatusCode::FORBIDDEN,
        "a retired vehicle stops uploading even though its token is still good"
    );

    /*
     * Forbidden rather than unauthorized, and only reachable by a request that
     * presented the right token: the phone needs to tell "re-pair me" apart
     * from "this vehicle is retired", and those want different answers.
     */
    assert_eq!(
        challenge_error(&response).as_deref(),
        Some("insufficient_scope")
    );
}

#[tokio::test]
async fn a_second_upload_should_not_rewrite_last_seen_immediately() {
    let state = state_or_skip!();
    let device_id = unique_device_id("write-interval");

    register_device(&state.db_pool, &device_id).await;

    build_app(state.clone())
        .oneshot(upload_request(Some(&device_id), &batch_with(1, now_ms())))
        .await
        .expect("the service should answer");

    let after_first = last_seen_at(&state, &device_id).await;

    build_app(state.clone())
        .oneshot(upload_request(Some(&device_id), &batch_with(2, now_ms())))
        .await
        .expect("the service should answer");

    let after_second = last_seen_at(&state, &device_id).await;

    forget_device(&state, &device_id).await;

    assert!(
        after_first.is_some(),
        "the first upload should have stamped last_seen_at"
    );

    assert_eq!(
        after_second, after_first,
        "the write interval should keep a second upload from writing the column again"
    );
}

#[tokio::test]
async fn an_older_batch_should_not_move_the_live_snapshot_backwards() {
    let state = state_or_skip!();
    let device_id = unique_device_id("out-of-order");

    register_device(&state.db_pool, &device_id).await;

    let newest = now_ms();
    let older = newest - 60_000;

    build_app(state.clone())
        .oneshot(upload_request(Some(&device_id), &batch_with(1, newest)))
        .await
        .expect("the service should answer");

    // A delayed backlog upload, carrying a sample the database has not seen.
    build_app(state.clone())
        .oneshot(upload_request(Some(&device_id), &batch_with(2, older)))
        .await
        .expect("the service should answer");

    let snapshot: Option<String> = state
        .redis
        .clone()
        .get(format!("device:live:{device_id}"))
        .await
        .expect("the live key should be readable");

    let stored = stored_samples(&state, &device_id).await;

    forget_device(&state, &device_id).await;

    assert_eq!(
        stored, 2,
        "both uploads must have been stored, otherwise the second never reached the announcement"
    );

    assert!(
        snapshot.is_some_and(|snapshot| snapshot.contains(&format!(r#""timestamp":{newest}"#))),
        "a delayed upload must not drag the live position back in time"
    );
}
