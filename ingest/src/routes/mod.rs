use axum::{
    Router, middleware,
    routing::{get, post},
};

use crate::{AppState, helpers::middleware::require_known_device};

mod health;
mod telemetry;

pub fn router(app_state: AppState) -> Router<AppState> {
    let public_routes = Router::new().route("/health", get(health::health_check));

    let protected_routes = Router::new()
        .route("/telemetry/upload", post(telemetry::upload))
        .route_layer(middleware::from_fn_with_state(
            app_state,
            require_known_device,
        ));

    Router::new().nest("/api", public_routes.merge(protected_routes))
}
