use shared::sqlx::{Pool, Postgres, QueryBuilder};

use crate::models::telemetry_sample::TelemetrySample;

/// How many values each sample binds in the statement below.
const BINDS_PER_SAMPLE: usize = 34;

/// Postgres refuses a statement with more than 65,535 bind parameters, so a
/// batch is inserted in chunks that stay under that ceiling.
///
/// This is not theoretical: the device uploads its backlog in one request, and
/// before chunking anything above roughly 1,900 samples was rejected outright.
/// A rejected upload is retried with the same batch forever, so a single
/// oversized backlog would have stopped that device uploading for good.
const MAX_SAMPLES_PER_STATEMENT: usize = 65_535 / BINDS_PER_SAMPLE;

/// Holds the arithmetic above to its promise at compile time.
const _: () = assert!(MAX_SAMPLES_PER_STATEMENT * BINDS_PER_SAMPLE <= 65_535);

pub async fn insert_telemetry_batch(
    db_pool: &Pool<Postgres>,
    device_id: &str,
    samples: &[TelemetrySample],
) -> Result<u64, shared::sqlx::Error> {
    if samples.is_empty() {
        return Ok(0);
    }

    let mut tx = db_pool.begin().await?;
    let mut inserted = 0;

    // One transaction over every chunk: a partially stored backlog would leave
    // the device believing rows it still holds are safely on the server.
    for chunk in samples.chunks(MAX_SAMPLES_PER_STATEMENT) {
        let mut statement = insert_statement(device_id, chunk);

        inserted += statement.build().execute(&mut *tx).await?.rows_affected();
    }

    tx.commit().await?;

    Ok(inserted)
}

/// Builds a multi-row insert for one chunk of samples.
///
/// `ON CONFLICT DO NOTHING` against the `(device_id, id)` index is what makes a
/// re-uploaded batch harmless: the device may resend anything it did not get an
/// acknowledgement for.
fn insert_statement(device_id: &str, samples: &[TelemetrySample]) -> QueryBuilder<Postgres> {
    // sqlx encodes each bind as it is pushed, so the builder borrows nothing.
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

    builder
}

/// Tests for the insert path.
///
/// They run against a real Postgres, because what is worth checking here - that
/// every column binds, that the unique index actually swallows a re-uploaded
/// batch, that a backlog larger than one statement can hold still goes in -
/// only exists in the database. Point `TEST_DATABASE_URL` at a throwaway
/// database to run them; without one they report that they were skipped and
/// pass.
///
/// ```sh
/// cd tools && docker compose up -d postgres
/// TEST_DATABASE_URL=postgres://postgres:postgres@127.0.0.1:5432/ingest_test cargo test -p ingest
/// ```
#[cfg(test)]
mod tests {
    use std::time::{SystemTime, UNIX_EPOCH};

    use shared::pg::init_pg;
    use shared::sqlx::query_scalar;
    use shared::tokio;

    use super::*;

    /// Every test owns a device id, so tests running side by side and leftovers
    /// from an earlier run cannot decide the outcome.
    fn unique_device_id(name: &str) -> String {
        let nanos = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_nanos();

        format!("test-{name}-{nanos}")
    }

    fn sample(id: i64, timestamp: i64) -> TelemetrySample {
        let json = format!(
            r#"{{"id":{id},"event":"telemetry_sample","timestamp":{timestamp},
                 "latitude":48.1,"longitude":17.1,"speedKmh":42.5,"pressureHpa":1013.2}}"#
        );

        shared::serde_json::from_str(&json).expect("sample json should deserialize")
    }

    async fn stored_rows(db_pool: &Pool<Postgres>, device_id: &str) -> i64 {
        query_scalar::<_, i64>("SELECT COUNT(*) FROM telemetry_samples WHERE device_id = $1")
            .bind(device_id)
            .fetch_one(db_pool)
            .await
            .expect("the count should be readable")
    }

    async fn forget(db_pool: &Pool<Postgres>, device_id: &str) {
        shared::sqlx::query("DELETE FROM telemetry_samples WHERE device_id = $1")
            .bind(device_id)
            .execute(db_pool)
            .await
            .expect("cleanup should succeed");
    }

    macro_rules! pool_or_skip {
        () => {
            match std::env::var("TEST_DATABASE_URL") {
                Ok(url) => init_pg(&url)
                    .await
                    .expect("the database should be reachable"),
                Err(_) => {
                    eprintln!("skipped: set TEST_DATABASE_URL to run the telemetry insert tests");

                    return;
                }
            }
        };
    }

    #[tokio::test]
    async fn insert_telemetry_batch_should_store_every_sample() {
        let db_pool = pool_or_skip!();
        let device_id = unique_device_id("stores-all");

        let samples = [sample(1, 1_000), sample(2, 2_000), sample(3, 3_000)];

        let inserted = insert_telemetry_batch(&db_pool, &device_id, &samples)
            .await
            .expect("the batch should insert");

        let stored = stored_rows(&db_pool, &device_id).await;

        forget(&db_pool, &device_id).await;

        assert_eq!((inserted, stored), (3, 3));
    }

    #[tokio::test]
    async fn insert_telemetry_batch_should_ignore_a_batch_it_already_stored() {
        let db_pool = pool_or_skip!();
        let device_id = unique_device_id("dedupes");

        let samples = [sample(1, 1_000), sample(2, 2_000)];

        insert_telemetry_batch(&db_pool, &device_id, &samples)
            .await
            .expect("the first upload should insert");

        let reinserted = insert_telemetry_batch(&db_pool, &device_id, &samples)
            .await
            .expect("the repeated upload should be accepted");

        let stored = stored_rows(&db_pool, &device_id).await;

        forget(&db_pool, &device_id).await;

        assert_eq!(
            (reinserted, stored),
            (0, 2),
            "a device that re-uploads a batch must not duplicate its rows"
        );
    }

    #[tokio::test]
    async fn insert_telemetry_batch_should_store_a_backlog_larger_than_one_statement() {
        let db_pool = pool_or_skip!();
        let device_id = unique_device_id("large-backlog");

        let count = MAX_SAMPLES_PER_STATEMENT * 2 + 7;

        let samples: Vec<TelemetrySample> = (0..count)
            .map(|index| sample(index as i64, 1_000 + index as i64))
            .collect();

        let inserted = insert_telemetry_batch(&db_pool, &device_id, &samples)
            .await
            .expect("a backlog spanning several statements should insert");

        let stored = stored_rows(&db_pool, &device_id).await;

        forget(&db_pool, &device_id).await;

        assert_eq!((inserted as usize, stored as usize), (count, count));
    }

    #[tokio::test]
    async fn insert_telemetry_batch_should_report_nothing_stored_for_an_empty_batch() {
        let db_pool = pool_or_skip!();

        let inserted = insert_telemetry_batch(&db_pool, "unused", &[])
            .await
            .expect("an empty batch should be accepted");

        assert_eq!(inserted, 0);
    }
}
