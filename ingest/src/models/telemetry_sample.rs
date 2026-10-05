use serde::{Deserialize, Serialize};
use utoipa::ToSchema;

/// One row a device recorded: either a periodic reading or an event.
///
/// The device builds this by hand, and leaves out any field it has no value
/// for rather than sending `null`, so every field but the first three is
/// optional. A periodic reading carries whichever sensors had a value; an event
/// carries its details in `payload` and usually no readings at all.
#[derive(Debug, Clone, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "camelCase")]
#[schema(examples(json!({
    "id": 48213,
    "event": "telemetry_sample",
    "timestamp": 1_791_000_000_000_i64,
    "charging": true,
    "powerSource": "USB",
    "latitude": 48.148_6,
    "longitude": 17.107_7,
    "altitude": 152.3,
    "speedMps": 13.9,
    "speedKmh": 50.04,
    "bearing": 87.5,
    "accuracyM": 4.8,
    "provider": "gps",
    "accelX": 0.12,
    "accelY": 0.03,
    "accelZ": 9.79,
    "accelAccuracy": 3,
    "accelAccuracyLabel": "HIGH",
    "headingDeg": 88.0
})))]
pub struct TelemetrySample {
    /// The row's id on the device. Unique per device, not globally: together
    /// with the device it identifies the sample, which is what makes sending
    /// the same batch twice harmless.
    pub id: i64,
    /// `telemetry_sample` for a periodic reading. Anything else names an event,
    /// whose details are in `payload`.
    pub event: String,
    /// When the row was recorded, in milliseconds since the Unix epoch. The
    /// device's wall clock, corrected against GPS time once it has had a fix.
    pub timestamp: i64,
    /// A JSON object, sent as a string, whose shape depends on `event`. Absent
    /// on a periodic reading.
    #[schema(content_media_type = "application/json")]
    pub payload: Option<String>,

    // Power
    /// Whether the device was charging.
    pub charging: Option<bool>,
    /// What the device was plugged into: `AC`, `USB`, `WIRELESS`,
    /// `NOT_CHARGING` or `UNKNOWN`.
    pub power_source: Option<String>,

    // GPS
    /// Degrees, WGS 84.
    pub latitude: Option<f64>,
    /// Degrees, WGS 84.
    pub longitude: Option<f64>,
    /// Metres.
    pub altitude: Option<f64>,
    /// Metres per second.
    pub speed_mps: Option<f32>,
    /// The same speed in kilometres per hour.
    pub speed_kmh: Option<f32>,
    /// Direction of travel in degrees.
    pub bearing: Option<f32>,
    /// Horizontal accuracy radius in metres.
    pub accuracy_m: Option<f32>,
    /// The Android location provider that produced the fix.
    pub provider: Option<String>,

    // Accelerometer
    pub accel_x: Option<f32>,
    pub accel_y: Option<f32>,
    pub accel_z: Option<f32>,
    /// Android's sensor accuracy status.
    pub accel_accuracy: Option<i32>,
    /// `accelAccuracy` as a word: `UNRELIABLE`, `LOW`, `MEDIUM`, `HIGH` or
    /// `UNKNOWN`.
    pub accel_accuracy_label: Option<String>,

    // Gyroscope
    pub gyro_x: Option<f32>,
    pub gyro_y: Option<f32>,
    pub gyro_z: Option<f32>,
    /// Android's sensor accuracy status.
    pub gyro_accuracy: Option<i32>,
    /// `gyroAccuracy` as a word, as for `accelAccuracyLabel`.
    pub gyro_accuracy_label: Option<String>,

    // Magnetometer
    pub mag_x: Option<f32>,
    pub mag_y: Option<f32>,
    pub mag_z: Option<f32>,
    /// Android's sensor accuracy status.
    pub magnet_accuracy: Option<i32>,
    /// `magnetAccuracy` as a word, as for `accelAccuracyLabel`.
    pub magnet_accuracy_label: Option<String>,

    // Barometer
    /// Hectopascals.
    pub pressure_hpa: Option<f32>,
    /// Android's sensor accuracy status.
    pub pressure_accuracy: Option<i32>,
    /// `pressureAccuracy` as a word, as for `accelAccuracyLabel`.
    pub pressure_accuracy_label: Option<String>,

    /// Compass heading in degrees.
    pub heading_deg: Option<f32>,
}
