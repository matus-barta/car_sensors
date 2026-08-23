use std::env;

use ingest::{AppState, build_app};
use shared::{cache::init_redis, pg::init_pg, tokio};

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
        db_pool: init_pg(&db_url)
            .await
            .expect("Could not initialize Postgres"),
        redis: init_redis(&redis_url)
            .await
            .expect("Could not initialize Redis"),
    };

    let app = build_app(app_state);

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
        .with_graceful_shutdown(shutdown_signal())
        .await
        .expect("Could not successfully create server");
}

/// Resolves when the process is asked to stop.
///
/// Without this a `docker stop` drops connections mid-request, which for an
/// upload means the device retries a batch that may already be half committed.
/// Waiting for in-flight requests costs a moment and avoids that entirely.
async fn shutdown_signal() {
    #[cfg(unix)]
    {
        use tokio::signal::unix::{SignalKind, signal};

        let mut terminate = match signal(SignalKind::terminate()) {
            Ok(signal) => signal,
            Err(error) => {
                tracing::error!("Could not listen for SIGTERM: {}", error);

                return;
            }
        };

        tokio::select! {
            result = tokio::signal::ctrl_c() => {
                if let Err(error) = result {
                    tracing::error!("Could not listen for Ctrl+C: {}", error);
                }
            }
            _ = terminate.recv() => {}
        }
    }

    #[cfg(not(unix))]
    if let Err(error) = tokio::signal::ctrl_c().await {
        tracing::error!("Could not listen for Ctrl+C: {}", error);
    }

    tracing::info!("Shutdown signal received - finishing in-flight requests");
}
