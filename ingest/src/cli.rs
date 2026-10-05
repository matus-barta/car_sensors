use clap::{Parser, Subcommand};

/// Receives telemetry from devices. Runs the server when given no command.
#[derive(Parser)]
#[command(version)]
pub struct Cli {
    #[command(subcommand)]
    pub command: Option<Command>,
}

#[derive(Subcommand)]
pub enum Command {
    /// Exit 0 if the server running on SERVER_IP_PORT reports healthy, 1 otherwise
    Healthcheck,
    /// Print the OpenAPI document for the API to stdout, as JSON
    Openapi,
}
