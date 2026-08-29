use sqlx::{Pool, Postgres, migrate::Migrator};

static MIGRATOR: Migrator = sqlx::migrate!("../db/migrations");

/// Anything that stops the database from being made ready to use.
#[derive(Debug, thiserror::Error)]
pub enum PgInitError {
    #[error("could not connect to Postgres: {0}")]
    Connect(#[source] sqlx::Error),

    #[error("could not apply database migrations: {0}")]
    Migrate(#[from] sqlx::migrate::MigrateError),
}

/// Connects to Postgres and brings the schema up to date.
///
/// Returns rather than panics so that the caller stays in charge of what a
/// failed start means for it - a service will usually want to give up loudly,
/// but that is the binary's decision to make, not this crate's.
pub async fn init_pg(db_url: &str) -> Result<Pool<Postgres>, PgInitError> {
    let pool = sqlx::postgres::PgPoolOptions::new()
        .max_connections(20)
        .connect(db_url)
        .await
        .map_err(PgInitError::Connect)?;

    tracing::info!("Connected to Postgres");

    MIGRATOR.run(&pool).await?;

    tracing::info!("Database migrations are up to date");

    Ok(pool)
}
