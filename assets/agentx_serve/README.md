# AgentX Serve App Assets

**Package:** `com.agentx.admin`  
**Module:** `:admin`  
**Role:** Android-hosted API server and administration interface.

This directory contains branding assets and may contain historical APK artifacts.

**Important:** The historical `v1.0.0` APK predates the inference safety repair. The current source does not execute local GGUF models. Catalog entries are discovery-only, model memory is not reported until measured, and local completion requests fail explicitly until a real runtime is connected.

Before exposing the server beyond a trusted development network, validate authentication on every protected endpoint, TLS, CORS, request limits, and tunnel behavior.

## Branding assets
- `ic_launcher_foreground.xml` — server stack vector.
- `ic_launcher_background.xml` — dark background vector.
