use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TelemetrySample {
    pub id: i64,
    pub event: String,
    pub timestamp: i64,
    pub payload: Option<String>,

    // Power
    pub charging: Option<bool>,
    pub power_source: Option<String>,

    // GPS
    pub latitude: Option<f64>,
    pub longitude: Option<f64>,
    pub altitude: Option<f64>,
    pub speed_mps: Option<f32>,
    pub speed_kmh: Option<f32>,
    pub bearing: Option<f32>,
    pub accuracy_m: Option<f32>,
    pub provider: Option<String>,

    // Accelerometer
    pub accel_x: Option<f32>,
    pub accel_y: Option<f32>,
    pub accel_z: Option<f32>,
    pub accel_accuracy: Option<i32>,
    pub accel_accuracy_label: Option<String>,

    // Gyroscope
    pub gyro_x: Option<f32>,
    pub gyro_y: Option<f32>,
    pub gyro_z: Option<f32>,
    pub gyro_accuracy: Option<i32>,
    pub gyro_accuracy_label: Option<String>,

    // Magnetometer
    pub mag_x: Option<f32>,
    pub mag_y: Option<f32>,
    pub mag_z: Option<f32>,
    pub magnet_accuracy: Option<i32>,
    pub magnet_accuracy_label: Option<String>,

    // Barometer
    pub pressure_hpa: Option<f32>,
    pub pressure_accuracy: Option<i32>,
    pub pressure_accuracy_label: Option<String>,

    pub heading_deg: Option<f32>,
}
