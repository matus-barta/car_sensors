pub async fn init_pg(db_url: String) -> sqlx::Pool<sqlx::Postgres> {
    let conn = sqlx::postgres::PgPoolOptions::new()
        .max_connections(20)
        .connect(&db_url)
        .await
        .expect("Unable to connect to DB");

    tracing::info!("Connected to Postgres");

    return conn;
}
