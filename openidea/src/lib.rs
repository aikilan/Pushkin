//! Format a commit-generation request without accessing credentials or the network.

use serde::{Deserialize, Serialize};

/// Bounds data passed across the plugin boundary before JSON parsing.
const MAX_ARGUMENT_BYTES: usize = 4 * 1024 * 1024;
/// Mirrors the existing Pushkin limit for the selected commit context.
const MAX_DIFF_CHARS: usize = 12_000;
const MAX_PROMPT_CHARS: usize = 64_000;
const MAX_MODEL_CHARS: usize = 512;
const MAX_MESSAGE_CHARS: usize = 16_000;

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct RequestInput {
    diff: String,
    model: String,
    temperature: f64,
    system_prompt: String,
}

#[derive(Serialize)]
struct ChatRequest<'a> {
    model: &'a str,
    temperature: f64,
    messages: [ChatMessage<'a>; 2],
}

#[derive(Serialize)]
struct ChatMessage<'a> {
    role: &'static str,
    content: &'a str,
}

#[derive(Deserialize)]
struct ChatResponse {
    choices: Vec<Choice>,
}

#[derive(Deserialize)]
struct Choice {
    message: ResponseMessage,
}

#[derive(Deserialize)]
struct ResponseMessage {
    content: Option<String>,
}

/// Convert selected diff and model settings into an OpenAI Chat Completions body.
/// API URLs and credentials remain in the host and never cross this boundary.
pub fn build_request(arg: &str) -> Result<String, String> {
    if arg.len() > MAX_ARGUMENT_BYTES {
        return Err("Commit generation input is too large".into());
    }
    let input: RequestInput =
        serde_json::from_str(arg).map_err(|_| "Invalid commit generation input")?;
    if input.diff.trim().is_empty() {
        return Err("Select at least one change before generating a commit message".into());
    }
    if input.diff.chars().count() > MAX_DIFF_CHARS {
        return Err("Selected commit context is too large".into());
    }
    if input.model.trim().is_empty() || input.model.chars().count() > MAX_MODEL_CHARS {
        return Err("Model must be a non-empty name of at most 512 characters".into());
    }
    if !(0.0..=2.0).contains(&input.temperature) {
        return Err("Temperature must be between 0 and 2".into());
    }
    if input.system_prompt.chars().count() > MAX_PROMPT_CHARS {
        return Err("System prompt is too long".into());
    }

    let request = ChatRequest {
        model: input.model.trim(),
        temperature: input.temperature,
        messages: [
            ChatMessage {
                role: "system",
                content: &input.system_prompt,
            },
            ChatMessage {
                role: "user",
                content: &input.diff,
            },
        ],
    };
    serde_json::to_string(&request).map_err(|_| "Could not encode commit generation request".into())
}

/// Extract a nonempty commit message from the first Chat Completions choice.
pub fn parse_response(arg: &str) -> Result<String, String> {
    if arg.len() > MAX_ARGUMENT_BYTES {
        return Err("Commit generation response is too large".into());
    }
    let response: ChatResponse =
        serde_json::from_str(arg).map_err(|_| "Invalid commit generation response")?;
    let message = response
        .choices
        .into_iter()
        .next()
        .and_then(|choice| choice.message.content)
        .unwrap_or_default();
    let message = message.trim();
    if message.is_empty() {
        return Err("Commit generation returned an empty message".into());
    }
    if message.chars().count() > MAX_MESSAGE_CHARS {
        return Err("Generated commit message is too long".into());
    }
    Ok(message.to_owned())
}

#[cfg(target_arch = "wasm32")]
mod component {
    use super::{build_request, parse_response};

    wit_bindgen::generate!({ world: "plugin", path: "wit" });

    struct Pushkin;

    impl Guest for Pushkin {
        fn activate(_ctx: Context) -> Result<(), String> {
            Ok(())
        }

        fn deactivate() {}

        /// Internal commands are invoked by the host with JSON arguments.
        fn run_command(id: String, arg: Option<String>) -> Result<Option<String>, String> {
            let arg = arg.ok_or("Missing plugin command input")?;
            let result = match id.as_str() {
                "build-request" => build_request(&arg),
                "parse-response" => parse_response(&arg),
                _ => Err("Unknown plugin command".into()),
            }?;
            Ok(Some(result))
        }
    }

    export!(Pushkin);
}
