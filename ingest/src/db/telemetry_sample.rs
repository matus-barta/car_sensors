use shared::sqlx::{Pool, Postgres, QueryBuilder};

use crate::models::telemetry_sample::TelemetrySample;

pub async fn insert_telemetry_batch(
    db_pool: &Pool<Postgres>,
    device_id: Option<&str>,
    samples: &[TelemetrySample],
) -> Result<u64, shared::sqlx::Error> {
    if samples.is_empty() {
        return Ok(0);
    }

    let mut tx = db_pool.begin().await?;

    let mut builder: QueryBuilder<Postgres> = QueryBuilder::new(
        r#"
        INSERT INTO telemetry_samples (
            id,
            device_id,
            event,
            timestamp,
            payload,
            charging,
            power_source,
            latitude,
            longitude,
            altitude,
            speed_mps,
            speed_kmh,
            bearing,
            accuracy_m,
            provider,
            accel_x,
            accel_y,
            accel_z,
            accel_accuracy,
            accel_accuracy_label,
            gyro_x,
            gyro_y,
            gyro_z,
            gyro_accuracy,
            gyro_accuracy_label,
            mag_x,
            mag_y,
            mag_z,
            magnet_accuracy,
            magnet_accuracy_label,
            pressure_hpa,
            pressure_accuracy,
            pressure_accuracy_label,
            heading_deg
        )
        "#,
    );

    builder.push_values(samples, |mut b, s| {
        b.push_bind(s.id)
            .push_bind(device_id)
            .push_bind(&s.event)
            .push_bind(s.timestamp)
            .push_bind(&s.payload)
            .push_bind(s.charging)
            .push_bind(&s.power_source)
            .push_bind(s.latitude)
            .push_bind(s.longitude)
            .push_bind(s.altitude)
            .push_bind(s.speed_mps)
            .push_bind(s.speed_kmh)
            .push_bind(s.bearing)
            .push_bind(s.accuracy_m)
            .push_bind(&s.provider)
            .push_bind(s.accel_x)
            .push_bind(s.accel_y)
            .push_bind(s.accel_z)
            .push_bind(s.accel_accuracy)
            .push_bind(&s.accel_accuracy_label)
            .push_bind(s.gyro_x)
            .push_bind(s.gyro_y)
            .push_bind(s.gyro_z)
            .push_bind(s.gyro_accuracy)
            .push_bind(&s.gyro_accuracy_label)
            .push_bind(s.mag_x)
            .push_bind(s.mag_y)
            .push_bind(s.mag_z)
            .push_bind(s.magnet_accuracy)
            .push_bind(&s.magnet_accuracy_label)
            .push_bind(s.pressure_hpa)
            .push_bind(s.pressure_accuracy)
            .push_bind(&s.pressure_accuracy_label)
            .push_bind(s.heading_deg);
    });

    builder.push(
        r#"
    ON CONFLICT (device_id, id) DO NOTHING
    "#,
    );

    let result = builder.build().execute(&mut *tx).await?;
    tx.commit().await?;

    Ok(result.rows_affected())
}

// use std::collections::HashSet;
// pub async fn load_known_devices(db: &Pool<Postgres>) -> Result<HashSet<String>, sqlx::Error> {
//     let rows = sqlx::query_scalar::<_, String>(
//         r#"
//         SELECT device_id
//         FROM known_devices
//         WHERE is_active = TRUE
//         "#,
//     )
//     .fetch_all(db)
//     .await?;

//     Ok(rows.into_iter().collect())
// }
