# Pushkin for OpenIDEA

This directory contains the OpenIDEA WebAssembly plugin. The JetBrains plugin
remains in `../src` and uses its existing Gradle build.

The plugin requires an OpenIDEA version that supports the `ai-completions`
permission and the commit message generator integration. A stock OpenIDEA
version without that permission cannot load this plugin.

## Build and install

Install Rust with `rustup`, then run:

```sh
cd openidea
./build.sh
```

The script installs the `wasm32-wasip2` target if needed and creates
`dist/plugin.wasm`. In a compatible OpenIDEA build, open **Settings → Plugins →
Install Plugin from Disk** and select the `openidea` directory containing
`plugin.toml`. Configure the model provider in OpenIDEA and use the generation
control in its Commit panel.

`wit/plugin.wit` is the OpenIDEA 0.1 plugin interface, copied from
[OpenIDEA](https://github.com/l0ng-ai/openidea/blob/c8cc69a56ef251bb41170f7b9ae747e86a4145ba/wit/plugin.wit)
under Apache-2.0; its license is included at `wit/LICENSE`.

The host manages a global system prompt and an optional project-specific
prompt. When a project prompt is configured, it is used for that project;
otherwise, the global prompt is used. The plugin uses the resolved prompt
supplied by the host and does not persist project settings.

## Security and data flow

The host provides the selected Git diff, model name, temperature, and system
prompt to the plugin. The plugin builds the Chat Completions request body and
parses the response; only the host handles the provider URL, API key, and HTTP
request. The host limits the selected diff to 12,000 Unicode characters before
the plugin sees it. The plugin sends that diff unchanged and rejects oversized
input.

## Tests

```sh
cargo test --locked
```
