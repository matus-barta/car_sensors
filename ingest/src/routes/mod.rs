use axum::{
    Router, middleware,
    routing::{get, post},
};
use utoipa::{
    Modify, OpenApi,
    openapi::security::{ApiKey, ApiKeyValue, HttpAuthScheme, HttpBuilder, SecurityScheme},
};

use crate::{
    AppState,
    helpers::middleware::require_known_device,
    models::{device_auth::DEVICE_ID_HEADER, telemetry_sample::TelemetrySample},
};

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

/// The OpenAPI document for the routes above.
///
/// A route added to `router` must be listed in `paths` here as well; the
/// integration tests request every documented path and fail on one the
/// router does not serve.
#[derive(OpenApi)]
#[openapi(
    info(
        title = "car_sensors ingest",
        description = "Receives telemetry from devices and stores it. Generated from the \
            `ingest` source - do not edit the JSON by hand.",
    ),
    paths(health::health_check, telemetry::upload),
    components(schemas(TelemetrySample)),
    modifiers(&DeviceCredentials),
    tags(
        (name = "health", description = "Liveness"),
        (name = "telemetry", description = "Uploads from paired devices"),
    ),
)]
pub(crate) struct ApiDoc;

/// Declares the two halves of a device's credentials.
///
/// A device presents both on every protected request. `Device-Id` names the
/// device and is not a secret; the bearer token proves the request came from
/// it. Only the token's SHA-256 is stored.
struct DeviceCredentials;

impl Modify for DeviceCredentials {
    fn modify(&self, openapi: &mut utoipa::openapi::OpenApi) {
        let components = openapi.components.get_or_insert_with(Default::default);

        components.add_security_scheme(
            "device_id",
            SecurityScheme::ApiKey(ApiKey::Header(ApiKeyValue::with_description(
                DEVICE_ID_HEADER,
                "The device's id, as registered in the web application. Not a secret.",
            ))),
        );

        components.add_security_scheme(
            "device_token",
            SecurityScheme::Http(
                HttpBuilder::new()
                    .scheme(HttpAuthScheme::Bearer)
                    .description(Some(
                        "The token issued when the device was paired: 32 random bytes as \
                         unpadded base64url.",
                    ))
                    .build(),
            ),
        );
    }
}
