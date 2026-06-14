use axum::{
    Router, middleware,
    routing::{get, post},
};

use crate::{AppState, helpers::middleware::require_known_device};

mod device;
mod health;
mod telemetry;

pub fn router(app_state: AppState) -> Router<AppState> {
    let public_routes = Router::new()
        .route("/health", get(health::health_check))
        .route("/device/add/{device_id}", get(device::add_device));

    let protected_routes = Router::new()
        .route("/telemetry/upload", post(telemetry::upload))
        .route_layer(middleware::from_fn_with_state(
            app_state,
            require_known_device,
        ));

    public_routes.merge(protected_routes)
}
