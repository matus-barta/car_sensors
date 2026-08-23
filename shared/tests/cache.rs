//! Integration tests for the cache helpers.
//!
//! They talk to a real Valkey, because that is the whole of what these helpers
//! do - anything a mock could prove here would be a test of the mock. Point
//! `TEST_REDIS_URL` (or `REDIS_URL`) at an instance to run them; without one
//! they report that they were skipped and pass, so `cargo test` stays usable on
//! a machine with no infrastructure.
//!
//! ```sh
//! cd tools && docker compose up -d valkey
//! TEST_REDIS_URL=redis://127.0.0.1:6379 cargo test -p shared
//! ```

use std::time::{SystemTime, UNIX_EPOCH};

use serde::{Deserialize, Serialize};
use shared::cache::{CacheError, get_key, init_redis, publish, set_key_w_ttl};
use shared::redis::{self, AsyncTypedCommands};
use tokio::time::{Duration, timeout};

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
struct Payload {
    name: String,
    count: u32,
}

fn redis_url() -> Option<String> {
    std::env::var("TEST_REDIS_URL")
        .or_else(|_| std::env::var("REDIS_URL"))
        .ok()
}

/// Keys are unique per test run so a leftover value from an earlier run, or a
/// test running beside this one, cannot decide the outcome.
fn unique_key(name: &str) -> String {
    let nanos = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_nanos();

    format!("shared-test:{name}:{nanos}")
}

macro_rules! redis_or_skip {
    () => {
        match redis_url() {
            Some(url) => init_redis(&url).await.expect("the cache should connect"),
            None => {
                eprintln!("skipped: set TEST_REDIS_URL to run the cache integration tests");

                return;
            }
        }
    };
}

fn payload() -> Payload {
    Payload {
        name: "car-1".to_string(),
        count: 7,
    }
}

#[tokio::test]
async fn set_key_w_ttl_should_store_a_value_that_get_key_reads_back() {
    let redis = redis_or_skip!();
    let key = unique_key("round-trip");

    set_key_w_ttl(&redis, &key, &payload(), 60)
        .await
        .expect("the value should be stored");

    let read_back: Option<Payload> = get_key(&redis, &key)
        .await
        .expect("the read should succeed");

    redis.clone().del(&key).await.expect("cleanup");

    assert_eq!(read_back, Some(payload()));
}

#[tokio::test]
async fn get_key_should_report_a_miss_for_a_key_that_was_never_set() {
    let redis = redis_or_skip!();

    let missing: Option<Payload> = get_key(&redis, &unique_key("absent"))
        .await
        .expect("a miss is not a failure");

    assert_eq!(missing, None);
}

#[tokio::test]
async fn set_key_w_ttl_should_apply_the_requested_expiry() {
    let redis = redis_or_skip!();
    let key = unique_key("ttl");

    set_key_w_ttl(&redis, &key, &payload(), 60)
        .await
        .expect("the value should be stored");

    let ttl = redis
        .clone()
        .ttl(&key)
        .await
        .expect("ttl should be readable")
        .raw();

    redis.clone().del(&key).await.expect("cleanup");

    // `raw()` reports -1 for a key with no expiry and -2 for a missing one.
    assert!(
        (1..=60).contains(&ttl),
        "expected a positive ttl, got {ttl}"
    );
}

#[tokio::test]
async fn get_key_should_fail_when_the_stored_value_is_not_the_expected_shape() {
    let redis = redis_or_skip!();
    let key = unique_key("garbage");

    redis
        .clone()
        .set(&key, "not json")
        .await
        .expect("the raw value should be storable");

    let read_back = get_key::<Payload>(&redis, &key).await;

    redis.clone().del(&key).await.expect("cleanup");

    assert!(
        matches!(read_back, Err(CacheError::Payload(_))),
        "an unreadable value must be distinguishable from a miss"
    );
}

#[tokio::test]
async fn publish_should_deliver_the_message_to_a_subscriber() {
    let Some(url) = redis_url() else {
        eprintln!("skipped: set TEST_REDIS_URL to run the cache integration tests");

        return;
    };

    let channel = unique_key("channel");
    let client = redis::Client::open(url.clone()).expect("the client should open");

    let mut subscriber = client
        .get_async_pubsub()
        .await
        .expect("the subscriber should connect");

    subscriber
        .subscribe(&channel)
        .await
        .expect("the subscription should be accepted");

    let redis = init_redis(&url).await.expect("the cache should connect");

    publish(&redis, &channel, &payload())
        .await
        .expect("the message should be published");

    let message = timeout(Duration::from_secs(5), async {
        use tokio_stream::StreamExt;

        subscriber.on_message().next().await
    })
    .await
    .expect("the message should arrive before the timeout")
    .expect("the stream should yield a message");

    let received: Payload = shared::serde_json::from_str(
        &message
            .get_payload::<String>()
            .expect("the payload should be a string"),
    )
    .expect("the payload should be the json that was published");

    assert_eq!(received, payload());
}
