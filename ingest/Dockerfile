# https://github.com/LukeMathWalker/cargo-chef
# Leveraging the pre-built Docker images with 
# cargo-chef and the Rust toolchain
FROM lukemathwalker/cargo-chef:latest-rust-slim-trixie AS chef
WORKDIR /build
# RUN apt-get update
# RUN apt-get install libwebkit2gtk-4.1-dev build-essential curl wget file libxdo-dev libssl-dev libayatana-appindicator3-dev librsvg2-dev g++ pkg-config libx11-dev libasound2-dev libudev-dev libxkbcommon-x11-0 libwayland-dev libxkbcommon-dev mesa-vulkan-drivers -y

FROM chef AS planner
COPY . .
RUN cargo chef prepare --recipe-path recipe.json

FROM chef AS builder 
COPY --from=planner /build/recipe.json recipe.json
RUN cargo chef cook --release --recipe-path recipe.json

FROM builder AS final
COPY . .
RUN cargo build --bin ingest --release --locked

FROM alpine AS tools
RUN apk add --no-cache curl

FROM gcr.io/distroless/cc-debian13

COPY --from=tools /usr/bin/curl /usr/bin/curl
COPY --from=final /build/target/release/ingest /app/ingest
ENTRYPOINT ["/app/ingest"]