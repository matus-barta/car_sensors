//! Receives telemetry from devices and stores it.
//!
//! The crate is a library with a thin binary on top so that the router can be
//! built - and driven by tests - without starting a server or reading the
//! environment.

use axum::{Router, extract::DefaultBodyLimit};
use shared::redis::aio::ConnectionManager;
use shared::sqlx::{Pool, Postgres};
use tower_http::{
    decompression::RequestDecompressionLayer, limit::RequestBodyLimitLayer, trace::TraceLayer,
};

mod db;
mod helpers;
mod live;
mod models;
mod routes;

/// The largest upload accepted off the wire, before any decompression.
///
/// Devices gzip their uploads, and telemetry compresses extremely well - a
/// 25,000 sample batch measures 4 MB as JSON and 142 KB once gzipped. A normal
/// upload of 500 samples is around 3 KB. Four mebibytes is therefore roughly
/// thirty times the largest batch this service has ever been given, and still
/// leaves room for a device that has been offline for days.
const MAX_COMPRESSED_BODY_BYTES: usize = 4 * 1024 * 1024;

/// The largest upload accepted once decompressed, which is what reaches memory.
///
/// The limit above cannot bound this on its own: at the compression ratio real
/// telemetry achieves, four mebibytes of gzip expands to over a hundred, and a
/// payload crafted to compress well expands to far more. The JSON extractor
/// buffers the whole body before parsing it, so without a second limit inside
/// the decompression layer a small request could exhaust the process.
///
/// Thirty-two mebibytes of JSON is on the order of two hundred thousand
/// samples - more than a day of continuous recording in a single upload.
const MAX_DECOMPRESSED_BODY_BYTES: usize = 32 * 1024 * 1024;

/// What every handler needs to serve a request.
///
/// Cloned per request, which is why the connection manager is held by value:
/// it is cheap to clone and multiplexes concurrent commands over one socket,
/// so a lock around it would serialise every cache operation in the service.
#[derive(Clone)]
pub struct AppState {
    pub db_pool: Pool<Postgres>,
    pub redis: ConnectionManager,
}

/// Assembles the routes and the middleware stack.
///
/// Each layer wraps the ones added before it, so a request meets them from the
/// bottom of this list upwards: the wire limit first, then decompression, and
/// only then the limit that measures what decompression produced. Reordering
/// them changes which side of the compression each limit counts.
pub fn build_app(app_state: AppState) -> Router {
    Router::new()
        .merge(routes::router(app_state.clone()))
        .layer(RequestBodyLimitLayer::new(MAX_DECOMPRESSED_BODY_BYTES))
        .layer(RequestDecompressionLayer::new())
        .layer(TraceLayer::new_for_http())
        // Superseded by the two limits here, which also cover extractors that
        // never consult axum's own default.
        .layer(DefaultBodyLimit::disable())
        .layer(RequestBodyLimitLayer::new(MAX_COMPRESSED_BODY_BYTES))
        .with_state(app_state)
}
