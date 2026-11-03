# KIVYA AI backend

FastAPI backend for KIVYA using Google's Gemini Interactions API, Gemini 3.8 Flash,
medium thinking, and the built-in Google Search tool.

## Configuration

Set these environment variables in the hosting platform's secret/configuration
manager. Never commit a .env file or put secrets in the Android APK.

- GEMINI_API_KEY: API key created in Google AI Studio. Store as a secret.
- KIVYA_API_TOKEN: a separate, long random token used by the Android app to
  authenticate with this backend. Store as a secret.
- GEMINI_MODEL: optional; defaults to gemini-3.8-flash.
- MAX_OUTPUT_TOKENS: optional; defaults to 1200 and is capped at 4096 to limit
  response size and unexpected usage.
- GEMINI_TIMEOUT_SECONDS: optional; defaults to 45 seconds.
- CORS_ORIGINS: optional comma-separated trusted web origins.

## Gemini capabilities

- Uses the Interactions API: `client.interactions.create(...)`.
- Enables live web grounding through `tools=[{"type": "google_search"}]`.
- Sets `thinking_level` to `medium`.
- Uses `interaction.output_text` for the final answer.
- Google Search is available to the model when useful; it is not forced for
  every request.

## Free-tier and cost safeguards

- Confirm the selected model and Google Search grounding are available to the
  Google AI Studio project and check its current quotas before use.
- Free-tier eligibility, model access, and search/tool quotas vary by project
  and can change. Do not assume all requests will be free.
- Do not link a billing account if the intention is to avoid paid Gemini API
  usage. If a quota is exhausted, the endpoint returns HTTP 429 and does not
  switch to a paid model or paid provider.
- Review the current model availability, rate limits, and terms in Google AI
  Studio before enabling the key.
- The Gemini API Free Tier may use submitted prompts and responses to improve
  Google's products. Do not send passwords, personal contact details, private
  messages, or other sensitive information to this endpoint.
- AWS hosting is separate from Gemini API usage and can still incur AWS charges.
  Do not deploy billable AWS resources until the account owner/guardian approves
  the service and its spending.

## Security

- Keep GEMINI_API_KEY and KIVYA_API_TOKEN in AWS Secrets Manager or the
  hosting platform's secret manager, never in source code, GitHub, logs, or APK.
- The previously pasted API key must be revoked and must not be reused.
- Use HTTPS and restrict access to the backend.
- Use a strong KIVYA_API_TOKEN; the Android app sends it as a Bearer token.
- Native Android is not governed by browser CORS.
- The endpoint only answers chat requests. It does not execute phone actions,
  access contacts, make calls, or run shell/GitHub/AWS operations.

## Run locally

1. Create a Python virtual environment.
2. Install dependencies: `pip install -r requirements.txt`.
3. Set GEMINI_API_KEY and KIVYA_API_TOKEN in your local environment.
4. Start from this directory:
   `uvicorn main:app --host 127.0.0.1 --port 8000`

Health check: GET /health

Chat: POST /v1/chat with JSON
`{"message":"नमस्ते","language":"hi"}` and header
`Authorization: Bearer <your-token>`.

The Android app still needs its API client and configured HTTPS backend URL
before AI Studio mode can call this endpoint.
