#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"
rustup target add wasm32-wasip2
cargo build --locked --release --target wasm32-wasip2
mkdir -p dist
cp target/wasm32-wasip2/release/pushkin_openidea.wasm dist/plugin.wasm
printf 'OpenIDEA plugin package ready at %s\n' "$PWD"
