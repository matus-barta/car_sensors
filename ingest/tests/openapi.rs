//! Keeps `docs/api/openapi.json` honest.
//!
//! The document is generated from the `ingest` source and committed, so that
//! it can be read - by people, by the documentation site, by the Android tests -
//! without building anything. These tests fail when the committed copy has
//! drifted from the code, and when an example in it would not pass its own
//! schema. Neither needs a database.

use std::path::PathBuf;

use serde_json::{Value, json};

const REGENERATE: &str = "cargo run -p ingest -- openapi > docs/api/openapi.json";

fn committed_path() -> PathBuf {
    PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../docs/api/openapi.json")
}

fn generated() -> Value {
    serde_json::to_value(ingest::openapi()).expect("the OpenAPI document should serialise")
}

#[test]
fn the_committed_openapi_document_should_match_the_code() {
    let path = committed_path();

    let committed = std::fs::read_to_string(&path).unwrap_or_else(|error| {
        panic!(
            "could not read {}: {error}\n\nGenerate it from the repository root with:\n\n  {REGENERATE}\n",
            path.display()
        )
    });

    let committed: Value =
        serde_json::from_str(&committed).expect("the committed document should be valid JSON");

    /*
     * Compared as values rather than text, so the check is about what the
     * document says and not about whitespace or a trailing newline.
     */
    assert!(
        committed == generated(),
        "docs/api/openapi.json no longer matches the routes and types in ingest.\n\n\
         Regenerate it from the repository root with:\n\n  {REGENERATE}\n\n\
         and commit the result."
    );
}

#[test]
fn every_schema_example_should_be_valid_against_its_own_schema() {
    let document = generated();

    let schemas = document["components"]["schemas"]
        .as_object()
        .expect("the document should declare component schemas");

    let mut checked = 0;

    for (name, schema) in schemas {
        let Some(examples) = schema.get("examples").and_then(Value::as_array) else {
            continue;
        };

        /*
         * The schema is validated in place, inside a root carrying every
         * component, so a `$ref` to another schema resolves exactly as it
         * would in the document.
         */
        let root = json!({
            "$schema": "https://json-schema.org/draft/2020-12/schema",
            "components": document["components"],
            "$ref": format!("#/components/schemas/{name}"),
        });

        let validator = jsonschema::draft202012::new(&root)
            .unwrap_or_else(|error| panic!("the schema for {name} should compile: {error}"));

        for example in examples {
            let errors: Vec<String> = validator
                .iter_errors(example)
                .map(|error| format!("{} at {}", error, error.instance_path()))
                .collect();

            assert!(
                errors.is_empty(),
                "an example for {name} does not match its schema:\n  {}",
                errors.join("\n  ")
            );

            checked += 1;
        }
    }

    assert!(checked > 0, "no schema carried an example to check");
}
