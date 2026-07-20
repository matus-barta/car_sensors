use redis::AsyncTypedCommands;
use redis::aio::MultiplexedConnection;
use std::sync::Arc;

pub async fn init_redis(redis_url: String) -> MultiplexedConnection {
    let client = redis::Client::open(redis_url).expect("unable to open Redis connection");
    let conn = client
        .get_multiplexed_async_connection()
        .await
        .expect("cant get Multiplexed connection");

    tracing::info!("Connected to Redis");

    return conn;
}

pub async fn get_key<T>(
    redis_conn: &Arc<tokio::sync::Mutex<MultiplexedConnection>>,
    key: &String,
) -> Option<T>
where
    T: for<'a> serde::Deserialize<'a>,
{
    let mut redis = redis_conn.lock().await;
    let redis_response = redis.get(&key).await;

    match redis_response {
        Ok(data) => match data {
            Some(data) => {
                tracing::debug!("Cache hit - key: {}", &key);
                match serde_json::from_str(&data) {
                    //FIXME: ??? we are deserializing the data and the serializing again,
                    Ok(model) => Some(model), //the deserialization makes sure we get correct data but it may be some slowdown because it it
                    Err(e) => {
                        cache_error(e);
                        None
                    }
                }
            }
            None => {
                tracing::debug!("Cache miss - key: {}", &key);
                None
            }
        },
        Err(e) => {
            cache_error(e);
            None
        }
    }
}

pub async fn set_key_w_ttl<T>(
    redis_conn: &Arc<tokio::sync::Mutex<MultiplexedConnection>>,
    key: &String,
    response: &T,
    ttl: u32,
) where
    T: serde::Serialize,
{
    let mut redis = redis_conn.lock().await;

    match serde_json::to_string(&response) {
        Ok(json) => match redis.set_ex(&key, json, ttl.into()).await {
            Ok(_) => tracing::debug!("Set cached key: {}", &key),
            Err(e) => cache_error(e),
        },
        Err(e) => cache_error(e),
    };
}

fn cache_error<E>(err: E)
where
    E: std::error::Error,
{
    tracing::error!("{}", err.to_string());
}
