# KIVYA AI backend

FastAPI backend for KIVYA. It sends chat requests to the Google Gemini API.
The default model is gemini-2.5-flash. Change it with GEMINI_MODEL only after
checking that the selected model is available to the project's Free Tier.

## Configuration

Set these environment variables in the hosting platform's secret/configuration
manager. Never commit a .env file or put secrets in the Android APK.

- GEMINI_API_KEY: API key created in Google AI Studio. Store as a secret.
- KIVYA_API_TOKEN: a separate, long random token used by the Android app to
  authenticate with this backend. Store as a secret.
- GEMINI_MODEL: optional; defaults to gemini-2.5-flash.
- MAX_OUTPUT_TOKENS: optional; defaults to 600 and is capped at 1200.
- GEMINI_TIMEOUT_SECONDS: optional; defaults to 25 seconds.
- CORS_ORIGINS: optional comma-separated trusted web origins.

## Free-tier and cost safeguards

- Use a Google AI Studio project that is explicitly on the Gemini API Free Tier.
  Free-tier eligibility and per-model quotas vary by project and can change.
- Do not link a billing account to this project if the intention is to keep
  Gemini API inference free. If the free quota is exhausted, the endpoint
  returns HTTP 429 and does not switch to a paid model or paid provider.
- Review the current model availability, rate limits, and terms in Google AI
  Studio before enabling the key. Free-tier availability is not guaranteed.
- The Gemini API Free Tier may use submitted prompts and responses to improve
  Google's products. Do not send passwords, personal contact details, private
  messages, or other sensitive information to this endpoint.
- AWS hosting is separate from Gemini API usage and can still incur AWS charges.
  Do not deploy billable AWS resources until the account owner/guardian approves
  the service and its spending.

## Security

- Keep GEMINI_API_KEY and KIVYA_API_TOKEN in AWS Secrets Manager or the
  hosting platform's secret manager, never in source code, GitHub, logs, or APK.
- Use HTTPS and restrict access to the backend.
- Use a strong KIVYA_API_TOKEN; the Android app sends it as a Bearer token.
- Native Android is not governed by browser CORS.
- The endpoint only answers chat requests. It does not execute phone actions,
  access contacts, make calls, or run shell/GitHub/AWS operations.

## Run locally

1. Create a Python virtual environment.
2. Install dependencies: pip install -r requirements.txt.
3. Set GEMINI_API_KEY and KIVYA_API_TOKEN in your local environment.
4. Start from this directory:
   uvicorn main:app --host 127.0.0.1 --port 8000

Health check: GET /health

Chat: POST /v1/chat with JSON
{"message":"नमस्ते","language":"hi"} and header
Authorization: Bearer <your-token>.

The Android app still needs its API client and configured HTTPS backend URL
before AI Studio mode can call this endpoint.
