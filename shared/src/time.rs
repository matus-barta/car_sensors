use std::time::{Duration, SystemTime, UNIX_EPOCH};

/// Time elapsed since the Unix epoch.
///
/// A clock standing before 1970 reports zero rather than an error. Every caller
/// is stamping or naming something and none of them has anything better to do
/// with that failure, so the decision is made once, here, instead of being
/// repeated at each call site.
pub fn since_epoch() -> Duration {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
}

/// Milliseconds since the Unix epoch, the unit devices stamp their samples in.
pub fn now_ms() -> i64 {
    since_epoch().as_millis() as i64
}

/// Seconds since the Unix epoch.
pub fn now_secs() -> i64 {
    since_epoch().as_secs() as i64
}
