//! What a device must present to upload, and what it is told when it cannot.
//!
//! The identity and the credential are deliberately separate. `Device-Id`
//! names the row; the bearer token proves the request came from the phone that
//! row belongs to. Rotating the token therefore withdraws a credential without
//! disturbing the identity every stored sample is filed under.

use std::fmt::Write as _;

use axum::{
    http::{HeaderMap, HeaderValue, StatusCode, header},
    response::{IntoResponse, Response},
};
use sha2::{Digest, Sha256};
use subtle::ConstantTimeEq;

/// The header naming the device.
///
/// Spelled without the `X-` prefix that RFC 6648 deprecated for new headers.
pub const DEVICE_ID_HEADER: &str = "Device-Id";

/// The identity a request authenticated as, handed to the route behind the
/// middleware.
#[derive(Debug, Clone)]
pub struct KnownDeviceId(pub String);

/// What `known_devices` holds about a device's right to upload.
///
/// Cached as a unit, because caching only "this device exists" would let a hit
/// answer the request without the token ever being compared.
#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct DeviceCredential {
    /// Lowercase hex SHA-256 of the device's token.
    ///
    /// `None` for a row registered before tokens existed. Such a device cannot
    /// authenticate at all until one is minted for it, which is deliberate:
    /// accepting the identity alone is the hole this scheme closes.
    pub token_hash: Option<String>,

    pub is_active: bool,
}

impl DeviceCredential {
    /// Whether `presented` is this device's token.
    ///
    /// A plain SHA-256 rather than a password hash. Argon2 and bcrypt are slow
    /// on purpose because passwords carry little entropy; a 256-bit random
    /// token has plenty, and the slowness would be paid on every upload.
    pub fn accepts(&self, presented: &str) -> bool {
        let Some(stored) = self.token_hash.as_deref() else {
            return false;
        };

        /*
         * Constant time, though it is worth being honest that this is belt and
         * braces rather than load-bearing. Both sides are digests, and a timing
         * oracle on a digest cannot be walked: producing a token whose SHA-256
         * shares a longer prefix with the stored one means finding a preimage,
         * which is the whole strength of the hash. Comparing hashes with `==`
         * would be defensible for exactly that reason.
         *
         * It stays because it costs nothing - `subtle` is already in the tree
         * as a `rustls` dependency, at this same version - and because the
         * argument above quietly stops being true if anyone ever compares the
         * token itself here. `ct_eq` on slices also answers false for differing
         * lengths, so a corrupted row cannot panic.
         */
        hash_token(presented).as_bytes().ct_eq(stored.as_bytes()).into()
    }
}

/// Renders a token into the form `known_devices.token_hash` stores.
///
/// The hash covers the token exactly as it arrived on the wire, so its
/// encoding is part of what is hashed - `www` mints unpadded base64url and
/// both sides must agree on that spelling or nothing will ever match.
pub fn hash_token(token: &str) -> String {
    let digest = Sha256::digest(token.as_bytes());

    let mut hex = String::with_capacity(digest.len() * 2);

    for byte in digest {
        // Writing into a String cannot fail, so there is nothing to handle.
        let _ = write!(hex, "{byte:02x}");
    }

    hex
}

/// Reads the bearer token out of an `Authorization` header.
///
/// `None` covers every way of not presenting one, including another scheme
/// entirely - RFC 6750 section 3 treats an unsupported authentication method
/// as a request that simply lacks credentials rather than as a bad token.
pub fn bearer_token(headers: &HeaderMap) -> Option<&str> {
    let value = headers.get(header::AUTHORIZATION)?.to_str().ok()?;
    let (scheme, token) = value.split_once(' ')?;

    // RFC 7235 makes the scheme case-insensitive.
    if !scheme.eq_ignore_ascii_case("Bearer") {
        return None;
    }

    let token = token.trim();

    (!token.is_empty()).then_some(token)
}

/// Why a request was refused, in the shape RFC 6750 section 3 describes.
///
/// The distinctions matter to the phone rather than to a person reading a log:
/// a rejected credential is something re-pairing fixes, while a deactivated
/// device never will be, and the two used to be indistinguishable.
#[derive(Debug, Clone, Copy)]
pub enum DeviceAuthRejection {
    /// Nothing usable was presented. The challenge carries no error code,
    /// which is what section 3 asks for when a request lacks credentials.
    MissingCredentials,

    /// A token was presented and was not accepted - wrong, withdrawn, or
    /// belonging to an identity that does not exist.
    InvalidToken,

    /// The credential is good, but this device may no longer upload.
    Deactivated,

    /// The lookup itself could not be completed.
    Unavailable,
}

impl DeviceAuthRejection {
    fn challenge(self) -> Option<&'static str> {
        match self {
            Self::MissingCredentials => Some(r#"Bearer realm="telemetry""#),

            Self::InvalidToken => Some(
                r#"Bearer realm="telemetry", error="invalid_token", "#,
            ),

            Self::Deactivated => Some(
                r#"Bearer realm="telemetry", error="insufficient_scope", "#,
            ),

            Self::Unavailable => None,
        }
    }

    fn description(self) -> Option<&'static str> {
        match self {
            Self::MissingCredentials | Self::Unavailable => None,
            Self::InvalidToken => Some(r#"error_description="the device token was not accepted""#),
            Self::Deactivated => Some(r#"error_description="the device has been deactivated""#),
        }
    }

    fn status(self) -> StatusCode {
        match self {
            Self::MissingCredentials | Self::InvalidToken => StatusCode::UNAUTHORIZED,
            Self::Deactivated => StatusCode::FORBIDDEN,
            Self::Unavailable => StatusCode::INTERNAL_SERVER_ERROR,
        }
    }
}

impl IntoResponse for DeviceAuthRejection {
    fn into_response(self) -> Response {
        let mut response = self.status().into_response();

        let Some(challenge) = self.challenge() else {
            return response;
        };

        let header_value = match self.description() {
            Some(description) => format!("{challenge}{description}"),
            None => challenge.to_string(),
        };

        /*
         * Every challenge above is a literal made of characters a header may
         * carry, so this cannot fail; dropping it rather than panicking keeps
         * a refusal a refusal even if that stops being true.
         */
        if let Ok(value) = HeaderValue::from_str(&header_value) {
            response
                .headers_mut()
                .insert(header::WWW_AUTHENTICATE, value);
        }

        response
    }
}
