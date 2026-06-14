use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TelemetrySample {
    pub id: i64,
    pub event: String,
    pub timestamp: i64,
    pub payload: Option<String>,

    // Power
    pub charging: Option<bool>,
    #[serde(rename = "powerSource")]
    pub power_source: Option<String>,

    // GPS
    pub latitude: Option<f64>,
    pub longitude: Option<f64>,
    pub altitude: Option<f64>,
    #[serde(rename = "speedMps")]
    pub speed_mps: Option<f32>,
    #[serde(rename = "speedKmh")]
    pub speed_kmh: Option<f32>,
    pub bearing: Option<f32>,
    #[serde(rename = "accuracyM")]
    pub accuracy_m: Option<f32>,
    pub provider: Option<String>,

    // Accelerometer
    #[serde(rename = "accelX")]
    pub accel_x: Option<f32>,
    #[serde(rename = "accelY")]
    pub accel_y: Option<f32>,
    #[serde(rename = "accelZ")]
    pub accel_z: Option<f32>,
    #[serde(rename = "accelAccuracy")]
    pub accel_accuracy: Option<i32>,
    #[serde(rename = "accelAccuracyLabel")]
    pub accel_accuracy_label: Option<String>,

    // Gyroscope
    #[serde(rename = "gyroX")]
    pub gyro_x: Option<f32>,
    #[serde(rename = "gyroY")]
    pub gyro_y: Option<f32>,
    #[serde(rename = "gyroZ")]
    pub gyro_z: Option<f32>,
    #[serde(rename = "gyroAccuracy")]
    pub gyro_accuracy: Option<i32>,
    #[serde(rename = "gyroAccuracyLabel")]
    pub gyro_accuracy_label: Option<String>,

    // Magnetometer
    #[serde(rename = "magX")]
    pub mag_x: Option<f32>,
    #[serde(rename = "magY")]
    pub mag_y: Option<f32>,
    #[serde(rename = "magZ")]
    pub mag_z: Option<f32>,
    #[serde(rename = "magnetAccuracy")]
    pub magnet_accuracy: Option<i32>,
    #[serde(rename = "magnetAccuracyLabel")]
    pub magnet_accuracy_label: Option<String>,

    #[serde(rename = "headingDeg")]
    pub heading_deg: Option<f32>,
}
