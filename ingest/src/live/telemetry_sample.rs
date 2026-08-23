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
    let Some(sample) = newest_located_sample(samples) else {
        return;
    };

    let key = live_sample_key(device_id);

    /*
     * Uploads are store and forward: a batch can carry hours of history and can
     * reach us after a newer one already has. Announcing it unguarded would drag
     * the map marker backwards in time.
     */
    let is_stale = get_key::<LiveSample>(&state.redis, &key)
        .await
        .is_some_and(|stored| stored.timestamp >= sample.timestamp);

    if is_stale {
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

    set_key_w_ttl(&state.redis, &key, &live, LIVE_SAMPLE_TTL_SECS).await;
    publish(&state.redis, LIVE_SAMPLE_CHANNEL, &live).await;
}

/// The newest sample carrying a position.
///
/// A sample without coordinates cannot move a marker, and the batch holds plenty
/// of them: the device merges its sensors into a row every 500 ms whether or not
/// a location arrived in that window.
fn newest_located_sample(samples: &[TelemetrySample]) -> Option<&TelemetrySample> {
    samples
        .iter()
        .filter(|sample| sample.latitude.is_some() && sample.longitude.is_some())
        .max_by_key(|sample| sample.timestamp)
}

#[cfg(test)]
mod tests {
    use super::*;

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

        let newest = newest_located_sample(&samples).expect("a located sample should be found");

        assert_eq!(newest.timestamp, 3_000);
    }

    #[test]
    fn newest_located_sample_should_ignore_samples_without_coordinates() {
        let samples = [sample(1_000, Some((48.1, 17.1))), sample(9_000, None)];

        let newest = newest_located_sample(&samples).expect("a located sample should be found");

        assert_eq!(newest.timestamp, 1_000);
    }

    #[test]
    fn newest_located_sample_should_return_none_without_any_position() {
        let samples = [sample(1_000, None), sample(2_000, None)];

        assert!(newest_located_sample(&samples).is_none());
    }

    #[test]
    fn live_sample_key_should_be_namespaced_per_device() {
        assert_eq!(live_sample_key("car-1"), "device:live:car-1");
    }
}
