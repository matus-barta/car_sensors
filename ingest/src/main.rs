use axum::{Router, extract::DefaultBodyLimit};
use shared::redis::aio::MultiplexedConnection;
use shared::sqlx::{Pool, Postgres};
use shared::tokio;
use std::env;
use std::sync::Arc;
use tower_http::cors::CorsLayer;
use tower_http::decompression::RequestDecompressionLayer;
use tower_http::limit::RequestBodyLimitLayer;
use tower_http::trace::TraceLayer;

use shared::cache::init_redis;
use shared::pg::init_pg;

mod db;
mod helpers;
mod models;
mod routes;

#[tokio::main]
async fn main() {
    rustls::crypto::aws_lc_rs::default_provider()
        .install_default()
        .expect("rustls error");

    const TRACING_LVL: tracing::Level = if cfg!(debug_assertions) {
        tracing::Level::DEBUG
    } else {
        tracing::Level::INFO
    };

    // initialize tracing
    tracing_subscriber::fmt().with_max_level(TRACING_LVL).init();

    let _ = dotenvy::dotenv(); //we try to load .env file we don't care if it fails because is expected if env file is missing the variables itself are initialized

    let server_ip_port = env::var("SERVER_IP_PORT").unwrap_or("0.0.0.0:3000".into());
    let db_url = env::var("DATABASE_URL").expect("Missing DATABASE_URL env var");
    let redis_url = env::var("REDIS_URL").expect("Missing Redis URL env var");

    let app_state = AppState {
        db_pool: init_pg(db_url).await,
        redis: Arc::new(tokio::sync::Mutex::new(init_redis(redis_url).await)),
    };

    // build our application with a route
    let app = Router::new()
        .merge(routes::router(app_state.clone()))
        .layer(RequestDecompressionLayer::new())
        .layer(TraceLayer::new_for_http())
        .layer(DefaultBodyLimit::disable())
        .layer(CorsLayer::permissive()) //TODO: check if needed in prod and if yes research CORS
        .layer(RequestBodyLimitLayer::new(
            250 * 1024 * 1024, /* 250MiB */ //make configurable?
        ))
        .with_state(app_state);

    // run our app with hyper
    let listener = tokio::net::TcpListener::bind(server_ip_port)
        .await
        .expect("Could not initialize TcpListener");

    tracing::info!(
        "Started server - listening on {}",
        listener
            .local_addr()
            .expect("Could not convert listener to local address")
    );

    axum::serve(listener, app)
        .await
        .expect("Could not successfully create server");
}

#[derive(Clone)]
struct AppState {
    // that holds some api specific state
    db_pool: Pool<Postgres>,
    redis: Arc<tokio::sync::Mutex<MultiplexedConnection>>,
}
