use sqlx::{Pool, Postgres, migrate::Migrator};

static MIGRATOR: Migrator = sqlx::migrate!("../db/migrations");

pub async fn init_pg(db_url: String) -> Pool<Postgres> {
    let pool = sqlx::postgres::PgPoolOptions::new()
        .max_connections(20)
        .connect(&db_url)
        .await
        .expect("Unable to connect to database");

    tracing::info!("Connected to Postgres");

    MIGRATOR
        .run(&pool)
        .await
        .expect("Unable to apply database migrations");

    tracing::info!("Database migrations are up to date");

    pool
}
