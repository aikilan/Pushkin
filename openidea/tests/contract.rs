use pushkin_openidea::{build_request, parse_response};
use serde_json::{Value, json};

fn request(diff: &str) -> String {
    json!({ "diff": diff, "model": "example-model", "temperature": 0.2, "systemPrompt": "Use conventional commits" }).to_string()
}

#[test]
fn builds_chat_completion_body_from_the_selected_diff() {
    let body: Value =
        serde_json::from_str(&build_request(&request("diff --git a/a.ts b/a.ts\n+new")).unwrap())
            .unwrap();
    assert_eq!(body["model"], "example-model");
    assert_eq!(body["temperature"], json!(0.2));
    assert_eq!(
        body["messages"][0],
        json!({"role": "system", "content": "Use conventional commits"})
    );
    assert_eq!(body["messages"][1]["role"], "user");
    assert_eq!(
        body["messages"][1]["content"],
        "diff --git a/a.ts b/a.ts\n+new"
    );
    assert_eq!(body.as_object().unwrap().len(), 3);
    assert!(body.get("apiKey").is_none());
    assert!(body.get("baseUrl").is_none());
}

#[test]
fn rejects_missing_or_invalid_request_fields() {
    for arg in [
        "invalid json".to_owned(),
        json!({"diff": "", "model": "m", "temperature": 0.2, "systemPrompt": "p"}).to_string(),
        json!({"diff": "change", "model": " ", "temperature": 0.2, "systemPrompt": "p"})
            .to_string(),
        json!({"diff": "change", "model": "m", "temperature": 2.1, "systemPrompt": "p"})
            .to_string(),
        json!({"diff": "change", "model": "m", "temperature": -0.1, "systemPrompt": "p"})
            .to_string(),
    ] {
        assert!(build_request(&arg).is_err(), "unexpected success for {arg}");
    }
}

#[test]
fn accepts_bounded_unicode_diff_without_changing_it() {
    let diff = "变".repeat(12_000);
    let body: Value = serde_json::from_str(&build_request(&request(&diff)).unwrap()).unwrap();
    assert_eq!(body["messages"][1]["content"], diff);
    assert!(build_request(&request(&"变".repeat(12_001))).is_err());
}

#[test]
fn parses_and_trims_first_completion_message() {
    let response = json!({ "choices": [{"message": {"role": "assistant", "content": "  feat: add search\n\n"}}, {"message": {"content": "ignored"}}] });
    assert_eq!(
        parse_response(&response.to_string()).unwrap(),
        "feat: add search"
    );
}

#[test]
fn rejects_malformed_or_empty_completions() {
    for raw in [
        "not json",
        "{}",
        r#"{"choices":[]}"#,
        r#"{"choices":[{"message":{"content":"  "}}]}"#,
        r#"{"choices":[{"message":{"content":null}}]}"#,
    ] {
        assert!(parse_response(raw).is_err(), "unexpected success for {raw}");
    }
}

#[test]
fn bounds_inputs_and_generated_message_without_leaking_response_content() {
    let too_large = "x".repeat(4 * 1024 * 1024 + 1);
    assert!(build_request(&too_large).is_err());
    assert!(parse_response(&too_large).is_err());
    let secret_marker = "unexpected-sensitive-response";
    let invalid =
        json!({"choices": [{"message": {"content": null}}], "debug": secret_marker}).to_string();
    let error = parse_response(&invalid).unwrap_err();
    assert!(!error.contains(secret_marker));
    let long = json!({"choices": [{"message": {"content": "x".repeat(16_001)}}]}).to_string();
    assert!(parse_response(&long).is_err());
}

#[test]
fn accepts_empty_system_prompt_and_temperature_boundaries() {
    for temperature in [0.0, 2.0] {
        let input = json!({"diff": "+change", "model": "model", "temperature": temperature, "systemPrompt": ""}).to_string();
        let body: Value = serde_json::from_str(&build_request(&input).unwrap()).unwrap();
        assert_eq!(body["temperature"], json!(temperature));
        assert_eq!(body["messages"][0]["content"], "");
    }
}
