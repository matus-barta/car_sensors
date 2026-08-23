use std::time::{SystemTime, UNIX_EPOCH};

use serde::{Deserialize, Serialize};
use shared::cache::{get_key, publish, set_key_w_ttl};

use crate::{AppState, models::telemetry_sample::TelemetrySample};

/// Channel `www` subscribes to for live positions.
///
/// The channel name, the key below and the shape of `LiveSample` are a contract
/// with `www`, which is written in another language and cannot share these
/// definitions - change them on both sides together.
const LIVE_SAMPLE_CHANNEL: &str = "telemetry:live";

/// Covers the window in which `www` still treats a vehicle as stale rather than
/// offline. Past it a stored position is of no use to anybody, so let it go.
const LIVE_SAMPLE_TTL_SECS: u32 = 15 * 60;

/// How far ahead of the server a device's clock may run before its samples stop
/// counting as live.
///
/// Samples are stamped with `System.currentTimeMillis()` on the phone, and
/// Android takes that clock from the cellular network or NTP rather than from
/// GPS, so a device that has been offline can be wrong. Ordinary skew is small;
/// this only has to catch a clock that is grossly wrong.
const MAX_CLOCK_SKEW_MS: i64 = 5 * 60 * 1000;

/// The newest known position of a device, as `www` receives it.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct LiveSample {
    pub device_id: String,
    pub timestamp: i64,
    pub latitude: f64,
    pub longitude: f64,
    pub altitude: Option<f64>,
    pub speed_kmh: Option<f32>,
    pub bearing: Option<f32>,
    pub accuracy_m: Option<f32>,
    pub charging: Option<bool>,
    pub power_source: Option<String>,
}

fn live_sample_key(device_id: &str) -> String {
    format!("device:live:{device_id}")
}

/// Announces the newest position in `samples` as the device's live location.
///
/// Best effort on purpose: the batch is already committed to Postgres, which
/// holds the durable copy, and Valkey only carries the live view. Every failure
/// here is logged by the cache helpers and leaves the upload successful.
pub(crate) async fn publish_live_sample(
    state: &AppState,
    device_id: &str,
    samples: &[TelemetrySample],
) {
    let Some(sample) = newest_located_sample(samples, now_ms()) else {
        return;
    };

    let key = live_sample_key(device_id);

    /*
     * Uploads are store and forward: a batch can carry hours of history and can
     * reach us after a newer one already has. Announcing it unguarded would drag
     * the map marker backwards in time.
     */
    let stored = get_key::<LiveSample>(&state.redis, &key)
        .await
        .unwrap_or_else(|error| {
            /*
             * Without the stored snapshot there is nothing to compare against.
             * Announcing the sample is the better failure: the position is the
             * newest this upload carries, and a subscriber that receives an
             * out-of-order one corrects itself on the next message.
             */
            tracing::warn!("Could not read the stored live sample: {}", error);

            None
        });

    if stored.is_some_and(|stored| stored.timestamp >= sample.timestamp) {
        tracing::debug!(
            "Skipping live sample for {} - a newer one is stored",
            device_id
        );

        return;
    }

    let live = LiveSample {
        device_id: device_id.to_string(),
        timestamp: sample.timestamp,
        latitude: sample.latitude.unwrap_or_default(),
        longitude: sample.longitude.unwrap_or_default(),
        altitude: sample.altitude,
        speed_kmh: sample.speed_kmh,
        // Mirrors how `www` reads a bearing out of Postgres.
        bearing: sample.bearing.or(sample.heading_deg),
        accuracy_m: sample.accuracy_m,
        charging: sample.charging,
        power_source: sample.power_source.clone(),
    };

    if let Err(error) = set_key_w_ttl(&state.redis, &key, &live, LIVE_SAMPLE_TTL_SECS).await {
        tracing::error!("Could not store the live sample: {}", error);
    }

    if let Err(error) = publish(&state.redis, LIVE_SAMPLE_CHANNEL, &live).await {
        tracing::error!("Could not announce the live sample: {}", error);
    }
}

