use redis::AsyncTypedCommands;
use redis::aio::ConnectionManager;

/// Anything that stops a cache operation from completing.
///
/// Callers decide what a failure means for them: falling back to the database,
/// logging and carrying on, or giving up. That decision does not belong to a
/// library, so nothing here swallows an error on their behalf.
#[derive(Debug, thiserror::Error)]
pub enum CacheError {
    #[error("cache is unreachable: {0}")]
    Unreachable(#[from] redis::RedisError),

    #[error("cached value could not be converted: {0}")]
    Payload(#[from] serde_json::Error),
}

/// Connects to Valkey, or any Redis-compatible server.
///
/// A [`ConnectionManager`] reconnects on its own after the server goes away,
/// which a plain multiplexed connection does not - without it a restart of the
/// cache would leave this process failing every command until it is restarted
/// too. It is cheap to clone and multiplexes concurrent requests over a single
/// socket, so give each task its own clone rather than sharing one behind a
/// lock, which would serialise every command the service issues.
pub async fn init_redis(redis_url: &str) -> Result<ConnectionManager, redis::RedisError> {
    let client = redis::Client::open(redis_url)?;
    let connection = ConnectionManager::new(client).await?;

    tracing::info!("Connected to Redis");

    Ok(connection)
}

/// Reads `key` and converts it into a `T`.
///
/// `Ok(None)` means the key is not there. An error means the value could not be
/// obtained at all - the cache is unreachable, or what it held is no longer a
/// `T` because the type has changed since it was written.
pub async fn get_key<T>(redis: &ConnectionManager, key: &str) -> Result<Option<T>, CacheError>
where
    T: for<'a> serde::Deserialize<'a>,
{
    let mut redis = redis.clone();

    let Some(data) = redis.get(key).await? else {
        tracing::debug!("Cache miss - key: {}", key);

        return Ok(None);
    };

    tracing::debug!("Cache hit - key: {}", key);

    Ok(Some(serde_json::from_str(&data)?))
}

/// Stores `value` as JSON under `key`, to be forgotten after `ttl_secs`.
pub async fn set_key_w_ttl<T>(
    redis: &ConnectionManager,
    key: &str,
    value: &T,
    ttl_secs: u32,
) -> Result<(), CacheError>
where
    T: serde::Serialize,
{
    let mut redis = redis.clone();
    let json = serde_json::to_string(value)?;

    redis.set_ex(key, json, ttl_secs.into()).await?;

    tracing::debug!("Set cached key: {}", key);

    Ok(())
}

/// Publishes `message` to `channel` as JSON.
///
/// Pub/sub delivery is at most once: a subscriber that is not connected at this
/// moment never sees the message, and nothing is stored for it to catch up on.
/// What is published must therefore be a hint that fresher data exists, never
/// the only copy of it.
pub async fn publish<T>(
    redis: &ConnectionManager,
    channel: &str,
    message: &T,
) -> Result<(), CacheError>
where
    T: serde::Serialize,
{
    let mut redis = redis.clone();
    let json = serde_json::to_string(message)?;

    let receivers = redis.publish(channel, json).await?;

    tracing::debug!(
        "Published on channel: {} - {} receiver(s)",
        channel,
        receivers
    );

    Ok(())
}
