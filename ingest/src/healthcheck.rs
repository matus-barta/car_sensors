use std::env;
use std::net::{IpAddr, Ipv4Addr, Ipv6Addr, SocketAddr};
use std::time::Duration;

/// Asks the running server whether it is healthy, for the container healthcheck.
///
/// The image is distroless, so there is no curl or wget to do this with, and a
/// binary copied in from another image fails on whatever libc it was linked
/// against. The server's own binary is the one thing the image is sure to run.
pub async fn check() -> bool {
    let _ = dotenvy::dotenv();

    let mut address: SocketAddr = env::var("SERVER_IP_PORT")
        .ok()
        .and_then(|value| value.parse().ok())
        .unwrap_or(SocketAddr::from((Ipv4Addr::UNSPECIFIED, 3000)));

    // A server bound to every interface is still reached over loopback.
    if address.ip().is_unspecified() {
        address.set_ip(match address.ip() {
            IpAddr::V4(_) => Ipv4Addr::LOCALHOST.into(),
            IpAddr::V6(_) => Ipv6Addr::LOCALHOST.into(),
        });
    }

    let Ok(client) = reqwest::Client::builder()
        .timeout(Duration::from_secs(3))
        .no_proxy()
        .build()
    else {
        return false;
    };

    client
        .get(format!("http://{address}/api/health"))
        .send()
        .await
        .is_ok_and(|response| response.status().is_success())
}