/// The newest sample carrying a position and a believable timestamp.
///
/// A sample without coordinates cannot move a marker, and the batch holds plenty
/// of them: the device merges its sensors into a row every 500 ms whether or not
/// a location arrived in that window.
///
/// A sample stamped in the future is skipped individually rather than rejecting
/// the upload: the whole batch is already stored in Postgres, which keeps the
/// history whatever the device believed the time was, and only the live position
/// needs a clock that can be trusted. One such sample would otherwise sit in the
/// snapshot and suppress every genuine position until its key expired. Samples
/// stamped in the past are left alone - a device uploads its backlog, so old
/// timestamps are normal and simply lose to newer ones.
fn newest_located_sample(samples: &[TelemetrySample], now_ms: i64) -> Option<&TelemetrySample> {
    let horizon = now_ms.saturating_add(MAX_CLOCK_SKEW_MS);

    samples
        .iter()
        .filter(|sample| sample.latitude.is_some() && sample.longitude.is_some())
        .filter(|sample| {
            if sample.timestamp > horizon {
                tracing::warn!(
                    "Ignoring live sample stamped {} ms ahead of the server clock",
                    sample.timestamp - now_ms
                );

                return false;
            }

            true
        })
        .max_by_key(|sample| sample.timestamp)
}

fn now_ms() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as i64
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Server clock the tests measure their samples against.
    const NOW_MS: i64 = 10_000;

    /// Builds a sample through the real deserializer, which fills every sensor
    /// field this test does not care about with `None`.
    fn sample(timestamp: i64, coordinates: Option<(f64, f64)>) -> TelemetrySample {
        let position = match coordinates {
            Some((latitude, longitude)) => {
                format!(r#","latitude":{latitude},"longitude":{longitude}"#)
            }
            None => String::new(),
        };

        let json = format!(r#"{{"id":1,"event":"tick","timestamp":{timestamp}{position}}}"#);

        shared::serde_json::from_str(&json).expect("sample json should deserialize")
    }

    #[test]
    fn newest_located_sample_should_pick_the_latest_position() {
        let samples = [
            sample(1_000, Some((48.1, 17.1))),
            sample(3_000, Some((48.3, 17.3))),
            sample(2_000, Some((48.2, 17.2))),
        ];

        let newest =
            newest_located_sample(&samples, NOW_MS).expect("a located sample should be found");

        assert_eq!(newest.timestamp, 3_000);
    }

    #[test]
    fn newest_located_sample_should_ignore_samples_without_coordinates() {
        let samples = [sample(1_000, Some((48.1, 17.1))), sample(9_000, None)];

        let newest =
            newest_located_sample(&samples, NOW_MS).expect("a located sample should be found");

        assert_eq!(newest.timestamp, 1_000);
    }

    #[test]
    fn newest_located_sample_should_return_none_without_any_position() {
        let samples = [sample(1_000, None), sample(2_000, None)];

        assert!(newest_located_sample(&samples, NOW_MS).is_none());
    }

    #[test]
    fn newest_located_sample_should_skip_a_sample_from_a_grossly_wrong_clock() {
        let samples = [
            sample(1_000, Some((48.1, 17.1))),
            sample(NOW_MS + MAX_CLOCK_SKEW_MS + 1, Some((48.9, 17.9))),
        ];

        let newest = newest_located_sample(&samples, NOW_MS).expect("a sample should be found");

        assert_eq!(newest.timestamp, 1_000);
    }

    #[test]
    fn newest_located_sample_should_accept_a_sample_inside_the_skew_tolerance() {
        let samples = [
            sample(1_000, Some((48.1, 17.1))),
            sample(NOW_MS + MAX_CLOCK_SKEW_MS, Some((48.9, 17.9))),
        ];

        let newest = newest_located_sample(&samples, NOW_MS).expect("a sample should be found");

        assert_eq!(newest.timestamp, NOW_MS + MAX_CLOCK_SKEW_MS);
    }

    #[test]
    fn live_sample_key_should_be_namespaced_per_device() {
        assert_eq!(live_sample_key("car-1"), "device:live:car-1");
    }
}
